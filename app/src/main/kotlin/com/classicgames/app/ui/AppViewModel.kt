package com.classicgames.app.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.classicgames.app.data.ChatClient
import com.classicgames.app.data.NetworkWatcher
import com.classicgames.app.data.SavedSession
import com.classicgames.app.data.SessionEnd
import com.classicgames.app.data.Settings
import com.classicgames.app.data.SettingsStore
import com.classicgames.app.server.PhoneServer
import com.classicgames.app.server.PhoneServerState
import com.classicgames.core.BattleState
import com.classicgames.core.BriscaState
import com.classicgames.core.CheckersState
import com.classicgames.core.ClientMessage
import com.classicgames.core.ConnectState
import com.classicgames.core.DotsState
import com.classicgames.core.GameState
import com.classicgames.core.HexState
import com.classicgames.core.MastermindState
import com.classicgames.core.MazeState
import com.classicgames.core.MemoryState
import com.classicgames.core.OthelloState
import com.classicgames.core.OwareState
import com.classicgames.core.QuartoState
import com.classicgames.core.ServerMessage
import com.classicgames.core.ShipPlacement
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Connecting: first connection attempt. Connected: in the room. Reconnecting: the connection dropped and we are
 * trying to take our seat back. Closed: it is over (refused, seat expired, gave up); only Leave is left to do.
 */
enum class ConnectionState { Connecting, Connected, Reconnecting, Closed }

data class ChatLine(
    val from: String,
    val text: String,
    val system: Boolean = false,
    val mine: Boolean = false,
)

/** Everything the chat screen needs. A non-null value means "we are in a room". */
data class RoomUi(
    val code: String,
    val state: ConnectionState = ConnectionState.Connecting,
    val lines: List<ChatLine> = emptyList(),
    val closeReason: String? = null,
    /** The tic-tac-toe game running in this room, if any. */
    val game: GameState? = null,
    /** The Battleship game running in this room, if any (as this player sees it). */
    val battle: BattleState? = null,
    /** The Connect Four game running in this room, if any. */
    val connect: ConnectState? = null,
    /** The Othello game running in this room, if any. */
    val othello: OthelloState? = null,
    /** The Checkers game running in this room, if any. */
    val checkers: CheckersState? = null,
    /** The Dots and Boxes game running in this room, if any. */
    val dots: DotsState? = null,
    /** The Oware game running in this room, if any. */
    val oware: OwareState? = null,
    /** The Mastermind game running in this room, if any (as this player sees it). */
    val mastermind: MastermindState? = null,
    /** The Hex game running in this room, if any. */
    val hex: HexState? = null,
    /** The Memory game running in this room, if any. */
    val memory: MemoryState? = null,
    /** The Quarto game running in this room, if any. */
    val quarto: QuartoState? = null,
    /** The Brisca game running in this room, if any (as this player sees it). */
    val brisca: BriscaState? = null,
    /** The Maze Guide game running in this room, if any (as this player sees it). */
    val maze: MazeState? = null,
    /** The game whose tile was tapped in the lobby to host this room; the room offers it first. */
    val preferredGame: String? = null,
    /** The server's last rejection (e.g. "Not your turn"); cleared by the next game update. */
    val notice: String? = null,
    /** Name of the other player while their connection is down (the server holds their seat for a while). */
    val opponentOffline: String? = null,
    /** Version of the last "game" message applied; an older one that arrives late is ignored. */
    val gameVersion: Long = 0,
)

/** Waits between reconnect attempts; the last value repeats. A network coming back skips the wait. */
private val BACKOFF_MS = longArrayOf(1_000, 2_000, 3_000, 5_000)

