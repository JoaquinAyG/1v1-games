package com.classicgames.core

import io.ktor.websocket.Frame
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One Battleship ship: the board cells it covers (row * 10 + column), in a straight unbroken line. */
@Serializable
data class ShipPlacement(val cells: List<Int>)

/**
 * Sent by a client. [type] decides which other fields matter:
 *  - "chat":      [text]
 *  - "leave":     goodbye for good: the seat is freed at once (without it, a dropped connection keeps the seat
 *                 for a while so the player can come back)
 *  - "startGame": [game] ("tictactoe", "battleship", "connectfour", "othello", "checkers", "dotsandboxes" or
 *                 "oware")
 *  - "move":      [cell] (tic-tac-toe: 0..8 row by row; battleship: 0..99 row by row, the cell to fire at;
 *                 connect four: the column 0..6 to drop a disc into; othello: 0..63 row by row;
 *                 dots and boxes: the line to draw, see below; oware: the pit to sow from, see below),
 *                 or [from] and [to] (checkers: 0..63 row by row, one step or one jump)
 *  - "place":     [ships] (battleship: your whole fleet, once)
 * A frame that is not JSON at all is treated as a chat message with that text.
 *
 * Dots and Boxes lines, on a board of n x n boxes ([DotsState.size], 5 by default) with dot rows and dot
 * columns 0..n counted from the top-left, are numbered horizontal lines first, then vertical lines:
 *  - 0 until n * (n + 1): horizontal, row by row. Line row * n + column joins dot (row, column) to the dot on
 *    its right, for row 0..n and column 0 until n.
 *  - n * (n + 1) until 2 * n * (n + 1): vertical, row by row. Line n * (n + 1) + row * (n + 1) + column joins
 *    dot (row, column) to the dot below it, for row 0 until n and column 0..n.
 * So box (row, column) has the sides row * n + column (top), (row + 1) * n + column (bottom),
 * n * (n + 1) + row * (n + 1) + column (left) and that plus 1 (right). On the 5 x 5 board that is 30 horizontal
 * lines (0..29) and 30 vertical lines (30..59).
 *
 * Oware pits are numbered 0..11 counter-clockwise, the direction seeds are sown in. The first player (south)
 * owns 0..5, their row from left to right, and the second player (north) owns 6..11, their row from right to left
 * as south sees it:
 * ```
 *   11 10  9  8  7  6     north
 *    0  1  2  3  4  5     south
 * ```
 * A move must be one of your own pits that holds seeds and, when the opponent has none, gives them some: exactly
 * the pits in [OwareState.legalMoves].
 */
@Serializable
data class ClientMessage(
    val type: String = "chat",
    val text: String = "",
    val game: String? = null,
    val cell: Int? = null,
    val ships: List<ShipPlacement>? = null,
    val from: Int? = null,
    val to: Int? = null,
)

/**
 * Snapshot of a tic-tac-toe game, sent to both players after every change.
 * [board] has 9 characters, row by row: '.' empty, 'X' or 'O'.
 * [status] is "playing", "won" or "draw". [line] holds the 3 winning cells when someone won.
 */
@Serializable
data class GameState(
    val game: String,
    val board: String,
    val xPlayer: String,
    val oPlayer: String,
    val turn: String,
    val status: String,
    val winner: String? = null,
    val line: List<Int> = emptyList(),
)

/**
 * Snapshot of a Connect Four game, sent to both players after every change.
 * [board] has 42 characters, row by row from the TOP row (7 columns per row): '.' empty, 'R' red, 'Y' yellow.
 * [turn] is "R" or "Y". [status] is "playing", "won" or "draw". [line] holds the 4 winning cells when someone won.
 * [lastMove] is the cell of the disc dropped last (-1 before the first move).
 */
@Serializable
data class ConnectState(
    val game: String,
    val board: String,
    val redPlayer: String,
    val yellowPlayer: String,
    val turn: String,
    val status: String,
    val winner: String? = null,
    val line: List<Int> = emptyList(),
    val lastMove: Int = -1,
)

