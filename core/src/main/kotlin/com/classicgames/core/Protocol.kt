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
 *  - "startGame": [game] ("tictactoe", "battleship", "connectfour", "othello", "checkers", "dotsandboxes",
 *                 "oware", "mastermind", "hex" or "memory")
 *  - "move":      [cell] (tic-tac-toe: 0..8 row by row; battleship: 0..99 row by row, the cell to fire at;
 *                 connect four: the column 0..6 to drop a disc into; othello: 0..63 row by row;
 *                 dots and boxes: the line to draw, see below; oware: the pit to sow from, see below;
 *                 hex: the cell to put a stone on, see below; memory: the card to flip, 0 until columns * rows
 *                 row by row),
 *                 or [from] and [to] (checkers: 0..63 row by row, one step or one jump),
 *                 or [code] (mastermind: your guess at the opponent's code)
 *  - "place":     [ships] (battleship: your whole fleet, once)
 *  - "secret":    [code] (mastermind: the code your opponent has to crack, once)
 *  - "swap":      nothing else (hex: the second player takes over the first stone instead of placing one, see below)
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
 *
 * A Mastermind code is a list of [MastermindState.pegs] colours (4 by default), from left to right, each colour a
 * number 0 until [MastermindState.colors] (6 by default). Repeats are allowed.
 *
 * Hex cells, on a board of n x n ([HexState.size], 7 by default), are numbered row by row from the top-left:
 * cell = row * n + column. Every row sits half a cell further right than the one above it, so the board is a rhombus
 * and cell (row, column) touches (row - 1, column), (row - 1, column + 1), (row, column - 1), (row, column + 1),
 * (row + 1, column - 1) and (row + 1, column). On a 3 x 3 board:
 * ```
 *    0   1   2
 *      3   4   5
 *        6   7   8
 * ```
 * Red (the first player) joins the top row to the bottom row, blue joins the left column to the right column.
 * Swap: on blue's first turn ([HexState.canSwap]), blue may send "swap" instead of a move. Red's only stone is then
 * removed and a blue stone is put on its mirror image across the long diagonal, from the top-left corner to the
 * bottom-right one: cell (row, column) becomes (column, row). The mirror turns top-bottom into left-right, so blue
 * gets exactly the position red had made, and red moves next. Nobody changes colour or sides.
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
    val code: List<Int>? = null,
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

/** One Mastermind guess: the [code] guessed and its score. */
@Serializable
data class MastermindGuess(
    val code: List<Int>,
    /** Pegs of the right colour in the right place. */
    val black: Int,
    /** Pegs of the right colour in the wrong place. */
    val white: Int,
)

/**
 * A Mastermind duel as ONE player sees it; each player gets their own version, so the opponent's code is never
 * sent until the game is over. Codes are lists of colours 0 until [colors], see [ClientMessage].
 *  - [phase]: "secret" (players are choosing their codes), "guessing" or "finished".
 *  - [firstPlayer] guesses first in every round. The second player always gets their guess in a round, even after
 *    the first player cracked the code, so both get the same number of guesses.
 *  - [yourSecret]: the code you chose (empty until you set it). [opponentSecret]: theirs, only once finished.
 *  - [yourGuesses]: your guesses at the opponent's code; [opponentGuesses]: theirs at yours. Oldest first.
 *  - [winner]: who cracked the code first, counted in rounds; null while playing and when the game ended in a
 *    draw (both cracked it in the same round, or nobody did in [maxGuesses] guesses each).
 *  - [event]: a short sentence about what just happened, written for this player.
 */
@Serializable
data class MastermindState(
    val game: String,
    val phase: String,
    val you: String,
    val opponent: String,
    val firstPlayer: String,
    val pegs: Int,
    val colors: Int,
    val maxGuesses: Int,
    val youReady: Boolean,
    val opponentReady: Boolean,
    val yourTurn: Boolean,
    val yourSecret: List<Int> = emptyList(),
    val opponentSecret: List<Int> = emptyList(),
    val yourGuesses: List<MastermindGuess> = emptyList(),
    val opponentGuesses: List<MastermindGuess> = emptyList(),
    val winner: String? = null,
    val event: String = "",
)

/**
 * Snapshot of a Hex game, sent to both players after every change.
 * [size] is the number of cells per side (n). [board] has n * n characters, one per cell numbered as described on
 * [ClientMessage]: '.' empty, 'R' red (joins top and bottom) or 'B' blue (joins left and right).
 * [turn] is "R" or "B". [status] is "playing" or "won" (Hex has no draws). [lastMove] is the cell played last (-1
 * before the first move; after a swap, blue's mirrored stone). [canSwap] is true while blue may swap, and [swapped]
 * once blue has. [winningPath] is the winner's chain of cells from one of their sides to the other.
 */
@Serializable
data class HexState(
    val game: String,
    val size: Int,
    val board: String,
    val redPlayer: String,
    val bluePlayer: String,
    val turn: String,
    val status: String,
    val winner: String? = null,
    val lastMove: Int = -1,
    val canSwap: Boolean = false,
    val swapped: Boolean = false,
    val winningPath: List<Int> = emptyList(),
)

/**
 * Snapshot of a Memory (pairs) game, sent to both players after every change. Cards are numbered row by row from the
 * top-left (card = row * [columns] + column), and faces 0 until columns * rows / 2.
 * [faces] has one entry per card: its face if it is face up or taken, or -1 while it is face down. The face of a
 * face-down card is never sent, to anyone. [taken] has one character per card: '.' still in play, '1' taken by the
 * first player or '2' by the second. [turn] is "1" or "2": the player who flips next. [status] is "playing", "won" or
 * "draw".
 * [flipped] is the card flipped first in the current turn (empty before it and once the second card is turned).
 * [lastPair] are the two cards turned over last, and [lastMatched] says whether they were a pair: a pair is taken and
 * its player flips again; two different cards end the turn but stay face up, until the next player flips a card.
 */
@Serializable
data class MemoryState(
    val game: String,
    val columns: Int,
    val rows: Int,
    val faces: List<Int>,
    val taken: String,
    val firstPlayer: String,
    val secondPlayer: String,
    val turn: String,
    val status: String,
    val winner: String? = null,
    val firstScore: Int,
    val secondScore: Int,
    val flipped: List<Int> = emptyList(),
    val lastPair: List<Int> = emptyList(),
    val lastMatched: Boolean = false,
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
 *              [othello] for Othello, [checkers] for Checkers, [dots] for Dots and Boxes, [oware] for Oware,
 *              [mastermind] for Mastermind, [hex] for Hex or [memory] for Memory; all null means "no game any more".
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
    val mastermind: MastermindState? = null,
    val hex: HexState? = null,
    val memory: MemoryState? = null,
    val token: String? = null,
    val history: List<HistoryLine>? = null,
    val version: Long = 0,
)

/** One chat or system line from the room's recent history. [type] is "chat" or "system". */
@Serializable
data class HistoryLine(val type: String, val from: String, val text: String, val time: Long)

val protocolJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun ServerMessage.toFrame() = Frame.Text(protocolJson.encodeToString(ServerMessage.serializer(), this))
