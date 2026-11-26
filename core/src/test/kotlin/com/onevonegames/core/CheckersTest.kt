package com.onevonegames.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckersTest {
    private val ann = "Ann" // red, moves first (up the board)
    private val bob = "Bob" // black (down the board)

    private fun game() = Checkers(ann, bob)

    /** A board with the given pieces at the given cells. */
    private fun pieces(vararg placed: Pair<Int, Char>): String {
        val cells = CharArray(64) { '.' }
        for ((cell, piece) in placed) cells[cell] = piece
        return String(cells)
    }

    private fun Checkers.move(player: String, from: Int, to: Int) = play(player, from, to)

    @Test
    fun startsFromTheStandardPosition() {
        val s = game().toState()
        assertEquals(12, s.redCount)
        assertEquals(12, s.blackCount)
        assertEquals('.', s.board[0])   // light square
        assertEquals('b', s.board[1])   // black starts on rows 0-2
        assertEquals('r', s.board[56])  // red starts on rows 5-7
        assertEquals("R", s.turn)
        assertEquals("playing", s.status)
        assertEquals(
            setOf(
                CheckersMove(40, 33), CheckersMove(42, 33), CheckersMove(42, 35), CheckersMove(44, 35),
                CheckersMove(44, 37), CheckersMove(46, 37), CheckersMove(46, 39),
            ),
            s.legalMoves.toSet(),
        )
    }

    @Test
    fun aSimpleStepMovesThePieceAndPassesTheTurn() {
        val g = game()
        assertNull(g.move(ann, 40, 33))
        val s = g.toState()
        assertEquals('.', s.board[40])
        assertEquals('r', s.board[33])
        assertEquals(40, s.lastFrom)
        assertEquals(33, s.lastTo)
        assertEquals("B", s.turn)
        assertTrue(CheckersMove(17, 24) in s.legalMoves)
    }

    @Test
    fun rejectsIllegalMovesWithoutChangingAnything() {
        val g = game()
        val before = g.toState().board
        assertEquals("Not your turn", g.move(bob, 17, 24))
        assertEquals("Pick one of your own pieces", g.move(ann, 17, 24)) // a black piece
        assertEquals("Pick one of your own pieces", g.move(ann, 33, 24)) // an empty square
        assertEquals("That move isn't allowed", g.move(ann, 49, 40))     // onto your own piece
        assertEquals("That move isn't allowed", g.move(ann, 40, 32))     // straight, not diagonal
        assertEquals("Invalid square", g.move(ann, 40, 64))
        assertEquals("Invalid square", g.move(ann, -1, 33))
        assertEquals("You are not in this game", g.move("Eve", 40, 33))
        assertEquals(before, g.toState().board)
        assertEquals("R", g.toState().turn)
    }

    @Test
    fun menCannotMoveBackwards() {
        val g = Checkers(ann, bob, pieces(35 to 'r', 1 to 'b'), 'R')
        assertEquals(setOf(CheckersMove(35, 26), CheckersMove(35, 28)), g.toState().legalMoves.toSet())
        assertEquals("That move isn't allowed", g.move(ann, 35, 42))
    }

    @Test
    fun capturingIsMandatory() {
        // Red can jump 42 over 35; the other red man (62) may not just step.
        val g = Checkers(ann, bob, pieces(42 to 'r', 62 to 'r', 35 to 'b', 1 to 'b'), 'R')
        assertEquals(listOf(CheckersMove(42, 28)), g.toState().legalMoves)
        assertEquals("You must capture", g.move(ann, 62, 53))

        assertNull(g.move(ann, 42, 28))
        val s = g.toState()
        assertEquals('.', s.board[35]) // the jumped piece is gone
        assertEquals('r', s.board[28])
        assertEquals(1, s.blackCount)
        assertEquals("B", s.turn)
    }

    @Test
    fun aJumpMustBeContinuedWithTheSamePiece() {
        val g = Checkers(ann, bob, pieces(49 to 'r', 62 to 'r', 42 to 'b', 28 to 'b', 1 to 'b'), 'R')
        assertNull(g.move(ann, 49, 35)) // first jump

        var s = g.toState()
        assertEquals(35, s.chain)
        assertEquals("R", s.turn)                                   // still red's turn
        assertEquals(listOf(CheckersMove(35, 21)), s.legalMoves)    // only the second jump
        assertEquals("You must keep jumping with the same piece", g.move(ann, 62, 53))

        assertNull(g.move(ann, 35, 21)) // second jump
        s = g.toState()
        assertEquals(-1, s.chain)
        assertEquals("B", s.turn)
        assertEquals('.', s.board[42])
        assertEquals('.', s.board[28])
        assertEquals(1, s.blackCount)
    }

    @Test
    fun aManReachingTheFarRowBecomesAKingWhichMovesBothWays() {
        val g = Checkers(ann, bob, pieces(10 to 'r', 23 to 'b'), 'R')
        assertEquals(setOf(CheckersMove(10, 1), CheckersMove(10, 3)), g.toState().legalMoves.toSet())
        assertNull(g.move(ann, 10, 3))
        assertEquals('R', g.toState().board[3]) // crowned

        assertNull(g.move(bob, 23, 30))
        // The king can now step backwards (down the board) as well.
        assertTrue(CheckersMove(3, 10) in g.toState().legalMoves)
        assertTrue(CheckersMove(3, 12) in g.toState().legalMoves)
    }

    @Test
    fun beingCrownedInTheMiddleOfAJumpEndsTheTurn() {
        // 17 jumps 10 and lands on the back row. As a king it could jump 12 as well, but the turn ends.
        val g = Checkers(ann, bob, pieces(17 to 'r', 10 to 'b', 12 to 'b'), 'R')
        assertNull(g.move(ann, 17, 3))
        val s = g.toState()
        assertEquals('R', s.board[3])
        assertEquals('.', s.board[10])
        assertEquals(-1, s.chain)
        assertEquals("B", s.turn)
        assertEquals("playing", s.status)
    }

    @Test
    fun capturingTheLastPieceWins() {
        val g = Checkers(ann, bob, pieces(42 to 'r', 35 to 'b'), 'R')
        assertNull(g.move(ann, 42, 28))
        assertTrue(g.finished)
        assertEquals(ann, g.winner)
        assertEquals("won", g.toState().status)
        assertTrue(g.toState().legalMoves.isEmpty())
        assertEquals("The game is over", g.move(bob, 1, 8))
    }

    @Test
    fun blockingTheOpponentSoItCannotMoveWins() {
        // The black man on 55 is stuck behind the red man on 62 once red makes any other move.
        val g = Checkers(ann, bob, pieces(40 to 'r', 62 to 'r', 55 to 'b'), 'R')
        assertNull(g.move(ann, 40, 33))
        assertTrue(g.finished)
        assertEquals(ann, g.winner)
    }

    @Test
    fun fortyMovesEachWithoutACaptureOrCrowningIsADraw() {
        // Two far-apart kings shuffle back and forth; nothing is ever captured or crowned.
        val g = Checkers(ann, bob, pieces(56 to 'R', 7 to 'B'), 'R')
        val cycle = listOf(56 to 49, 7 to 14, 49 to 56, 14 to 7)
        for (ply in 0 until Checkers.DRAW_AFTER_PLIES - 1) {
            val (from, to) = cycle[ply % 4]
            assertNull(g.move(if (ply % 2 == 0) ann else bob, from, to), "ply ${ply + 1}")
        }
        assertFalse(g.finished)

        val (from, to) = cycle[(Checkers.DRAW_AFTER_PLIES - 1) % 4]
        assertNull(g.move(bob, from, to))
        assertTrue(g.finished)
        assertEquals("draw", g.toState().status)
        assertNull(g.winner)
    }

    @Test
    fun eachPlayerGetsTheSameBoardAndOnlyTheSideToMoveHasMoves() {
        val g = game()
        val view = g.messageFor(ann).checkers!!
        assertEquals(view, g.messageFor(bob).checkers)
        assertEquals(7, view.legalMoves.size)
        assertTrue(g.messageFor(ann).text.contains("Ann's turn"))
    }
}
