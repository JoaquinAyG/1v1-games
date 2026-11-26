package com.classicgames.core

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cards are written "R3" (red 3), with the colours R, Y, G, B, W in the order of [Hanabi.COLORS]. Ann plays first. Most
 * tests choose the cards with [deck]: Ann's 5 cards, Bob's 5, then the top of the deck.
 */
class HanabiTest {
    private val ann = "Ann"
    private val bob = "Bob"

    private fun c(name: String) = Hanabi.cardOf("RYGBW".indexOf(name[0]), name.substring(1).toInt())

    /** A full deck that starts with these cards and goes on with every other card, smallest first. */
    private fun deck(ann: String, bob: String, top: String = ""): List<Int> {
        val start = (ann.split(" ") + bob.split(" ") + top.split(" ").filter { it.isNotEmpty() }).map(::c)
        val rest = Hanabi.FULL_DECK.toMutableList()
        for (card in start) assertTrue(rest.remove(card), "too many ${Hanabi.nameOf(card)}")
        return start + rest
    }

    /** Ann holds R1 Y1 G2 B3 W5, Bob R1 B1 R2 Y1 G4, and the deck starts R3 W1 B2. */
    private val plain = deck("R1 Y1 G2 B3 W5", "R1 B1 R2 Y1 G4", "R3 W1 B2")

    private fun name(card: HanabiCard) = "${"RYGBW"[card.color]}${card.number}"

    /** What each player has been told about each card in their hand: (known colour, known number, ruled-out colours, numbers). */
    private fun knowledge(hand: List<HanabiCard>) = hand.map { listOf(it.knownColor, it.knownNumber, it.notColors, it.notNumbers) }

    /**
     * The invariants of hidden information, for both players: your own cards never show, what you know about them is the
     * same in your view and your partner's, and it is true of the real cards. Also nothing is lost: every card is in a
     * hand, on a firework, on the discard pile or in the deck.
     */
    private fun assertHidden(g: Hanabi) {
        for (player in listOf(ann, bob)) {
            val mine = g.toState(player)
            val theirs = g.toState(if (player == ann) bob else ann)
            assertTrue(mine.yourHand.all { it.color == -1 && it.number == 0 }, "$player sees their own cards: ${mine.yourHand}")
            assertEquals(knowledge(mine.yourHand), knowledge(theirs.partnerHand))
            for (card in theirs.partnerHand) {
                assertTrue(card.color in 0..4 && card.number in 1..5)
                assertTrue(card.knownColor == -1 || card.knownColor == card.color, "$card")
                assertTrue(card.knownNumber == 0 || card.knownNumber == card.number, "$card")
                assertFalse(card.color in card.notColors, "$card")
                assertFalse(card.number in card.notNumbers, "$card")
            }
            val json = protocolJson.encodeToString(ServerMessage.serializer(), g.messageFor(player))
            assertFalse("deck\"" in json.replace("deckSize\"", ""), "the deck itself is never sent")
        }
        val s = g.toState(ann)
        assertEquals(50, s.yourHand.size + s.partnerHand.size + s.fireworks.sum() + s.discards.size + s.deckSize)
    }

    /** One legal action picked at random (rarely a play, so games last); returns its description. */
    private fun randomAction(g: Hanabi, random: Random): String {
        val player = g.turn
        val s = g.toState(player)
        val choices = buildList {
            if (s.hints > 0) add("hint")
            if (s.hints < Hanabi.MAX_HINTS) add("discard")
            if (random.nextInt(6) == 0 || isEmpty()) add("play")
        }
        val index = random.nextInt(s.yourHand.size)
        return when (choices[random.nextInt(choices.size)]) {
            "hint" -> {
                val target = s.partnerHand[random.nextInt(s.partnerHand.size)]
                if (random.nextBoolean()) assertNull(g.hint(player, target.color, null)) else assertNull(g.hint(player, null, target.number))
                "hint"
            }
            "discard" -> { assertNull(g.discard(player, index)); "discard" }
            else -> { assertNull(g.play(player, index)); "play" }
        }
    }

