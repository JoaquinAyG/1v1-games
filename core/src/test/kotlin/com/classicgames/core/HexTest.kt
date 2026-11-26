package com.classicgames.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cells are numbered row by row and every row sits half a cell further right; Ann is red (top to bottom) and moves
 * first, Bob is blue (left to right). On the 9 x 9 board cell = row * 9 + column.
 */
class HexTest {
    private val ann = "Ann" // red
    private val bob = "Bob" // blue

    private fun cell(row: Int, column: Int, size: Int = Hex.SIZE) = row * size + column

    /** A board of [rows] (one string per row, '.', 'R' or 'B'). */
    private fun board(vararg rows: String) = rows.joinToString("")

    @Test
    fun startsEmptyWithRedToMove() {
        val s = Hex(ann, bob).toState()
        assertEquals(9, s.size)
        assertEquals(".".repeat(81), s.board)
        assertEquals("R", s.turn)
        assertEquals("playing", s.status)
        assertEquals(-1, s.lastMove)
        assertFalse(s.canSwap)
        assertFalse(s.swapped)
        assertTrue(s.winningPath.isEmpty())
    }

    @Test
    fun everyCellTouchesSixNeighboursExceptAtTheEdges() {
        // Interior: two above (straight up and up-right), two beside, two below (down-left and straight down).
        assertEquals(
            listOf(cell(3, 4), cell(3, 5), cell(4, 3), cell(4, 5), cell(5, 3), cell(5, 4)).sorted(),
            Hex.neighbours(9, cell(4, 4)).sorted(),
        )
        // The two sharp corners have 2 neighbours, the two blunt ones 3.
        assertEquals(listOf(cell(0, 1), cell(1, 0)).sorted(), Hex.neighbours(9, cell(0, 0)).sorted())
        assertEquals(listOf(cell(7, 8), cell(8, 7)).sorted(), Hex.neighbours(9, cell(8, 8)).sorted())
        assertEquals(3, Hex.neighbours(9, cell(0, 8)).size)
        assertEquals(3, Hex.neighbours(9, cell(8, 0)).size)
        // Neighbourhood is symmetric.
        for (a in 0 until 81) for (b in Hex.neighbours(9, a)) assertTrue(a in Hex.neighbours(9, b))
    }

    @Test
    fun playersTakeTurnsPlacingStones() {
        val g = Hex(ann, bob)
        assertNull(g.play(ann, cell(4, 4)))
        var s = g.toState()
        assertEquals('R', s.board[cell(4, 4)])
        assertEquals(cell(4, 4), s.lastMove)
        assertEquals("B", s.turn)
        assertTrue(s.canSwap)

        assertNull(g.play(bob, cell(3, 5)))
        s = g.toState()
        assertEquals('B', s.board[cell(3, 5)])
        assertEquals("R", s.turn)
        assertFalse(s.canSwap)
    }

    @Test
    fun rejectsWrongTurnsTakenCellsAndCellsOffTheBoard() {
        val g = Hex(ann, bob)
        assertEquals("Not your turn", g.play(bob, 0))
        assertEquals("You are not in this game", g.play("Eve", 0))
        assertEquals("Invalid cell", g.play(ann, -1))
        assertEquals("Invalid cell", g.play(ann, 81))
        assertNull(g.play(ann, 10))
        assertEquals("That cell is taken", g.play(bob, 10))
        assertEquals("Not your turn", g.play(ann, 11))
        assertEquals("Hex understands 'move' and 'swap'", g.handle(bob, ClientMessage(type = "place")))
        assertEquals(".".repeat(10) + "R" + ".".repeat(70), g.toState().board)
        assertEquals("B", g.toState().turn)
    }

    @Test
    fun swappingMirrorsTheFirstStoneAcrossTheLongDiagonalAndMakesItBlue() {
        val g = Hex(ann, bob)
        assertNull(g.play(ann, cell(2, 6)))
        assertNull(g.handle(bob, ClientMessage(type = "swap")))
        val s = g.toState()
        // Red's stone at row 2, column 6 is gone; blue's is at row 6, column 2.
        assertEquals(".", s.board[cell(2, 6)].toString())
        assertEquals('B', s.board[cell(6, 2)])
        assertEquals(1, s.board.count { it != '.' })
        assertEquals(cell(6, 2), s.lastMove)
        assertTrue(s.swapped)
        assertFalse(s.canSwap)
        assertEquals("R", s.turn)
        assertEquals("Hex: Bob swapped. Ann's turn (red, top to bottom)", g.describe())

        // Play goes on normally, red first.
        assertNull(g.play(ann, cell(4, 4)))
        assertEquals("Hex: Bob's turn (blue, left to right)", g.describe())
    }

    @Test
    fun aStoneOnTheLongDiagonalStaysWhereItIsWhenSwapped() {
        val g = Hex(ann, bob)
        assertNull(g.play(ann, cell(4, 4)))
        assertNull(g.swap(bob))
        assertEquals(".".repeat(40) + "B" + ".".repeat(40), g.toState().board)
        assertEquals(Hex.mirror(9, cell(1, 7)), cell(7, 1))
        assertEquals(Hex.mirror(9, cell(0, 0)), cell(0, 0))
    }