/** After this long without managing to reconnect we stop trying (the server gave the seat up long before). */
private const val GIVE_UP_AFTER_MS = 3 * 60_000L

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val client = ChatClient()

    /** null while the saved settings are still loading. */
    val settings: StateFlow<Settings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _room = MutableStateFlow<RoomUi?>(null)
    val room: StateFlow<RoomUi?> = _room.asStateFlow()

    var lobbyError by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /**
     * One player's presence in one room. It lives on across dropped connections: [runSession] keeps reconnecting
     * with the same [token] until the player leaves, the server refuses, or it gives up.
     */
    private class ActiveSession(val server: String, val name: String, val room: String, var token: String?) {
        val outgoing = Channel<ClientMessage>(Channel.UNLIMITED)
        @Volatile var left = false
        @Volatile var sawSession = false
        var job: Job? = null
    }

    private var active: ActiveSession? = null

    /** Wakes a reconnect that is waiting between attempts. */
    private val nudge = Channel<Unit>(Channel.CONFLATED)
    private val network = NetworkWatcher(app) { nudge.trySend(Unit) }

    /** The game server running inside this phone (when "Host the server on this phone" is on). */
    val phoneServer: StateFlow<PhoneServerState> = PhoneServer.state

    init {
        network.start()
        resumeSavedSession()

        // If this phone was hosting the server when the app was last used, start it again.
        viewModelScope.launch {
            val saved = settings.filterNotNull().first()
            if (saved.hostOnPhone) PhoneServer.start(getApplication())
        }
        // The server can also be stopped from its notification: then the switch must turn itself off.
        viewModelScope.launch {
            var previous: PhoneServerState = PhoneServer.state.value
            PhoneServer.state.collect { now ->
                if (previous is PhoneServerState.Running && now is PhoneServerState.Stopped &&
                    settings.value?.hostOnPhone == true
                ) {
                    store.setHostOnPhone(false)
                }
                previous = now
            }
        }
    }

    fun saveSettings(name: String, server: String) {
        viewModelScope.launch { store.save(name.trim(), server.trim()) }
    }

    /** Turns "Host the server on this phone" on or off, starting or stopping the server service. */
    fun setHostOnPhone(enabled: Boolean) {
        viewModelScope.launch {
            lobbyError = null
            store.setHostOnPhone(enabled)
            if (enabled) PhoneServer.start(getApplication()) else PhoneServer.stop(getApplication())
        }
    }

    /** Called when the app comes to the foreground: a good moment to retry a broken connection right away. */
    fun onForeground() {
        nudge.trySend(Unit)
    }

    /**
     * Creates a new room on the server (random code) and joins it as host.
     * [gameId] is the game whose tile was tapped, if any; the room then offers that game first.
     */
    fun hostRoom(gameId: String? = null) {
        val s = settings.value ?: return
        viewModelScope.launch {
            busy = true
            lobbyError = null
            try {
                joinRoom(client.newRoomCode(s.serverToUse), gameId)
            } catch (e: Exception) {
                lobbyError = "Can't reach the server: ${e.message ?: e::class.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    fun joinRoom(code: String, preferredGame: String? = null) {
        val s = settings.value ?: return
        val roomCode = code.trim().uppercase()
        if (roomCode.isEmpty()) {
            lobbyError = "Enter a room code"
            return
        }
        lobbyError = null
        leaveRoom()
        begin(ActiveSession(s.serverToUse, s.name, roomCode, token = null), RoomUi(roomCode, preferredGame = preferredGame))
    }

    /** At launch: if the app was closed or killed while in a room, take the seat back. */
    private fun resumeSavedSession() {
        viewModelScope.launch {
            val saved = store.savedSession.first() ?: return@launch
            if (active != null) return@launch
            begin(
                ActiveSession(saved.server, saved.name, saved.room, saved.token),
                RoomUi(saved.room, state = ConnectionState.Reconnecting),
            )
        }
    }

    private fun begin(session: ActiveSession, room: RoomUi) {
        active = session
        _room.value = room
        session.job = viewModelScope.launch { runSession(session) }
    }

    /** Connects, and keeps reconnecting while that makes sense. Returns when the session is over for good. */
    private suspend fun runSession(session: ActiveSession) {
        var attempts = 0
        var downSince = 0L
        while (!session.left) {
            session.sawSession = false
            val end = client.runSession(
                session.server, session.name, session.room, session.token, session.outgoing,
            ) { message -> onMessage(session, message) }
            if (session.left) return

            val now = SystemClock.elapsedRealtime()
            if (session.sawSession) { // we were in and just got cut off: the outage starts now
                attempts = 0
                downSince = now
            }
            when (end) {
                is SessionEnd.Left -> return
                is SessionEnd.Rejected -> {
                    giveUp(session, end.reason)
                    return
                }
                is SessionEnd.Dropped -> {
                    if (session.token == null) { // never got in: tell the user right away
                        giveUp(session, end.reason ?: "Couldn't connect")
                        return
                    }
                    if (downSince == 0L) downSince = now
                    if (now - downSince > GIVE_UP_AFTER_MS) {
                        giveUp(session, "Couldn't reconnect. Check your connection and try again.")
                        return
                    }
                    if (active === session) {
                        _room.update { it?.copy(state = ConnectionState.Reconnecting) }
                    }
                    withTimeoutOrNull(BACKOFF_MS[minOf(attempts++, BACKOFF_MS.lastIndex)]) { nudge.receive() }
                }
            }
        }
    }

    /** Called (on a background thread) for every message the server sends. */
    private fun onMessage(session: ActiveSession, msg: ServerMessage) {
        if (active !== session) return // we have left that room already
        if (msg.type == "session") {
            session.sawSession = true
            val token = msg.token ?: return
            if (token != session.token) {
                session.token = token
                // Remember the seat so the app can rejoin it after being closed or killed.
                viewModelScope.launch { store.saveSession(SavedSession(session.server, session.name, session.room, token)) }
            }
            _room.update { it?.copy(state = ConnectionState.Connected, closeReason = null, opponentOffline = null) }
            return
        }
        _room.update { current -> current?.let { applyMessage(it, msg, session.name) } }
    }

    private fun applyMessage(room: RoomUi, msg: ServerMessage, myName: String): RoomUi = when (msg.type) {
        "history" -> room.copy(
            lines = msg.history.orEmpty().map { h ->
                ChatLine(from = h.from, text = h.text, system = h.type == "system", mine = h.type == "chat" && h.from == myName)
            },
        )
        "presence" -> when {
            msg.from == myName -> room
            msg.text == "offline" -> room.copy(opponentOffline = msg.from)
            else -> room.copy(opponentOffline = null)
        }
        "game" -> if (msg.version < room.gameVersion) room else room.copy(
            gameVersion = msg.version,
            game = msg.state,
            battle = msg.battle,
            connect = msg.connect,
            othello = msg.othello,
            checkers = msg.checkers,
            dots = msg.dots,
            oware = msg.oware,
            mastermind = msg.mastermind,
            hex = msg.hex,
            memory = msg.memory,
            quarto = msg.quarto,
            brisca = msg.brisca,
            maze = msg.maze,
            notice = null,
            // No game at all means it was cancelled (the other player left): say so in the chat.
            lines = if (msg.state == null && msg.battle == null && msg.connect == null &&
                msg.othello == null && msg.checkers == null && msg.dots == null && msg.oware == null &&
                msg.mastermind == null && msg.hex == null && msg.memory == null && msg.quarto == null &&
                msg.brisca == null && msg.maze == null
            ) {
                room.lines + ChatLine("server", msg.text, system = true)
            } else {
                room.lines
            },
        )
        "error" -> room.copy(notice = msg.text)
        else -> room.copy(lines = room.lines + ChatLine(msg.from, msg.text, system = msg.type == "system"))
    }

    /** The session is over for good: show why, forget the saved seat. */
    private fun giveUp(session: ActiveSession, reason: String?) {
        if (active !== session) return
        _room.update { it?.copy(state = ConnectionState.Closed, closeReason = friendly(reason), opponentOffline = null) }
        viewModelScope.launch { store.clearSession() }
    }

    private fun friendly(reason: String?): String = when (reason) {
        null -> "Connection lost"
        "Session expired" -> "This room is gone: the other player left, or you were away for too long."
        else -> reason
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        val me = settings.value?.name ?: return
        if (trimmed.isEmpty() || _room.value?.state != ConnectionState.Connected) return
        active?.outgoing?.trySend(ClientMessage(type = "chat", text = trimmed))
        // The server only relays to the *other* player, so show our own message locally.
        _room.update { it?.copy(lines = it.lines + ChatLine(me, trimmed, mine = true)) }
    }

    /** Asks the server to start [gameId] in this room (it checks that two players are present). */
    fun startGame(gameId: String) {
        if (_room.value?.state != ConnectionState.Connected) return
        _room.update { it?.copy(notice = null) }
        active?.outgoing?.trySend(ClientMessage(type = "startGame", game = gameId))
    }

    /**
     * Plays [cell]. Tic-tac-toe: 0..8, row by row. Battleship: 0..99, the cell to fire at.
     * Connect Four: the column (0..6) to drop a disc into. Othello: 0..63, row by row.
     * Dots and Boxes: the line to draw (numbered as described on [ClientMessage]).
     * Oware: the pit to sow from (0..11 counter-clockwise, as described on [ClientMessage]).
     * Hex: the cell to put a stone on (row * size + column, as described on [ClientMessage]).
     * Memory: the card to flip (row * columns + column).
     * Quarto: the cell (0..15, row by row) to put the piece you were given on.
     * The server validates it and answers with the new state.
     */
    fun playCell(cell: Int) {
        if (_room.value?.state != ConnectionState.Connected) return
        active?.outgoing?.trySend(ClientMessage(type = "move", cell = cell))
    }

    /** Checkers: moves the piece on square [from] to square [to] (one step or one jump, squares 0..63). */
    fun movePiece(from: Int, to: Int) {
        if (_room.value?.state != ConnectionState.Connected) return
        active?.outgoing?.trySend(ClientMessage(type = "move", from = from, to = to))
    }

    /** Battleship: sends the whole fleet, one list of cells per ship. Can only be done once per game. */
    fun placeFleet(ships: List<List<Int>>) {
        if (_room.value?.state != ConnectionState.Connected) return
        _room.update { it?.copy(notice = null) }
        active?.outgoing?.trySend(ClientMessage(type = "place", ships = ships.map { ShipPlacement(it) }))
    }

    /** Mastermind: sets the code the opponent has to crack (one colour 0..5 per peg). Can only be done once per game. */
    fun setSecretCode(code: List<Int>) {
        if (_room.value?.state != ConnectionState.Connected) return
        _room.update { it?.copy(notice = null) }
        active?.outgoing?.trySend(ClientMessage(type = "secret", code = code))
    }

    /** Mastermind: guesses the opponent's code (one colour 0..5 per peg). */
    fun guessCode(code: List<Int>) {
        if (_room.value?.state != ConnectionState.Connected) return
        active?.outgoing?.trySend(ClientMessage(type = "move", code = code))
    }

    /** Quarto: hands [piece] (0..15, one bit per trait, see QuartoState) from the pool to the opponent. */
    fun givePiece(piece: Int) {
        if (_room.value?.state != ConnectionState.Connected) return
        active?.outgoing?.trySend(ClientMessage(type = "move", piece = piece))
    }

    /** Brisca: plays [card] (0..39, suit * 10 + rank, see BriscaState) from your hand. */
    fun playCard(card: Int) {
        if (_room.value?.state != ConnectionState.Connected) return
        active?.outgoing?.trySend(ClientMessage(type = "move", card = card))
    }

    /** Brisca: swaps the 7 of trumps in your hand for the face-up trump, before playing your card. */
    fun swapSeven() {
        if (_room.value?.state != ConnectionState.Connected) return
        active?.outgoing?.trySend(ClientMessage(type = "swap7"))
    }

    /** Maze Guide, as the walker: steps one cell "up", "down", "left" or "right". */
    fun moveWalker(direction: String) {
        if (_room.value?.state != ConnectionState.Connected) return
        active?.outgoing?.trySend(ClientMessage(type = "move", direction = direction))
    }

    /** Maze Guide, as the guide: tells the walker to go "up", "down", "left" or "right". */
    fun sendHint(direction: String) {
        if (_room.value?.state != ConnectionState.Connected) return
        active?.outgoing?.trySend(ClientMessage(type = "hint", direction = direction))
    }

    /** Hex: instead of placing a stone on your first turn as the second player, take over the first stone. */
    fun swapHex() {
        if (_room.value?.state != ConnectionState.Connected) return
        active?.outgoing?.trySend(ClientMessage(type = "swap"))
    }

    /** Leaves the room for good: the seat is freed on the server, and the saved seat is forgotten. */
    fun leaveRoom() {
        val session = active
        active = null
        _room.value = null
        viewModelScope.launch { store.clearSession() }
        if (session == null) return

        session.left = true
        session.outgoing.trySend(ClientMessage(type = "leave"))
        session.outgoing.close()
        // The server closes the connection once it sees "leave"; make sure it is gone even if that message is lost.
        viewModelScope.launch {
            delay(2_000)
            session.job?.cancel()
        }
    }

    override fun onCleared() {
        network.stop()
        // The app is going away, not the player: drop the connection without saying goodbye, so the seat is held
        // on the server and the app can take it back the next time it opens.
        active?.job?.cancel()
        client.close()
    }
}
