package com.classicgames.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One Battleship ship: the board cells it covers (row * 10 + column), in a straight unbroken line. */
@Serializable
data class ShipPlacement(val cells: List<Int>)

/**
 * What the app sends to the server. Mirrors ClientMessage in the server project.
 *  - type "chat":      [text]
 *  - type "startGame": [game] ("tictactoe", "battleship", "connectfour", "othello" or "checkers")
 *  - type "move":      [cell] (tic-tac-toe: 0..8; battleship: 0..99, the cell to fire at;
 *                      connect four: the column 0..6 to drop a disc into; othello: 0..63 row by row),
 *                      or [from] and [to] (checkers: 0..63 row by row, one step or one jump)
 *  - type "place":     [ships] (battleship: the whole fleet, once)
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
 * Snapshot of a tic-tac-toe game. Mirrors GameState in the server project.
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
 * Snapshot of a Connect Four game. Mirrors ConnectState in the server project.
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
 * Snapshot of a Checkers game. Mirrors CheckersState in the server project.
 * [board] has 64 characters, row by row from the top-left: '.' empty, 'r' red man, 'R' red king,
 * 'b' black man, 'B' black king. [turn] is "R" (red) or "B" (black), the side to move.
 * [status] is "playing", "won" or "draw".
 * [legalMoves] lists every move the side to move may make (jumps only when a jump exists).
 * [chain] is the square of a piece that just captured and must keep jumping (-1 if none).
 * [lastFrom] / [lastTo] are the squares of the last move.
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
 * Snapshot of an Othello game. Mirrors OthelloState in the server project.
 * [board] has 64 characters, row by row from the top-left: '.' empty, 'B' black, 'W' white.
 * [turn] is "B" or "W". [status] is "playing", "won" or "draw".
 * [legalMoves] lists the cells the player whose turn it is may play (empty once the game is over).
 * [flipped] are the cells that flipped on the last move; [passed] is the player who had to skip their turn
 * because they had no legal move (or null).
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
 * A Battleship game as this player sees it. Mirrors BattleState in the server project.
 * Boards are 100 characters, row by row.
 *  - [phase]: "placing", "playing" or "finished".
 *  - [myBoard]: '.' water, 'S' my ship, 'H' my ship that was hit, 'M' a missed shot by the opponent.
 *  - [shots]: my shots at the opponent: '.' not fired, 'M' miss, 'H' hit, 'X' hit on a sunk ship,
 *    'S' an enemy ship I never found (only once the game is finished).
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
 * What the server sends to the app. Mirrors ServerMessage in the server project.
 * type = "chat" | "system" | "game" (new snapshot in [state] or [battle]; both null = no game any more) | "error".
 */
@Serializable
data class ServerMessage(
    val type: String,
    val from: String,
    val text: String,
    val time: Long = 0,
    val state: GameState? = null,
    val battle: BattleState? = null,
    val connect: ConnectState? = null,
    val othello: OthelloState? = null,
    val checkers: CheckersState? = null,
)

val protocolJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
