package com.onevonegames.core

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ann is the guide and Bob the walker. Walls are bit masks per cell, 1 up, 2 right, 4 down, 8 left (see [MazeState]).
 * Most tests use [serpentine], a 3 x 3 maze with a single winding corridor, so every move is known in advance.
 */
class MazeTest {
    private val ann = "Ann"
    private val bob = "Bob"

    /**
     * ```
     *   0 → 1 → 2
     *           ↓
     *   3 ← 4 ← 5
     *   ↓
     *   6 → 7 → 8
     * ```
     * One corridor from the start (0) to the exit (8): 8 moves, so the limit is ceil(1.5 x 8) = 12.
     */
    private val serpentine = Maze.Layout(3, listOf(13, 5, 3, 9, 5, 6, 12, 5, 7), start = 0, exit = 8)
    private val wayOut = listOf("right", "right", "down", "left", "left", "down", "right", "right")

    private fun small() = Maze(ann, bob, serpentine)

    private val deltas = mapOf("up" to (-1 to 0), "right" to (0 to 1), "down" to (1 to 0), "left" to (0 to -1))
    private val bits = mapOf("up" to 1, "right" to 2, "down" to 4, "left" to 8)

    /** Checked here from scratch rather than with Maze.isPerfect: borders, matching sides, connected, no loops. */
    private fun assertPerfect(size: Int, walls: List<Int>) {
        assertEquals(size * size, walls.size)
        var openings = 0
        for (cell in walls.indices) {
            assertTrue(walls[cell] in 0..15, "cell $cell has walls ${walls[cell]}")
            val row = cell / size
            val column = cell % size
            for ((name, delta) in deltas) {
                val r = row + delta.first
                val c = column + delta.second
                val walled = walls[cell] and bits.getValue(name) != 0
                if (r !in 0 until size || c !in 0 until size) {
                    assertTrue(walled, "cell $cell is open $name, out of the maze")
                    continue
                }
                val opposite = mapOf("up" to "down", "down" to "up", "left" to "right", "right" to "left").getValue(name)
                assertEquals(walled, walls[r * size + c] and bits.getValue(opposite) != 0, "the wall $name of cell $cell")
                if (!walled) openings++
            }
        }
        assertEquals(size * size - 1, openings / 2, "a perfect maze is a tree: one opening fewer than cells")
        assertTrue(Maze.distances(size, walls, 0).all { it >= 0 }, "every cell can be reached")
    }

    /** What the walker should see: the 3 x 3 cells around [position], -1 past the edge. */
    private fun expectedWindow(size: Int, walls: List<Int>, position: Int): List<Int> =
        (-1..1).flatMap { dr ->
            (-1..1).map { dc ->
                val r = position / size + dr
                val c = position % size + dc
                if (r in 0 until size && c in 0 until size) walls[r * size + c] else -1
            }
        }

    /** The walker's view while playing holds the window and the shared counters, and nothing else about the maze. */
    private fun assertWalkerSeesOnlyTheWindow(g: Maze, full: MazeState) {
        val s = g.toState(bob)
        assertEquals("walker", s.role)
        assertEquals(emptyList(), s.walls)
        assertEquals(emptyList(), s.trail)
        assertEquals(-1, s.start)
        assertEquals(-1, s.exit)
        assertEquals(-1, s.position)
        assertEquals(0, s.shortest)
        assertEquals("", s.result)
        assertEquals(expectedWindow(full.size, full.walls, full.position), s.window)
        val exitIndex = (0 until 9).firstOrNull { i ->
            val r = full.position / full.size + i / 3 - 1
            val c = full.position % full.size + i % 3 - 1
            r in 0 until full.size && c in 0 until full.size && r * full.size + c == full.exit
        } ?: -1
        assertEquals(exitIndex, s.windowExit)
        // Everything else is the same for both players.
        assertEquals(full.copy(you = bob, role = "walker", walls = emptyList(), trail = emptyList(), start = -1, exit = -1, position = -1), s)
        // The text sent with every update doesn't give the walker's whereabouts away either.
        assertTrue(Regex("\\d+").findAll(g.describe()).map { it.value.toInt() }.toList() == listOf(g.movesUsed, g.moveLimit))
    }

