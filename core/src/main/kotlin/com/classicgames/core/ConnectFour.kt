package com.classicgames.core

/**
 * Connect Four on a 7-column, 6-row board. Players take turns dropping a disc into a column;
 * it falls to the lowest free spot. The first to line up four discs (across, down or diagonally) wins.
 * [red] moves first.
 *
 * Cells are numbered row by row from the TOP-left: cell = row * 7 + column (row 0 is the top row).
 * A move is just the column number (0..6).
 */
class ConnectFour(val red: String, val yellow: String) : Game {
    enum class Status { PLAYING, WON, DRAW }

    private val cells = CharArray(CELLS) { '.' }

    var turn = 'R'
        private set
    var status = Status.PLAYING
        private set
    var winner: String? = null
        private set
    var line: List<Int> = emptyList()
        private set

    /** Cell of the disc dropped last, or -1 before the first move. */
    var lastMove = -1
        private set

    override val finished get() = status != Status.PLAYING

    override fun handle(player: String, msg: ClientMessage): String? =
        if (msg.type == "move") drop(player, msg.cell ?: -1) else "Connect Four only understands moves"

    /** Drops a disc for [player] into [column] (0..6). Returns an error message, or null if the move was made. */
    fun drop(player: String, column: Int): String? {
        if (status != Status.PLAYING) return "The game is over"
        val mark = when (player) {
            red -> 'R'
            yellow -> 'Y'
            else -> return "You are not in this game"
        }
        if (mark != turn) return "Not your turn"
        if (column !in 0 until COLUMNS) return "Invalid column"
        val row = (ROWS - 1 downTo 0).firstOrNull { cells[it * COLUMNS + column] == '.' }
            ?: return "That column is full"

        val cell = row * COLUMNS + column
        cells[cell] = mark
        lastMove = cell

        val winningLine = LINES.firstOrNull { l -> cell in l && l.all { cells[it] == mark } }
        when {
            winningLine != null -> {
                status = Status.WON
                winner = player
                line = winningLine
            }
            cells.none { it == '.' } -> status = Status.DRAW
            else -> turn = if (turn == 'R') 'Y' else 'R'
        }
        return null
    }

    override fun messageFor(player: String) =
        ServerMessage(type = "game", from = "server", text = describe(), connect = toState())

    fun toState() = ConnectState(
        game = ID,
        board = String(cells),
        redPlayer = red,
        yellowPlayer = yellow,
        turn = turn.toString(),
        status = status.name.lowercase(),
        winner = winner,
        line = line,
        lastMove = lastMove,
    )

    /** Short human-readable summary, for clients that only show text. */
    fun describe(): String = when (status) {
        Status.PLAYING -> "Connect Four: ${if (turn == 'R') red else yellow}'s turn (${if (turn == 'R') "red" else "yellow"})"
        Status.WON -> "Connect Four: $winner won"
        Status.DRAW -> "Connect Four: draw"
    }

    companion object {
        const val ID = "connectfour"
        const val COLUMNS = 7
        const val ROWS = 6
        const val CELLS = COLUMNS * ROWS

        /** Every group of four cells in a straight line: 24 across, 21 down, 24 diagonal = 69. */
        private val LINES: List<List<Int>> = buildList {
            for (r in 0 until ROWS) for (c in 0 until COLUMNS) {
                for ((dr, dc) in listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)) {
                    val endRow = r + 3 * dr
                    val endColumn = c + 3 * dc
                    if (endRow in 0 until ROWS && endColumn in 0 until COLUMNS) {
                        add(List(4) { (r + it * dr) * COLUMNS + c + it * dc })
                    }
                }
            }
        }
    }
}
