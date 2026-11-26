package com.classicgames.core

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cards are numbered suit * 10 + rank (suits Oros, Copas, Espadas, Bastos; ranks As, 2..7, Sota, Caballo, Rey) and
 * written here by name with [c]. Ann is the first player and leads the first trick. Most tests choose the cards with
 * [deck]: Ann's 3 cards, Bob's 3, the card turned up, then the top of the deck.
 */
class BriscaTest {
    private val ann = "Ann"
    private val bob = "Bob"

    private fun c(rank: String, suit: String) = Brisca.cardOf(Brisca.SUITS.indexOf(suit), Brisca.RANKS.indexOf(rank))

    /** A full deck that starts with these cards and goes on with every other card, smallest first. */
    private fun deck(ann: List<Int>, bob: List<Int>, trump: Int, vararg top: Int): List<Int> {
        val start = ann + bob + trump + top.toList()
        return start + (0 until 40).filter { it !in start }
    }

    /** Ann holds As, 4 and 5; Bob 3, 6 and 2; the Rey de Espadas is turned up, so Espadas are trumps. */
    private val plain = deck(
        listOf(c("As", "Oros"), c("4", "Copas"), c("5", "Bastos")),
        listOf(c("3", "Oros"), c("6", "Copas"), c("2", "Bastos")),
        c("Rey", "Espadas"),
        c("Sota", "Copas"), c("Caballo", "Bastos"), c("2", "Espadas"), c("As", "Copas"),
    )

    /** A deck that ends 60-60 when both players always play the first card in their hand (found by a search). */
    private val drawnDeck = listOf(
        36, 35, 31, 39, 6, 17, 12, 15, 24, 11, 3, 21, 29, 10, 25, 26, 20, 18, 23, 38,
        5, 1, 7, 27, 33, 0, 34, 2, 4, 14, 8, 16, 19, 9, 22, 13, 37, 30, 32, 28,
    )

    /** Plays [g] to the end, each player always playing the first card in their hand; checks every state with [check]. */
    private fun playFirstCards(g: Brisca, check: (Brisca) -> Unit = {}) {
        var plays = 0
        while (!g.finished) {
            val player = g.turn
            assertNull(g.play(player, g.toState(player).hand.first()))
            check(g)
            assertTrue(++plays <= 40, "the game never ended")
        }
        assertEquals(40, plays)
    }

    @Test
    fun dealsThreeCardsEachAndTurnsUpTheTrump() {
        val g = Brisca(ann, bob, plain)
        val a = g.toState(ann)
        val b = g.toState(bob)
        assertEquals(plain.subList(0, 3), a.hand)
        assertEquals(plain.subList(3, 6), b.hand)
        assertEquals(3, a.opponentCards)
        assertEquals(3, b.opponentCards)
        assertEquals(34, a.deckSize) // the 33 cards of the deck and the face-up trump under it
        assertEquals(c("Rey", "Espadas"), a.trumpCard)
        assertEquals(a.trumpCard, b.trumpCard)
        assertEquals(2, g.trumpSuit)
        assertTrue(a.yourTurn)
        assertFalse(b.yourTurn)
        assertEquals(ann, a.firstPlayer)
        assertEquals(bob, a.opponent)
        assertEquals("playing", a.status)
        assertEquals(0, a.yourPoints + a.opponentPoints)
        assertTrue(a.trick.isEmpty() && a.lastTrick.isEmpty())
        assertNull(a.lastTrickWinner)
        assertEquals("Brisca: Ann to play, trumps Espadas (Ann 0, Bob 0)", g.describe())
    }

