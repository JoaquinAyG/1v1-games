package com.classicgames.core

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.SecureRandom

const val MAX_PLAYERS_PER_ROOM = 2
const val CODE_LENGTH = 4

/** How long a player's seat (and the running game) is kept after their connection drops. */
const val DEFAULT_GRACE_MILLIS = 90_000L

private const val HISTORY_LIMIT = 100
private const val TOKEN_LENGTH = 16

/** Result of connecting to a room. */
sealed interface JoinOutcome {
    /**
     * You are in. [token] is the seat's secret. [resumed] is true when you took your old seat back;
     * [replaced] is the previous connection of that seat, if it was still open (the caller closes it).
     */
    data class Joined(val token: String, val resumed: Boolean, val replaced: WebSocketSession?) : JoinOutcome
    data class Rejected(val reason: String) : JoinOutcome
}

/**
 * Result of a game request: either a tailored message for each player in the room,
 * or a reason to tell only the requester.
 */
sealed interface GameOutcome {
    data class Update(val messages: Map<String, ServerMessage>) : GameOutcome
    data class Rejected(val reason: String) : GameOutcome
}

/** What a player who has just connected needs to catch up. */
class JoinSnapshot(
    val history: List<HistoryLine>,
    /** Other players that are offline right now. */
    val offlinePeers: List<String>,
    /** The running (or just finished) game from this player's point of view, if any. */
    val gameMessage: ServerMessage?,
)

/** A player's seat in a room. [session] is null while the player is offline (their seat is being held). */
private class Member(val name: String, val token: String, var session: WebSocketSession?) {
    var graceJob: Job? = null
}

private class Room(val code: String) {
    val members = LinkedHashMap<String, Member>()
    var game: Game? = null
    var gamesPlayed = 0

    /** Bumped on every change to [game], under the lock, and sent as the "game" messages' version. */
    var gameVersion = 0L

    val history = ArrayDeque<HistoryLine>()
}

/**
 * Tracks rooms, their players and their games. Messages never leave the room they were sent in.
 *
 * A connection that drops without saying goodbye does not lose its seat: the player is marked offline and the
 * seat, the room and the game are kept for [graceMillis]. Coming back with the seat's token (see [join]) takes the
 * seat over again, even if the old connection has not been noticed as dead yet. Only after the grace period, or
 * after an explicit [leave], is the player removed and the game cancelled.
 */
class Hub(private val scope: CoroutineScope, private val graceMillis: Long = DEFAULT_GRACE_MILLIS) {
    private val rooms = HashMap<String, Room>()
    private val lock = Any()
    private val random = SecureRandom()

    /**
     * Connects [session] to [roomCode] as [name].
     * Without a [token] this is a fresh join (a free name and a free seat are needed).
     * With a [token] it resumes the seat that issued it; a wrong or expired token is rejected.
     */
    fun join(roomCode: String, name: String, token: String?, session: WebSocketSession): JoinOutcome = synchronized(lock) {
        if (token != null) {
            val member = rooms[roomCode]?.members?.get(name)
            if (member == null || member.token != token) return JoinOutcome.Rejected("Session expired")
            val replaced = member.session
            member.session = session
            member.graceJob?.cancel()
            member.graceJob = null
            return JoinOutcome.Joined(member.token, resumed = true, replaced = replaced)
        }

        val room = rooms.getOrPut(roomCode) { Room(roomCode) }
        when {
            name in room.members -> JoinOutcome.Rejected("Name already in use in this room")
            room.members.size >= MAX_PLAYERS_PER_ROOM -> JoinOutcome.Rejected("Room is full")
            else -> {
                val newToken = newToken()
                room.members[name] = Member(name, newToken, session)
                JoinOutcome.Joined(newToken, resumed = false, replaced = null)
            }
        }.also { if (room.members.isEmpty()) rooms.remove(roomCode) }
    }

