package com.onevonegames.core

/**
 * Oware (Abapa rules), a mancala game on 12 pits with 4 seeds each.
 *  - On your turn, take every seed from one of your own non-empty pits and sow them one by one into the following
 *    pits, counter-clockwise. With 12 or more seeds the starting pit is skipped on every lap.
 *  - If the last seed lands in an opponent's pit that now holds 2 or 3 seeds, you capture them, and so on backwards
 *    (clockwise) for every pit before it that is on the opponent's side and holds 2 or 3, stopping at the first pit
 *    that doesn't qualify or at your own side.
 *  - Grand slam: a move that would capture every seed on the opponent's side is played, but captures nothing.
 *  - If the opponent has no seeds, you must give them some. If no move can, the game ends and you take the seeds
 *    left on your side.
 *  - Capturing more than half of the seeds ([WINNING_CAPTURES]) wins at once, and 24-24 is a draw. To stop
 *    endless cycles, after [MAX_PLIES_WITHOUT_CAPTURE] moves in a row without a capture the game ends, each player
 *    takes the seeds on their own side, and the totals decide.
 * There are no stores in the sowing loop and no extra turns. [south] moves first.
 *
 * Pits are numbered 0..11 counter-clockwise: [south] owns 0..5 (the bottom row, left to right) and [north] owns
 * 6..11 (the top row, right to left). A move is just the pit number.
 *
 * [startPits] (12 seed counts), [startTurn] ('S' or 'N') and the captured totals exist so positions can be set up
 * directly, e.g. for tests; by default the game starts with [SEEDS_PER_PIT] seeds in every pit.
 */