    @Test
    fun onlyBlueMaySwapAndOnlyOnTheirFirstTurn() {
        val g = Hex(ann, bob)
        assertEquals("Not your turn", g.swap(bob)) // red has not moved yet
        assertEquals("Only Bob can swap", g.swap(ann))
        assertNull(g.play(ann, cell(0, 0)))
        assertNull(g.play(bob, cell(1, 1))) // Bob places instead of swapping...
        assertNull(g.play(ann, cell(2, 2)))
        assertEquals("You can only swap on your first turn, instead of placing a stone", g.swap(bob)) // ...for good
        assertFalse(g.toState().canSwap)

        // After a swap, nobody can swap again.
        val again = Hex(ann, bob)
        assertNull(again.play(ann, cell(3, 3)))
        assertNull(again.swap(bob))
        assertEquals("Only Bob can swap", again.swap(ann))
        assertNull(again.play(ann, cell(5, 5)))
        assertEquals("You can only swap on your first turn, instead of placing a stone", again.swap(bob))
    }

    @Test
    fun redWinsByJoiningTheTopAndTheBottom() {
        val g = Hex(ann, bob)
        // Red walks straight down column 0; blue plays along column 8, which never joins left and right.
        for (row in 0 until 9) {
            assertNull(g.play(ann, cell(row, 0)))
            if (row < 8) assertNull(g.play(bob, cell(row, 8)))
        }
        val s = g.toState()
        assertEquals("won", s.status)
        assertEquals(ann, s.winner)
        assertEquals((0 until 9).map { cell(it, 0) }, s.winningPath)
        assertEquals("Hex: Ann won", g.describe())

        assertEquals("The game is over", g.play(bob, cell(8, 8)))
        assertEquals("The game is over", g.swap(bob))
    }

    @Test
    fun blueWinsByJoiningTheLeftAndTheRight() {
        // Blue only needs one more stone in row 4; red is far from the bottom.
        val g = Hex(
            ann, bob, size = 5,
            start = board(
                "RR...",
                ".R...",
                "..RR.",
                ".....",
                "BB.BB",
            ),
        )
        assertEquals("B", g.toState().turn)
        assertNull(g.play(bob, cell(4, 2, 5)))
        assertEquals(bob, g.winner)
        assertEquals((0 until 5).map { cell(4, it, 5) }, g.winningPath)
    }

    @Test
    fun onlyTouchingCellsJoinAChain() {
        // (row, column) and (row + 1, column + 1) do NOT touch: this red diagonal joins nothing.
        val g = Hex(
            ann, bob, size = 3,
            start = board(
                "R.B",
                ".R.",
                "B..",
            ),
        )
        assertNull(g.play(ann, cell(2, 2, 3)))
        assertEquals("playing", g.toState().status)

        // (row, column) and (row + 1, column - 1) do touch: this one wins.
        val h = Hex(
            ann, bob, size = 3,
            start = board(
                "..R",
                "BR.",
                "B..",
            ),
        )
        assertNull(h.play(ann, cell(2, 1, 3)))
        assertEquals(ann, h.winner)
        assertEquals(listOf(cell(0, 2, 3), cell(1, 1, 3), cell(2, 1, 3)), h.winningPath)
    }

    @Test
    fun theWinningPathLeavesOutStonesThatLeadNowhere() {
        // Red has an extra stone on the top row and a dead-end branch to the right of row 1. Blue is nowhere near
        // the left side.
        val g = Hex(
            ann, bob, size = 5,
            start = board(
                "RR...",
                ".RRRR",
                ".RBBB",
                ".RBBB",
                "...BB",
            ),
        )
        assertEquals("R", g.toState().turn)
        assertEquals("playing", g.toState().status)
        assertNull(g.play(ann, cell(4, 1, 5)))
        assertEquals(ann, g.winner)
        assertEquals((0 until 5).map { cell(it, 1, 5) }, g.winningPath)
    }

    @Test
    fun aPositionHandedInIsCheckedAndTheSideToMoveFollowsFromTheStones() {
        assertEquals("B", Hex(ann, bob, size = 3, start = board("R..", "...", "...")).toState().turn)
        assertEquals("R", Hex(ann, bob, size = 3, start = board("RB.", "...", "...")).toState().turn)
        val won = Hex(ann, bob, size = 3, start = board("R.B", "RB.", "R.."))
        assertTrue(won.finished)
        assertEquals(ann, won.winner)
        assertEquals("The game is over", won.play(bob, 8))
    }

    @Test
    fun everyGamePlayedToTheEndHasAWinnerAndAValidChain() {
        val random = Random(2026)
        repeat(30) {
            val g = Hex(ann, bob)
            val empty = (0 until 81).shuffled(random).toMutableList()
            while (!g.finished) {
                val mover = if (g.turn == 'R') ann else bob
                assertNull(g.play(mover, empty.removeAt(0)))
            }
            val s = g.toState()
            val mark = if (s.winner == ann) 'R' else 'B'
            val path = s.winningPath
            assertTrue(path.all { s.board[it] == mark })
            assertTrue(path.zipWithNext().all { (a, b) -> b in Hex.neighbours(9, a) })
            if (mark == 'R') {
                assertEquals(0, path.first() / 9)
                assertEquals(8, path.last() / 9)
            } else {
                assertEquals(0, path.first() % 9)
                assertEquals(8, path.last() % 9)
            }
            // The game ended on the move that joined the sides.
            assertTrue(s.lastMove in path)
        }
    }
}