    /**
     * The connection [session] of [name] ended without a goodbye: take the player offline and hold the seat.
     * Returns false if [session] was no longer the player's current connection (it had been replaced), in which
     * case nothing changed.
     */
    fun disconnect(roomCode: String, name: String, session: WebSocketSession): Boolean = synchronized(lock) {
        val member = rooms[roomCode]?.members?.get(name) ?: return false
        if (member.session !== session) return false
        member.session = null
        member.graceJob?.cancel()
        member.graceJob = scope.launch {
            delay(graceMillis)
            expire(roomCode, name, member)
        }
        true
    }

    /** The player said goodbye for good: free the seat now, cancel their game, and tell the others. */
    suspend fun leave(roomCode: String, name: String, session: WebSocketSession) {
        val cancellation = synchronized(lock) {
            val room = rooms[roomCode] ?: return
            val member = room.members[name] ?: return
            if (member.session !== session) return
            member.graceJob?.cancel()
            remove(room, name)
        }
        announceDeparture(roomCode, name, cancellation)
    }

    /** The grace period of an offline [member] ran out. */
    private suspend fun expire(roomCode: String, name: String, member: Member) {
        val cancellation = synchronized(lock) {
            val room = rooms[roomCode] ?: return
            if (room.members[name] !== member || member.session != null) return
            remove(room, name)
        }
        announceDeparture(roomCode, name, cancellation)
    }

    /**
     * Removes the player. If an unfinished game is cancelled because of it, returns the "game" message that tells
     * the others; a finished game is dropped quietly, so the remaining player keeps its final board on screen.
     * Call under the lock.
     */
    private fun remove(room: Room, name: String): ServerMessage? {
        room.members.remove(name)
        val cancelled = room.game?.finished == false
        room.game = null
        if (room.members.isEmpty()) rooms.remove(room.code)
        if (!cancelled) return null
        return ServerMessage("game", "server", "Game ended because $name left", version = ++room.gameVersion)
    }

    private suspend fun announceDeparture(roomCode: String, name: String, cancellation: ServerMessage?) {
        say(roomCode, ServerMessage("system", "server", "$name left"))
        cancellation?.let { deliverToAll(roomCode, it) }
    }