class Oware(
    val south: String,
    val north: String,
    startPits: List<Int> = STANDARD_START,
    startTurn: Char = 'S',
    startSouthCaptured: Int = 0,
    startNorthCaptured: Int = 0,
) : Game {
    enum class Status { PLAYING, WON, DRAW }

    private val pits = startPits.toIntArray().also {
        require(it.size == PITS) { "the board must have $PITS pits" }
        require(it.all { seeds -> seeds >= 0 }) { "a pit can't hold a negative number of seeds" }
    }

    /** The side to move: 'S' (south) or 'N' (north). */
    var turn = startTurn
        private set
    var status = Status.PLAYING
        private set
    var winner: String? = null
        private set

    var southCaptured = startSouthCaptured
        private set
    var northCaptured = startNorthCaptured
        private set

    /** The pit sown from last, or -1 before the first move. */
    var lastPit = -1
        private set

    /** Pits the last move captured from. */
    var lastCaptured: List<Int> = emptyList()
        private set

    /** What just happened, in a sentence or two (empty when there is nothing to say). */
    var event = ""
        private set

    private var pliesWithoutCapture = 0

    init {
        resolve(emptyList()) // a position handed in may already need feeding, or be over
    }

    override val finished get() = status != Status.PLAYING

    override fun handle(player: String, msg: ClientMessage): String? =
        if (msg.type == "move") play(player, msg.cell ?: -1) else "Oware only understands moves"

    /** Sows the seeds of [pit] (0..11) for [player]. Returns an error message, or null if the move was made. */
    fun play(player: String, pit: Int): String? {
        if (status != Status.PLAYING) return "The game is over"
        val side = when (player) {
            south -> 'S'
            north -> 'N'
            else -> return "You are not in this game"
        }
        if (side != turn) return "Not your turn"
        if (pit !in 0 until PITS) return "Invalid pit"
        if (ownerOf(pit) != side) return "Pick one of your own pits"
        if (pits[pit] == 0) return "That pit is empty"
        if (pit !in legalMoves(side)) return "${nameOf(other(side))} has no seeds: you must give them some"

        // Sow, skipping the starting pit on every lap.
        var seeds = pits[pit]
        pits[pit] = 0
        var at = pit
        while (seeds > 0) {
            at = (at + 1) % PITS
            if (at == pit) continue
            pits[at]++
            seeds--
        }

        // Capture backwards from the last seed while the pits are the opponent's and hold 2 or 3.
        val enemy = other(side)
        val taken = ArrayList<Int>()
        while (ownerOf(at) == enemy && pits[at] in 2..3) {
            taken += at
            at = (at - 1 + PITS) % PITS
        }
        val events = ArrayList<String>()
        val grandSlam = taken.isNotEmpty() && pitsOf(enemy).all { it in taken || pits[it] == 0 }
        if (grandSlam) {
            taken.clear()
            events += "Grand slam: nothing captured"
        }
        val gained = taken.sumOf { pits[it] }
        taken.forEach { pits[it] = 0 }
        if (side == 'S') southCaptured += gained else northCaptured += gained
        if (gained > 0) events += "$player captured $gained seeds"

        lastPit = pit
        lastCaptured = taken.sorted()
        pliesWithoutCapture = if (gained > 0) 0 else pliesWithoutCapture + 1
        turn = enemy
        resolve(events)
        return null
    }

    /**
     * The pits [side] may sow from right now: their non-empty pits, or, when the opponent has no seeds, only those
     * that give the opponent at least one.
     */
    fun legalMoves(side: Char): List<Int> {
        val own = pitsOf(side).filter { pits[it] > 0 }
        if (seedsOn(other(side)) > 0) return own
        // Pit p is PITS_PER_SIDE - p % PITS_PER_SIDE pits away from the opponent's first pit.
        return own.filter { pits[it] >= PITS_PER_SIDE - it % PITS_PER_SIDE }
    }

    /** Ends the game if it is over after a move (or in a position handed in), and finishes the [event] text. */
    private fun resolve(news: List<String>) {
        val events = news.toMutableList()
        val mover = nameOf(turn)
        val waiting = nameOf(other(turn))
        when {
            southCaptured >= WINNING_CAPTURES -> finish(south)
            northCaptured >= WINNING_CAPTURES -> finish(north)
            southCaptured == TOTAL_SEEDS / 2 && northCaptured == TOTAL_SEEDS / 2 -> finish(null)
            pliesWithoutCapture >= MAX_PLIES_WITHOUT_CAPTURE -> {
                events += "$MAX_PLIES_WITHOUT_CAPTURE moves without a capture: each player takes their own seeds"
                takeOwnSeedsAndFinish()
            }
            legalMoves(turn).isEmpty() -> {
                // In play this only happens when the opponent has no seeds and no move reaches them.
                events += if (seedsOn(other(turn)) == 0) {
                    "$waiting can't be fed: $mover takes the rest"
                } else {
                    "$mover can't move: each player takes their own seeds"
                }
                takeOwnSeedsAndFinish()
            }
            seedsOn(other(turn)) == 0 -> events += "$waiting has no seeds: $mover must give them some"
        }
        event = events.joinToString(". ")
    }

    private fun takeOwnSeedsAndFinish() {
        southCaptured += seedsOn('S')
        northCaptured += seedsOn('N')
        pits.fill(0)
        finish(
            when {
                southCaptured > northCaptured -> south
                northCaptured > southCaptured -> north
                else -> null
            },
        )
    }

    private fun finish(winnerName: String?) {
        if (winnerName == null) {
            status = Status.DRAW
        } else {
            status = Status.WON
            winner = winnerName
        }
    }

    private fun ownerOf(pit: Int) = if (pit < PITS_PER_SIDE) 'S' else 'N'
    private fun pitsOf(side: Char) = if (side == 'S') 0 until PITS_PER_SIDE else PITS_PER_SIDE until PITS
    private fun seedsOn(side: Char) = pitsOf(side).sumOf { pits[it] }
    private fun other(side: Char) = if (side == 'S') 'N' else 'S'
    private fun nameOf(side: Char) = if (side == 'S') south else north

    override fun messageFor(player: String) =
        ServerMessage(type = "game", from = "server", text = describe(), oware = toState())

    fun toState() = OwareState(
        game = ID,
        pits = pits.toList(),
        southPlayer = south,
        northPlayer = north,
        southCaptured = southCaptured,
        northCaptured = northCaptured,
        turn = turn.toString(),
        status = status.name.lowercase(),
        winner = winner,
        legalMoves = if (status == Status.PLAYING) legalMoves(turn) else emptyList(),
        lastPit = lastPit,
        capturedPits = lastCaptured,
        event = event,
    )

    /** Short human-readable summary, for clients that only show text. */
    fun describe(): String {
        val score = "$southCaptured-$northCaptured"
        val news = if (event.isEmpty()) "" else "$event. "
        return when (status) {
            Status.PLAYING -> "Oware: $news${nameOf(turn)}'s turn (${if (turn == 'S') "south" else "north"}), $score"
            Status.WON -> "Oware: $news$winner won $score"
            Status.DRAW -> "Oware: ${news}draw $score"
        }
    }

    companion object {
        const val ID = "oware"
        const val PITS = 12
        const val PITS_PER_SIDE = PITS / 2
        const val SEEDS_PER_PIT = 4
        const val TOTAL_SEEDS = PITS * SEEDS_PER_PIT

        /** Captured seeds that win at once: more than half of them. */
        const val WINNING_CAPTURES = TOTAL_SEEDS / 2 + 1

        /** Moves in a row without a capture after which the game ends and each player takes their own seeds. */
        const val MAX_PLIES_WITHOUT_CAPTURE = 100

        val STANDARD_START: List<Int> = List(PITS) { SEEDS_PER_PIT }
    }
}
