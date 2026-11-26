package com.onevonegames.core

/**
 * Quarto on a 4 x 4 board with 16 different pieces. Every piece is tall or short, dark or light, square or round, and
 * hollow or solid, one piece for each mix of the four (see [QuartoState] for how pieces are numbered). Pieces belong to
 * nobody: you choose the piece your opponent has to place, and they choose yours.
 *
 * [first] starts by choosing a piece from the pool for [second]. From then on every turn is: place the piece you were
 * given on an empty cell, then choose a piece from the pool for your opponent. Placing a piece that completes a row, a
 * column or one of the two diagonals of four pieces sharing at least one trait (four tall ones, four hollow ones, ...)
 * wins for the player who placed it. When the 16th piece fills the board without such a line, the game is a draw.
 *
 * Cells are numbered row by row from the top-left: cell = row * 4 + column.
 *
 * [board] (16 entries, -1 for an empty cell, row by row) and [piece] (the piece already handed to the player who
 * places next) exist so tests can set up a position; rooms always start with an empty board. Who acts next follows
 * from the position: the second player places the 1st, 3rd, ... piece and the first player the 2nd, 4th, ..., and the
 * player who placed last chooses next.
 */
class Quarto(val first: String, val second: String, board: List<Int>? = null, piece: Int? = null) : Game {
    enum class Phase { CHOOSE, PLACE, FINISHED }
    enum class Status { PLAYING, WON, DRAW }

    private val cells = (board ?: List(CELLS) { -1 }).toIntArray()

    /** The piece handed to the player who must place it, or -1 when someone has to choose one (or it is over). */
    var piece = piece ?: -1
        private set

    var phase: Phase
        private set
    var status = Status.PLAYING
        private set
    var winner: String? = null
        private set

    /** '1' or '2': the player who must choose or place now (after the end, the one who acted last). */
    var turn: Char
        private set

    /** The cell the last piece went on, or -1 before the first one. */
    var lastCell = -1
        private set

    /** The winning line's four cells; empty unless someone won. */
    var line: List<Int> = emptyList()
        private set

    /** The traits every piece of the winning line has, as words ("tall", "hollow", ...); empty unless someone won. */
    var sharedTraits: List<String> = emptyList()
        private set

    init {
        require(cells.size == CELLS) { "the board has $CELLS cells" }
        val placed = cells.filter { it >= 0 }
        require(cells.all { it in -1 until PIECES } && placed.toSet().size == placed.size) {
            "every cell is -1 or a different piece 0 until $PIECES"
        }
        require(this.piece in -1 until PIECES && this.piece !in placed) { "the piece in hand must be in the pool" }
        require(LINES.none { sharedBy(it).isNotEmpty() }) { "the position is already won" }
        require(placed.size < CELLS || this.piece < 0) { "nobody can be handed a piece on a full board" }

        // The second player places the 1st, 3rd, ... piece: an even number placed so far means it is theirs.
        val secondPlaces = placed.size % 2 == 0
        if (this.piece >= 0) {
            phase = Phase.PLACE
            turn = if (secondPlaces) '2' else '1'
        } else {
            // Whoever placed last chooses; before anything is placed, that is the first player.
            phase = Phase.CHOOSE
            turn = if (secondPlaces) '1' else '2'
        }
        if (placed.size == CELLS) {
            phase = Phase.FINISHED
            status = Status.DRAW
        }
    }

    override val finished get() = status != Status.PLAYING

    /** The pieces nobody has placed or been handed yet, smallest first. */
    val pool: List<Int>
        get() = (0 until PIECES).filter { it !in cells && it != piece }

    /**
     * "move" with [ClientMessage.cell] places the piece you were given, and "move" with [ClientMessage.piece] hands a
     * piece from the pool to your opponent; see [ClientMessage].
     */
    override fun handle(player: String, msg: ClientMessage): String? {
        if (msg.type != "move") return "Quarto only understands moves"
        val cell = msg.cell
        val chosen = msg.piece
        return when {
            cell != null && chosen == null -> place(player, cell)
            chosen != null && cell == null -> give(player, chosen)
            else -> "Send a cell to place your piece, or a piece to give"
        }
    }

