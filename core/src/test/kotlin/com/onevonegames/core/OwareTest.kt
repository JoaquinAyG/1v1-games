package com.onevonegames.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pits are numbered counter-clockwise, as in the protocol; Ann is south and moves first:
 *
 * ```
 *   11 10  9  8  7  6     Bob (north)
 *    0  1  2  3  4  5     Ann (south)
 * ```
 */
class OwareTest {
    private val ann = "Ann" // south, moves first
    private val bob = "Bob" // north

    /** A board that is empty except for the given (pit, seeds) pairs. */
    private fun board(vararg seeds: Pair<Int, Int>): List<Int> =
        MutableList(Oware.PITS) { 0 }.also { pits -> seeds.forEach { (pit, count) -> pits[pit] = count } }

    /** The seeds in every pit plus both captured totals: nothing is ever created or lost. */
    private fun OwareState.total() = pits.sum() + southCaptured + northCaptured

    @Test
    fun startsWithFourSeedsInEveryPitAndSouthToMove() {
        val s = Oware(ann, bob).toState()
        assertEquals(List(12) { 4 }, s.pits)
        assertEquals("S", s.turn)
        assertEquals("playing", s.status)
        assertEquals((0..5).toList(), s.legalMoves)
        assertEquals(0, s.southCaptured)
        assertEquals(0, s.northCaptured)
        assertEquals(-1, s.lastPit)
        assertTrue(s.capturedPits.isEmpty())
        assertEquals("", s.event)
    }

    @Test
    fun sowsOneSeedIntoEachFollowingPitCounterClockwise() {
        val g = Oware(ann, bob)
        assertNull(g.play(ann, 2))
        var s = g.toState()
        assertEquals(listOf(4, 4, 0, 5, 5, 5, 5, 4, 4, 4, 4, 4), s.pits)
        assertEquals(2, s.lastPit)
        assertTrue(s.capturedPits.isEmpty())
        assertEquals("N", s.turn)
        assertEquals((6..11).toList(), s.legalMoves)

        // North sows across the end of the row onto south's side: 10, 11, 0, 1. Pit 1 now holds 5, no capture.
        assertNull(g.play(bob, 9))
        s = g.toState()
        assertEquals(listOf(5, 5, 0, 5, 5, 5, 5, 4, 4, 0, 5, 5), s.pits)
        assertEquals(0, s.northCaptured)
        assertEquals("S", s.turn)
        assertEquals(48, s.total())
    }

    @Test
    fun twelveOrMoreSeedsSkipTheStartingPit() {
        // 12 seeds: one into each of the other 11 pits, then the starting pit is skipped and pit 1 gets the last.
        val g = Oware(ann, bob, startPits = board(0 to 12, 9 to 4))
        assertNull(g.play(ann, 0))
        assertEquals(listOf(0, 2, 1, 1, 1, 1, 1, 1, 1, 5, 1, 1), g.toState().pits)

        // 23 seeds: two full laps of the other 11 pits (pit 6 is skipped twice), the last seed lands in pit 7.
        val laps = Oware(ann, bob, startPits = board(0 to 4, 6 to 23), startTurn = 'N')
        assertNull(laps.play(bob, 6))
        val s = laps.toState()
        assertEquals(listOf(6, 2, 2, 2, 2, 2, 0, 3, 2, 2, 2, 2), s.pits)
        assertTrue(s.capturedPits.isEmpty()) // pit 7 is north's own
    }