    @Test
    fun cardsAreNamedAndCountedTheSpanishWay() {
        assertEquals("As de Oros", Brisca.nameOf(0))
        assertEquals("3 de Copas", Brisca.nameOf(12))
        assertEquals("7 de Espadas", Brisca.nameOf(26))
        assertEquals("Sota de Bastos", Brisca.nameOf(37))
        assertEquals("Rey de Bastos", Brisca.nameOf(39))
        assertEquals(120, (0 until 40).sumOf { Brisca.pointsOf(it) })
        assertEquals(
            listOf(11, 0, 10, 0, 0, 0, 0, 2, 3, 4),
            Brisca.RANKS.map { Brisca.pointsOf(c(it, "Copas")) },
        )
    }

    @Test
    fun theHighestCardOfTheSuitLedWinsUnlessSomeoneTrumps() {
        val espadas = 2
        val order = listOf("As", "3", "Rey", "Caballo", "Sota", "7", "6", "5", "4", "2")
        // Within a suit, trumps or not, the order decides, whoever played first.
        for (suit in Brisca.SUITS) for (i in order.indices) for (j in order.indices) {
            if (i == j) continue
            assertEquals(i < j, Brisca.leadWins(c(order[i], suit), c(order[j], suit), espadas), "${order[i]} vs ${order[j]} de $suit")
        }
        // Another suit that is not trumps never wins, however high.
        assertTrue(Brisca.leadWins(c("2", "Oros"), c("As", "Copas"), espadas))
        assertTrue(Brisca.leadWins(c("4", "Bastos"), c("3", "Oros"), espadas))
        // Any trump beats any other suit, whether it is led or played second.
        assertFalse(Brisca.leadWins(c("As", "Oros"), c("2", "Espadas"), espadas))
        assertTrue(Brisca.leadWins(c("2", "Espadas"), c("As", "Oros"), espadas))
    }

    @Test
    fun theTrickWinnerTakesThePointsDrawsFirstAndLeads() {
        val g = Brisca(ann, bob, plain)
        assertNull(g.play(ann, c("As", "Oros")))
        var a = g.toState(ann)
        var b = g.toState(bob)
        assertEquals(listOf(BriscaPlay(ann, c("As", "Oros"))), a.trick)
        assertEquals(a.trick, b.trick)
        assertEquals(2, a.hand.size)
        assertEquals(2, b.opponentCards)
        assertTrue(b.yourTurn)
        assertFalse(a.yourTurn)

        // Bob doesn't have to beat it: his 3 is lower than the As.
        assertNull(g.play(bob, c("3", "Oros")))
        a = g.toState(ann)
        b = g.toState(bob)
        assertEquals(21, a.yourPoints)
        assertEquals(0, a.opponentPoints)
        assertEquals(21, b.opponentPoints)
        assertTrue(a.trick.isEmpty())
        assertEquals(listOf(BriscaPlay(ann, c("As", "Oros")), BriscaPlay(bob, c("3", "Oros"))), a.lastTrick)
        assertEquals(ann, a.lastTrickWinner)
        // Ann won, so she drew the top card and Bob the next one.
        assertEquals(listOf(c("4", "Copas"), c("5", "Bastos"), c("Sota", "Copas")), a.hand)
        assertEquals(listOf(c("6", "Copas"), c("2", "Bastos"), c("Caballo", "Bastos")), b.hand)
        assertEquals(32, a.deckSize)
        assertTrue(a.yourTurn)
        assertEquals("You took the trick, 21 points", a.event)
        assertEquals("Ann took the trick, 21 points", b.event)

        // Bob's 6 beats Ann's 4 of the same suit: he takes it, draws first and leads.
        assertNull(g.play(ann, c("4", "Copas")))
        assertNull(g.play(bob, c("6", "Copas")))
        a = g.toState(ann)
        b = g.toState(bob)
        assertEquals(bob, a.lastTrickWinner)
        assertEquals("Bob took the trick, no points", a.event)
        assertEquals(c("2", "Espadas"), b.hand.last())
        assertEquals(c("As", "Copas"), a.hand.last())
        assertTrue(b.yourTurn)
        assertEquals("Brisca: Bob to play, trumps Espadas (Ann 21, Bob 0)", g.describe())
    }

