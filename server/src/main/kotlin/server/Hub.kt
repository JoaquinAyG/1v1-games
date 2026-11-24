package server

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send

const val MAX_PLAYERS_PER_ROOM = 2
const val CODE_LENGTH = 4

enum class JoinResult { OK, ROOM_FULL, NAME_TAKEN }

/** Ids a client may send in "startGame". */
private val KNOWN_GAMES = setOf(TicTacToe.ID, Battleship.ID, ConnectFour.ID, Othello.ID, Checkers.ID)

/**
 * Result of a game request: either a tailored message for each player in the room,
 * or a reason to tell only the requester.
 */
sealed interface GameOutcome {
    data class Update(val messages: Map<String, ServerMessage>) : GameOutcome
    data class Rejected(val reason: String) : GameOutcome
}

private class Room(val code: String) {
    val members = LinkedHashMap<String, WebSocketSession>()
    var game: Game? = null
    var gamesPlayed = 0
}

/** Tracks rooms, their players and their games. Messages never leave the room they were sent in. */
class Hub {
    private val rooms = HashMap<String, Room>()
    private val lock = Any()
    private val random = java.security.SecureRandom()

    fun join(roomCode: String, name: String, session: WebSocketSession): JoinResult = synchronized(lock) {
        val room = rooms.getOrPut(roomCode) { Room(roomCode) }
        when {
            name in room.members -> JoinResult.NAME_TAKEN
            room.members.size >= MAX_PLAYERS_PER_ROOM -> JoinResult.ROOM_FULL
            else -> { room.members[name] = session; JoinResult.OK }
        }.also { if (room.members.isEmpty()) rooms.remove(roomCode) }
    }

    /** Removes the player. Returns true if a game was running and has been cancelled because of it. */
    fun leave(roomCode: String, name: String, session: WebSocketSession): Boolean = synchronized(lock) {
        val room = rooms[roomCode] ?: return false
        if (room.members[name] === session) room.members.remove(name) else return false
        val cancelled = room.game != null
        room.game = null
        if (room.members.isEmpty()) rooms.remove(roomCode)
        cancelled
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

    fun online(roomCode: String): List<String> = synchronized(lock) {
        rooms[roomCode]?.members?.keys?.toList().orEmpty()
    }

    /** Starts a new game in the room. Allowed when two players are present and no game is running. */
    fun startGame(roomCode: String, gameId: String, by: String): GameOutcome = synchronized(lock) {
        val room = rooms[roomCode]?.takeIf { by in it.members }
            ?: return GameOutcome.Rejected("You are not in a room")
        if (gameId !in KNOWN_GAMES) return GameOutcome.Rejected("Unknown game: $gameId")
        if (room.members.size < 2) return GameOutcome.Rejected("Wait for a second player to join")
        if (room.game?.finished == false) return GameOutcome.Rejected("A game is already in progress")

        // Players take turns going first (X in tic-tac-toe, the first shot in Battleship, red in Connect Four,
        // black in Othello, red in Checkers).
        val names = room.members.keys.toList()
        val first = names[room.gamesPlayed % 2]
        val second = names[(room.gamesPlayed + 1) % 2]
        val created: Game = when (gameId) {
            TicTacToe.ID -> TicTacToe(first, second)
            Battleship.ID -> Battleship(first, second)
            ConnectFour.ID -> ConnectFour(first, second)
            Othello.ID -> Othello(first, second)
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

    private fun messagesFor(room: Room, game: Game): Map<String, ServerMessage> =
        room.members.keys.associateWith { game.messageFor(it) }

    /** Sends each player in the room their own message. */
    suspend fun deliver(roomCode: String, messages: Map<String, ServerMessage>) {
        val targets = synchronized(lock) { rooms[roomCode]?.members?.toMap().orEmpty() }
        for ((name, msg) in messages) {
            val session = targets[name] ?: continue
            runCatching { session.send(msg.toFrame()) }
        }
    }

    /** Sends [msg] to everyone in the room, optionally skipping [except] (the sender). */
    suspend fun broadcast(roomCode: String, msg: ServerMessage, except: String? = null) {
        val targets = synchronized(lock) { rooms[roomCode]?.members?.toMap().orEmpty() }
        for ((name, session) in targets) {
            if (name == except) continue
            // A Frame wraps a mutable buffer that gets consumed when read, so never share one between sessions.
            runCatching { session.send(msg.toFrame()) }
        }
    }
}
