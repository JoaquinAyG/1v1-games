package com.classicgames.core

/**
 * Dots and Boxes on a grid of [size] x [size] boxes ((size + 1) x (size + 1) dots). Players take turns drawing
 * one line between two neighbouring dots. A line that completes the fourth side of a box (or of two boxes at
 * once) scores those boxes for the player who drew it, and that player draws again; otherwise the turn passes.
 * The game ends when every line is drawn, and whoever owns more boxes wins. [red] moves first.
 *
 * Lines are numbered horizontal lines first, then vertical lines (see [horizontalLine] and [verticalLine]).
 * Boxes are numbered row by row from the top-left: box = row * size + column. A move is just the line number.
 *
 * [size] exists so tests can play on a smaller board; rooms always use [SIZE].
 */
class DotsAndBoxes(val red: String, val blue: String, val size: Int = SIZE) : Game {
    enum class Status { PLAYING, WON, DRAW }

    init {
        require(size >= 1) { "the board needs at least one box" }
    }

    private val lines = CharArray(lineCount(size)) { '.' }
    private val boxes = CharArray(size * size) { '.' }

    var turn = 'R'
        private set
    var status = Status.PLAYING
        private set
    var winner: String? = null
        private set

    /** The line drawn last, or -1 before the first move. */
    var lastLine = -1
        private set

    /** Boxes the last line completed (none, one or two). */
    var lastClosed: List<Int> = emptyList()
        private set

    override val finished get() = status != Status.PLAYING

    override fun handle(player: String, msg: ClientMessage): String? =
        if (msg.type == "move") draw(player, msg.cell ?: -1) else "Dots and Boxes only understands moves"

    /** Draws [line] for [player]. Returns an error message, or null if the line was drawn. */
    fun draw(player: String, line: Int): String? {
        if (status != Status.PLAYING) return "The game is over"
        val mark = when (player) {
            red -> 'R'
            blue -> 'B'
            else -> return "You are not in this game"
        }
        if (mark != turn) return "Not your turn"
        if (line !in lines.indices) return "Invalid line"
        if (lines[line] != '.') return "That line is already drawn"

        lines[line] = mark
        lastLine = line
        lastClosed = boxesNextTo(line).filter { box -> sidesOf(size, box).all { lines[it] != '.' } }.sorted()
        lastClosed.forEach { boxes[it] = mark }

        when {
            lines.all { it != '.' } -> {
                val r = score('R')
                val b = score('B')
                when {
                    r > b -> { status = Status.WON; winner = red }
                    b > r -> { status = Status.WON; winner = blue }
                    else -> status = Status.DRAW
                }
            }
            lastClosed.isEmpty() -> turn = if (turn == 'R') 'B' else 'R'
            // Completing a box earns another line: the turn stays.
        }
        return null
    }

    /** The one or two boxes that [line] is a side of. */
    private fun boxesNextTo(line: Int): List<Int> {
        val horizontal = size * (size + 1)
        return if (line < horizontal) {
            val row = line / size
            val column = line % size
            // The box below the line and the box above it, where they exist.
            listOf(row, row - 1).filter { it in 0 until size }.map { it * size + column }
        } else {
            val row = (line - horizontal) / (size + 1)
            val column = (line - horizontal) % (size + 1)
            // The box to the right of the line and the box to its left.
            listOf(column, column - 1).filter { it in 0 until size }.map { row * size + it }
        }
    }

    private fun nameOf(mark: Char) = if (mark == 'R') red else blue
    private fun score(mark: Char) = boxes.count { it == mark }

    override fun messageFor(player: String) =
        ServerMessage(type = "game", from = "server", text = describe(), dots = toState())

    fun toState() = DotsState(
        game = ID,
        size = size,
        lines = String(lines),
        boxes = String(boxes),
        redPlayer = red,
        bluePlayer = blue,
        turn = turn.toString(),
        status = status.name.lowercase(),
        winner = winner,
        redScore = score('R'),
        blueScore = score('B'),
        lastLine = lastLine,
        closed = lastClosed,
    )

    /** Short human-readable summary, for clients that only show text. */
    fun describe(): String {
        val score = "${score('R')}-${score('B')}"
        return when (status) {
            Status.PLAYING -> {
                val again = if (lastClosed.isNotEmpty()) " again" else ""
                "Dots and Boxes: ${nameOf(turn)}'s turn$again (${if (turn == 'R') "red" else "blue"}), $score"
            }
            Status.WON -> "Dots and Boxes: $winner won $score"
            Status.DRAW -> "Dots and Boxes: draw $score"
        }
    }

    companion object {
        const val ID = "dotsandboxes"

        /** Boxes per side of the board used in rooms. */
        const val SIZE = 5

        /** Number of lines on a board of [size] x [size] boxes: as many horizontal as vertical ones. */
        fun lineCount(size: Int) = 2 * size * (size + 1)

        /**
         * The horizontal line from dot ([row], [column]) to dot ([row], [column] + 1);
         * [row] is 0..size (dot rows from the top), [column] is 0 until size.
         */
        fun horizontalLine(size: Int, row: Int, column: Int) = row * size + column

        /**
         * The vertical line from dot ([row], [column]) down to dot ([row] + 1, [column]);
         * [row] is 0 until size, [column] is 0..size. Vertical lines come after all the horizontal ones.
         */
        fun verticalLine(size: Int, row: Int, column: Int) = size * (size + 1) + row * (size + 1) + column

        /** The four sides of [box] (row * size + column): top, bottom, left, right. */
        fun sidesOf(size: Int, box: Int): List<Int> {
            val row = box / size
            val column = box % size
            return listOf(
                horizontalLine(size, row, column),
                horizontalLine(size, row + 1, column),
                verticalLine(size, row, column),
                verticalLine(size, row, column + 1),
            )
        }
    }
}
