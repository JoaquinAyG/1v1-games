package com.classicgames.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.classicgames.app.data.BattleState
import com.classicgames.app.data.ChatClient
import com.classicgames.app.data.ChatEvent
import com.classicgames.app.data.CheckersState
import com.classicgames.app.data.ClientMessage
import com.classicgames.app.data.ConnectState
import com.classicgames.app.data.GameState
import com.classicgames.app.data.OthelloState
import com.classicgames.app.data.Settings
import com.classicgames.app.data.SettingsStore
import com.classicgames.app.data.ShipPlacement
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionState { Connecting, Connected, Closed }

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
    /** The game whose tile was tapped in the lobby to host this room; the room offers it first. */
    val preferredGame: String? = null,
    /** The server's last rejection (e.g. "Not your turn"); cleared by the next game update. */
    val notice: String? = null,
)

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

    private var session: Job? = null
    private var outgoing: Channel<ClientMessage>? = null

    fun saveSettings(name: String, server: String) {
        viewModelScope.launch { store.save(name.trim(), server.trim()) }
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
                joinRoom(client.newRoomCode(s.server), gameId)
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

        val channel = Channel<ClientMessage>(Channel.UNLIMITED)
        outgoing = channel
        _room.value = RoomUi(roomCode, preferredGame = preferredGame)
        session = viewModelScope.launch {
            client.runSession(s.server, s.name, roomCode, channel) { event ->
                _room.update { current -> current?.let { applyEvent(it, event) } }
            }
        }
    }

    private fun applyEvent(room: RoomUi, event: ChatEvent): RoomUi = when (event) {
        is ChatEvent.Received -> {
            val msg = event.message
            // The server's first message ("Joined room ...") confirms we are really in.
            val connected = room.copy(state = ConnectionState.Connected)
            when (msg.type) {
                "game" -> connected.copy(
                    game = msg.state,
                    battle = msg.battle,
                    connect = msg.connect,
                    othello = msg.othello,
                    checkers = msg.checkers,
                    notice = null,
                    // No game at all means it was cancelled (the other player left): say so in the chat.
                    lines = if (msg.state == null && msg.battle == null && msg.connect == null &&
                        msg.othello == null && msg.checkers == null
                    ) {
                        connected.lines + ChatLine("server", msg.text, system = true)
                    } else {
                        connected.lines
                    },
                )
                "error" -> connected.copy(notice = msg.text)
                else -> connected.copy(
                    lines = connected.lines + ChatLine(msg.from, msg.text, system = msg.type == "system"),
                )
            }
        }
        is ChatEvent.Closed -> room.copy(state = ConnectionState.Closed, closeReason = event.reason)
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        val me = settings.value?.name ?: return
        if (trimmed.isEmpty() || _room.value?.state != ConnectionState.Connected) return
        outgoing?.trySend(ClientMessage(type = "chat", text = trimmed))
        // The server only relays to the *other* player, so show our own message locally.
        _room.update { it?.copy(lines = it.lines + ChatLine(me, trimmed, mine = true)) }
    }

    /** Asks the server to start [gameId] in this room (it checks that two players are present). */
    fun startGame(gameId: String) {
        if (_room.value?.state != ConnectionState.Connected) return
        _room.update { it?.copy(notice = null) }
        outgoing?.trySend(ClientMessage(type = "startGame", game = gameId))
    }

    /**
     * Plays [cell]. Tic-tac-toe: 0..8, row by row. Battleship: 0..99, the cell to fire at.
     * Connect Four: the column (0..6) to drop a disc into. Othello: 0..63, row by row.
     * The server validates it and answers with the new state.
     */
    fun playCell(cell: Int) {
        if (_room.value?.state != ConnectionState.Connected) return
        outgoing?.trySend(ClientMessage(type = "move", cell = cell))
    }

    /** Checkers: moves the piece on square [from] to square [to] (one step or one jump, squares 0..63). */
    fun movePiece(from: Int, to: Int) {
        if (_room.value?.state != ConnectionState.Connected) return
        outgoing?.trySend(ClientMessage(type = "move", from = from, to = to))
    }

    /** Battleship: sends the whole fleet, one list of cells per ship. Can only be done once per game. */
    fun placeFleet(ships: List<List<Int>>) {
        if (_room.value?.state != ConnectionState.Connected) return
        _room.update { it?.copy(notice = null) }
        outgoing?.trySend(ClientMessage(type = "place", ships = ships.map { ShipPlacement(it) }))
    }

    fun leaveRoom() {
        session?.cancel()
        session = null
        outgoing?.close()
        outgoing = null
        _room.value = null
    }

    override fun onCleared() {
        leaveRoom()
        client.close()
    }
}