    @Test
    fun generatedMazesArePerfectWithTheEndsOfALongestPath() {
        val seen = HashSet<List<Int>>()
        for (seed in 1L..300L) {
            val layout = Maze.generate(Maze.SIZE, Random(seed))
            assertEquals(8, layout.size)
            assertPerfect(layout.size, layout.walls)
            assertTrue(layout.start in 0 until 64 && layout.exit in 0 until 64 && layout.start != layout.exit)
            // The exit is as far from the start as any cell can be from any other: the length of a longest path.
            val all = (0 until 64).map { Maze.distances(8, layout.walls, it) }
            val longest = all.maxOf { it.max() }
            assertEquals(longest, all[layout.start][layout.exit], "seed $seed")
            seen += layout.walls
        }
        assertTrue(seen.size >= 299, "mazes should differ from game to game, got ${seen.size} different ones")
        // The same random numbers give the same maze, so tests can fix one.
        assertEquals(Maze.generate(8, Random(7)).walls, Maze.generate(8, Random(7)).walls)
    }

    @Test
    fun aRoomGameIsAnEightByEightMazeWithALimitOfOneAndAHalfTimesTheShortestWay() {
        val g = Maze(ann, bob)
        val s = g.toState(ann)
        assertEquals(Maze.SIZE, s.size)
        assertPerfect(s.size, s.walls)
        assertEquals(s.start, s.position)
        assertEquals(listOf(s.start), s.trail)
        val shortest = Maze.distances(s.size, s.walls, s.start)[s.exit]
        assertEquals(shortest, g.shortest)
        assertEquals(Math.ceil(1.5 * shortest).toInt(), s.moveLimit)
        assertEquals(0, s.movesUsed)
        assertEquals("playing", s.status)
        assertEquals("guide", s.role)
        assertEquals(ann, s.guide)
        assertEquals(bob, s.walker)

        // An odd shortest way rounds the limit up: 7 moves give ceil(10.5) = 11.
        assertEquals(11, Maze(ann, bob, Maze.Layout(3, serpentine.walls, start = 0, exit = 7)).moveLimit)
        assertEquals(2, Maze(ann, bob, Maze.Layout(3, serpentine.walls, start = 0, exit = 1)).moveLimit)
    }

    @Test
    fun layoutsThatAreNotPerfectMazesAreRefused() {
        val walls = serpentine.walls
        // A gap in the outer wall (cell 0 open to the left).
        assertFailsWith<IllegalArgumentException> { Maze.Layout(3, listOf(5) + walls.drop(1), 0, 8) }
        // The two sides of a wall disagree (cell 1 says it is walled from cell 0, cell 0 says it is open).
        assertFailsWith<IllegalArgumentException> { Maze.Layout(3, walls.toMutableList().also { it[1] = 13 }, 0, 8) }
        // A loop: also opening 1-4 (cell 1 down, cell 4 up).
        assertFailsWith<IllegalArgumentException> {
            Maze.Layout(3, walls.toMutableList().also { it[1] = 1; it[4] = 4 }, 0, 8)
        }
        // A cell cut off: closing 7-8 (cell 7 right, cell 8 left).
        assertFailsWith<IllegalArgumentException> {
            Maze.Layout(3, walls.toMutableList().also { it[7] = 7; it[8] = 15 }, 0, 8)
        }
        assertFailsWith<IllegalArgumentException> { Maze.Layout(3, walls, 0, 0) }
        assertFailsWith<IllegalArgumentException> { Maze.Layout(3, walls, 0, 9) }
        assertFailsWith<IllegalArgumentException> { Maze.Layout(3, walls.dropLast(1), 0, 7) }
    }