    @Test
    fun capturesTwoOrThreeSeedsInTheOpponentsPit() {
        // The last seed makes 2 in pit 7.
        val two = Oware(ann, bob, startPits = board(4 to 3, 7 to 1, 10 to 4))
        assertNull(two.play(ann, 4))
        var s = two.toState()
        assertEquals(listOf(7), s.capturedPits)
        assertEquals(0, s.pits[7])
        assertEquals(2, s.southCaptured)
        assertEquals("Ann captured 2 seeds", s.event)

        // North's last seed crosses the end of the row and makes 3 in pit 0; pit 11 before it is north's own.
        val three = Oware(ann, bob, startPits = board(0 to 2, 3 to 4, 6 to 2, 11 to 1), startTurn = 'N')
        assertNull(three.play(bob, 11))
        s = three.toState()
        assertEquals(listOf(0), s.capturedPits)
        assertEquals(listOf(0, 0, 0, 4, 0, 0, 2, 0, 0, 0, 0, 0), s.pits)
        assertEquals(3, s.northCaptured)
        assertEquals(0, s.southCaptured)
    }

    @Test
    fun noCaptureWhenTheLastPitHoldsAnythingElse() {
        // 1 or 4 seeds in the last pit capture nothing, and nor does landing on your own side.
        for (before in listOf(0, 3)) {
            val g = Oware(ann, bob, startPits = board(5 to 1, 6 to before, 9 to 4))
            assertNull(g.play(ann, 5))
            assertTrue(g.toState().capturedPits.isEmpty(), "$before seeds before the move")
            assertEquals(0, g.toState().southCaptured)
        }
    }