    @Test
    fun aTrumpBeatsAnyOtherSuitAndAnotherSuitNeverWins() {
        val g = Brisca(ann, bob, deck(plain.subList(0, 3), plain.subList(3, 6), c("Rey", "Espadas"), c("Sota", "Copas"), c("2", "Espadas")))
        assertNull(g.play(ann, c("As", "Oros")))
        assertNull(g.play(bob, c("2", "Bastos"))) // another suit, not trumps: Ann keeps the trick
        assertEquals(ann, g.toState(ann).lastTrickWinner)
        assertEquals(11, g.toState(ann).yourPoints)

        // Bob drew the 2 de Espadas, a trump: it takes Ann's Sota de Copas.
        assertNull(g.play(ann, c("Sota", "Copas")))
        assertNull(g.play(bob, c("2", "Espadas")))
        assertEquals(bob, g.toState(ann).lastTrickWinner)
        assertEquals(2, g.toState(bob).yourPoints)
    }

    @Test
    fun theDeckIsDrawnInOrderAndTheFaceUpTrumpGoesLastToTheLoser() {
        val g = Brisca(ann, bob, plain)
        val expected = ArrayDeque(plain.subList(7, 40) + plain[6]) // the deck from the top, then the face-up trump
        var tricks = 0
        playFirstCards(g) {
            val s = g.toState(ann)
            if (s.trick.isNotEmpty()) return@playFirstCards
            tricks++
            if (tricks <= 17) {
                val taker = s.lastTrickWinner!!
                val loser = if (taker == ann) bob else ann
                assertEquals(expected.removeFirst(), g.toState(taker).hand.last(), "trick $tricks: the winner draws first")
                assertEquals(expected.removeFirst(), g.toState(loser).hand.last(), "trick $tricks: then the loser")
                if (tricks == 17) {
                    assertEquals(plain[6], g.toState(loser).hand.last()) // the face-up trump, drawn last
                    assertEquals(0, s.deckSize)
                    assertTrue(s.event.endsWith(". The deck is empty"), s.event)
                } else {
                    assertEquals(34 - 2 * tricks, s.deckSize)
                }
            }
            // Nobody draws once the deck is empty; the hands are played out.
            if (tricks > 17) assertEquals(20 - tricks, s.hand.size)
            assertEquals(s.hand.size, s.opponentCards)
        }
        assertEquals(20, tricks)
        assertTrue(expected.isEmpty())
        // The face-up card is still shown after it was drawn, since both players saw who took it.
        assertEquals(plain[6], g.toState(bob).trumpCard)
    }

    @Test
    fun theLeaderMaySwapTheSevenOfTrumpsForTheFaceUpCard() {
        val seven = c("7", "Espadas")
        val g = Brisca(ann, bob, deck(listOf(c("4", "Oros"), seven, c("5", "Oros")), listOf(0, 10, 20), c("Rey", "Espadas")))
        assertTrue(g.toState(ann).canSwap)
        assertFalse(g.toState(bob).canSwap)
        assertNull(g.swapSeven(ann))
        val a = g.toState(ann)
        val b = g.toState(bob)
        assertEquals(listOf(c("4", "Oros"), c("Rey", "Espadas"), c("5", "Oros")), a.hand) // in the 7's place
        assertEquals(seven, a.trumpCard)
        assertEquals(seven, b.trumpCard)
        assertEquals(34, a.deckSize)
        assertTrue(a.yourTurn) // she still has to play her card
        assertFalse(a.canSwap)
        assertEquals("You swapped the 7 de Espadas for the Rey de Espadas", a.event)
        assertEquals("Ann swapped the 7 de Espadas for the Rey de Espadas", b.event)
        assertEquals("You don't have the 7 of trumps", g.swapSeven(ann))
        assertNull(g.play(ann, c("Rey", "Espadas")))
    }

