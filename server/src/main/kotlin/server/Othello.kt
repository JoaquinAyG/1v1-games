package server

/**
 * Othello (Reversi) on an 8x8 board. Players take turns placing a disc so that it brackets at least one
 * line of enemy discs between the new disc and another of their own discs; the bracketed discs flip.
 * A player with no legal move is skipped (passes). The game ends when neither player can move, and whoever
 * has more discs wins. [black] moves first.
 *
 * Cells are numbered row by row from the top-left: cell = row * 8 + column. A move is just the cell number.
 *
 * [startBoard] (64 characters: '.', 'B', 'W') and [startTurn] exist so positions can be set up directly,
 * e.g. for tests; by default the game starts from the standard position.
 */
class Othello(
    val black: String,
    val white: String,
    startBoard: String = STANDARD_START,
    startTurn: Char = 'B',
) : Game {
    enum class Status { PLAYING, WON, DRAW }

    private val cells = startBoard.toCharArray().also { require(it.size == CELLS) { "board must have $CELLS cells" } }

    var turn = startTurn
        private set
    var status = Status.PLAYING
        private set
    var winner: String? = null
        private set

    /** Cell of the disc placed last, or -1 before the first move. */
    var lastMove = -1
        private set

    /** Cells that flipped because of the last move. */
    var lastFlips: List<Int> = emptyList()
        private set

    /** The player who had no legal move and was skipped after the last move, if any. */
    var passed: String? = null
        private set

    init {
        resolveTurn() // a position handed in may already be a pass or the end of the game
    }

    override val finished get() = status != Status.PLAYING

    override fun handle(player: String, msg: ClientMessage): String? =
        if (msg.type == "move") play(player, msg.cell ?: -1) else "Othello only understands moves"

    /** Places a disc for [player] on [cell] (0..63). Returns an error message, or null if the move was made. */
    fun play(player: String, cell: Int): String? {
        if (status != Status.PLAYING) return "The game is over"
        val mark = when (player) {
            black -> 'B'
            white -> 'W'
            else -> return "You are not in this game"
        }
        if (mark != turn) return "Not your turn"
        if (cell !in 0 until CELLS) return "Invalid cell"
        if (cells[cell] != '.') return "That cell is already taken"
        val flips = flipsFor(cell, mark)
        if (flips.isEmpty()) return "That move doesn't flip any discs"

        cells[cell] = mark
        flips.forEach { cells[it] = mark }
        lastMove = cell
        lastFlips = flips.sorted()
        turn = other(mark)
        resolveTurn()
        return null
    }

    /** All cells where [mark] could legally place a disc right now. */
    fun legalMoves(mark: Char): List<Int> = (0 until CELLS).filter { flipsFor(it, mark).isNotEmpty() }

    /**
     * Makes [turn] point at a player who can move: skips a player with no legal move,
     * and ends the game when nobody can move.
     */
    private fun resolveTurn() {
        passed = null
        if (legalMoves(turn).isNotEmpty()) return
        val next = other(turn)
        if (legalMoves(next).isNotEmpty()) {
            passed = nameOf(turn)
            turn = next
            return
        }
        // Nobody can move: count discs.
        val b = count('B')
        val w = count('W')
        when {
            b > w -> { status = Status.WON; winner = black }
            w > b -> { status = Status.WON; winner = white }
            else -> status = Status.DRAW
        }
    }

    /** The discs that [mark] would flip by placing on [cell]; empty if the cell is taken or the move is illegal. */
    private fun flipsFor(cell: Int, mark: Char): List<Int> {
        if (cells[cell] != '.') return emptyList()
        val enemy = other(mark)
        val flips = ArrayList<Int>()
        for ((dr, dc) in DIRECTIONS) {
            val line = ArrayList<Int>()
            var r = cell / SIZE + dr
            var c = cell % SIZE + dc
            while (r in 0 until SIZE && c in 0 until SIZE && cells[r * SIZE + c] == enemy) {
                line += r * SIZE + c
                r += dr
                c += dc
            }
            // The run of enemy discs only counts if one of our own discs closes it.
            if (line.isNotEmpty() && r in 0 until SIZE && c in 0 until SIZE && cells[r * SIZE + c] == mark) {
                flips += line
            }
        }
        return flips
    }

    private fun other(mark: Char) = if (mark == 'B') 'W' else 'B'
    private fun nameOf(mark: Char) = if (mark == 'B') black else white
    private fun count(mark: Char) = cells.count { it == mark }

    override fun messageFor(player: String) =
        ServerMessage(type = "game", from = "server", text = describe(), othello = toState())

    fun toState() = OthelloState(
        game = ID,
        board = String(cells),
        blackPlayer = black,
        whitePlayer = white,
        turn = turn.toString(),
        status = status.name.lowercase(),
        winner = winner,
        blackCount = count('B'),
        whiteCount = count('W'),
        legalMoves = if (status == Status.PLAYING) legalMoves(turn) else emptyList(),
        lastMove = lastMove,
        flipped = lastFlips,
        passed = passed,
    )

    /** Short human-readable summary, for clients that only show text. */
    fun describe(): String {
        val score = "${count('B')}-${count('W')}"
        return when (status) {
            Status.PLAYING -> {
                val skipped = passed?.let { "$it has no moves and passes. " }.orEmpty()
                "Othello: $skipped${nameOf(turn)}'s turn (${if (turn == 'B') "black" else "white"}), $score"
            }
            Status.WON -> "Othello: $winner won $score"
            Status.DRAW -> "Othello: draw $score"
        }
    }

    companion object {
        const val ID = "othello"
        const val SIZE = 8
        const val CELLS = SIZE * SIZE

        /** White on d4 and e5, black on e4 and d5. */
        val STANDARD_START: String = CharArray(CELLS) { '.' }.also {
            it[3 * SIZE + 3] = 'W'
            it[4 * SIZE + 4] = 'W'
            it[3 * SIZE + 4] = 'B'
            it[4 * SIZE + 3] = 'B'
        }.concatToString()

        private val DIRECTIONS = listOf(
            -1 to -1, -1 to 0, -1 to 1,
            0 to -1, 0 to 1,
            1 to -1, 1 to 0, 1 to 1,
        )
    }
}
