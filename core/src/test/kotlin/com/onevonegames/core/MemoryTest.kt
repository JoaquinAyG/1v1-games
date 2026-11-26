package com.onevonegames.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cards are numbered row by row; Ann flips first. Most tests lay the pairs out themselves with a deck. */
class MemoryTest {
    private val ann = "Ann"
    private val bob = "Bob"

    /** 4 x 2 cards: the top row holds faces 0 1 2 3 and the bottom row the same again. */
    private val twoRows = listOf(0, 1, 2, 3, 0, 1, 2, 3)

    private fun small() = Memory(ann, bob, columns = 4, rows = 2, deck = twoRows)

    /** The face shown for every card is exactly what the rules allow: face down cards always show -1. */
    private fun assertOnlyFaceUpCardsShowTheirFace(s: MemoryState) {
        for (card in s.faces.indices) {
            val faceUp = s.taken[card] != '.' || card in s.flipped || card in s.lastPair
            assertEquals(faceUp, s.faces[card] >= 0, "card $card in $s")
        }
    }

    /**
     * Plays [g] to the end for both players with a perfect memory of every face seen (take a known pair if there is
     * one, otherwise flip an unknown card and then its known partner, or another unknown card). Checks every state
     * with [check] and returns the whole deck as it was learned.
     */
    private fun playWithPerfectMemory(g: Memory, check: (MemoryState) -> Unit = {}): List<Int> {
        val known = HashMap<Int, Int>() // card -> face
        var s = g.toState()

        fun flip(card: Int) {
            assertNull(g.flip(if (s.turn == "1") g.first else g.second, card))
            s = g.toState()
            check(s)
            s.faces.forEachIndexed { c, face -> if (face >= 0) known[c] = face }
        }

        while (!g.finished) {
            val open = s.taken.indices.filter { s.taken[it] == '.' }
            val pair = open.filter { it in known }.groupBy { known.getValue(it) }.values.firstOrNull { it.size == 2 }
            val a = pair?.get(0) ?: open.first { it !in known }
            flip(a)
            val b = pair?.get(1)
                ?: open.firstOrNull { it != a && known[it] == known[a] }
                ?: open.first { it != a && it !in known }
            flip(b)
        }
        return s.faces.indices.map { known.getValue(it) }
    }

    @Test
    fun startsWithEveryCardFaceDownAndTheFirstPlayerToFlip() {
        val s = Memory(ann, bob).toState()
        assertEquals(6, s.columns)
        assertEquals(4, s.rows)
        assertEquals(List(24) { -1 }, s.faces)
        assertEquals(".".repeat(24), s.taken)
        assertEquals("1", s.turn)
        assertEquals("playing", s.status)
        assertEquals(0, s.firstScore)
        assertEquals(0, s.secondScore)
        assertTrue(s.flipped.isEmpty())
        assertTrue(s.lastPair.isEmpty())
        assertEquals("Memory: Ann's turn, 0-0", Memory(ann, bob).describe())
    }

    @Test
    fun roomsShuffleTwelvePairsDifferentlyEveryTime() {
        val one = playWithPerfectMemory(Memory(ann, bob))
        val two = playWithPerfectMemory(Memory(ann, bob))
        for (deck in listOf(one, two)) {
            assertEquals(24, deck.size)
            assertEquals((0 until 12).flatMap { listOf(it, it) }, deck.sorted())
        }
        // 24! / 2^12 orders: the same one twice would be a broken shuffle.
        assertNotEquals(one, two)
    }

    @Test
    fun flippingOneCardShowsOnlyThatFace() {
        val g = small()
        assertNull(g.flip(ann, 2))
        val s = g.toState()
        assertEquals(listOf(-1, -1, 2, -1, -1, -1, -1, -1), s.faces)
        assertEquals(listOf(2), s.flipped)
        assertEquals("1", s.turn)
    }

    @Test
    fun aPairIsTakenAndThePlayerFlipsAgain() {
        val g = small()
        assertNull(g.flip(ann, 1))
        assertNull(g.flip(ann, 5))
        val s = g.toState()
        assertEquals(".1...1..", s.taken)
        assertEquals(listOf(-1, 1, -1, -1, -1, 1, -1, -1), s.faces)
        assertEquals(listOf(1, 5), s.lastPair)
        assertTrue(s.lastMatched)
        assertEquals(1, s.firstScore)
        assertEquals("1", s.turn)
        assertEquals("Memory: Ann found a pair and goes again, 1-0", g.describe())

        // Her next flip starts a new try; the pair stays face up because it is hers now.
        assertNull(g.flip(ann, 0))
        assertEquals(listOf(0, 1, -1, -1, -1, 1, -1, -1), g.toState().faces)
        assertTrue(g.toState().lastPair.isEmpty())
    }

