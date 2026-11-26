package com.classicgames.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MastermindTest {
    private val ann = "Ann" // guesses first
    private val bob = "Bob"

    private val annCode = listOf(0, 0, 1, 1) // what Bob has to crack
    private val bobCode = listOf(5, 4, 3, 2) // what Ann has to crack
    private val wrong = listOf(1, 2, 3, 4) // cracks neither code

    /** A game where both codes are already set and Ann is to guess. */
    private fun started(maxGuesses: Int = Mastermind.MAX_GUESSES) =
        Mastermind(ann, bob, maxGuesses = maxGuesses).also {
            assertNull(it.setSecret(ann, annCode))
            assertNull(it.setSecret(bob, bobCode))
        }

    private fun Mastermind.view(player: String) = messageFor(player).mastermind!!

    @Test
    fun scoresBlackForTheRightPlaceAndWhiteForTheRightColourElsewhere() {
        assertEquals(4 to 0, Mastermind.score(listOf(0, 1, 2, 3), listOf(0, 1, 2, 3)))
        assertEquals(0 to 0, Mastermind.score(listOf(0, 1, 2, 3), listOf(4, 4, 5, 5)))
        assertEquals(0 to 4, Mastermind.score(listOf(0, 1, 2, 3), listOf(3, 2, 1, 0)))
        assertEquals(2 to 2, Mastermind.score(listOf(0, 1, 2, 3), listOf(0, 1, 3, 2)))
        assertEquals(1 to 1, Mastermind.score(listOf(0, 1, 2, 3), listOf(0, 2, 4, 5)))
    }

    @Test
    fun aRepeatedColourOnlyScoresAsOftenAsItIsInTheOtherCode() {
        // Four of a colour the secret has once: one black, and the other three score nothing.
        assertEquals(1 to 0, Mastermind.score(listOf(0, 1, 2, 3), listOf(0, 0, 0, 0)))
        // Same, but the secret's one is somewhere else: a single white.
        assertEquals(0 to 1, Mastermind.score(listOf(1, 0, 2, 3), listOf(0, 4, 0, 0)))
        // Pairs swapped: every peg is the right colour in the wrong place.
        assertEquals(0 to 4, Mastermind.score(listOf(0, 0, 1, 1), listOf(1, 1, 0, 0)))
        // Secret 0 0 1 2, guess 0 3 0 0: the first 0 is black, one more 0 is white, the third 0 is unmatched.
        assertEquals(1 to 1, Mastermind.score(listOf(0, 0, 1, 2), listOf(0, 3, 0, 0)))
        // A black peg uses up its colour: secret 1 1 1 2, guess 1 2 2 2 is two blacks and no white.
        assertEquals(2 to 0, Mastermind.score(listOf(1, 1, 1, 2), listOf(1, 2, 2, 2)))
        // Secret 2 2 3 3, guess 3 2 2 2: one black 2, the other 2 and the 3 are white, the last 2 is unmatched.
        assertEquals(1 to 2, Mastermind.score(listOf(2, 2, 3, 3), listOf(3, 2, 2, 2)))
        // The repeat is in the secret this time.
        assertEquals(1 to 1, Mastermind.score(listOf(4, 4, 4, 5), listOf(4, 5, 0, 1)))
    }

    @Test
    fun scoringMatchesTheTextbookTwoPassMethodForEveryPairOfCodes() {
        // Exact matches first, then each remaining guess peg takes the first unused secret peg of its colour.
        fun twoPass(secret: List<Int>, guess: List<Int>): Pair<Int, Int> {
            val usedSecret = BooleanArray(secret.size)
            val usedGuess = BooleanArray(guess.size)
            var black = 0
            for (i in secret.indices) if (secret[i] == guess[i]) { black++; usedSecret[i] = true; usedGuess[i] = true }
            var white = 0
            for (i in guess.indices) {
                if (usedGuess[i]) continue
                val j = secret.indices.firstOrNull { !usedSecret[it] && secret[it] == guess[i] } ?: continue
                usedSecret[j] = true
                white++
            }
            return black to white
        }

        // Every code of 4 pegs from 4 colours: all the ways colours can repeat, on both sides.
        val codes = (0 until 256).map { n -> List(4) { n shr (2 * it) and 3 } }
        for (secret in codes) for (guess in codes) {
            assertEquals(twoPass(secret, guess), Mastermind.score(secret, guess), "secret $secret, guess $guess")
        }
    }

    @Test
    fun bothPlayersSetACodeAndThenTheFirstPlayerGuesses() {
        val g = Mastermind(ann, bob)
        var a = g.view(ann)
        assertEquals("secret", a.phase)
        assertEquals(4, a.pegs)
        assertEquals(6, a.colors)
        assertEquals(10, a.maxGuesses)
        assertEquals(ann, a.firstPlayer)
        assertFalse(a.yourTurn)
        assertEquals("Set your secret code", a.event)

        assertNull(g.setSecret(bob, bobCode))
        a = g.view(ann)
        val b = g.view(bob)
        assertEquals("secret", a.phase)
        assertTrue(a.opponentReady)
        assertFalse(a.youReady)
        assertEquals("Bob has set their code. Set yours!", a.event)
        assertEquals(bobCode, b.yourSecret)
        assertEquals("Waiting for Ann to set their code", b.event)

        assertNull(g.setSecret(ann, annCode))
        a = g.view(ann)
        assertEquals("guessing", a.phase)
        assertEquals(annCode, a.yourSecret)
        assertTrue(a.yourTurn)
        assertFalse(g.view(bob).yourTurn)
        assertEquals("Both codes are set. You guess first!", a.event)
        assertEquals("Both codes are set. Ann guesses first", g.view(bob).event)
    }

    @Test
    fun rejectsBadCodesAndSettingACodeTwice() {
        val g = Mastermind(ann, bob)
        val bad = "A code is 4 pegs, each one of the 6 colours"
        assertEquals(bad, g.setSecret(ann, null))
        assertEquals(bad, g.setSecret(ann, listOf(0, 1, 2)))
        assertEquals(bad, g.setSecret(ann, listOf(0, 1, 2, 3, 4)))
        assertEquals(bad, g.setSecret(ann, listOf(0, 1, 2, 6)))
        assertEquals(bad, g.setSecret(ann, listOf(-1, 1, 2, 3)))
        assertEquals("You are not in this game", g.setSecret("Eve", annCode))
        assertFalse(g.view(ann).youReady)

        assertNull(g.setSecret(ann, annCode))
        assertEquals("You already set your code", g.setSecret(ann, bobCode))
        assertEquals(annCode, g.view(ann).yourSecret)

        assertNull(g.setSecret(bob, bobCode))
        assertEquals("The codes are already set", g.setSecret(bob, annCode))
        assertEquals(bad, g.guess(ann, listOf(0, 1)))
        assertEquals(bad, g.guess(ann, null))
        assertTrue(g.view(ann).yourGuesses.isEmpty())
    }

    @Test
    fun guessesWaitForBothCodesAndFollowTheTurns() {
        val g = Mastermind(ann, bob)
        assertEquals("Set your code first", g.guess(ann, wrong))
        assertNull(g.setSecret(ann, annCode))
        assertEquals("Wait for Bob to set their code", g.guess(ann, wrong))
        assertNull(g.setSecret(bob, bobCode))

        assertEquals("Not your turn", g.guess(bob, wrong))
        assertNull(g.guess(ann, wrong))
        assertEquals("Not your turn", g.guess(ann, wrong))
        assertEquals("You are not in this game", g.guess("Eve", wrong))
        assertNull(g.guess(bob, wrong))
        assertNull(g.guess(ann, wrong))
        assertEquals(2, g.view(ann).yourGuesses.size)
        assertEquals(1, g.view(bob).yourGuesses.size)
    }

    @Test
    fun handleUnderstandsSecretAndMove() {
        val g = Mastermind(ann, bob)
        assertNull(g.handle(ann, ClientMessage(type = "secret", code = annCode)))
        assertNull(g.handle(bob, ClientMessage(type = "secret", code = bobCode)))
        assertNull(g.handle(ann, ClientMessage(type = "move", code = wrong)))
        assertEquals("Mastermind understands 'secret' and 'move'", g.handle(bob, ClientMessage(type = "place")))
        assertEquals("You are not in this game", g.handle("Eve", ClientMessage(type = "move", code = wrong)))
        assertEquals(1, g.view(ann).yourGuesses.size)
    }

    @Test
    fun eachPlayerSeesTheirGuessesAndTheOpponentsGuessesWithTheirScores() {
        val g = started()
        assertNull(g.guess(ann, listOf(5, 3, 0, 0))) // against 5 4 3 2: one black, one white
        assertNull(g.guess(bob, listOf(1, 1, 0, 0))) // against 0 0 1 1: four whites

        val a = g.view(ann)
        val b = g.view(bob)
        assertEquals(listOf(MastermindGuess(listOf(5, 3, 0, 0), 1, 1)), a.yourGuesses)
        assertEquals(listOf(MastermindGuess(listOf(1, 1, 0, 0), 0, 4)), a.opponentGuesses)
        assertEquals(a.yourGuesses, b.opponentGuesses)
        assertEquals(a.opponentGuesses, b.yourGuesses)
        assertEquals("Bob scored 0 black, 4 white. Your turn", a.event)
        assertEquals("You scored 0 black, 4 white", b.event)
    }

    @Test
    fun theOpponentsCodeIsNeverSentBeforeTheEnd() {
        fun assertHidden(g: Mastermind, players: List<String> = listOf(ann, bob)) {
            for (player in players) {
                val hidden = if (player == ann) bobCode else annCode
                val message = g.messageFor(player)
                assertTrue(message.mastermind!!.opponentSecret.isEmpty())
                val json = protocolJson.encodeToString(ServerMessage.serializer(), message)
                assertFalse(hidden.joinToString(",", "[", "]") in json, "$player's view leaks the opponent's code: $json")
            }
        }

        val g = Mastermind(ann, bob)
        assertNull(g.setSecret(bob, bobCode))
        assertHidden(g)
        assertNull(g.setSecret(ann, annCode))
        assertHidden(g)
        assertNull(g.guess(ann, listOf(5, 4, 3, 0))) // three blacks, close but not cracked
        assertNull(g.guess(bob, listOf(0, 0, 1, 0)))
        assertHidden(g)
        // Ann cracked it (so her own guess shows Bob's code), but Bob's guess in this round is still to come.
        assertNull(g.guess(ann, bobCode))
        assertTrue(g.view(ann).opponentSecret.isEmpty())
        assertHidden(g, listOf(bob))

        // Once it is over, both codes are shown to both.
        assertNull(g.guess(bob, wrong))
        assertEquals(bobCode, g.view(ann).opponentSecret)
        assertEquals(annCode, g.view(bob).opponentSecret)
    }

    @Test
    fun whenTheFirstPlayerCracksTheCodeTheSecondStillGetsTheirGuess() {
        val g = started()
        assertNull(g.guess(ann, bobCode))
        assertFalse(g.finished)
        assertTrue(g.view(bob).yourTurn)
        assertEquals(MastermindGuess(bobCode, 4, 0), g.view(ann).yourGuesses.last())
        assertEquals("You cracked Bob's code! Bob has one guess left to draw", g.view(ann).event)
        assertEquals("Ann cracked your code! Crack theirs with this guess to draw", g.view(bob).event)
        assertTrue(g.view(bob).opponentSecret.isEmpty())

        // Bob misses: Ann wins.
        assertNull(g.guess(bob, wrong))
        assertTrue(g.finished)
        assertEquals(ann, g.winner)
        val a = g.view(ann)
        assertEquals("finished", a.phase)
        assertEquals(ann, a.winner)
        assertFalse(a.yourTurn)
        assertEquals("You cracked Bob's code in 1 guess!", a.event)
        assertEquals("Ann cracked your code in 1 guess", g.view(bob).event)
    }

    @Test
    fun bothCrackingTheCodeInTheSameRoundIsADraw() {
        val g = started()
        assertNull(g.guess(ann, wrong))
        assertNull(g.guess(bob, wrong))
        assertNull(g.guess(ann, bobCode))
        assertNull(g.guess(bob, annCode))
        assertTrue(g.finished)
        assertNull(g.winner)
        val a = g.view(ann)
        assertEquals("finished", a.phase)
        assertNull(a.winner)
        assertEquals("Both codes cracked in 2 guesses: it's a draw", a.event)
        assertEquals(bobCode, a.opponentSecret)
    }

    @Test
    fun theSecondPlayerCrackingTheCodeWinsAtOnce() {
        val g = started()
        assertNull(g.guess(ann, wrong))
        assertNull(g.guess(bob, annCode))
        assertTrue(g.finished)
        assertEquals(bob, g.winner)
        assertEquals("You cracked Ann's code in 1 guess!", g.view(bob).event)
        // Ann does not get another guess: the round is over and she had as many as Bob.
        assertEquals("The game is over", g.guess(ann, bobCode))
        assertEquals(1, g.view(ann).yourGuesses.size)
    }

    @Test
    fun nobodyCrackingACodeInTheLastRoundIsADraw() {
        val g = started()
        repeat(Mastermind.MAX_GUESSES) {
            assertNull(g.guess(ann, wrong))
            if (it < Mastermind.MAX_GUESSES - 1) assertFalse(g.finished)
            assertNull(g.guess(bob, wrong))
        }
        assertTrue(g.finished)
        assertNull(g.winner)
        assertEquals("Nobody cracked a code in 10 guesses: it's a draw", g.view(ann).event)
        assertEquals(10, g.view(ann).yourGuesses.size)
        assertEquals(10, g.view(bob).yourGuesses.size)
        assertEquals(annCode, g.view(bob).opponentSecret)
    }

    @Test
    fun crackingTheCodeWithTheLastGuessStillCounts() {
        val g = started(maxGuesses = 2)
        assertNull(g.guess(ann, wrong))
        assertNull(g.guess(bob, wrong))
        assertNull(g.guess(ann, wrong))
        assertNull(g.guess(bob, annCode))
        assertEquals(bob, g.winner)

        val first = started(maxGuesses = 1)
        assertNull(first.guess(ann, bobCode))
        assertFalse(first.finished) // Bob's last guess is still to come
        assertNull(first.guess(bob, wrong))
        assertEquals(ann, first.winner)
    }

    @Test
    fun nothingCanBePlayedOnceTheGameIsOver() {
        val g = started()
        assertNull(g.guess(ann, wrong))
        assertNull(g.guess(bob, annCode))
        assertEquals("The game is over", g.guess(ann, wrong))
        assertEquals("The game is over", g.guess(bob, wrong))
        assertEquals("The codes are already set", g.setSecret(ann, annCode))
        assertEquals(1, g.view(bob).yourGuesses.size)
    }

    @Test
    fun smallerGamesUseTheirOwnPegsAndColours() {
        val g = Mastermind(ann, bob, pegs = 2, colors = 3, maxGuesses = 1)
        assertEquals("A code is 2 pegs, each one of the 3 colours", g.setSecret(ann, listOf(0, 3)))
        assertNull(g.setSecret(ann, listOf(2, 2)))
        assertNull(g.setSecret(bob, listOf(0, 1)))
        assertNull(g.guess(ann, listOf(1, 0)))
        assertEquals(MastermindGuess(listOf(1, 0), 0, 2), g.view(ann).yourGuesses.single())
        assertNull(g.guess(bob, listOf(2, 0)))
        assertEquals(MastermindGuess(listOf(2, 0), 1, 0), g.view(bob).yourGuesses.single())
        assertTrue(g.finished)
        assertNull(g.winner)
    }
}