    @Test
    fun theSecondPlayerMaySwapToo() {
        val seven = c("7", "Bastos")
        val g = Brisca(ann, bob, deck(listOf(0, 1, 2), listOf(10, seven, 11), c("2", "Bastos")))
        assertFalse(g.toState(bob).canSwap) // not his turn yet
        assertEquals("Not your turn", g.swapSeven(bob))
        assertEquals("You don't have the 7 of trumps", g.swapSeven(ann))
        assertNull(g.play(ann, 0))
        assertTrue(g.toState(bob).canSwap)
        assertNull(g.handle(bob, ClientMessage(type = "swap7")))
        assertEquals(listOf(10, c("2", "Bastos"), 11), g.toState(bob).hand)
        assertEquals(seven, g.trumpCard)
        assertNull(g.play(bob, 10))
        assertEquals(seven, g.toState(ann).trumpCard) // Ann sees the 7 under the deck now
        assertEquals(32, g.toState(ann).deckSize) // the swap drew nothing, the trick two cards
    }

    @Test
    fun theSevenCanNotBeSwappedOnceTheFaceUpTrumpIsDrawn() {
        // The 7 de Oros is the deck's last card: the winner of the 17th trick draws it while the loser takes the face-up
        // trump, so it comes too late to swap.
        val seven = c("7", "Oros")
        val start = deck(listOf(10, 11, 12), listOf(20, 21, 22), c("Rey", "Oros"))
        val g = Brisca(ann, bob, start.filter { it != seven } + seven)
        var tricks = 0
        while (tricks < 17) {
            val player = g.turn
            assertNull(g.play(player, g.toState(player).hand.first()))
            if (g.toState(ann).trick.isEmpty()) tricks++
        }
        val holder = if (seven in g.toState(ann).hand) ann else bob
        assertEquals(0, g.deckSize)
        assertEquals(holder, g.turn) // the winner drew it and leads
        assertFalse(g.toState(holder).canSwap)
        assertEquals("The face-up trump has already been drawn", g.swapSeven(holder))
    }

    @Test
    fun rejectsWrongTurnsBadCardsAndBadMessages() {
        val g = Brisca(ann, bob, plain)
        assertEquals("Not your turn", g.play(bob, c("3", "Oros")))
        assertEquals("You are not in this game", g.play("Eve", 0))
        assertEquals("Invalid card", g.play(ann, -1))
        assertEquals("Invalid card", g.play(ann, 40))
        assertEquals("That card is not in your hand", g.play(ann, c("3", "Oros"))) // Bob's
        assertEquals("That card is not in your hand", g.play(ann, c("Sota", "Copas"))) // still in the deck
        assertEquals("Send the card to play", g.handle(ann, ClientMessage(type = "move")))
        assertEquals("Send the card to play", g.handle(ann, ClientMessage(type = "move", cell = 0)))
        assertEquals("Send the card to play", g.handle(ann, ClientMessage(type = "move", cell = 1, card = 0)))
        assertEquals("Brisca understands 'move' and 'swap7'", g.handle(ann, ClientMessage(type = "swap")))
        assertEquals("You don't have the 7 of trumps", g.swapSeven(ann))

        assertNull(g.handle(ann, ClientMessage(type = "move", card = c("As", "Oros"))))
        assertEquals("Not your turn", g.play(ann, c("4", "Copas")))
        assertEquals("That card is not in your hand", g.play(bob, c("As", "Oros"))) // on the table now
        assertEquals(1, g.toState(bob).trick.size)
    }