    /** Random code that no live room is using. Letters/digits without look-alikes (no 0/O, 1/I/L). */
    fun newRoomCode(): String = synchronized(lock) {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        while (true) {
            val code = (1..CODE_LENGTH).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
            if (code !in rooms) return code
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    private fun newToken(): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        return (1..TOKEN_LENGTH).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    /** Everything [name] needs to catch up right after connecting (null if they are not in the room). */
    fun snapshotFor(roomCode: String, name: String): JoinSnapshot? = synchronized(lock) {
        val room = rooms[roomCode]?.takeIf { name in it.members } ?: return null
        JoinSnapshot(
            history = room.history.toList(),
            offlinePeers = room.members.values.filter { it.name != name && it.session == null }.map { it.name },
            gameMessage = room.game?.messageFor(name)?.copy(version = room.gameVersion),
        )
    }

    /** Starts a new game in the room. Allowed when two players are present and no game is running. */
    fun startGame(roomCode: String, gameId: String, by: String): GameOutcome = synchronized(lock) {
        val room = rooms[roomCode]?.takeIf { by in it.members }
            ?: return GameOutcome.Rejected("You are not in a room")
        if (gameId !in KNOWN_GAMES) return GameOutcome.Rejected("Unknown game: $gameId")
        if (room.members.size < 2) return GameOutcome.Rejected("Wait for a second player to join")
        if (room.game?.finished == false) return GameOutcome.Rejected("A game is already in progress")

        // Players take turns going first (X in tic-tac-toe, the first shot in Battleship, red in Connect Four,
        // black in Othello, red in Checkers, red in Dots and Boxes, south in Oware, the first guess in Mastermind,
        // red in Hex, the first flip in Memory, the first choice of a piece in Quarto).
        val names = room.members.keys.toList()
        val first = names[room.gamesPlayed % 2]
        val second = names[(room.gamesPlayed + 1) % 2]
        val created: Game = when (gameId) {
            TicTacToe.ID -> TicTacToe(first, second)
            Battleship.ID -> Battleship(first, second)
            ConnectFour.ID -> ConnectFour(first, second)
            Othello.ID -> Othello(first, second)
            DotsAndBoxes.ID -> DotsAndBoxes(first, second)
            Oware.ID -> Oware(first, second)
            Mastermind.ID -> Mastermind(first, second)
            Hex.ID -> Hex(first, second)
            Memory.ID -> Memory(first, second)
            Quarto.ID -> Quarto(first, second)
            else -> Checkers(first, second)
        }
        room.gamesPlayed++
        room.game = created
        GameOutcome.Update(messagesFor(room, created))
    }

    /** Hands a game-specific message ("move", "place", ...) to the running game. */
    fun act(roomCode: String, by: String, msg: ClientMessage): GameOutcome = synchronized(lock) {
        val room = rooms[roomCode]?.takeIf { by in it.members }
            ?: return GameOutcome.Rejected("You are not in a room")
        val game = room.game ?: return GameOutcome.Rejected("No game in progress")
        game.handle(by, msg)?.let { return GameOutcome.Rejected(it) }
        GameOutcome.Update(messagesFor(room, game))
    }

    /**
     * Each player's view of [game] after a change, stamped with the room's next game version. Call under the lock:
     * the version order is then the order of the changes, however the messages overtake each other on the way out.
     */
    private fun messagesFor(room: Room, game: Game): Map<String, ServerMessage> {
        val version = ++room.gameVersion
        return room.members.keys.associateWith { game.messageFor(it).copy(version = version) }
    }

    /**
     * Sends each player in the room their own message. Offline players are skipped (they catch up on return).
     * Runs outside the lock, so two updates sent at once may arrive in either order; see [ServerMessage.version].
     */
    suspend fun deliver(roomCode: String, messages: Map<String, ServerMessage>) {
        val targets = synchronized(lock) {
            rooms[roomCode]?.members?.mapValues { it.value.session }.orEmpty()
        }
        for ((name, msg) in messages) {
            val session = targets[name] ?: continue
            runCatching { session.send(msg.toFrame()) }
        }
    }

    /**
     * Shows a chat or system line to the room, optionally skipping [except] (the sender), and remembers it so a
     * player who reconnects later still sees it.
     */
    suspend fun say(roomCode: String, msg: ServerMessage, except: String? = null) {
        val targets = synchronized(lock) {
            val room = rooms[roomCode] ?: return
            room.history.addLast(HistoryLine(msg.type, msg.from, msg.text, msg.time))
            while (room.history.size > HISTORY_LIMIT) room.history.removeFirst()
            room.members.values.filter { it.name != except }.mapNotNull { it.session }
        }
        // A Frame wraps a mutable buffer that gets consumed when read, so never share one between sessions.
        for (session in targets) runCatching { session.send(msg.toFrame()) }
    }

    /** Tells everyone except [name] that [name] went offline or came back. */
    suspend fun announcePresence(roomCode: String, name: String, online: Boolean) {
        val targets = synchronized(lock) {
            rooms[roomCode]?.members?.values?.filter { it.name != name }?.mapNotNull { it.session }.orEmpty()
        }
        for (session in targets) {
            runCatching { session.send(ServerMessage("presence", name, if (online) "online" else "offline").toFrame()) }
        }
    }

    /** Sends [msg] to every connected player in the room (nothing is remembered). */
    private suspend fun deliverToAll(roomCode: String, msg: ServerMessage) {
        val targets = synchronized(lock) {
            rooms[roomCode]?.members?.values?.mapNotNull { it.session }.orEmpty()
        }
        for (session in targets) runCatching { session.send(msg.toFrame()) }
    }
}

/** Ids a client may send in "startGame". */
private val KNOWN_GAMES = setOf(
    TicTacToe.ID, Battleship.ID, ConnectFour.ID, Othello.ID, Checkers.ID, DotsAndBoxes.ID, Oware.ID, Mastermind.ID,
    Hex.ID, Memory.ID, Quarto.ID,
)