    @Test
    fun capturesBackwardsUntilAPitDoesNotQualify() {
        // Sowing 4, 5, 6, 7, 8 from pit 3: pit 8 makes 3, pit 7 makes 2, pit 6 makes 4 and stops the chain.
        val g = Oware(ann, bob, startPits = board(3 to 5, 6 to 3, 7 to 1, 8 to 2, 11 to 1))
        assertNull(g.play(ann, 3))
        val s = g.toState()
        assertEquals(listOf(7, 8), s.capturedPits)
        assertEquals(5, s.southCaptured)
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 4, 0, 0, 0, 0, 1), s.pits)
        assertEquals("Ann captured 5 seeds", s.event)
    }

    @Test
    fun capturesBackwardsUntilYourOwnSide() {
        // Sowing 5, 6, 7 from pit 4: pits 7 and 6 are captured; pit 5 also holds 2, but it is Ann's own.
        val g = Oware(ann, bob, startPits = board(4 to 3, 5 to 1, 6 to 1, 7 to 2, 10 to 4))
        assertNull(g.play(ann, 4))
        var s = g.toState()
        assertEquals(listOf(6, 7), s.capturedPits)
        assertEquals(5, s.southCaptured)
        assertEquals(2, s.pits[5])

        // The same for north across the end of the row: pits 1 and 0 are captured, north's own pit 11 is not.
        val wrap = Oware(ann, bob, startPits = board(0 to 1, 1 to 2, 3 to 4, 10 to 3, 11 to 1), startTurn = 'N')
        assertNull(wrap.play(bob, 10))
        s = wrap.toState()
        assertEquals(listOf(0, 1), s.capturedPits)
        assertEquals(5, s.northCaptured)
        assertEquals(2, s.pits[11])
        assertEquals(4, s.pits[3])
    }

    @Test
    fun aGrandSlamIsPlayedButCapturesNothing() {
        // Pits 6 and 7 would both make 2, and they hold every seed north has.
        val g = Oware(ann, bob, startPits = board(0 to 3, 5 to 2, 6 to 1, 7 to 1))
        assertNull(g.play(ann, 5))
        val s = g.toState()
        assertEquals(listOf(3, 0, 0, 0, 0, 0, 2, 2, 0, 0, 0, 0), s.pits)
        assertTrue(s.capturedPits.isEmpty())
        assertEquals(0, s.southCaptured)
        assertEquals("Grand slam: nothing captured", s.event)
        assertEquals("N", s.turn)
        assertEquals(5, s.lastPit)

        // With one more seed elsewhere on north's side the same move captures normally.
        val normal = Oware(ann, bob, startPits = board(0 to 3, 5 to 2, 6 to 1, 7 to 1, 11 to 1))
        assertNull(normal.play(ann, 5))
        assertEquals(listOf(6, 7), normal.toState().capturedPits)
        assertEquals(4, normal.toState().southCaptured)
    }

    @Test
    fun anOpponentWithoutSeedsMustBeFed() {
        // North has nothing: of Ann's pits only 4 (2 seeds: 5, 6) reaches north. 0 and 3 stay on south's side.
        val g = Oware(
            ann, bob,
            startPits = board(0 to 1, 3 to 2, 4 to 2),
            startSouthCaptured = 20,
            startNorthCaptured = 23,
        )
        var s = g.toState()
        assertEquals(listOf(4), s.legalMoves)
        assertEquals("Bob has no seeds: Ann must give them some", s.event)

        assertEquals("Bob has no seeds: you must give them some", g.play(ann, 3))
        assertEquals("Bob has no seeds: you must give them some", g.play(ann, 0))
        assertEquals(listOf(1, 0, 0, 2, 2, 0, 0, 0, 0, 0, 0, 0), g.toState().pits) // nothing changed

        assertNull(g.play(ann, 4))
        s = g.toState()
        assertEquals(1, s.pits[6])
        assertEquals("N", s.turn)
        assertEquals(listOf(6), s.legalMoves)
        assertEquals("", s.event)
    }

    @Test
    fun theGameEndsWhenNobodyCanFeedTheOpponent() {
        // Bob sows his last seed onto Ann's side; none of Ann's pits can then reach him, so she takes all of hers.
        val g = Oware(
            ann, bob,
            startPits = board(1 to 1, 2 to 2, 11 to 1),
            startTurn = 'N',
            startSouthCaptured = 22,
            startNorthCaptured = 22,
        )
        assertNull(g.play(bob, 11))
        val s = g.toState()
        assertEquals("won", s.status)
        assertEquals(ann, s.winner)
        assertEquals(26, s.southCaptured) // 22 + the 4 seeds on her side, including the one Bob just sowed
        assertEquals(22, s.northCaptured)
        assertEquals(List(12) { 0 }, s.pits)
        assertTrue(s.legalMoves.isEmpty())
        assertEquals("Bob can't be fed: Ann takes the rest", s.event)
        assertEquals(48, s.total())
    }

    @Test
    fun twentyFiveSeedsWinAtOnce() {
        val g = Oware(
            ann, bob,
            startPits = board(5 to 1, 6 to 1, 10 to 3),
            startSouthCaptured = 23,
            startNorthCaptured = 20,
        )
        assertNull(g.play(ann, 5))
        val s = g.toState()
        assertEquals("won", s.status)
        assertEquals(ann, s.winner)
        assertEquals(25, s.southCaptured)
        assertEquals(3, s.pits[10]) // the seeds left on the board stay where they are
        assertTrue(s.legalMoves.isEmpty())
        assertEquals("The game is over", g.play(bob, 10))
    }

    @Test
    fun twentyFourEachIsADraw() {
        // As above, but the totals end level after Ann takes the 4 seeds on her side.
        val g = Oware(
            ann, bob,
            startPits = board(1 to 1, 2 to 2, 11 to 1),
            startTurn = 'N',
            startSouthCaptured = 20,
            startNorthCaptured = 24,
        )
        assertNull(g.play(bob, 11))
        val s = g.toState()
        assertEquals("draw", s.status)
        assertNull(s.winner)
        assertEquals(24, s.southCaptured)
        assertEquals(24, s.northCaptured)
        assertEquals("Oware: Bob can't be fed: Ann takes the rest. draw 24-24", g.describe())
    }

    @Test
    fun aHundredMovesWithoutACaptureEndTheGame() {
        // One seed each chasing the other round the board: nobody ever captures, and every move is forced. When
        // Ann's seed crosses over, Bob has to feed her with his seed in pit 11. The cycle repeats every 12 moves.
        val g = Oware(ann, bob, startPits = board(0 to 1, 6 to 1), startSouthCaptured = 24, startNorthCaptured = 22)
        repeat(Oware.MAX_PLIES_WITHOUT_CAPTURE - 1) { ply ->
            var s = g.toState()
            assertEquals("playing", s.status, "after $ply moves")
            if (ply % 12 == 11) {
                assertEquals(listOf(11), s.legalMoves) // Ann has no seeds: only pit 11 reaches her
                assertEquals("Ann has no seeds: Bob must give them some", s.event)
            }
            val player = if (s.turn == "S") ann else bob
            assertNull(g.play(player, s.legalMoves.single()), "move ${ply + 1}")
            s = g.toState()
            assertEquals(24, s.southCaptured)
            assertEquals(22, s.northCaptured)
        }

        // The 100th move: Ann's seed is in pit 2 and Bob's in pit 8 after it, and each takes their own.
        assertEquals("playing", g.toState().status)
        assertNull(g.play(bob, 7))
        val s = g.toState()
        assertEquals("won", s.status)
        assertEquals(ann, s.winner)
        assertEquals(25, s.southCaptured)
        assertEquals(23, s.northCaptured)
        assertEquals(List(12) { 0 }, s.pits)
        assertEquals("100 moves without a capture: each player takes their own seeds", s.event)
    }

    @Test
    fun aCaptureRestartsTheCountOfMovesWithoutOne() {
        // Four quiet moves, then Ann's seed from pit 5 makes 2 in pit 6 and captures it. From there every move is
        // forced and nobody captures again (the same chase as above), so the game ends 100 moves after the capture.
        val g = Oware(
            ann, bob,
            startPits = board(1 to 1, 5 to 1, 6 to 1, 7 to 1),
            startSouthCaptured = 20,
            startNorthCaptured = 24,
        )
        for ((player, pit) in listOf(ann to 1, bob to 7, ann to 2, bob to 8, ann to 5)) assertNull(g.play(player, pit))
        assertEquals(listOf(6), g.toState().capturedPits)
        assertEquals(22, g.toState().southCaptured)

        repeat(Oware.MAX_PLIES_WITHOUT_CAPTURE) { ply ->
            val s = g.toState()
            assertEquals("playing", s.status, "$ply moves after the capture")
            assertNull(g.play(if (s.turn == "S") ann else bob, s.legalMoves.single()))
        }
        val s = g.toState()
        assertEquals("100 moves without a capture: each player takes their own seeds", s.event)
        assertEquals(List(12) { 0 }, s.pits)
        assertEquals(48, s.total())
    }

    @Test
    fun rejectsIllegalMovesWithoutChangingAnything() {
        val g = Oware(ann, bob, startPits = board(0 to 4, 2 to 0, 6 to 4, 7 to 4))
        val before = g.toState()
        assertEquals("Not your turn", g.play(bob, 6))
        assertEquals("Pick one of your own pits", g.play(ann, 6))
        assertEquals("That pit is empty", g.play(ann, 2))
        assertEquals("Invalid pit", g.play(ann, 12))
        assertEquals("Invalid pit", g.play(ann, -1))
        assertEquals("You are not in this game", g.play("Eve", 0))
        assertEquals("Oware only understands moves", g.handle(ann, ClientMessage(type = "place")))
        assertEquals(before, g.toState())

        // A move without a cell is invalid too.
        assertEquals("Invalid pit", g.handle(ann, ClientMessage(type = "move")))
        assertNull(g.handle(ann, ClientMessage(type = "move", cell = 0)))
    }

    @Test
    fun describeSaysWhoseTurnItIsAndTheScore() {
        val g = Oware(ann, bob)
        assertEquals("Oware: Ann's turn (south), 0-0", g.describe())
        g.play(ann, 0)
        assertEquals("Oware: Bob's turn (north), 0-0", g.describe())
    }
}
