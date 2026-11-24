package server

import kotlin.math.abs

/**
 * Checkers (American/English draughts) on an 8x8 board, played on the dark squares.
 *  - Men move one square diagonally forward; kings move one square diagonally in any direction.
 *  - A piece jumps by leaping over an adjacent enemy piece onto the empty square behind it, capturing it.
 *  - Capturing is mandatory: if you can jump, you must. A jump that can be followed by another jump
 *    with the same piece must be continued (each jump is sent as its own move; see [chain]).
 *  - A man reaching the far row becomes a king. If that happens in the middle of a jump, the turn ends.
 *  - You win when the opponent has no pieces or no legal move. After [DRAW_AFTER_PLIES] moves in a row
 *    without a capture or a crowning, the game is a draw.
 * [red] moves first.
 *
 * Squares are numbered row by row from the top-left: cell = row * 8 + column. Dark squares are those where
 * row + column is odd. Red starts at the bottom (rows 5-7) and moves up; black starts at the top (rows 0-2).
 * Board characters: '.' empty, 'r' red man, 'R' red king, 'b' black man, 'B' black king.
 *
 * [startBoard] and [startTurn] exist so positions can be set up directly, e.g. for tests.
 */
class Checkers(
    val red: String,
    val black: String,
    startBoard: String = STANDARD_START,
    startTurn: Char = 'R',
) : Game {
    enum class Status { PLAYING, WON, DRAW }

    private val cells = startBoard.toCharArray().also { require(it.size == CELLS) { "board must have $CELLS cells" } }

    /** The side to move: 'R' (red) or 'B' (black). */
    var turn = startTurn
        private set
    var status = Status.PLAYING
        private set
    var winner: String? = null
        private set

    /** The squares of the last move (-1 before the first one). */
    var lastFrom = -1
        private set
    var lastTo = -1
        private set

    /** Square of a piece that just jumped and must keep jumping, or -1. */
    var chain = -1
        private set

    private var pliesWithoutProgress = 0

    init {
        if (movesFor(turn).isEmpty()) finish(winnerSide = other(turn)) // a position handed in may already be over
    }

    override val finished get() = status != Status.PLAYING

    override fun handle(player: String, msg: ClientMessage): String? =
        if (msg.type == "move") play(player, msg.from ?: -1, msg.to ?: -1) else "Checkers only understands moves"

    /** Moves the piece on [from] to [to] (one step or one jump). Returns an error message, or null if it was made. */
    fun play(player: String, from: Int, to: Int): String? {
        if (status != Status.PLAYING) return "The game is over"
        val side = when (player) {
            red -> 'R'
            black -> 'B'
            else -> return "You are not in this game"
        }
        if (side != turn) return "Not your turn"
        if (from !in 0 until CELLS || to !in 0 until CELLS) return "Invalid square"

        val moves = movesFor(turn)
        if (moves.none { it.from == from && it.to == to }) {
            return when {
                sideOf(cells[from]) != side -> "Pick one of your own pieces"
                chain >= 0 && from != chain -> "You must keep jumping with the same piece"
                moves.isNotEmpty() && moves.all { it.isJump() } -> "You must capture"
                else -> "That move isn't allowed"
            }
        }

        val piece = cells[from]
        val jump = CheckersMove(from, to).isJump()
        cells[from] = '.'
        if (jump) cells[(from + to) / 2] = '.' // the jumped piece sits exactly between the two squares
        val crowned = when {
            piece == 'r' && to / SIZE == 0 -> 'R'
            piece == 'b' && to / SIZE == SIZE - 1 -> 'B'
            else -> piece
        }
        cells[to] = crowned
        lastFrom = from
        lastTo = to
        pliesWithoutProgress = if (jump || crowned != piece) 0 else pliesWithoutProgress + 1

        if (jump && crowned == piece && jumpsFrom(to).isNotEmpty()) {
            chain = to // the same player jumps again
            return null
        }
        chain = -1
        turn = other(turn)
        when {
            movesFor(turn).isEmpty() -> finish(winnerSide = other(turn)) // the player who just moved wins
            pliesWithoutProgress >= DRAW_AFTER_PLIES -> finish(winnerSide = null)
        }
        return null
    }

    private fun finish(winnerSide: Char?) {
        if (winnerSide == null) {
            status = Status.DRAW
        } else {
            status = Status.WON
            winner = nameOf(winnerSide)
        }
    }

    /** All legal moves for [side]: only jumps if any exist, and only the chained piece while a chain is running. */
    private fun movesFor(side: Char): List<CheckersMove> {
        val pieces = if (chain >= 0) listOf(chain) else cells.indices.filter { sideOf(cells[it]) == side }
        val jumps = pieces.flatMap { jumpsFrom(it) }
        if (jumps.isNotEmpty()) return jumps
        if (chain >= 0) return emptyList()
        return pieces.flatMap { stepsFrom(it) }
    }

    /** Row directions a piece may move in: red men go up (-1), black men go down (+1), kings both. */
    private fun rowDirections(piece: Char): List<Int> = when (piece) {
        'r' -> listOf(-1)
        'b' -> listOf(1)
        else -> listOf(-1, 1)
    }

    private fun stepsFrom(cell: Int): List<CheckersMove> {
        val result = ArrayList<CheckersMove>()
        for (dr in rowDirections(cells[cell])) for (dc in listOf(-1, 1)) {
            val r = cell / SIZE + dr
            val c = cell % SIZE + dc
            if (r in 0 until SIZE && c in 0 until SIZE && cells[r * SIZE + c] == '.') {
                result += CheckersMove(cell, r * SIZE + c)
            }
        }
        return result
    }

    private fun jumpsFrom(cell: Int): List<CheckersMove> {
        val enemy = other(sideOf(cells[cell]))
        val result = ArrayList<CheckersMove>()
        for (dr in rowDirections(cells[cell])) for (dc in listOf(-1, 1)) {
            val overR = cell / SIZE + dr
            val overC = cell % SIZE + dc
            val landR = overR + dr
            val landC = overC + dc
            if (landR in 0 until SIZE && landC in 0 until SIZE &&
                sideOf(cells[overR * SIZE + overC]) == enemy &&
                cells[landR * SIZE + landC] == '.'
            ) {
                result += CheckersMove(cell, landR * SIZE + landC)
            }
        }
        return result
    }

    private fun CheckersMove.isJump() = abs(to / SIZE - from / SIZE) == 2

    private fun sideOf(piece: Char): Char = when (piece) {
        'r', 'R' -> 'R'
        'b', 'B' -> 'B'
        else -> ' '
    }

    private fun other(side: Char): Char = if (side == 'R') 'B' else 'R'
    private fun nameOf(side: Char) = if (side == 'R') red else black
    private fun count(side: Char) = cells.count { sideOf(it) == side }

    override fun messageFor(player: String) =
        ServerMessage(type = "game", from = "server", text = describe(), checkers = toState())

    fun toState() = CheckersState(
        game = ID,
        board = String(cells),
        redPlayer = red,
        blackPlayer = black,
        turn = turn.toString(),
        status = status.name.lowercase(),
        winner = winner,
        redCount = count('R'),
        blackCount = count('B'),
        legalMoves = if (status == Status.PLAYING) movesFor(turn) else emptyList(),
        lastFrom = lastFrom,
        lastTo = lastTo,
        chain = chain,
    )

    /** Short human-readable summary, for clients that only show text. */
    fun describe(): String = when (status) {
        Status.PLAYING -> {
            val again = if (chain >= 0) " (keeps jumping)" else ""
            "Checkers: ${nameOf(turn)}'s turn (${if (turn == 'R') "red" else "black"})$again"
        }
        Status.WON -> "Checkers: $winner won"
        Status.DRAW -> "Checkers: draw"
    }

    companion object {
        const val ID = "checkers"
        const val SIZE = 8
        const val CELLS = SIZE * SIZE

        /** Moves in a row without a capture or a crowning after which the game is drawn (40 moves each). */
        const val DRAW_AFTER_PLIES = 80

        val STANDARD_START: String = CharArray(CELLS).also { board ->
            for (r in 0 until SIZE) for (c in 0 until SIZE) {
                board[r * SIZE + c] = when {
                    (r + c) % 2 == 0 -> '.' // light squares stay empty
                    r <= 2 -> 'b'
                    r >= 5 -> 'r'
                    else -> '.'
                }
            }
        }.concatToString()
    }
}