/** One checkers move: the piece on square [from] goes to square [to] (a single step or a single jump). */
@Serializable
data class CheckersMove(val from: Int, val to: Int)

/**
 * Snapshot of a Checkers game, sent to both players after every change.
 * [board] has 64 characters, row by row from the top-left: '.' empty, 'r' red man, 'R' red king,
 * 'b' black man, 'B' black king. [turn] is "R" (red) or "B" (black) and is the side to move.
 * [status] is "playing", "won" or "draw".
 * [legalMoves] lists every move the side to move may make; if a jump exists, only jumps are listed.
 * [chain] is the square of a piece that just captured and must keep jumping (-1 if none); while it is
 * set, [legalMoves] only contains that piece's jumps. [lastFrom] / [lastTo] are the squares of the last move.
 */
@Serializable
data class CheckersState(
    val game: String,
    val board: String,
    val redPlayer: String,
    val blackPlayer: String,
    val turn: String,
    val status: String,
    val winner: String? = null,
    val redCount: Int,
    val blackCount: Int,
    val legalMoves: List<CheckersMove> = emptyList(),
    val lastFrom: Int = -1,
    val lastTo: Int = -1,
    val chain: Int = -1,
)

/**
 * Snapshot of an Othello game, sent to both players after every change.
 * [board] has 64 characters, row by row from the top-left: '.' empty, 'B' black, 'W' white.
 * [turn] is "B" or "W". [status] is "playing", "won" or "draw".
 * [legalMoves] lists the cells the player whose turn it is may play (empty once the game is over).
 * [flipped] are the cells that flipped on the last move, and [passed] is the player who had to skip
 * their turn because they had no legal move (or null).
 */
@Serializable
data class OthelloState(
    val game: String,
    val board: String,
    val blackPlayer: String,
    val whitePlayer: String,
    val turn: String,
    val status: String,
    val winner: String? = null,
    val blackCount: Int,
    val whiteCount: Int,
    val legalMoves: List<Int> = emptyList(),
    val lastMove: Int = -1,
    val flipped: List<Int> = emptyList(),
    val passed: String? = null,
)

/**
 * Snapshot of a Dots and Boxes game, sent to both players after every change.
 * [size] is the number of boxes per side (n). [lines] has 2 * n * (n + 1) characters, one per line in the order
 * described on [ClientMessage]: '.' not drawn yet, 'R' or 'B' for the player (red or blue) who drew it.
 * [boxes] has n * n characters, row by row from the top-left: '.' open, 'R' or 'B' for the player who closed it.
 * [turn] is "R" or "B": the player to draw next, which stays the same after a line that closed a box.
 * [status] is "playing", "won" or "draw". [lastLine] is the line drawn last (-1 before the first move), and
 * [closed] lists the boxes that line completed (empty, one or two boxes).
 */
@Serializable
data class DotsState(
    val game: String,
    val size: Int,
    val lines: String,
    val boxes: String,
    val redPlayer: String,
    val bluePlayer: String,
    val turn: String,
    val status: String,
    val winner: String? = null,
    val redScore: Int,
    val blueScore: Int,
    val lastLine: Int = -1,
    val closed: List<Int> = emptyList(),
)

/**
 * Snapshot of an Oware game, sent to both players after every change.
 * [pits] holds the 12 seed counts, indexed by pit number as described on [ClientMessage]: south owns 0..5 and
 * north owns 6..11. [southCaptured] / [northCaptured] are the seeds each player has captured.
 * [turn] is "S" (south, who moved first) or "N" (north): the side to move. [status] is "playing", "won" or "draw".
 * [legalMoves] lists the pits the side to move may sow from (empty once the game is over); when the opponent has
 * no seeds it only has the moves that give them some. [lastPit] is the pit sown from last (-1 before the first
 * move), [capturedPits] are the pits that move captured from (now empty), and [event] says in a sentence what
 * happened (a capture, a grand slam that captured nothing, a player who must be fed, how the game ended), or is
 * empty.
 */