    @Test
    fun dealsFiveCardsEachWithEightHintsAndThreeLives() {
        val g = Hanabi(ann, bob, plain)
        val a = g.toState(ann)
        val b = g.toState(bob)
        assertEquals(listOf("R1", "B1", "R2", "Y1", "G4"), a.partnerHand.map(::name))
        assertEquals(listOf("R1", "Y1", "G2", "B3", "W5"), b.partnerHand.map(::name))
        assertEquals(List(5) { HanabiCard() }, a.yourHand)
        assertEquals(List(5) { HanabiCard() }, b.yourHand)
        assertEquals(listOf(0, 0, 0, 0, 0), a.fireworks)
        assertEquals(8, a.hints)
        assertEquals(3, a.lives)
        assertEquals(40, a.deckSize)
        assertEquals(-1, a.turnsLeft)
        assertTrue(a.yourTurn)
        assertFalse(b.yourTurn)
        assertEquals("playing", a.status)
        assertEquals(0, a.score)
        assertEquals(ann, b.firstPlayer)
        assertEquals(bob, a.partner)
        assertEquals("Hanabi: Ann to play, score 0, 8 hints, 3 lives, 40 cards left", g.describe())
    }

    @Test
    fun theDeckHasFiftyCardsAndIsShuffled() {
        assertEquals(50, Hanabi.FULL_DECK.size)
        for (color in 0..4) {
            assertEquals(listOf(3, 2, 2, 2, 1), (1..5).map { n -> Hanabi.FULL_DECK.count { it == Hanabi.cardOf(color, n) } })
        }
        val hands = (1L..20L).map { seed -> Hanabi(ann, bob, random = Random(seed)).toState(ann).partnerHand.map(::name) }
        assertTrue(hands.toSet().size > 15, "different games should deal different cards")
        val room = Hanabi(ann, bob)
        assertEquals(40, room.deckSize)
        assertHidden(room)
        // A deck that is not exactly the 50 cards is refused.
        assertFailsWith<IllegalArgumentException> { Hanabi(ann, bob, Hanabi.FULL_DECK.drop(1)) }
        assertFailsWith<IllegalArgumentException> { Hanabi(ann, bob, Hanabi.FULL_DECK.drop(1) + c("R5")) }
    }

    @Test
    fun aHintPointsAtEveryCardOfThatNumberAndRulesItOutForTheOthers() {
        val g = Hanabi(ann, bob, plain)
        assertNull(g.hint(ann, null, 1))
        val a = g.toState(ann)
        val b = g.toState(bob)
        assertEquals(listOf(1, 1, 0, 1, 0), b.yourHand.map { it.knownNumber })
        assertEquals(listOf(emptyList(), emptyList(), listOf(1), emptyList(), listOf(1)), b.yourHand.map { it.notNumbers })
        assertTrue(b.yourHand.all { it.color == -1 && it.number == 0 && it.knownColor == -1 })
        // Ann sees what Bob now knows, next to the real cards.
        assertEquals(knowledge(b.yourHand), knowledge(a.partnerHand))
        assertEquals(7, a.hints)
        assertEquals(listOf(0, 1, 3), b.hintedYours)
        assertEquals(listOf(0, 1, 3), a.hintedPartners)
        assertEquals(emptyList(), a.hintedYours)
        assertEquals("You hinted Bob's 1s", a.lastAction)
        assertEquals("Ann hinted your 1s", b.lastAction)
        assertTrue(b.yourTurn)
        assertEquals(40, b.deckSize) // a hint draws nothing

        assertNull(g.hint(bob, 0, null))
        val a2 = g.toState(ann)
        assertEquals(listOf(0, -1, -1, -1, -1), a2.yourHand.map { it.knownColor })
        assertEquals(listOf(emptyList(), listOf(0), listOf(0), listOf(0), listOf(0)), a2.yourHand.map { it.notColors })
        assertEquals("Bob hinted your red cards", a2.lastAction)
        assertEquals("You hinted Ann's red cards", g.toState(bob).lastAction)
        assertEquals(6, a2.hints)
        assertHidden(g)
    }

