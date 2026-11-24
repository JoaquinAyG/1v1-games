package server

/** Rules and state of one tic-tac-toe game. Knows nothing about networking. */
class TicTacToe(val xPlayer: String, val oPlayer: String) : Game {
    enum class Status { PLAYING, WON, DRAW }

    private val cells = CharArray(9) { '.' }

    var turn = 'X'
        private set
    var status = Status.PLAYING
        private set
    var winner: String? = null
        private set
    var line: List<Int> = emptyList()
        private set

    override val finished get() = status != Status.PLAYING

    override fun handle(player: String, msg: ClientMessage): String? =
        if (msg.type == "move") play(player, msg.cell ?: -1) else "Tic-Tac-Toe only understands moves"

    /** Both players see the same board. */
    override fun messageFor(player: String) =
        ServerMessage(type = "game", from = "server", text = describe(), state = toState())

    /** Plays [cell] (0..8, row by row) for [player]. Returns an error message, or null if the move was made. */
    fun play(player: String, cell: Int): String? {
        if (status != Status.PLAYING) return "The game is over"
        val mark = when (player) {
            xPlayer -> 'X'
            oPlayer -> 'O'
            else -> return "You are not in this game"
        }
        if (mark != turn) return "Not your turn"
        if (cell !in cells.indices) return "Invalid cell"
        if (cells[cell] != '.') return "That cell is already taken"

        cells[cell] = mark
        val winningLine = LINES.firstOrNull { l -> l.all { cells[it] == mark } }
        when {
            winningLine != null -> {
                status = Status.WON
                winner = player
                line = winningLine
            }
            cells.none { it == '.' } -> status = Status.DRAW
            else -> turn = if (turn == 'X') 'O' else 'X'
        }
        return null
    }

    fun toState() = GameState(
        game = ID,
        board = String(cells),
        xPlayer = xPlayer,
        oPlayer = oPlayer,
        turn = turn.toString(),
        status = status.name.lowercase(),
        winner = winner,
        line = line,
    )

    /** Short human-readable summary, for clients that only show text. */
    fun describe(): String = when (status) {
        Status.PLAYING -> "Tic-Tac-Toe: ${if (turn == 'X') xPlayer else oPlayer}'s turn ($turn)"
        Status.WON -> "Tic-Tac-Toe: $winner won"
        Status.DRAW -> "Tic-Tac-Toe: draw"
    }

    companion object {
        const val ID = "tictactoe"

        private val LINES = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // rows
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // columns
            listOf(0, 4, 8), listOf(2, 4, 6),                  // diagonals
        )
    }
}