@Serializable
data class OwareState(
    val game: String,
    val pits: List<Int>,
    val southPlayer: String,
    val northPlayer: String,
    val southCaptured: Int,
    val northCaptured: Int,
    val turn: String,
    val status: String,
    val winner: String? = null,
    val legalMoves: List<Int> = emptyList(),
    val lastPit: Int = -1,
    val capturedPits: List<Int> = emptyList(),
    val event: String = "",
)

/**
 * A Battleship game as ONE player sees it; each player gets their own version, so the opponent's
 * ships are never revealed until the game is over. Boards are 100 characters, row by row.
 *  - [phase]: "placing" (players are setting up), "playing" or "finished".
 *  - [myBoard]: '.' water, 'S' my ship, 'H' my ship that was hit, 'M' a shot the opponent missed with.
 *  - [shots]: my shots at the opponent: '.' not fired, 'M' miss, 'H' hit, 'X' hit on a sunk ship,
 *    and (only once finished) 'S' for the opponent's ships I never found.
 *  - [myShipsLeft] / [enemyShipsLeft]: sizes of the ships still afloat.
 *  - [event]: a short sentence about what just happened, written for this player.
 */
@Serializable
data class BattleState(
    val phase: String,
    val you: String,
    val opponent: String,
    val youReady: Boolean,
    val opponentReady: Boolean,
    val yourTurn: Boolean,
    val myBoard: String,
    val shots: String,
    val myShipsLeft: List<Int> = emptyList(),
    val enemyShipsLeft: List<Int> = emptyList(),
    val winner: String? = null,
    val event: String = "",
)

/**
 * Sent by the server. [type] is:
 *  - "chat":   a player's message ([from], [text])
 *  - "system": a notice from the server (joins, leaves)
 *  - "game":   the current game: [state] for tic-tac-toe, [battle] for Battleship, [connect] for Connect Four,
 *              [othello] for Othello, [checkers] for Checkers, [dots] for Dots and Boxes or [oware] for Oware;
 *              all null means "no game any more".
 *              [version] grows with every change to the room's game. Two changes made at nearly the same time
 *              can arrive in the wrong order, so ignore a "game" message whose [version] is lower than the last
 *              one you applied (an equal one, as sent on reconnect, is the same state again).
 *  - "error":  a rejected request, sent only to the player who made it
 *
 * Connection handling (sent only to the player who just connected, right after they get in):
 *  - "session":  the first message. [token] identifies this player's seat; keep it and send it as `?token=` to take
 *                the same seat back after a dropped connection (see Hub for the grace period).
 *  - "history":  the room's recent chat and system lines in [history], oldest first. Replace what you show.
 *  - and then, if a game is running, its current "game" message, plus a "presence" message for every player
 *    who is currently offline.
 * To everyone else in the room:
 *  - "presence": [from] went "offline" or came back "online" (in [text]).
 */
@Serializable
data class ServerMessage(
    val type: String,
    val from: String,
    val text: String,
    val time: Long = System.currentTimeMillis(),
    val state: GameState? = null,
    val battle: BattleState? = null,
    val connect: ConnectState? = null,
    val othello: OthelloState? = null,
    val checkers: CheckersState? = null,
    val dots: DotsState? = null,
    val oware: OwareState? = null,
    val token: String? = null,
    val history: List<HistoryLine>? = null,
    val version: Long = 0,
)

/** One chat or system line from the room's recent history. [type] is "chat" or "system". */
@Serializable
data class HistoryLine(val type: String, val from: String, val text: String, val time: Long)

val protocolJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun ServerMessage.toFrame() = Frame.Text(protocolJson.encodeToString(ServerMessage.serializer(), this))
