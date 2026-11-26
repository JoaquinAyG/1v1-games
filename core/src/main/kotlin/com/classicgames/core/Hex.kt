package com.classicgames.core

/**
 * Hex on a rhombus of [size] x [size] hexagonal cells. Players take turns putting a stone of their colour on any
 * empty cell. [red] moves first and wants to connect the top row to the bottom row; [blue] wants to connect the left
 * column to the right column. The first to join their two sides with a chain of touching stones wins. The board can
 * never fill up without one of them doing so, so there are no draws.
 *
 * Swap rule: on blue's first turn, instead of placing a stone, blue may take over red's opening stone. It is removed
 * and a blue stone is put on its mirror image across the long diagonal (cell (row, column) goes to (column, row)).
 * The mirror turns top-bottom into left-right, so blue now has exactly the position red had, and red moves next.
 * This stops red from simply opening in the strongest cell. Both players keep their colour and their sides.
 *
 * Cells are numbered row by row from the top-left: cell = row * size + column. Each row sits half a cell further
 * right than the one above it, so cell (row, column) touches the six cells listed by [neighbours].
 *
 * [size] exists so tests can play on smaller boards, and [start] (size * size characters, '.', 'R' or 'B', row by
 * row) so they can set up a position; rooms always start with an empty board of [SIZE]. The side to move follows
 * from the stones: red when both have as many, blue when red has one more.
 */
class Hex(val red: String, val blue: String, val size: Int = SIZE, start: String? = null) : Game {
    enum class Status { PLAYING, WON }

    private val cells = (start ?: ".".repeat(size * size)).toCharArray()

    /** 'R' or 'B': the side to move. */
    var turn: Char
        private set
    var status = Status.PLAYING
        private set
    var winner: String? = null
        private set

    /** The cell played last (after a swap, blue's mirrored stone), or -1 before the first move. */
    var lastMove = -1
        private set

    /** True once blue has used the swap. */
    var swapped = false
        private set

    /** The winner's chain from one of their sides to the other, in order; empty while playing. */
    var winningPath: List<Int> = emptyList()
        private set

    init {
        require(size >= 2) { "the board needs at least 2 x 2 cells" }
        require(cells.size == size * size && cells.all { it in ".RB" }) { "the board must be ${size * size} cells of . R B" }
        val reds = cells.count { it == 'R' }
        val blues = cells.count { it == 'B' }
        require(reds - blues in -1..1) { "red and blue take turns, so their stone counts differ by at most one" }
        turn = if (reds > blues) 'B' else 'R'
        // A position handed in may already be won.
        for (mark in "RB") chain(mark)?.let { win(mark, it) }
    }

    override val finished get() = status != Status.PLAYING

    /** True when blue may swap now: it is blue's first turn and the only stone on the board is red's first one. */
    val canSwap: Boolean
        get() = status == Status.PLAYING && turn == 'B' && !swapped &&
            cells.count { it == 'R' } == 1 && cells.none { it == 'B' }

    override fun handle(player: String, msg: ClientMessage): String? = when (msg.type) {
        "move" -> play(player, msg.cell ?: -1)
        "swap" -> swap(player)
        else -> "Hex understands 'move' and 'swap'"
    }

    /** Puts a stone of [player]'s colour on [cell]. Returns an error message, or null if the stone was placed. */
    fun play(player: String, cell: Int): String? {
        refusal(player)?.let { return it }
        val mark = markOf(player)!!
        if (cell !in cells.indices) return "Invalid cell"
        if (cells[cell] != '.') return "That cell is taken"

        cells[cell] = mark
        lastMove = cell
        val chain = chain(mark)
        if (chain != null) win(mark, chain) else turn = other(mark)
        return null
    }

    /** Blue takes over red's first stone (see the class comment). Returns an error message, or null if it was done. */
    fun swap(player: String): String? {
        refusal(player)?.let { return it }
        if (markOf(player) != 'B') return "Only $blue can swap"
        if (!canSwap) return "You can only swap on your first turn, instead of placing a stone"

        val stone = cells.indexOf('R')
        cells[stone] = '.'
        val mirrored = mirror(size, stone)
        cells[mirrored] = 'B'
        lastMove = mirrored
        swapped = true
        turn = 'R'
        return null
    }

