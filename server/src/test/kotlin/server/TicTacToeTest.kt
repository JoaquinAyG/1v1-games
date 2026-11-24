package server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TicTacToeTest {
    private fun game() = TicTacToe("Ann", "Bob")

    @Test
    fun xMovesFirstAndTurnsAlternate() {
        val g = game()
        assertEquals('X', g.turn)
        assertNull(g.play("Ann", 4))
        assertEquals('O', g.turn)
        assertEquals("Not your turn", g.play("Ann", 0))
        assertNull(g.play("Bob", 0))
        assertEquals('X', g.turn)
    }

    @Test
    fun rejectsTakenInvalidAndStrangers() {
        val g = game()
        g.play("Ann", 4)
        assertEquals("That cell is already taken", g.play("Bob", 4))
        assertEquals("Invalid cell", g.play("Bob", 9))
        assertEquals("Invalid cell", g.play("Bob", -1))
        assertEquals("You are not in this game", g.play("Eve", 0))
        assertEquals('O', g.turn) // rejected moves change nothing
    }

    @Test
    fun detectsRowWin() {
        val g = game()
        g.play("Ann", 0); g.play("Bob", 3)
        g.play("Ann", 1); g.play("Bob", 4)
        assertNull(g.play("Ann", 2))
        assertEquals(TicTacToe.Status.WON, g.status)
        assertEquals("Ann", g.winner)
        assertEquals(listOf(0, 1, 2), g.line)
        assertEquals("The game is over", g.play("Bob", 5))
    }

    @Test
    fun detectsDiagonalWinForO() {
        val g = game()
        g.play("Ann", 1); g.play("Bob", 0)
        g.play("Ann", 2); g.play("Bob", 4)
        g.play("Ann", 3); g.play("Bob", 8)
        assertEquals("Bob", g.winner)
        assertEquals(listOf(0, 4, 8), g.line)
    }

    @Test
    fun detectsDraw() {
        val g = game()
        // X O X
        // X O O
        // O X X
        listOf("Ann" to 0, "Bob" to 1, "Ann" to 2, "Bob" to 4, "Ann" to 3,
            "Bob" to 5, "Ann" to 7, "Bob" to 6, "Ann" to 8).forEach { (p, c) -> assertNull(g.play(p, c)) }
        assertEquals(TicTacToe.Status.DRAW, g.status)
        assertNull(g.winner)
    }

    @Test
    fun stateSnapshotMatches() {
        val g = game()
        g.play("Ann", 0)
        val s = g.toState()
        assertEquals("X........".take(9), s.board)
        assertEquals("O", s.turn)
        assertEquals("playing", s.status)
    }
}
