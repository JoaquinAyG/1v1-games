package com.onevonegames.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectFourTest {
    private val ann = "Ann" // red, moves first
    private val bob = "Bob" // yellow

    private fun game() = ConnectFour(ann, bob)

    /** Plays the columns in order, alternating Ann, Bob, Ann... and checks every move is accepted. */
    private fun ConnectFour.playAll(columns: List<Int>) {
        for ((i, column) in columns.withIndex()) {
            assertNull(drop(if (i % 2 == 0) ann else bob, column), "move ${i + 1} (column $column) was rejected")
        }
    }

    @Test
    fun discsFallToTheBottomAndStack() {
        val g = game()
        assertNull(g.drop(ann, 0))
        assertEquals('R', g.toState().board[35]) // bottom row, column 0
        assertNull(g.drop(bob, 0))
        assertEquals('Y', g.toState().board[28]) // sits on top of the red disc
        assertEquals(28, g.lastMove)
        assertNull(g.drop(ann, 6))
        assertEquals('R', g.toState().board[41]) // bottom row, last column
    }

    @Test
    fun redMovesFirstAndTurnsAlternate() {
        val g = game()
        assertEquals("Not your turn", g.drop(bob, 0))
        assertNull(g.drop(ann, 0))
        assertEquals("Not your turn", g.drop(ann, 1))
        assertNull(g.drop(bob, 1))
        assertEquals('R', g.turn)
    }

    @Test
    fun rejectsBadMovesWithoutChangingAnything() {
        val g = game()
        assertEquals("Invalid column", g.drop(ann, 7))
        assertEquals("Invalid column", g.drop(ann, -1))
        assertEquals("You are not in this game", g.drop("Eve", 0))
        assertEquals(".".repeat(42), g.toState().board)
        assertEquals('R', g.turn)
    }

    @Test
    fun aFullColumnIsRejected() {
        val g = game()
        g.playAll(listOf(0, 0, 0, 0, 0, 0)) // alternating colours, nobody wins
        assertEquals("That column is full", g.drop(ann, 0))
        assertEquals('R', g.turn) // the rejected move did not use up the turn
        assertNull(g.drop(ann, 1))
    }

    @Test
    fun detectsVerticalWin() {
        val g = game()
        g.playAll(listOf(0, 1, 0, 1, 0, 1, 0))
        assertEquals(ConnectFour.Status.WON, g.status)
        assertEquals(ann, g.winner)
        assertEquals(listOf(14, 21, 28, 35), g.line)
    }

    @Test
    fun detectsHorizontalWinForYellow() {
        val g = game()
        g.playAll(listOf(0, 3, 0, 4, 1, 5, 6, 2)) // Bob fills 3, 4, 5 and then 2 on the bottom row
        assertEquals(ConnectFour.Status.WON, g.status)
        assertEquals(bob, g.winner)
        assertEquals(listOf(37, 38, 39, 40), g.line)
    }

    @Test
    fun detectsDiagonalWinGoingUpAndRight() {
        val g = game()
        g.playAll(listOf(0, 1, 1, 2, 3, 2, 2, 3, 3, 6, 3))
        assertEquals(ConnectFour.Status.WON, g.status)
        assertEquals(ann, g.winner)
        assertEquals(listOf(17, 23, 29, 35), g.line)
    }

    @Test
    fun detectsDiagonalWinGoingUpAndLeft() {
        val g = game()
        g.playAll(listOf(6, 5, 5, 4, 3, 4, 4, 3, 3, 0, 3))
        assertEquals(ConnectFour.Status.WON, g.status)
        assertEquals(ann, g.winner)
        assertEquals(listOf(17, 25, 33, 41), g.line)
    }

    @Test
    fun noMovesAfterTheGameIsOver() {
        val g = game()
        g.playAll(listOf(0, 1, 0, 1, 0, 1, 0))
        assertEquals("The game is over", g.drop(bob, 2))
        assertEquals("The game is over", g.drop(ann, 2))
        assertTrue(g.finished)
    }

    @Test
    fun threeInARowIsNotAWin() {
        val g = game()
        g.playAll(listOf(0, 1, 0, 1, 0, 1))
        assertEquals(ConnectFour.Status.PLAYING, g.status)
        assertFalse(g.finished)
        assertEquals("playing", g.toState().status)
    }

    @Test
    fun aFullBoardWithoutAWinnerIsADraw() {
        val moves = findDrawingGame()
        val g = game()
        g.playAll(moves)
        assertEquals(ConnectFour.Status.DRAW, g.status)
        assertNull(g.winner)
        assertFalse('.' in g.toState().board)
        assertEquals("The game is over", g.drop(ann, 0))
    }

    /** Searches (depth first, deterministically) for a full 42-move game in which nobody ever connects four. */
    private fun findDrawingGame(): List<Int> {
        val order = listOf(3, 2, 4, 1, 5, 0, 6)

        fun replay(moves: List<Int>): ConnectFour? {
            val g = game()
            for ((i, c) in moves.withIndex()) {
                if (g.drop(if (i % 2 == 0) ann else bob, c) != null) return null
            }
            return g
        }

        fun search(moves: List<Int>): List<Int>? {
            if (moves.size == ConnectFour.CELLS) return moves
            for (c in order) {
                val next = moves + c
                val g = replay(next) ?: continue
                if (g.status == ConnectFour.Status.WON) continue
                search(next)?.let { return it }
            }
            return null
        }

        return search(emptyList()) ?: error("no drawn game found")
    }
}