    private fun markOf(player: String) = when (player) {
        red -> 'R'
        blue -> 'B'
        else -> null
    }

    /** Why [player] may not move now, or null if they may. */
    private fun refusal(player: String): String? {
        val mark = markOf(player)
        return when {
            status != Status.PLAYING -> "The game is over"
            mark == null -> "You are not in this game"
            mark != turn -> "Not your turn"
            else -> null
        }
    }

    private fun win(mark: Char, chain: List<Int>) {
        status = Status.WON
        winner = nameOf(mark)
        winningPath = chain
    }

    /**
     * A shortest chain of [mark]'s stones joining their two sides (top to bottom for red, left to right for blue),
     * or null if their sides are not joined yet. Found breadth first from every stone on their first side at once.
     */
    private fun chain(mark: Char): List<Int>? {
        fun onFirstSide(cell: Int) = if (mark == 'R') cell / size == 0 else cell % size == 0
        fun onLastSide(cell: Int) = if (mark == 'R') cell / size == size - 1 else cell % size == size - 1

        val cameFrom = IntArray(cells.size) { -2 } // -2 not reached yet, -1 a starting stone
        val queue = ArrayDeque<Int>()
        for (cell in cells.indices) {
            if (cells[cell] == mark && onFirstSide(cell)) {
                cameFrom[cell] = -1
                queue.addLast(cell)
            }
        }
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (onLastSide(cell)) {
                return generateSequence(cell) { cameFrom[it].takeIf { from -> from >= 0 } }.toList().reversed()
            }
            for (next in neighbours(size, cell)) {
                if (cells[next] == mark && cameFrom[next] == -2) {
                    cameFrom[next] = cell
                    queue.addLast(next)
                }
            }
        }
        return null
    }

    private fun other(mark: Char) = if (mark == 'R') 'B' else 'R'
    private fun nameOf(mark: Char) = if (mark == 'R') red else blue

    override fun messageFor(player: String) =
        ServerMessage(type = "game", from = "server", text = describe(), hex = toState())

    fun toState() = HexState(
        game = ID,
        size = size,
        board = String(cells),
        redPlayer = red,
        bluePlayer = blue,
        turn = turn.toString(),
        status = status.name.lowercase(),
        winner = winner,
        lastMove = lastMove,
        canSwap = canSwap,
        swapped = swapped,
        winningPath = winningPath,
    )

    /** Short human-readable summary, for clients that only show text. */
    fun describe(): String = when (status) {
        Status.WON -> "Hex: $winner won"
        Status.PLAYING -> {
            val sides = if (turn == 'R') "red, top to bottom" else "blue, left to right"
            val news = if (swapped && cells.count { it != '.' } == 1) "$blue swapped. " else ""
            "Hex: $news${nameOf(turn)}'s turn ($sides)"
        }
    }

    companion object {
        const val ID = "hex"

        /**
         * Cells per side of the board used in rooms. 7 rather than the classic 11 (or 9) because the board lies on its
         * side, 1.5 * SIZE cells wide, and a phone screen has to fit it with cells big enough to tap in one go.
         */
        const val SIZE = 7

        /**
         * The cells touching [cell] on a board of [size] x [size]: above it (row - 1, column) and (row - 1, column + 1),
         * beside it (row, column - 1) and (row, column + 1), and below it (row + 1, column - 1) and (row + 1, column),
         * leaving out the ones off the board.
         */
        fun neighbours(size: Int, cell: Int): List<Int> {
            val row = cell / size
            val column = cell % size
            return listOf(-1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0)
                .map { (dr, dc) -> row + dr to column + dc }
                .filter { (r, c) -> r in 0 until size && c in 0 until size }
                .map { (r, c) -> r * size + c }
        }

        /** [cell] mirrored across the long diagonal, from the top-left corner to the bottom-right one. */
        fun mirror(size: Int, cell: Int) = (cell % size) * size + cell / size
    }
}