    @Test
    fun aMismatchStaysFaceUpUntilTheNextPlayersFirstFlip() {
        val g = small()
        assertNull(g.flip(ann, 0))
        assertNull(g.flip(ann, 1))
        var s = g.toState()
        assertEquals("2", s.turn)
        assertEquals(listOf(0, 1), s.lastPair)
        assertFalse(s.lastMatched)
        assertEquals(listOf(0, 1, -1, -1, -1, -1, -1, -1), s.faces) // both can still see them
        assertEquals("Memory: no match, Bob's turn, 0-0", g.describe())

        // Bob's first flip turns them face down again.
        assertNull(g.flip(bob, 6))
        s = g.toState()
        assertEquals(listOf(-1, -1, -1, -1, -1, -1, 2, -1), s.faces)
        assertTrue(s.lastPair.isEmpty())
        assertEquals(listOf(6), s.flipped)
    }

    @Test
    fun theNextPlayerMayFlipACardOfTheMismatchedPair() {
        val g = small()
        assertNull(g.flip(ann, 0))
        assertNull(g.flip(ann, 1))
        assertNull(g.flip(bob, 1)) // face up already, but it is a new turn: allowed
        assertEquals(listOf(-1, 1, -1, -1, -1, -1, -1, -1), g.toState().faces)
        assertNull(g.flip(bob, 5))
        assertEquals(".2...2..", g.toState().taken)
        assertEquals(1, g.toState().secondScore)
    }

    @Test
    fun rejectsWrongTurnsBadCardsAndCardsThatAreTakenOrFaceUp() {
        val g = small()
        assertEquals("Not your turn", g.flip(bob, 0))
        assertEquals("You are not in this game", g.flip("Eve", 0))
        assertEquals("Invalid card", g.flip(ann, -1))
        assertEquals("Invalid card", g.flip(ann, 8))
        assertEquals("Memory only understands moves", g.handle(ann, ClientMessage(type = "swap")))
        assertNull(g.flip(ann, 0))
        assertEquals("That card is already face up", g.flip(ann, 0))
        assertNull(g.flip(ann, 4)) // a pair
        assertEquals("That pair is already taken", g.flip(ann, 4))
        assertEquals("That pair is already taken", g.flip(ann, 0))
        assertEquals("Not your turn", g.flip(bob, 1))
        assertEquals(1, g.toState().firstScore)
        assertEquals("1", g.toState().turn)
    }

    @Test
    fun mostPairsWins() {
        val g = small()
        for ((a, b) in listOf(0 to 4, 1 to 5, 2 to 6, 3 to 7)) {
            assertNull(g.flip(ann, a))
            assertNull(g.flip(ann, b))
        }
        val s = g.toState()
        assertEquals("won", s.status)
        assertEquals(ann, s.winner)
        assertEquals(4, s.firstScore)
        assertEquals(twoRows, s.faces) // everything is face up at the end
        assertEquals("Memory: Ann won 4-0", g.describe())
        assertEquals("The game is over", g.flip(bob, 0))
        assertEquals("The game is over", g.flip(ann, 0))
    }

    @Test
    fun asManyPairsEachIsADraw() {
        val g = small()
        assertNull(g.flip(ann, 0))
        assertNull(g.flip(ann, 1)) // no match
        for ((a, b) in listOf(0 to 4, 1 to 5)) {
            assertNull(g.flip(bob, a))
            assertNull(g.flip(bob, b))
        }
        assertNull(g.flip(bob, 2))
        assertNull(g.flip(bob, 3)) // no match
        for ((a, b) in listOf(2 to 6, 3 to 7)) {
            assertNull(g.flip(ann, a))
            assertNull(g.flip(ann, b))
        }
        val s = g.toState()
        assertEquals("draw", s.status)
        assertNull(s.winner)
        assertEquals(2, s.firstScore)
        assertEquals(2, s.secondScore)
        assertEquals("22112211", s.taken)
        assertEquals("Memory: draw 2-2", g.describe())
    }

    @Test
    fun noFaceDownCardIsEverSentInAWholeGame() {
        val g = Memory(ann, bob)
        var states = 0
        playWithPerfectMemory(g) { s ->
            states++
            assertOnlyFaceUpCardsShowTheirFace(s)
            // Both players get exactly the same view, so neither learns more than the other.
            assertEquals(g.messageFor(ann).memory, g.messageFor(bob).memory)
            assertEquals(s, g.messageFor(ann).memory)
        }
        assertTrue(states >= 24)
        assertTrue(g.finished)
        val s = g.toState()
        assertEquals(12, s.firstScore + s.secondScore)
    }

    @Test
    fun aDeckMustHoldEveryFaceExactlyTwice() {
        for (deck in listOf(listOf(0, 0, 1), listOf(0, 0, 0, 1), listOf(0, 1, 2, 3), listOf(0, 1, 1, 2))) {
            val columns = deck.size
            val failure = runCatching { Memory(ann, bob, columns = columns, rows = 1, deck = deck) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException, "deck $deck was accepted")
        }
    }
}