    @Test
    fun wallsBlockMovesWithoutCostingOne() {
        val g = small()
        for (blocked in listOf("up", "left", "down")) {
            assertEquals("There's a wall that way", g.move(bob, blocked))
        }
        assertEquals(0, g.movesUsed)
        assertEquals(0, g.position)
        assertNull(g.move(bob, "right"))
        assertEquals(1, g.position)
        assertEquals(1, g.movesUsed)
        // Cell 1 is walled above and below.
        assertEquals("There's a wall that way", g.move(bob, "down"))
        assertEquals(1, g.movesUsed)
        assertNull(g.move(bob, "left"))
        assertEquals(listOf(0, 1, 0), g.toState(ann).trail)
        assertEquals("Maze Guide: Ann guides Bob, 2 of 12 moves used", g.describe())
    }

    @Test
    fun reachingTheExitOnTheShortestWayIsAPerfectWin() {
        val g = small()
        assertEquals(12, g.moveLimit)
        for (direction in wayOut) assertNull(g.move(bob, direction))
        assertTrue(g.finished)
        for (player in listOf(ann, bob)) {
            val s = g.toState(player)
            assertEquals("won", s.status)
            assertEquals(8, s.movesUsed)
            assertEquals(8, s.shortest)
            assertEquals("Out in 8 moves: the shortest way, a perfect run", s.result)
        }
        assertEquals("Maze Guide: Bob got out in 8 moves (shortest 8)", g.describe())
    }

    @Test
    fun reachingTheExitWithTheLastMoveStillWins() {
        val g = small()
        repeat(2) {
            assertNull(g.move(bob, "right"))
            assertNull(g.move(bob, "left"))
        }
        for (direction in wayOut) assertNull(g.move(bob, direction))
        val s = g.toState(bob)
        assertEquals("won", s.status)
        assertEquals(12, s.movesUsed)
        assertEquals(12, s.moveLimit)
        assertEquals("Out in 12 moves; the shortest way is 8", s.result)
    }

    @Test
    fun usingUpTheLimitAnywhereElseLosesForBoth() {
        val g = small()
        repeat(5) {
            assertNull(g.move(bob, "right"))
            assertNull(g.move(bob, "left"))
        }
        assertNull(g.move(bob, "right"))
        assertEquals("playing", g.toState(ann).status)
        assertNull(g.move(bob, "right")) // the 12th move ends on cell 2, 6 steps from the exit
        for (player in listOf(ann, bob)) {
            val s = g.toState(player)
            assertEquals("lost", s.status)
            assertEquals(12, s.movesUsed)
            assertEquals(8, s.shortest)
            assertEquals("Out of moves: 12 used, still 6 steps from the exit; the shortest way was 8", s.result)
        }
        assertEquals("Maze Guide: out of moves after 12 (shortest 8)", g.describe())
        assertEquals("The game is over", g.move(bob, "down"))
        assertEquals("The game is over", g.hint(ann, "down"))
    }

    @Test
    fun theWalkerSeesOnlyTheThreeByThreeCellsAroundThem() {
        val g = small()
        // At the start, in the top-left corner: the window reaches past the edge on two sides.
        val start = g.toState(bob)
        assertEquals(listOf(-1, -1, -1, -1, 13, 5, -1, 9, 5), start.window)
        assertEquals(-1, start.windowExit)
        assertWalkerSeesOnlyTheWindow(g, g.toState(ann))

        // Next to the exit it shows up in the window, still without the maze around it.
        for (direction in wayOut.dropLast(1)) assertNull(g.move(bob, direction))
        val near = g.toState(bob)
        assertEquals(7, g.position)
        assertEquals(listOf(9, 5, 6, 12, 5, 7, -1, -1, -1), near.window)
        assertEquals(5, near.windowExit)
        assertWalkerSeesOnlyTheWindow(g, g.toState(ann))

        // Once the game is over the walker sees the whole maze too.
        assertNull(g.move(bob, "right"))
        val over = g.toState(bob)
        assertEquals(serpentine.walls, over.walls)
        assertEquals(listOf(0, 1, 2, 5, 4, 3, 6, 7, 8), over.trail)
        assertEquals(8, over.position)
        assertEquals(8, over.exit)
        assertEquals(0, over.start)
    }