    @Test
    fun ruledOutColoursAddUpAndTokensRunOut() {
        val g = Hanabi(ann, bob, plain)
        // Bob names red, yellow, green and blue in Ann's hand; Ann answers with hints too. That spends all 8 tokens.
        for ((i, color) in listOf(0, 1, 2, 3).withIndex()) {
            assertNull(g.hint(ann, null, listOf(1, 2, 4, 1)[i]))
            assertNull(g.hint(bob, color, null))
        }
        val a = g.toState(ann)
        // Her white 5 has been ruled out of every other colour: she knows it is white.
        assertEquals(4, a.yourHand[4].knownColor)
        assertEquals(listOf(0, 1, 2, 3), a.yourHand[4].notColors)
        assertEquals(listOf(0, 1, 2, 3, 4), a.yourHand.map { it.knownColor })
        assertEquals(0, a.hints)
        assertEquals("No hint tokens left", g.hint(ann, 0, null))
        assertEquals(0, g.hints)
        assertHidden(g)
    }

    @Test
    fun aHintMustPointAtSomethingAndNameOneThing() {
        val g = Hanabi(ann, bob, plain)
        assertEquals("That hint points at none of Bob's cards", g.hint(ann, 4, null)) // Bob has no white card
        assertEquals("That hint points at none of Bob's cards", g.hint(ann, null, 5))
        assertEquals("Hint one colour or one number", g.hint(ann, 0, 1))
        assertEquals("Hint one colour or one number", g.hint(ann, null, null))
        assertEquals("Invalid colour", g.hint(ann, 5, null))
        assertEquals("Invalid colour", g.hint(ann, -1, null))
        assertEquals("Invalid number", g.hint(ann, null, 0))
        assertEquals("Invalid number", g.hint(ann, null, 6))
        assertEquals(8, g.hints)
        assertTrue(g.toState(ann).yourTurn)
    }

    @Test
    fun discardingNeedsASpentTokenGivesItBackAndDraws() {
        val g = Hanabi(ann, bob, plain)
        assertEquals("All 8 hint tokens are there: give a hint or play instead", g.discard(ann, 0))
        assertNull(g.hint(ann, null, 1))
        assertEquals(7, g.hints)
        assertNull(g.discard(bob, 4)) // the green 4
        val a = g.toState(ann)
        val b = g.toState(bob)
        assertEquals(8, a.hints)
        assertEquals(listOf("G4"), a.discards.map(::name))
        assertEquals(listOf("R1", "B1", "R2", "Y1", "R3"), a.partnerHand.map(::name))
        assertEquals(39, a.deckSize)
        assertEquals("Bob discarded a green 4", a.lastAction)
        assertEquals("You discarded a green 4", b.lastAction)
        assertEquals(emptyList(), b.hintedYours) // only a hint marks cards
        assertEquals(listOf(1, 1, 0, 1, 0), b.yourHand.map { it.knownNumber })
        assertHidden(g)
    }

    @Test
    fun whatAPlayerKnowsFollowsTheCardsWhenTheHandShifts() {
        val g = Hanabi(ann, bob, plain)
        assertNull(g.hint(ann, null, 1)) // Bob's R1, B1 and Y1, at 0, 1 and 3
        assertNull(g.play(bob, 0)) // the red 1 goes up; Bob draws the red 3
        val b = g.toState(bob)
        assertEquals(listOf(1, 0, 1, 0, 0), b.yourHand.map { it.knownNumber })
        assertEquals(listOf(emptyList(), listOf(1), emptyList(), listOf(1), emptyList()), b.yourHand.map { it.notNumbers })
        assertEquals(listOf("B1", "R2", "Y1", "G4", "R3"), g.toState(ann).partnerHand.map(::name))
        assertEquals(listOf(1, 0, 0, 0, 0), b.fireworks)
        assertEquals("Bob played a red 1", g.toState(ann).lastAction)
        assertEquals("You played a red 1", b.lastAction)
        assertHidden(g)
    }

