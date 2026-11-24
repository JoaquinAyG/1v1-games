package server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OthelloTest {
    private val ann = "Ann" // black, moves first
    private val bob = "Bob" // white

    private fun game() = Othello(ann, bob)

    /** Builds a 64-cell board from (row, text) pairs; everything not mentioned stays empty. */
    private fun board(vararg rows: Pair<Int, String>): String {
        val cells = CharArray(64) { '.' }
        for ((r, text) in rows) for ((c, ch) in text.withIndex()) cells[r * 8 + c] = ch
        return String(cells)
    }

    @Test
    fun startsFromTheStandardPosition() {
        val g = game()
        val s = g.toState()
        assertEquals(2, s.blackCount)
        assertEquals(2, s.whiteCount)
        assertEquals('W', s.board[27]) // d4
        assertEquals('B', s.board[28]) // e4
        assertEquals('B', s.board[35]) // d5
        assertEquals('W', s.board[36]) // e5
        assertEquals("B", s.turn)
        assertEquals("playing", s.status)
        // The four classic opening moves for black: d3, c4, f5, e6.
        assertEquals(listOf(19, 26, 37, 44), s.legalMoves)
    }

    @Test
    fun aMoveFlipsTheBracketedDiscs() {
        val g = game()
        assertNull(g.play(ann, 19)) // d3 flips d4
        val s = g.toState()
        assertEquals('B', s.board[19])
        assertEquals('B', s.board[27])
        assertEquals(listOf(27), s.flipped)
        assertEquals(19, s.lastMove)
        assertEquals(4, s.blackCount)
        assertEquals(1, s.whiteCount)
        assertEquals("W", s.turn)
    }

    @Test
    fun turnsAlternateAndEachSideSeesItsLegalMoves() {
        val g = game()
        assertNull(g.play(ann, 19))
        assertEquals(listOf(18, 20, 34), g.toState().legalMoves) // white may play c3, e3 or c5
        assertNull(g.play(bob, 18)) // c3 flips d4 back
        assertEquals(listOf(27), g.toState().flipped)
        assertEquals("B", g.toState().turn)
    }

    @Test
    fun rejectsIllegalMovesWithoutChangingAnything() {
        val g = game()
        val before = g.toState().board
        assertEquals("Not your turn", g.play(bob, 19))
        assertEquals("Invalid cell", g.play(ann, 64))
        assertEquals("Invalid cell", g.play(ann, -1))
        assertEquals("That cell is already taken", g.play(ann, 27))
        assertEquals("That move doesn't flip any discs", g.play(ann, 0))
        assertEquals("You are not in this game", g.play("Eve", 19))
        assertEquals(before, g.toState().board)
        assertEquals("B", g.toState().turn)
    }

    @Test
    fun flipsInSeveralDirectionsAtOnce() {
        val g = Othello(
            ann, bob,
            board(
                3 to "....WWB.",
                4 to "...WW...",
                5 to "...W.W..",
                6 to "...B..B.",
            ),
            'B',
        )
        assertTrue(27 in g.legalMoves('B'))
        assertNull(g.play(ann, 27))
        // east: 28, 29 | south: 35, 43 | south-east: 36, 45
        assertEquals(listOf(28, 29, 35, 36, 43, 45), g.toState().flipped)
        assertEquals(10, g.toState().blackCount)
        assertEquals(0, g.toState().whiteCount)
    }

    @Test
    fun aPlayerWithoutLegalMovesIsSkipped() {
        // Black captures on c1; white has a single disc and nothing it can bracket, so white must pass.
        val g = Othello(ann, bob, board(0 to "BW......", 2 to "BW......"), 'B')
        assertNull(g.play(ann, 2))

        val s = g.toState()
        assertEquals("playing", s.status)
        assertEquals("B", s.turn)             // black moves again
        assertEquals(bob, s.passed)
        assertTrue(18 in s.legalMoves)        // c3 would flip b3
        assertTrue(g.describe().contains("Bob has no moves and passes"))

        // The skip is announced once: the next move clears it.
        assertNull(g.play(ann, 18))
        assertNull(g.toState().passed)
    }

    @Test
    fun theGameEndsWhenNobodyCanMoveAndTheBiggerSideWins() {
        val g = Othello(ann, bob, board(0 to "BB......", 7 to ".......W"), 'B')
        assertTrue(g.finished)
        assertEquals("won", g.toState().status)
        assertEquals(ann, g.winner)
        assertEquals(2, g.toState().blackCount)
        assertEquals(1, g.toState().whiteCount)
        assertTrue(g.toState().legalMoves.isEmpty())
        assertEquals("The game is over", g.play(ann, 20))
    }

    @Test
    fun equalDiscCountsAreADraw() {
        val g = Othello(ann, bob, board(0 to "B.......", 7 to ".......W"), 'B')
        assertTrue(g.finished)
        assertEquals("draw", g.toState().status)
        assertNull(g.winner)
    }

    @Test
    fun fillingTheBoardEndsTheGame() {
        // Only a1 is empty; black takes it, flips b1, and the board is full.
        val rows = (1..7).map { it to "BBBBBBBB" }.toTypedArray()
        val g = Othello(ann, bob, board(0 to ".WBBBBBB", *rows), 'B')
        assertNull(g.play(ann, 0))
        assertTrue(g.finished)
        assertEquals(ann, g.winner)
        assertEquals(64, g.toState().blackCount)
        assertFalse('.' in g.toState().board)
    }

    @Test
    fun wipingOutTheOpponentEndsTheGameImmediately() {
        val g = Othello(ann, bob, board(0 to "BW......"), 'B')
        assertNull(g.play(ann, 2))
        assertTrue(g.finished)
        assertEquals(ann, g.winner)
        assertEquals("won", g.toState().status)
    }
}
