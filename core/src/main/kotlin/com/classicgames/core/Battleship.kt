package com.classicgames.core

/**
 * Battleship on a 10x10 board. Each player places a fleet of 5 ships (sizes 5, 4, 3, 3, 2), then the
 * players take turns firing at one cell of the opponent's board. The first to sink the whole enemy
 * fleet wins. [first] fires first.
 *
 * Cells are numbered row by row: cell = row * 10 + column (0 is the top-left corner).
 */
class Battleship(val first: String, val second: String) : Game {
    enum class Phase { PLACING, PLAYING, FINISHED }

    private class Ship(val name: String, val cells: List<Int>) {
        val hits = HashSet<Int>()
        val sunk get() = hits.size == cells.size
    }

    private class Fleet {
        var ships: List<Ship>? = null
        /** Cells the opponent has fired at on this player's board. */
        val incoming = HashSet<Int>()
    }

    private class LastShot(val shooter: String, val cell: Int, val hit: Boolean, val sunkShip: String?)

    var phase = Phase.PLACING
        private set
    var turn: String = first
        private set
    var winner: String? = null
        private set

    private val fleets = mapOf(first to Fleet(), second to Fleet())
    private var last: LastShot? = null

    override val finished get() = phase == Phase.FINISHED

    private fun other(player: String) = if (player == first) second else first

    override fun handle(player: String, msg: ClientMessage): String? {
        val mine = fleets[player] ?: return "You are not in this game"
        return when (msg.type) {
            "place" -> place(mine, msg.ships)
            "move" -> fire(player, msg.cell ?: -1)
            else -> "Battleship understands 'place' and 'move'"
        }
    }

    private fun place(mine: Fleet, placements: List<ShipPlacement>?): String? {
        if (phase != Phase.PLACING) return "The fleets are already placed"
        if (mine.ships != null) return "You already placed your fleet"
        validate(placements)?.let { return it }

        // Biggest first, so the two size-3 ships get the names Cruiser and Submarine in order.
        mine.ships = placements!!.sortedByDescending { it.cells.size }
            .mapIndexed { i, p -> Ship(NAMES[i], p.cells.sorted()) }
        if (fleets.values.all { it.ships != null }) {
            phase = Phase.PLAYING
            turn = first
        }
        return null
    }

    private fun validate(placements: List<ShipPlacement>?): String? {
        if (placements == null) return "Send your ships with the move"
        if (placements.map { it.cells.size }.sorted() != FLEET.sorted()) {
            return "Your fleet must be ships of sizes ${FLEET.joinToString(", ")}"
        }
        val used = HashSet<Int>()
        for (ship in placements) {
            val cells = ship.cells.sorted()
            if (cells.any { it !in 0 until CELLS }) return "A ship is outside the board"
            val sameRow = cells.map { it / SIZE }.toSet().size == 1
            val sameColumn = cells.map { it % SIZE }.toSet().size == 1
            val horizontal = sameRow && cells.zipWithNext().all { (a, b) -> b - a == 1 }
            val vertical = sameColumn && cells.zipWithNext().all { (a, b) -> b - a == SIZE }
            if (!horizontal && !vertical) return "Ships must be straight and unbroken"
            for (c in cells) if (!used.add(c)) return "Ships cannot overlap"
        }
        return null
    }

    private fun fire(player: String, cell: Int): String? {
        when (phase) {
            Phase.PLACING -> return "Place your fleet first"
            Phase.FINISHED -> return "The game is over"
            Phase.PLAYING -> {}
        }
        if (player != turn) return "Not your turn"
        if (cell !in 0 until CELLS) return "Invalid cell"

        val target = fleets.getValue(other(player))
        if (cell in target.incoming) return "You already fired there"
        target.incoming += cell

        val ship = target.ships!!.firstOrNull { cell in it.cells }
        ship?.hits?.add(cell)
        last = LastShot(player, cell, hit = ship != null, sunkShip = ship?.takeIf { it.sunk }?.name)

        if (target.ships!!.all { it.sunk }) {
            phase = Phase.FINISHED
            winner = player
        } else {
            turn = other(player) // turns alternate after every shot, hit or miss
        }
        return null
    }

    override fun messageFor(player: String): ServerMessage {
        val me = fleets.getValue(player)
        val opponent = fleets.getValue(other(player))

        // My own board: my ships, plus whatever the opponent has fired at.
        val myBoard = CharArray(CELLS) { '.' }
        me.ships?.forEach { s -> s.cells.forEach { myBoard[it] = 'S' } }
        me.incoming.forEach { myBoard[it] = if (myBoard[it] == 'S') 'H' else 'M' }

        // My view of the opponent's board: only what my shots revealed (everything once the game is over).
        val shots = CharArray(CELLS) { '.' }
        if (finished) opponent.ships?.forEach { s -> s.cells.forEach { shots[it] = 'S' } }
        for (c in opponent.incoming) {
            val ship = opponent.ships?.firstOrNull { c in it.cells }
            shots[c] = when {
                ship == null -> 'M'
                ship.sunk -> 'X'
                else -> 'H'
            }
        }

        val state = BattleState(
            phase = phase.name.lowercase(),
            you = player,
            opponent = other(player),
            youReady = me.ships != null,
            opponentReady = opponent.ships != null,
            yourTurn = phase == Phase.PLAYING && turn == player,
            myBoard = String(myBoard),
            shots = String(shots),
            myShipsLeft = me.ships?.filter { !it.sunk }?.map { it.cells.size }.orEmpty(),
            enemyShipsLeft = opponent.ships?.filter { !it.sunk }?.map { it.cells.size }.orEmpty(),
            winner = winner,
            event = describe(player),
        )
        return ServerMessage(type = "game", from = "server", text = state.event, battle = state)
    }

    /** One sentence about the latest happening, from [player]'s point of view. */
    private fun describe(player: String): String {
        val opponent = other(player)
        val shot = last
        if (shot == null) {
            return when (phase) {
                Phase.PLACING -> {
                    val mineReady = fleets.getValue(player).ships != null
                    val theirsReady = fleets.getValue(opponent).ships != null
                    when {
                        mineReady && !theirsReady -> "Waiting for $opponent to place their fleet"
                        !mineReady && theirsReady -> "$opponent is ready. Place your fleet!"
                        else -> "Place your fleet"
                    }
                }
                else -> if (turn == player) "Both fleets are ready. You fire first!" else "Both fleets are ready. $turn fires first"
            }
        }
        val where = "${'A' + shot.cell % SIZE}${shot.cell / SIZE + 1}"
        return if (shot.shooter == player) {
            when {
                shot.sunkShip != null -> "You sank their ${shot.sunkShip}!"
                shot.hit -> "Hit at $where!"
                else -> "Miss at $where"
            }
        } else {
            when {
                shot.sunkShip != null -> "${shot.shooter} sank your ${shot.sunkShip}!"
                shot.hit -> "${shot.shooter} hit your ship at $where"
                else -> "${shot.shooter} missed at $where"
            }
        }
    }

    companion object {
        const val ID = "battleship"
        const val SIZE = 10
        const val CELLS = SIZE * SIZE
        val FLEET = listOf(5, 4, 3, 3, 2)
        private val NAMES = listOf("Carrier", "Battleship", "Cruiser", "Submarine", "Destroyer")
    }
}