    @Test
    fun playingTheWrongCardLosesALifeAndDiscardsIt() {
        val g = Hanabi(ann, bob, plain)
        assertNull(g.play(ann, 0)) // red 1: fine
        assertNull(g.play(bob, 2)) // red 2: next on red, fine
        assertNull(g.play(ann, 3)) // white 5 (her hand is now Y1 G2 B3 W5 R3): white has nothing yet
        val a = g.toState(ann)
        assertEquals(listOf(2, 0, 0, 0, 0), a.fireworks)
        assertEquals(2, a.score)
        assertEquals(2, a.lives)
        assertEquals(listOf("W5"), a.discards.map(::name))
        assertEquals("You misplayed a white 5: 2 lives left", a.lastAction)
        assertEquals("Ann misplayed a white 5: 2 lives left", g.toState(bob).lastAction)
        assertEquals(8, a.hints) // a misplay gives no token
        assertEquals(37, a.deckSize)
        assertEquals(5, a.yourHand.size)
        // A second red 1 is a misplay too: red is past it.
        assertNull(g.play(bob, 0)) // R1, after the R2 left: his hand was R1 B1 Y1 G4 W1
        assertEquals("Bob misplayed a red 1: 1 life left", g.toState(ann).lastAction)
        assertHidden(g)
    }

    @Test
    fun theThirdMisplayLosesTheGameForBoth() {
        val g = Hanabi(ann, bob, deck("R2 R3 R4 Y2 Y3", "R1 B1 G1 W1 Y1"))
        assertNull(g.play(ann, 0))
        assertNull(g.hint(bob, 0, null))
        assertNull(g.play(ann, 0))
        assertNull(g.hint(bob, 0, null))
        assertNull(g.play(ann, 0))
        for (player in listOf(ann, bob)) {
            val s = g.toState(player)
            assertEquals("lost", s.status)
            assertEquals(0, s.lives)
            assertFalse(s.yourTurn)
            assertEquals("The third misplay ended the show: you both lost, with 0 of 25 on the fireworks", s.result)
        }
        assertEquals("You misplayed a red 4: no lives left", g.toState(ann).lastAction)
        assertEquals("The game is over", g.hint(bob, 0, null))
        assertEquals("The game is over", g.play(bob, 0))
        assertEquals("The game is over", g.discard(ann, 0))
        assertTrue(g.finished)
    }

    @Test
    fun allFiveFireworksAtFiveIsAPerfectWinAndAFiveGivesATokenBack() {
        // Ann plays red, then green, then white; Bob plays yellow, then blue, and then gives hints. Each draws as they
        // play: Ann the deck's even cards (G1..G5, then W1..W5), Bob the odd ones (B1..B5, then anything).
        val g = Hanabi(
            ann,
            bob,
            deck("R1 R2 R3 R4 R5", "Y1 Y2 Y3 Y4 Y5", "G1 B1 G2 B2 G3 B3 G4 B4 G5 B5 W1 R1 W2 R1 W3 Y1 W4 Y1 W5 G1"),
        )
        repeat(10) {
            assertNull(g.play(ann, 0))
            assertNull(g.play(bob, 0))
        }
        assertEquals(20, g.score)
        assertEquals(8, g.hints) // the 5s came back with all 8 tokens there: nothing to give back
        assertEquals("You played a blue 5", g.toState(bob).lastAction)
        repeat(4) {
            assertNull(g.play(ann, 0))
            assertNull(g.hint(bob, 4, null))
        }
        assertEquals(4, g.hints)
        assertNull(g.play(ann, 0)) // the white 5
        for (player in listOf(ann, bob)) {
            val s = g.toState(player)
            assertEquals("won", s.status)
            assertEquals(25, s.score)
            assertEquals(listOf(5, 5, 5, 5, 5), s.fireworks)
            assertEquals(5, s.hints)
            assertEquals("A perfect show: all five fireworks complete, 25 out of 25", s.result)
        }
        assertEquals("You played a white 5, and a hint token came back", g.toState(ann).lastAction)
        assertEquals("The game is over", g.hint(bob, 4, null))
        assertEquals("Hanabi: A perfect show: all five fireworks complete, 25 out of 25", g.describe())
    }

