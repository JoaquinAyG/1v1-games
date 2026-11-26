package com.onevonegames.core

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
 *                 "oware", "mastermind", "hex", "memory", "quarto", "brisca", "maze" or "hanabi")
 *  - "move":      [cell] (tic-tac-toe: 0..8 row by row; battleship: 0..99 row by row, the cell to fire at;
 *                 connect four: the column 0..6 to drop a disc into; othello: 0..63 row by row;
 *                 dots and boxes: the line to draw, see below; oware: the pit to sow from, see below;
 *                 hex: the cell to put a stone on, see below; memory: the card to flip, 0 until columns * rows
 *                 row by row; quarto: the cell 0..15, row by row, to put the piece you were given on),
 *                 or [piece] (quarto: the piece 0..15 from the pool to give your opponent, see below),
 *                 or [card] (brisca: the card 0..39 from your hand to play, numbered as on [BriscaState]),
 *                 or [from] and [to] (checkers: 0..63 row by row, one step or one jump),
 *                 or [code] (mastermind: your guess at the opponent's code),
 *                 or [direction] (maze: the walker's step, "up", "down", "left" or "right"),
 *                 or [action] (hanabi: "hint" with [color] 0..4 or [number] 1..5, exactly one of them, for your
 *                 partner's hand; "discard" or "play" with [card], the position 0.. of the card in your own hand, as
 *                 listed in [HanabiState.yourHand])
 *  - "place":     [ships] (battleship: your whole fleet, once)
 *  - "secret":    [code] (mastermind: the code your opponent has to crack, once)
 *  - "swap":      nothing else (hex: the second player takes over the first stone instead of placing one, see below)
 *  - "swap7":     nothing else (brisca: on your turn, before playing your card, swap the 7 of trumps in your hand for
 *                 the face-up trump under the deck; only while that card has not been drawn yet)
 *  - "hint":      [direction] (maze: the guide tells the walker which way to go, "up", "down", "left" or "right";
 *                 any time while playing, as often as they like)
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
 *
 * Quarto pieces are numbered 0..15 with one bit per trait, as described on [QuartoState]. A turn is two moves, each
 * one its own "move" message: first [cell] to place the piece you were handed (in phase "place"), then [piece] to hand
 * your opponent the piece they must place next (in phase "choose"). The game opens with the first player choosing. A
 * move that does not fit the phase, or that sends both fields or neither, is rejected; nothing is given after the
 * winning piece or the 16th one.
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
    val piece: Int? = null,
    val card: Int? = null,
    val direction: String? = null,
    val action: String? = null,
    val color: Int? = null,
    val number: Int? = null,
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
 * Snapshot of a Quarto game, sent to both players after every change. Cells are numbered row by row from the top-left
 * (cell = row * 4 + column). Pieces are numbered 0..15, one bit per trait (bit set / bit clear):
 *  - 1: tall / short
 *  - 2: dark / light
 *  - 4: square / round
 *  - 8: hollow (a hole in the top) / solid
 * so piece 0 is short, light, round and solid, piece 15 tall, dark, square and hollow, and piece 5 (1 + 4) tall, light,
 * square and solid.
 * [board] has 16 entries, one per cell: the piece on it, or -1 while it is empty. [pool] lists the pieces nobody has
 * placed or been handed yet, smallest first. [piece] is the piece handed to the player who must place it (-1 while a
 * piece is being chosen, and at the end).
 * [turn] is "1" (the first player) or "2": the player who must act now. [phase] says what they must do: "choose" a
 * piece from the pool for their opponent, "place" [piece] on an empty cell, or nothing because the game is "finished".
 * [status] is "playing", "won" or "draw". [lastCell] is the cell the last piece went on (-1 before the first).
 * [line] holds the 4 cells of the winning line, and [sharedTraits] what all its pieces have in common, as the words
 * "tall", "short", "dark", "light", "square", "round", "hollow" or "solid", in that trait order. If the winning piece
 * completed more than one line, [line] is the first of the rows (top to bottom), the columns (left to right), the
 * diagonal from the top-left corner and the one from the top-right corner.
 */
@Serializable
data class QuartoState(
    val game: String,
    val board: List<Int>,
    val pool: List<Int>,
    val piece: Int = -1,
    val firstPlayer: String,
    val secondPlayer: String,
    val turn: String,
    val phase: String,
    val status: String,
    val winner: String? = null,
    val lastCell: Int = -1,
    val line: List<Int> = emptyList(),
    val sharedTraits: List<String> = emptyList(),
)

/** One card played in a Brisca trick: [card] (numbered as on [BriscaState]), by [player]. */
@Serializable
data class BriscaPlay(val player: String, val card: Int)

/**
 * A Brisca game as ONE player sees it; each player gets their own version, with their own hand and only the size of
 * the opponent's. The opponent's cards and the order of the deck are never sent.
 *
 * Cards are numbered 0..39: card = suit * 10 + rank, with the suits 0 Oros, 1 Copas, 2 Espadas, 3 Bastos and the
 * ranks 0 As, 1..6 the 2 to the 7, 7 Sota, 8 Caballo, 9 Rey. So 0 is the As de Oros, 12 the 3 de Copas, 26 the 7 de
 * Espadas and 39 the Rey de Bastos. Within a suit the order is As, 3, Rey, Caballo, Sota, 7, 6, 5, 4, 2, and the points
 * are As 11, 3 10, Rey 4, Caballo 3, Sota 2, the others nothing.
 *  - [firstPlayer] led the first trick.
 *  - [hand]: your cards, in the order you got them. [opponentCards]: how many the opponent holds.
 *  - [deckSize]: cards still to be drawn, counting the face-up trump while it lies under the deck.
 *  - [trumpCard]: the card turned face up, whose suit (trumpCard / 10) is trumps; after a swap, the 7 of trumps. It is
 *    still sent once it has been drawn (deckSize 0), since both players saw who took it.
 *  - [trick]: the cards of the trick being played, in order: empty, or the leader's card while the other player has to
 *    answer. A trick is settled as soon as its second card is played, and then shows as [lastTrick], won by
 *    [lastTrickWinner].
 *  - [yourPoints] / [opponentPoints]: the points in the tricks each player has taken so far.
 *  - [yourTurn]: you must play a card now (and may swap first if [canSwap]).
 *  - [status]: "playing", "won" (61 points or more) or "draw" (60 each).
 *  - [event]: a short sentence about what just happened, written for this player.
 */
@Serializable
data class BriscaState(
    val game: String,
    val you: String,
    val opponent: String,
    val firstPlayer: String,
    val hand: List<Int>,
    val opponentCards: Int,
    val deckSize: Int,
    val trumpCard: Int,
    val trick: List<BriscaPlay> = emptyList(),
    val lastTrick: List<BriscaPlay> = emptyList(),
    val lastTrickWinner: String? = null,
    val yourPoints: Int,
    val opponentPoints: Int,
    val yourTurn: Boolean,
    val canSwap: Boolean = false,
    val status: String,
    val winner: String? = null,
    val event: String = "",
)

/**
 * A Maze Guide game as ONE player sees it. The game is cooperative: both players win or lose together, so there is no
 * winner, only the [status] and the [result].
 *
 * Cells are numbered row by row from the top-left: cell = row * [size] + column. A cell's walls are a bit mask with a
 * bit set for each side that has a wall: 1 up, 2 right, 4 down, 8 left. So 15 is walled on every side, and the cells
 * along the edge always have their outer walls.
 *  - [role]: what [you] do, "guide" or "walker". The [guide] is the first player of the game.
 *  - [walls] (one mask per cell), [start], [exit], [position] (the walker's cell) and [trail] (every cell the walker
 *    has stood on, in order, from [start] to [position]): sent to the guide, and to the walker only once the game is
 *    over. Until then the walker gets an empty [walls] and [trail], and -1 for the cells.
 *  - [window]: the 3 x 3 cells around the walker, row by row from the top-left with the walker in the middle (index
 *    4): each cell's walls, or -1 where the window reaches past the edge of the maze. It is all the walker sees while
 *    playing. [windowExit] is the index in [window] of the exit when it is one of those cells, otherwise -1.
 *  - [movesUsed] / [moveLimit]: the walker's moves so far and the most they may make, ceil(1.5 x the shortest way
 *    out). A move into a wall is refused and not counted.
 *  - [hint]: the direction the guide sent last, "up", "down", "left" or "right" (empty before the first); [hintAt]:
 *    the moves used when it was sent; [hints]: how many hints have been sent in all, so a repeated direction still
 *    shows up as a new hint.
 *  - [status]: "playing", "won" (the walker reached the exit within the limit) or "lost" (the limit was used up first).
 *  - [shortest]: the moves of the shortest way from the start to the exit, sent once the game is over (0 until then).
 *  - [result]: how it ended in a sentence, the same for both players: the moves used against the shortest way, and
 *    when lost how far from the exit the walker was. Empty while playing.
 */
@Serializable
data class MazeState(
    val game: String,
    val you: String,
    val role: String,
    val guide: String,
    val walker: String,
    val size: Int,
    val walls: List<Int> = emptyList(),
    val start: Int = -1,
    val exit: Int = -1,
    val position: Int = -1,
    val trail: List<Int> = emptyList(),
    val window: List<Int>,
    val windowExit: Int = -1,
    val movesUsed: Int,
    val moveLimit: Int,
    val hint: String = "",
    val hintAt: Int = 0,
    val hints: Int = 0,
    val status: String,
    val shortest: Int = 0,
    val result: String = "",
)

/**
 * One Hanabi card in a hand or on the discard pile. [color] is 0..4 (red, yellow, green, blue, white) and [number]
 * 1..5; in your own hand they are never sent (-1 and 0), since you can't see your own cards.
 * For a card in a hand, the rest is what its holder has been told by hints: [knownColor] (-1 if not known) and
 * [knownNumber] (0 if not known), and the colours and numbers it has been shown not to be ([notColors], [notNumbers]).
 * A colour or number is also known once every other one has been ruled out. On the discard pile only [color] and
 * [number] are set.
 */
@Serializable
data class HanabiCard(
    val color: Int = -1,
    val number: Int = 0,
    val knownColor: Int = -1,
    val knownNumber: Int = 0,
    val notColors: List<Int> = emptyList(),
    val notNumbers: List<Int> = emptyList(),
)

/**
 * A Hanabi game as ONE player sees it. The game is cooperative: both players win or lose together, so there is no
 * winner, only the [status], the [score] and the [result]. Each player sees their partner's cards but never their own,
 * and the order of the deck is never sent.
 *
 * Colours are numbered 0 red, 1 yellow, 2 green, 3 blue, 4 white, and cards carry a number 1..5.
 *  - [yourHand]: your cards in order, as positions 0.. for "play" and "discard", without their colour and number, but
 *    with what hints have told you about each (see [HanabiCard]). A drawn card goes to the end.
 *  - [partnerHand]: your partner's cards, with their colour and number and what they have been told about them.
 *  - [fireworks]: the highest number played on each colour's firework, by colour (0 for none yet).
 *  - [discards]: every card discarded or misplayed, in the order it happened.
 *  - [hints]: hint tokens left, 0..8. [lives]: lives left, 0..3. [deckSize]: cards still to be drawn.
 *  - [yourTurn]: you must act now. [turnsLeft]: once the deck is empty, how many turns the game still has (each player
 *    gets exactly one more, counted from the one who drew the last card); -1 while there are cards to draw.
 *  - [status]: "playing"; "won" (all five fireworks at 5, a perfect 25); "finished" (the last round after the deck ran
 *    out is over, [score] out of 25); or "lost" (the third life went). [score] is always the sum of the fireworks.
 *  - [lastAction]: the last action as one line written for this player, e.g. "Ann hinted your 2s", "You played a red 3",
 *    "Bob misplayed a blue 4: 2 lives left". [hintedYours] / [hintedPartners]: when the last action was a hint, the
 *    positions of the cards it pointed at, in your hand or in your partner's; otherwise empty.
 *  - [result]: how it ended in a sentence, the same for both players (empty while playing).
 */
@Serializable
data class HanabiState(
    val game: String,
    val you: String,
    val partner: String,
    val firstPlayer: String,
    val yourHand: List<HanabiCard>,
    val partnerHand: List<HanabiCard>,
    val fireworks: List<Int>,
    val discards: List<HanabiCard> = emptyList(),
    val hints: Int,
    val lives: Int,
    val deckSize: Int,
    val yourTurn: Boolean,
    val turnsLeft: Int = -1,
    val status: String,
    val score: Int,
    val lastAction: String = "",
    val hintedYours: List<Int> = emptyList(),
    val hintedPartners: List<Int> = emptyList(),
    val result: String = "",
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
 *              [mastermind] for Mastermind, [hex] for Hex, [memory] for Memory, [quarto] for Quarto, [brisca]
 *              for Brisca, [maze] for Maze Guide or [hanabi] for Hanabi; all null means "no game anymore".
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
    val quarto: QuartoState? = null,
    val brisca: BriscaState? = null,
    val maze: MazeState? = null,
    val hanabi: HanabiState? = null,
    val token: String? = null,
    val history: List<HistoryLine>? = null,
    val version: Long = 0,
)

/** One chat or system line from the room's recent history. [type] is "chat" or "system". */
@Serializable
data class HistoryLine(val type: String, val from: String, val text: String, val time: Long)

val protocolJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun ServerMessage.toFrame() = Frame.Text(protocolJson.encodeToString(ServerMessage.serializer(), this))