    @Test
    fun sixtyOnePointsWinAndNothingIsAcceptedAfterTheEnd() {
        val g = Brisca(ann, bob, plain)
        playFirstCards(g)
        val a = g.toState(ann)
        val b = g.toState(bob)
        assertEquals(120, a.yourPoints + a.opponentPoints)
        assertNotEquals(60, a.yourPoints)
        val annWon = a.yourPoints >= 61
        assertEquals("won", a.status)
        assertEquals(if (annWon) ann else bob, a.winner)
        assertEquals(a.winner, b.winner)
        assertFalse(a.yourTurn || b.yourTurn)
        assertTrue(a.hand.isEmpty() && b.hand.isEmpty())
        val (high, low) = maxOf(a.yourPoints, a.opponentPoints) to minOf(a.yourPoints, a.opponentPoints)
        assertEquals(if (annWon) "You won $high-$low" else "Bob won $high-$low", a.event)
        assertEquals(if (annWon) "Ann won $high-$low" else "You won $high-$low", b.event)

        assertEquals("The game is over", g.play(ann, 0))
        assertEquals("The game is over", g.play(bob, 0))
        assertEquals("The game is over", g.swapSeven(g.turn))
    }

    @Test
    fun sixtyEachIsADraw() {
        val g = Brisca(ann, bob, drawnDeck)
        playFirstCards(g)
        val a = g.toState(ann)
        assertEquals(60, a.yourPoints)
        assertEquals(60, a.opponentPoints)
        assertEquals("draw", a.status)
        assertNull(a.winner)
        assertEquals("A draw, 60-60", a.event)
        assertEquals("A draw, 60-60", g.toState(bob).event)
        assertEquals("Brisca: draw (Ann 60, Bob 60)", g.describe())
    }

    @Test
    fun nobodyEverSeesTheOtherHandOrTheDeck() {
        repeat(30) { seed ->
            val g = Brisca(ann, bob, random = Random(seed.toLong()))
            val played = mutableListOf<Int>()
            fun check(viewer: String) {
                val s = g.toState(viewer)
                val theirs = g.toState(if (viewer == ann) bob else ann).hand
                // Every card this player can see: their hand, the table and the face-up trump.
                val seen = s.hand + s.trick.map { it.card } + s.lastTrick.map { it.card } + s.trumpCard
                // The only card of the other hand they may know is the face-up trump, which they saw being drawn.
                assertTrue(seen.none { it in theirs && it != s.trumpCard }, "seed $seed: $viewer sees $seen, other hand $theirs")
                assertEquals(theirs.size, s.opponentCards)
                // The text sent along with the state names no card at all.
                assertFalse(g.messageFor(viewer).text.contains(" de "), g.messageFor(viewer).text)
            }
            check(ann)
            check(bob)
            playFirstCards(g) {
                check(ann)
                check(bob)
                g.toState(ann).trick.lastOrNull()?.let { played += it.card }
                if (g.toState(ann).trick.isEmpty()) played += g.toState(ann).lastTrick.last().card
            }
            // Every one of the 40 cards was played exactly once.
            assertEquals((0 until 40).toList(), played.sorted(), "seed $seed")
        }
    }

    @Test
    fun theShuffleUsesTheRandomItIsGiven() {
        fun hands(g: Brisca) = Triple(g.toState(ann).hand, g.toState(bob).hand, g.trumpCard)
        assertEquals(hands(Brisca(ann, bob, random = Random(42))), hands(Brisca(ann, bob, random = Random(42))))
        assertNotEquals(hands(Brisca(ann, bob, random = Random(1))), hands(Brisca(ann, bob, random = Random(2))))
        // Rooms use a SecureRandom: two games (40! orders) dealing the same seven cards would be a broken shuffle.
        assertNotEquals(hands(Brisca(ann, bob)), hands(Brisca(ann, bob)))
    }

    @Test
    fun aDeckMustHoldEveryCardOnce() {
        for (bad in listOf((0 until 39).toList(), (0 until 40).toList() + 0, List(40) { it % 39 }, (1..40).toList())) {
            assertTrue(runCatching { Brisca(ann, bob, bad) }.exceptionOrNull() is IllegalArgumentException, "deck $bad")
        }
    }
}