    @Test
    fun onRandomWalksInRoomMazesTheWalkerNeverSeesBeyondTheWindow() {
        for (seed in 1L..40L) {
            val random = Random(seed)
            val g = Maze(ann, bob, random = random)
            var steps = 0
            while (!g.finished) {
                val full = g.toState(ann)
                assertWalkerSeesOnlyTheWindow(g, full)
                val open = Maze.DIRECTIONS.filter { full.walls[full.position] and bits.getValue(it) == 0 }
                assertNull(g.move(bob, open[random.nextInt(open.size)]))
                steps++
            }
            assertEquals(g.movesUsed, steps)
            assertTrue(g.status == Maze.Status.WON || g.movesUsed == g.moveLimit)
        }
    }

    @Test
    fun theGuideSeesTheWholeMazeTheWalkerAndTheTrail() {
        val g = small()
        assertNull(g.move(bob, "right"))
        assertNull(g.move(bob, "right"))
        val s = g.toState(ann)
        assertEquals("guide", s.role)
        assertEquals(serpentine.walls, s.walls)
        assertEquals(0, s.start)
        assertEquals(8, s.exit)
        assertEquals(2, s.position)
        assertEquals(listOf(0, 1, 2), s.trail)
        assertEquals(expectedWindow(3, serpentine.walls, 2), s.window)
        assertEquals(0, s.shortest) // sent once the game is over
    }

    @Test
    fun theGuideHintsADirectionAndTheWalkerSeesTheLatestOne() {
        val g = small()
        assertEquals("", g.toState(bob).hint)
        assertNull(g.hint(ann, "right"))
        var b = g.toState(bob)
        assertEquals("right", b.hint)
        assertEquals(0, b.hintAt)
        assertEquals(1, b.hints)
        assertEquals(b.hint, g.toState(ann).hint)

        // Hints cost nothing and may point anywhere; the same direction again is still a new hint.
        assertNull(g.move(bob, "right"))
        assertNull(g.hint(ann, "right"))
        b = g.toState(bob)
        assertEquals(1, b.hintAt)
        assertEquals(2, b.hints)
        assertEquals(1, b.movesUsed)
        assertNull(g.hint(ann, "up"))
        b = g.toState(bob)
        assertEquals("up", b.hint)
        assertEquals(3, b.hints)
    }

    @Test
    fun eachRoleOnlyDoesItsOwnPartAndBadInputIsRefused() {
        val g = small()
        assertEquals("Only Bob moves: send them hints", g.move(ann, "right"))
        assertEquals("Only Ann gives hints", g.hint(bob, "right"))
        assertEquals("You are not in this game", g.move("Eve", "right"))
        assertEquals("You are not in this game", g.hint("Eve", "right"))
        for (bad in listOf(null, "", "north", "UP", "diagonal")) {
            assertEquals("Send a direction: up, down, left or right", g.move(bob, bad))
            assertEquals("Send a direction: up, down, left or right", g.hint(ann, bad))
        }
        assertEquals("Maze Guide understands 'move' and 'hint'", g.handle(bob, ClientMessage(type = "place")))
        assertNull(g.handle(bob, ClientMessage(type = "move", direction = "right")))
        assertNull(g.handle(ann, ClientMessage(type = "hint", direction = "down")))
        assertEquals(1, g.movesUsed)
        assertEquals("down", g.toState(bob).hint)
    }

    @Test
    fun swappedPlayersSwapRoles() {
        // Hub passes the players in the other order for the next game in a room.
        val rematch = Maze(bob, ann, serpentine)
        assertEquals("guide", rematch.toState(bob).role)
        assertEquals("walker", rematch.toState(ann).role)
        assertNull(rematch.move(ann, "right"))
        assertEquals("Only Ann moves: send them hints", rematch.move(bob, "right"))
        assertEquals(emptyList(), rematch.toState(ann).walls)
    }
}