    /** [player] hands [chosen] from the pool to the opponent. Returns an error message, or null if it was done. */
    fun give(player: String, chosen: Int): String? {
        refusal(player)?.let { return it }
        if (phase == Phase.PLACE) return "Place the piece you were given first"
        if (chosen !in 0 until PIECES) return "Invalid piece"
        if (chosen !in pool) return "That piece is not in the pool"

        piece = chosen
        phase = Phase.PLACE
        turn = other(turn)
        return null
    }

    /** [player] puts the piece they were given on [cell]. Returns an error message, or null if it was placed. */
    fun place(player: String, cell: Int): String? {
        refusal(player)?.let { return it }
        if (phase == Phase.CHOOSE) return "Choose a piece for ${nameOf(other(turn))} first"
        if (cell !in 0 until CELLS) return "Invalid cell"
        if (cells[cell] >= 0) return "That cell is taken"

        cells[cell] = piece
        piece = -1
        lastCell = cell
        // Only lines through this cell can be new. If it completes several, the first in LINES is the one shown.
        val won = LINES.filter { cell in it }.firstNotNullOfOrNull { l -> sharedBy(l).takeIf { it.isNotEmpty() }?.let { l to it } }
        when {
            won != null -> {
                phase = Phase.FINISHED
                status = Status.WON
                winner = nameOf(turn)
                line = won.first
                sharedTraits = won.second
            }
            cells.none { it < 0 } -> {
                phase = Phase.FINISHED
                status = Status.DRAW
            }
            else -> phase = Phase.CHOOSE
        }
        return null
    }

    /** Why [player] may not act now, or null if they may. */
    private fun refusal(player: String): String? {
        val mark = when (player) {
            first -> '1'
            second -> '2'
            else -> null
        }
        return when {
            status != Status.PLAYING -> "The game is over"
            mark == null -> "You are not in this game"
            mark != turn -> "Not your turn"
            else -> null
        }
    }

    /** The traits all four pieces on [cellsOfLine] share, as words; empty if a cell is empty or they share none. */
    private fun sharedBy(cellsOfLine: List<Int>): List<String> {
        val pieces = cellsOfLine.map { cells[it] }
        if (pieces.any { it < 0 }) return emptyList()
        return TRAITS.indices.mapNotNull { bit ->
            val mask = 1 shl bit
            when {
                pieces.all { it and mask != 0 } -> TRAITS[bit].first
                pieces.all { it and mask == 0 } -> TRAITS[bit].second
                else -> null
            }
        }
    }

    private fun other(mark: Char) = if (mark == '1') '2' else '1'
    private fun nameOf(mark: Char) = if (mark == '1') first else second

    override fun messageFor(player: String) =
        ServerMessage(type = "game", from = "server", text = describe(), quarto = toState())

    fun toState() = QuartoState(
        game = ID,
        board = cells.toList(),
        pool = pool,
        piece = piece,
        firstPlayer = first,
        secondPlayer = second,
        turn = turn.toString(),
        phase = phase.name.lowercase(),
        status = status.name.lowercase(),
        winner = winner,
        lastCell = lastCell,
        line = line,
        sharedTraits = sharedTraits,
    )

    /** Short human-readable summary, for clients that only show text. */
    fun describe(): String = when (status) {
        Status.WON -> "Quarto: $winner won with four ${sharedTraits.joinToString(", ")} pieces"
        Status.DRAW -> "Quarto: draw, the board is full"
        Status.PLAYING -> when (phase) {
            Phase.CHOOSE -> "Quarto: ${nameOf(turn)} chooses a piece for ${nameOf(other(turn))}"
            else -> "Quarto: ${nameOf(turn)} places piece $piece"
        }
    }

    companion object {
        const val ID = "quarto"

        const val CELLS = 16
        const val PIECES = 16

        /**
         * The four traits by bit, as (word when the bit is set, word when it is clear): bit 0 (1) tall/short, bit 1 (2)
         * dark/light, bit 2 (4) square/round, bit 3 (8) hollow/solid.
         */
        val TRAITS = listOf("tall" to "short", "dark" to "light", "square" to "round", "hollow" to "solid")

        /** Every line of four: the rows, the columns, then the diagonal from the top-left and the one from the top-right. */
        val LINES: List<List<Int>> = buildList {
            for (row in 0 until 4) add((0 until 4).map { row * 4 + it })
            for (column in 0 until 4) add((0 until 4).map { it * 4 + column })
            add(listOf(0, 5, 10, 15))
            add(listOf(3, 6, 9, 12))
        }
    }
}
