package com.onevonegames.core

import java.security.SecureRandom
import java.util.Random

/**
 * Maze Guide, a cooperative game: the two players win or lose together.
 *
 * Every game has a new perfect maze of [SIZE] x [SIZE] cells: exactly one way between any two cells. The [guide] (the
 * first player) sees all of it: the walls, the walker, their trail and the exit. The [walker] (the second player) sees
 * only the 3 x 3 cells around them, with those cells' walls, and the exit once it is one of them. The walker moves one
 * cell up, down, left or right with each "move"; a move into a wall is refused and costs nothing. The guide may send a
 * direction as a "hint" at any time, and both can use the room chat.
 *
 * The walker starts at one end of a longest path in the maze and the exit is at the other end. They have
 * ceil(1.5 x the shortest way out) moves: reaching the exit within them wins, using up the last one anywhere else loses.
 * Once the game is over both players see the whole maze.
 *
 * [layout] exists so tests can choose the maze; otherwise one is generated with [random], a SecureRandom in rooms.
 */
class Maze(
    val guide: String,
    val walker: String,
    layout: Layout? = null,
    random: Random = SecureRandom(),
) : Game {
    enum class Status { PLAYING, WON, LOST }

    /**
     * A perfect maze of [size] x [size] cells numbered row by row from the top-left, with one wall mask per cell in
     * [walls] (as described on [MazeState]), the walker's [start] and the [exit].
     */
    class Layout(val size: Int, val walls: List<Int>, val start: Int, val exit: Int) {
        init {
            require(size >= 2) { "a maze needs at least 2 x 2 cells" }
            require(isPerfect(size, walls)) { "the walls must make a perfect maze, walled all round" }
            require(start in walls.indices && exit in walls.indices && start != exit) { "start and exit must be two cells of the maze" }
        }
    }

    val size: Int
    private val walls: IntArray
    val start: Int
    val exit: Int

    /** Moves on the shortest way from [start] to [exit]. */
    val shortest: Int

    /** The most moves the walker may make: ceil(1.5 x [shortest]). */
    val moveLimit: Int

    init {
        val maze = layout ?: generate(SIZE, random)
        size = maze.size
        walls = maze.walls.toIntArray()
        start = maze.start
        exit = maze.exit
        shortest = distances(size, maze.walls, start)[exit]
        moveLimit = (3 * shortest + 1) / 2
    }

    /** Every cell the walker has stood on, in order, from [start] to where they are now. */
    private val trail = mutableListOf(start)

    val position get() = trail.last()
    val movesUsed get() = trail.size - 1

    var status = Status.PLAYING
        private set

    /** The direction the guide sent last (null before the first), the moves used when it was sent, and how many in all. */
    private var hint: String? = null
    private var hintAt = 0
    private var hints = 0

    override val finished get() = status != Status.PLAYING

    override fun handle(player: String, msg: ClientMessage): String? = when (msg.type) {
        "move" -> move(player, msg.direction)
        "hint" -> hint(player, msg.direction)
        else -> "Maze Guide understands 'move' and 'hint'"
    }

    /** The walker steps one cell in [direction]. Returns an error message, or null if they moved. */
    fun move(player: String, direction: String?): String? {
        refusal(player)?.let { return it }
        if (player != walker) return "Only $walker moves: send them hints"
        val d = DIRECTIONS.indexOf(direction)
        if (d < 0) return "Send a direction: up, down, left or right"
        if (walls[position] and WALL_BITS[d] != 0) return "There's a wall that way"

        trail += neighbour(size, position, d)
        when {
            position == exit -> status = Status.WON
            movesUsed >= moveLimit -> status = Status.LOST
        }
        return null
    }

    /** The guide tells the walker to go [direction]. Returns an error message, or null if the hint was sent. */
    fun hint(player: String, direction: String?): String? {
        refusal(player)?.let { return it }
        if (player != guide) return "Only $guide gives hints"
        val d = DIRECTIONS.indexOf(direction)
        if (d < 0) return "Send a direction: up, down, left or right"
        hint = DIRECTIONS[d]
        hintAt = movesUsed
        hints++
        return null
    }

    /** Why [player] may not act now, or null if they may. */
    private fun refusal(player: String) = when {
        status != Status.PLAYING -> "The game is over"
        player != guide && player != walker -> "You are not in this game"
        else -> null
    }

    /** The 3 x 3 cells around the walker, row by row: each cell's walls, or -1 past the edge of the maze. */
    private fun windowCells(): List<Int> {
        val row = position / size
        val column = position % size
        return (-1..1).flatMap { dr ->
            (-1..1).map { dc ->
                val r = row + dr
                val c = column + dc
                if (r in 0 until size && c in 0 until size) r * size + c else -1
            }
        }
    }

    override fun messageFor(player: String) =
        ServerMessage(type = "game", from = "server", text = describe(), maze = toState(player))

    /**
     * The game as [player] sees it: the guide gets the whole maze, the walker only the window around them until the
     * game is over.
     */
    fun toState(player: String): MazeState {
        val seesAll = player == guide || status != Status.PLAYING
        val window = windowCells()
        return MazeState(
            game = ID,
            you = player,
            role = if (player == guide) "guide" else "walker",
            guide = guide,
            walker = walker,
            size = size,
            walls = if (seesAll) walls.toList() else emptyList(),
            start = if (seesAll) start else -1,
            exit = if (seesAll) exit else -1,
            position = if (seesAll) position else -1,
            trail = if (seesAll) trail.toList() else emptyList(),
            window = window.map { if (it < 0) -1 else walls[it] },
            windowExit = window.indexOf(exit),
            movesUsed = movesUsed,
            moveLimit = moveLimit,
            hint = hint.orEmpty(),
            hintAt = hintAt,
            hints = hints,
            status = status.name.lowercase(),
            shortest = if (status == Status.PLAYING) 0 else shortest,
            result = result(),
        )
    }

    /** How the game ended, the same sentence for both players (empty while playing). */
    private fun result(): String = when (status) {
        Status.PLAYING -> ""
        Status.WON ->
            if (movesUsed == shortest) "Out in $movesUsed moves: the shortest way, a perfect run"
            else "Out in $movesUsed moves; the shortest way is $shortest"
        Status.LOST -> {
            val left = distances(size, walls.toList(), position)[exit]
            "Out of moves: $movesUsed used, still ${steps(left)} from the exit; the shortest way was $shortest"
        }
    }

    /** Short human-readable summary, for clients that only show text. It never says where the walker is. */
    fun describe(): String = when (status) {
        Status.PLAYING -> "Maze Guide: $guide guides $walker, $movesUsed of $moveLimit moves used"
        Status.WON -> "Maze Guide: $walker got out in $movesUsed moves (shortest $shortest)"
        Status.LOST -> "Maze Guide: out of moves after $movesUsed (shortest $shortest)"
    }

    companion object {
        const val ID = "maze"

        /** Cells per side in rooms. */
        const val SIZE = 8

        /** Direction names by number, the order of the wall bits. */
        val DIRECTIONS = listOf("up", "right", "down", "left")

        /** The wall bit of each direction: 1 up, 2 right, 4 down, 8 left. */
        private val WALL_BITS = listOf(1, 2, 4, 8)
        private const val ALL_WALLS = 15

        private fun steps(n: Int) = if (n == 1) "1 step" else "$n steps"

        /** The cell next to [cell] in direction [d] (0 up, 1 right, 2 down, 3 left), or -1 past the edge. */
        fun neighbour(size: Int, cell: Int, d: Int): Int {
            val row = cell / size
            val column = cell % size
            return when (d) {
                0 -> if (row > 0) cell - size else -1
                1 -> if (column < size - 1) cell + 1 else -1
                2 -> if (row < size - 1) cell + size else -1
                else -> if (column > 0) cell - 1 else -1
            }
        }

        /**
         * A new perfect maze, by randomized Prim: it grows from one random cell by opening a random wall between the
         * maze so far and a cell outside it, until it holds every cell. That opens exactly one way to each new cell, so
         * there is always exactly one way between two cells. The start is the cell farthest from a random cell and the
         * exit the cell farthest from the start, which makes them the two ends of a longest path.
         */
        fun generate(size: Int, random: Random): Layout {
            val cells = size * size
            val walls = IntArray(cells) { ALL_WALLS }
            val inMaze = BooleanArray(cells)
            // Walls between a cell of the maze and its neighbour in a direction: (cell, direction).
            val frontier = ArrayList<Pair<Int, Int>>()
            fun take(cell: Int) {
                inMaze[cell] = true
                for (d in 0 until 4) if (neighbour(size, cell, d) >= 0) frontier += cell to d
            }
            take(random.nextInt(cells))
            while (frontier.isNotEmpty()) {
                val i = random.nextInt(frontier.size)
                val (cell, d) = frontier[i]
                frontier[i] = frontier[frontier.lastIndex]
                frontier.removeAt(frontier.lastIndex)
                val next = neighbour(size, cell, d)
                if (inMaze[next]) continue
                walls[cell] = walls[cell] and WALL_BITS[d].inv()
                walls[next] = walls[next] and WALL_BITS[(d + 2) % 4].inv()
                take(next)
            }
            val list = walls.toList()
            val start = farthest(distances(size, list, random.nextInt(cells)))
            return Layout(size, list, start, farthest(distances(size, list, start)))
        }

        /** Moves from [from] to every cell without crossing a wall, by cell (-1 where it can't be reached). */
        fun distances(size: Int, walls: List<Int>, from: Int): IntArray {
            val dist = IntArray(size * size) { -1 }
            val queue = ArrayDeque<Int>()
            dist[from] = 0
            queue += from
            while (queue.isNotEmpty()) {
                val cell = queue.removeFirst()
                for (d in 0 until 4) {
                    if (walls[cell] and WALL_BITS[d] != 0) continue
                    val next = neighbour(size, cell, d)
                    if (next >= 0 && dist[next] < 0) {
                        dist[next] = dist[cell] + 1
                        queue += next
                    }
                }
            }
            return dist
        }

        /** The cell with the largest distance (the first one on a tie). */
        private fun farthest(dist: IntArray) = dist.indices.maxBy { dist[it] }

        /**
         * True when [walls] describe a perfect maze of [size] x [size] cells: masks 0..15, walled all round the edge,
         * the two sides of every inner wall agreeing, every cell reachable and no loops (exactly cells - 1 openings).
         */
        fun isPerfect(size: Int, walls: List<Int>): Boolean {
            if (size < 1 || walls.size != size * size || walls.any { it !in 0..ALL_WALLS }) return false
            var openings = 0
            for (cell in walls.indices) for (d in 0 until 4) {
                val next = neighbour(size, cell, d)
                val walled = walls[cell] and WALL_BITS[d] != 0
                if (next < 0) {
                    if (!walled) return false
                } else {
                    if (walled != (walls[next] and WALL_BITS[(d + 2) % 4] != 0)) return false
                    if (!walled) openings++
                }
            }
            return openings / 2 == walls.size - 1 && distances(size, walls, 0).none { it < 0 }
        }
    }
}