    @Test
    fun whenTheDeckRunsOutEachPlayerGetsExactlyOneMoreTurn() {
        val g = Hanabi(ann, bob, plain)
        assertNull(g.play(ann, 0)) // the red 1, so the score is 1
        // Then everyone discards their first card whenever they may, and hints otherwise, until the deck is empty.
        var drawer = ""
        while (g.deckSize > 0) {
            val player = g.turn
            val s = g.toState(player)
            if (s.hints < 8) assertNull(g.discard(player, 0)) else assertNull(g.hint(player, s.partnerHand[0].color, null))
            drawer = player
            assertHidden(g)
        }
        val partner = if (drawer == ann) bob else ann
        var d = g.toState(drawer)
        var p = g.toState(partner)
        assertEquals(2, d.turnsLeft)
        assertEquals(2, p.turnsLeft)
        assertTrue(p.yourTurn)
        assertEquals(5, d.yourHand.size)

        // The partner first: no card to draw any more.
        assertNull(g.play(partner, 0))
        d = g.toState(drawer)
        p = g.toState(partner)
        assertEquals("playing", d.status)
        assertEquals(1, d.turnsLeft)
        assertEquals(4, p.yourHand.size)
        assertTrue(d.yourTurn)
        assertEquals("Not your turn", g.discard(partner, 0))

        // Then the one who drew the last card, and that is the end.
        if (d.hints < 8) assertNull(g.discard(drawer, 0)) else assertNull(g.hint(drawer, d.partnerHand[0].color, null))
        for (player in listOf(ann, bob)) {
            val s = g.toState(player)
            assertEquals("finished", s.status)
            assertEquals(0, s.turnsLeft)
            assertFalse(s.yourTurn)
            assertEquals(s.fireworks.sum(), s.score)
            assertEquals("The deck ran out: ${s.score} out of 25", s.result)
        }
        assertTrue(g.score >= 1)
        assertEquals("The game is over", g.play(partner, 0))
        assertEquals("The game is over", g.hint(drawer, 0, null))
    }

    @Test
    fun ownCardsStayHiddenAndKnowledgeStaysTrueThroughWholeRandomGames() {
        val endings = HashSet<String>()
        for (seed in 1L..150L) {
            val random = Random(seed)
            val g = Hanabi(ann, bob, random = random)
            var actions = 0
            while (!g.finished) {
                randomAction(g, random)
                assertHidden(g)
                assertTrue(++actions < 200, "the game never ended")
            }
            val s = g.toState(bob)
            endings += s.status
            assertEquals(s.fireworks.sum(), s.score)
            assertTrue(s.status == "lost" || s.status == "won" || (s.status == "finished" && s.deckSize == 0 && s.turnsLeft == 0))
        }
        assertTrue("finished" in endings && "lost" in endings, "random games should end both ways: $endings")
    }

    @Test
    fun outOfTurnOutsidersAndBadMovesAreRefused() {
        val g = Hanabi(ann, bob, plain)
        assertEquals("Not your turn", g.hint(bob, 0, null))
        assertEquals("Not your turn", g.play(bob, 0))
        assertEquals("You are not in this game", g.play("Eve", 0))
        assertEquals("Invalid card", g.play(ann, 5))
        assertEquals("Invalid card", g.play(ann, -1))
        assertNull(g.hint(ann, 0, null))
        assertEquals("Invalid card", g.discard(bob, 7))

        fun move(action: String?, card: Int? = null, color: Int? = null, number: Int? = null) =
            g.handle(bob, ClientMessage(type = "move", action = action, card = card, color = color, number = number))
        assertEquals("Send an action: hint, discard or play", move(null, card = 0))
        assertEquals("Send an action: hint, discard or play", move("pass"))
        assertEquals("Send the card to play", move("play"))
        assertEquals("Send the card to discard", move("discard"))
        assertEquals("Only hints name a colour or a number", move("play", card = 0, color = 0))
        assertEquals("A hint names a colour or a number, not a card", move("hint", card = 0, color = 0))
        assertEquals("Hanabi only understands moves", g.handle(bob, ClientMessage(type = "swap")))
        assertEquals(7, g.hints)
        assertTrue(g.toState(bob).yourTurn)
        assertNull(move("hint", number = 2))
        assertEquals("Bob hinted your 2s", g.toState(ann).lastAction)
    }

    @Test
    fun swappedPlayersSwapWhoStarts() {
        // Hub passes the players in the other order for the next game in a room.
        val rematch = Hanabi(bob, ann, plain)
        assertTrue(rematch.toState(bob).yourTurn)
        assertEquals("Not your turn", rematch.hint(ann, 0, null))
        assertEquals(listOf("R1", "Y1", "G2", "B3", "W5"), rematch.toState(ann).partnerHand.map(::name))
    }
}
