package com.classicgames.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Most tests play on a 2 x 2 board (3 x 3 dots, 12 lines), numbered as in the protocol:
 *
 * ```
 *   .  0  .  1  .        horizontal: 0..5      boxes: 0 | 1
 *   6     7     8        vertical:   6..11            --+--
 *   .  2  .  3  .                                     2 | 3
 *   9    10    11
 *   .  4  .  5  .
 * ```
 * Box 0 has the sides 0, 2, 6, 7; box 1: 1, 3, 7, 8; box 2: 2, 4, 9, 10; box 3: 3, 5, 10, 11.
 */
class DotsAndBoxesTest {
    private val ann = "Ann" // red, moves first
    private val bob = "Bob" // blue

    private fun small() = DotsAndBoxes(ann, bob, size = 2)

    /** Draws the lines in order, each by the player paired with it, and checks every line is accepted. */
    private fun DotsAndBoxes.drawAll(vararg moves: Pair<String, Int>) {
        for ((i, move) in moves.withIndex()) {
            val (player, line) = move
            assertNull(draw(player, line), "move ${i + 1} ($player, line $line) was rejected")
        }
    }

    @Test
    fun startsEmptyWithRedToMove() {
        val s = DotsAndBoxes(ann, bob).toState()
        assertEquals(DotsAndBoxes.SIZE, s.size)
        assertEquals(".".repeat(60), s.lines)
        assertEquals(".".repeat(25), s.boxes)
        assertEquals("R", s.turn)
        assertEquals("playing", s.status)
        assertEquals(0, s.redScore)
        assertEquals(0, s.blueScore)
        assertEquals(-1, s.lastLine)
        assertTrue(s.closed.isEmpty())
    }

    @Test
    fun linesAreNumberedAsTheProtocolSays() {
        assertEquals(listOf(0, 5, 30, 31), DotsAndBoxes.sidesOf(5, 0))   // top-left box
        assertEquals(listOf(24, 29, 58, 59), DotsAndBoxes.sidesOf(5, 24)) // bottom-right box
        assertEquals(listOf(3, 5, 10, 11), DotsAndBoxes.sidesOf(2, 3))

        // Drawing the four sides of the top-left box on the real board closes exactly that box.
        val g = DotsAndBoxes(ann, bob)
        g.drawAll(ann to 0, bob to 5, ann to 30, bob to 31)
        assertEquals("B" + ".".repeat(24), g.toState().boxes)
        assertEquals(listOf(0), g.toState().closed)
    }

    @Test
    fun aLineThatClosesNothingPassesTheTurn() {
        val g = small()
        assertNull(g.draw(ann, 0))
        var s = g.toState()
        assertEquals('R', s.lines[0])
        assertEquals(0, s.lastLine)
        assertTrue(s.closed.isEmpty())
        assertEquals("B", s.turn)

        assertNull(g.draw(bob, 7))
        s = g.toState()
        assertEquals('B', s.lines[7])
        assertEquals("R", s.turn)
    }

    @Test
    fun rejectsIllegalLinesWithoutChangingAnything() {
        val g = small()
        assertEquals("Not your turn", g.draw(bob, 0))
        assertEquals("Invalid line", g.draw(ann, -1))
        assertEquals("Invalid line", g.draw(ann, 12))
        assertEquals("You are not in this game", g.draw("Eve", 0))
        assertEquals("Invalid line", g.handle(ann, ClientMessage(type = "move")))
        assertEquals("Dots and Boxes only understands moves", g.handle(ann, ClientMessage(type = "place")))
        assertEquals(".".repeat(12), g.toState().lines)
        assertEquals("R", g.toState().turn)

        assertNull(g.draw(ann, 0))
        assertEquals("That line is already drawn", g.draw(bob, 0))
        assertEquals("Not your turn", g.draw(ann, 1))
        assertEquals("B", g.toState().turn) // the rejected line did not use up Bob's turn
        assertEquals("R" + ".".repeat(11), g.toState().lines)
    }

    @Test
    fun closingABoxScoresItAndGivesAnotherLine() {
        val g = small()
        g.drawAll(ann to 0, bob to 2, ann to 6)
        assertNull(g.draw(bob, 7)) // the fourth side of box 0

        var s = g.toState()
        assertEquals("B...", s.boxes)
        assertEquals(listOf(0), s.closed)
        assertEquals(1, s.blueScore)
        assertEquals(0, s.redScore)
        assertEquals("B", s.turn) // Bob draws again
        assertEquals("Dots and Boxes: Bob's turn again (blue), 0-1", g.describe())
        assertEquals("Not your turn", g.draw(ann, 1))

        // Bob's extra line closes nothing, so the turn passes; box 0 stays his.
        assertNull(g.draw(bob, 1))
        s = g.toState()
        assertEquals("R", s.turn)
        assertTrue(s.closed.isEmpty())
        assertEquals("B...", s.boxes)
    }

    @Test
    fun oneLineCanCloseTwoBoxesAtOnce() {
        val g = small()
        g.drawAll(ann to 0, bob to 1, ann to 2, bob to 3, ann to 6, bob to 8)
        assertNull(g.draw(ann, 7)) // the middle line is a side of both top boxes

        val s = g.toState()
        assertEquals("RR..", s.boxes)
        assertEquals(listOf(0, 1), s.closed)
        assertEquals(2, s.redScore)
        assertEquals("R", s.turn)
        assertEquals("playing", s.status)
    }

    @Test
    fun theGameEndsWhenEveryLineIsDrawnAndMostBoxesWins() {
        val g = small()
        // The outer lines first; then Ann has to open a box and Bob takes all four.
        g.drawAll(
            ann to 0, bob to 1, ann to 4, bob to 5, ann to 6, bob to 8, ann to 9, bob to 11,
            ann to 2, bob to 7, bob to 3,
        )
        assertFalse(g.finished)
        assertNull(g.draw(bob, 10)) // the last line closes the two bottom boxes

        val s = g.toState()
        assertTrue(g.finished)
        assertEquals("won", s.status)
        assertEquals(bob, s.winner)
        assertEquals(0, s.redScore)
        assertEquals(4, s.blueScore)
        assertEquals(10, s.lastLine)
        assertEquals(listOf(2, 3), s.closed)
        assertFalse('.' in s.lines)
        assertEquals("Dots and Boxes: Bob won 0-4", g.describe())
    }

    @Test
    fun equalBoxesAreADraw() {
        val g = small()
        // The inner cross first, then the outer lines: each player in turn gives a box away.
        g.drawAll(
            ann to 2, bob to 3, ann to 7, bob to 10,
            ann to 0, bob to 6, bob to 1, ann to 8, ann to 4, bob to 9, bob to 5, ann to 11,
        )
        val s = g.toState()
        assertTrue(g.finished)
        assertEquals("draw", s.status)
        assertNull(s.winner)
        assertEquals(2, s.redScore)
        assertEquals(2, s.blueScore)
        assertEquals("BRBR", s.boxes)
    }

    @Test
    fun noLinesAfterTheGameIsOver() {
        val g = DotsAndBoxes(ann, bob, size = 1) // one box, four lines
        g.drawAll(ann to 0, bob to 1, ann to 2, bob to 3)
        assertTrue(g.finished)
        assertEquals(bob, g.winner)
        assertEquals("The game is over", g.draw(ann, 0))
        assertEquals("The game is over", g.draw(bob, 0))
        assertEquals("The game is over", g.draw(ann, 99))
    }
}
