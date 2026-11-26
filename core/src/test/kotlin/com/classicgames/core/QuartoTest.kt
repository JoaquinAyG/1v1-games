package com.classicgames.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cells are numbered row by row (cell = row * 4 + column) and pieces 0..15 with one bit per trait: 1 tall, 2 dark,
 * 4 square, 8 hollow. Ann is the first player and opens by choosing a piece for Bob. Positions are set up with a board
 * of 16 entries (-1 empty) and the piece in hand: with an odd number of pieces placed Ann places next, with an even
 * number Bob does.
 */
class QuartoTest {
    private val ann = "Ann"
    private val bob = "Bob"

    private val empty = List(16) { -1 }

    /** [empty] with [pieces] put on the cells they are paired with. */
    private fun board(vararg pieces: Pair<Int, Int>) = empty.toMutableList().apply { for ((cell, p) in pieces) this[cell] = p }

    /**
     * A full board where no line shares a trait (found by a search and checked by the tests below). With the piece on
     * cell 15 still in hand, the last move fills the board without a win.
     */
    private val drawn = listOf(12, 8, 2, 7, 5, 0, 10, 4, 11, 14, 9, 3, 1, 13, 6, 15)

    @Test
    fun startsWithTheFirstPlayerChoosingFromTheWholePool() {
        val s = Quarto(ann, bob).toState()
        assertEquals(empty, s.board)
        assertEquals((0 until 16).toList(), s.pool)
        assertEquals(-1, s.piece)
        assertEquals("1", s.turn)
        assertEquals("choose", s.phase)
        assertEquals("playing", s.status)
        assertEquals(ann, s.firstPlayer)
        assertEquals(bob, s.secondPlayer)
        assertEquals(-1, s.lastCell)
        assertTrue(s.line.isEmpty())
        assertTrue(s.sharedTraits.isEmpty())
        assertEquals("Quarto: Ann chooses a piece for Bob", Quarto(ann, bob).describe())
    }

    @Test
    fun aTurnIsPlaceTheGivenPieceThenChooseOneForTheOpponent() {
        val g = Quarto(ann, bob)
        assertNull(g.give(ann, 5))
        var s = g.toState()
        assertEquals(5, s.piece)
        assertEquals((0 until 16).filter { it != 5 }, s.pool)
        assertEquals("2", s.turn)
        assertEquals("place", s.phase)
        assertEquals("Quarto: Bob places piece 5", g.describe())

        assertNull(g.place(bob, 6))
        s = g.toState()
        assertEquals(5, s.board[6])
        assertEquals(-1, s.piece)
        assertEquals(6, s.lastCell)
        assertEquals("2", s.turn) // Bob placed, so Bob chooses
        assertEquals("choose", s.phase)
        assertEquals(15, s.pool.size)

        assertNull(g.give(bob, 10))
        s = g.toState()
        assertEquals("1", s.turn)
        assertEquals("place", s.phase)
        assertEquals(10, s.piece)
        assertEquals(14, s.pool.size)

        // The same moves through handle(), as the room sends them.
        assertNull(g.handle(ann, ClientMessage(type = "move", cell = 0)))
        assertNull(g.handle(ann, ClientMessage(type = "move", piece = 0)))
        s = g.toState()
        assertEquals(10, s.board[0])
        assertEquals(0, s.piece)
        assertEquals("2", s.turn)
    }

    @Test
    fun rejectsMovesOutOfTurnOrOutOfPhase() {
        val g = Quarto(ann, bob)
        assertEquals("Not your turn", g.give(bob, 0))
        assertEquals("Not your turn", g.place(bob, 0))
        assertEquals("You are not in this game", g.give("Eve", 0))
        assertEquals("Choose a piece for Bob first", g.place(ann, 0))
        assertEquals("Choose a piece for Bob first", g.handle(ann, ClientMessage(type = "move", cell = 0)))

        assertNull(g.give(ann, 3))
        assertEquals("Not your turn", g.give(ann, 4))
        assertEquals("Not your turn", g.place(ann, 0))
        assertEquals("Place the piece you were given first", g.give(bob, 4))
        assertEquals("Place the piece you were given first", g.handle(bob, ClientMessage(type = "move", piece = 4)))

        assertNull(g.place(bob, 0))
        assertEquals("Not your turn", g.place(ann, 1))
        assertEquals("Choose a piece for Ann first", g.place(bob, 1))

        // Nothing changed through all of that.
        val s = g.toState()
        assertEquals(board(0 to 3), s.board)
        assertEquals("2", s.turn)
        assertEquals("choose", s.phase)
    }

    @Test
    fun rejectsBadCellsBadPiecesAndBadMessages() {
        val g = Quarto(ann, bob)
        assertEquals("Invalid piece", g.give(ann, -1))
        assertEquals("Invalid piece", g.give(ann, 16))
        assertEquals("Quarto only understands moves", g.handle(ann, ClientMessage(type = "swap")))
        assertEquals("Send a cell to place your piece, or a piece to give", g.handle(ann, ClientMessage(type = "move")))
        assertEquals(
            "Send a cell to place your piece, or a piece to give",
            g.handle(ann, ClientMessage(type = "move", cell = 0, piece = 1)),
        )

        assertNull(g.give(ann, 7))
        assertEquals("Invalid cell", g.place(bob, -1))
        assertEquals("Invalid cell", g.place(bob, 16))
        assertNull(g.place(bob, 9))
        assertEquals("That piece is not in the pool", g.give(bob, 7)) // already on the board
        assertNull(g.give(bob, 8))
        assertEquals("That cell is taken", g.place(ann, 9))
        assertNull(g.place(ann, 10))
        assertEquals("That piece is not in the pool", g.give(ann, 8))
    }

    @Test
    fun everyLineWins() {
        // Three tall pieces on the line and a fourth one in hand; the other cells stay empty.
        val tall = listOf(1, 3, 5, 15)
        for (line in Quarto.LINES) {
            val g = Quarto(ann, bob, board(*line.dropLast(1).zip(tall.dropLast(1)).toTypedArray()), piece = tall.last())
            assertEquals("1", g.toState().turn)
            assertNull(g.place(ann, line.last()), "line $line")
            val s = g.toState()
            assertEquals("won", s.status, "line $line")
            assertEquals("finished", s.phase)
            assertEquals(ann, s.winner)
            assertEquals(line, s.line)
            assertEquals(listOf("tall"), s.sharedTraits)
            assertEquals(-1, s.piece)
        }
    }

    @Test
    fun everyTraitWinsBothWays() {
        // For one trait, four pieces that agree on it and on nothing else: the other three bits run through
        // 000, 011, 101 and 110, so each of them is set on exactly two pieces.
        val others = listOf(0b000, 0b011, 0b101, 0b110)
        for (bit in 0 until 4) for (set in listOf(true, false)) {
            val pieces = others.map { rest ->
                val low = rest and ((1 shl bit) - 1)
                val high = (rest shr bit) shl (bit + 1)
                low or high or (if (set) 1 shl bit else 0)
            }
            val g = Quarto(ann, bob, board(0 to pieces[0], 1 to pieces[1], 2 to pieces[2]), piece = pieces[3])
            assertNull(g.place(ann, 3))
            val s = g.toState()
            val word = Quarto.TRAITS[bit].let { if (set) it.first else it.second }
            assertEquals("won", s.status, "pieces $pieces")
            assertEquals(listOf(word), s.sharedTraits, "pieces $pieces")
            assertEquals(listOf(0, 1, 2, 3), s.line)
        }
    }

    @Test
    fun aWinNamesEveryTraitTheLineShares() {
        // 1, 3, 5 and 7 are all tall and all solid, and differ in colour and shape.
        val g = Quarto(ann, bob, board(0 to 1, 5 to 3, 10 to 5), piece = 7)
        assertNull(g.place(ann, 15))
        val s = g.toState()
        assertEquals(listOf(0, 5, 10, 15), s.line)
        assertEquals(listOf("tall", "solid"), s.sharedTraits)
        assertEquals("Quarto: Ann won with four tall, solid pieces", g.describe())
    }

    @Test
    fun aFullLineThatSharesNothingDoesNotWin() {
        // 0 and 15 alone already differ in every trait.
        val g = Quarto(ann, bob, board(0 to 0, 1 to 15, 2 to 3), piece = 12)
        assertNull(g.place(ann, 3))
        val s = g.toState()
        assertEquals("playing", s.status)
        assertEquals("choose", s.phase)
        assertEquals("1", s.turn)
        assertTrue(s.line.isEmpty())
    }

    @Test
    fun theSecondPlayerCanWinToo() {
        // Two pieces placed and one in hand: Bob places it.
        val g = Quarto(ann, bob, board(12 to 8, 13 to 9), piece = 10)
        assertEquals("2", g.toState().turn)
        assertNull(g.place(bob, 14))
        assertNull(g.give(bob, 0))
        assertNull(g.place(ann, 0))
        assertNull(g.give(ann, 11))
        assertNull(g.place(bob, 15))
        val s = g.toState()
        // 8, 9, 10 and 11 are hollow and round (bits 8 set, 4 clear), and differ in height and colour.
        assertEquals("won", s.status)
        assertEquals(bob, s.winner)
        assertEquals(listOf(12, 13, 14, 15), s.line)
        assertEquals(listOf("round", "hollow"), s.sharedTraits)
        assertEquals(15, s.lastCell)
    }

    @Test
    fun aFullBoardWithoutALineIsADraw() {
        val g = Quarto(ann, bob, drawn.dropLast(1) + (-1), piece = drawn.last())
        assertEquals(listOf<Int>(), g.toState().pool)
        assertEquals("1", g.toState().turn) // 15 placed: Ann places the 16th
        assertNull(g.place(ann, 15))
        val s = g.toState()
        assertEquals(drawn, s.board)
        assertEquals("draw", s.status)
        assertEquals("finished", s.phase)
        assertNull(s.winner)
        assertTrue(s.line.isEmpty())
        assertEquals("Quarto: draw, the board is full", g.describe())
        assertEquals("The game is over", g.place(ann, 0))
        assertEquals("The game is over", g.give(bob, 0))
    }

    @Test
    fun theLastPieceCanStillWin() {
        // Only the bottom row of this full board shares a trait: 0, 7, 6 and 2 are all solid.
        val full = listOf(4, 10, 1, 14, 15, 12, 5, 11, 9, 3, 13, 8, 0, 7, 6, 2)
        val g = Quarto(ann, bob, full.dropLast(1) + (-1), piece = full.last())
        assertNull(g.place(ann, 15))
        val s = g.toState()
        assertEquals("won", s.status)
        assertEquals(ann, s.winner)
        assertEquals(listOf(12, 13, 14, 15), s.line)
        assertEquals(listOf("solid"), s.sharedTraits)
    }

    @Test
    fun nothingIsAcceptedAfterAWin() {
        val g = Quarto(ann, bob, board(0 to 1, 1 to 3, 2 to 5), piece = 7)
        assertNull(g.place(ann, 3))
        assertEquals("The game is over", g.give(ann, 0))
        assertEquals("The game is over", g.place(bob, 4))
        assertEquals("The game is over", g.handle(bob, ClientMessage(type = "move", piece = 0)))
        assertEquals(12, g.toState().pool.size) // nothing is handed out after the winning piece
    }

    @Test
    fun whoActsNextFollowsFromTheStartPosition() {
        // Nothing in hand: whoever placed last chooses (Bob after an odd number of pieces, Ann after an even one).
        assertEquals("2" to "choose", Quarto(ann, bob, board(0 to 0)).toState().let { it.turn to it.phase })
        assertEquals("1" to "choose", Quarto(ann, bob, board(0 to 0, 1 to 15)).toState().let { it.turn to it.phase })
        // A piece in hand: Bob places the 1st, 3rd, ... piece and Ann the 2nd, 4th, ...
        assertEquals("2" to "place", Quarto(ann, bob, empty, piece = 4).toState().let { it.turn to it.phase })
        assertEquals("1" to "place", Quarto(ann, bob, board(0 to 0), piece = 4).toState().let { it.turn to it.phase })
    }

    @Test
    fun badStartPositionsAreRefused() {
        val bad = listOf(
            { Quarto(ann, bob, List(15) { -1 }) },
            { Quarto(ann, bob, board(0 to 3, 1 to 3)) },
            { Quarto(ann, bob, board(0 to 16)) },
            { Quarto(ann, bob, board(0 to 3), piece = 3) },
            { Quarto(ann, bob, empty, piece = 16) },
            { Quarto(ann, bob, board(0 to 1, 1 to 3, 2 to 5, 3 to 7)) }, // already won
            { Quarto(ann, bob, drawn, piece = 0) },
        )
        for ((i, make) in bad.withIndex()) {
            assertTrue(runCatching { make() }.exceptionOrNull() is IllegalArgumentException, "position $i was accepted")
        }
    }

    @Test
    fun randomGamesAlwaysKeepEveryPieceOnceAndEndProperly() {
        val random = Random(7)
        repeat(200) { game ->
            val g = Quarto(ann, bob)
            var moves = 0
            while (!g.finished) {
                val s = g.toState()
                val player = if (s.turn == "1") ann else bob
                if (s.phase == "choose") {
                    assertNull(g.give(player, s.pool.random(random)))
                } else {
                    assertNull(g.place(player, s.board.indices.filter { s.board[it] < 0 }.random(random)))
                }
                val after = g.toState()
                // Every piece is in exactly one place: on the board, in the pool or in hand.
                val everywhere = after.board.filter { it >= 0 } + after.pool + listOfNotNull(after.piece.takeIf { it >= 0 })
                assertEquals((0 until 16).toList(), everywhere.sorted(), "game $game")
                assertTrue(++moves <= 32, "game $game never ended")
            }
            val s = g.toState()
            assertEquals("finished", s.phase)
            if (s.status == "won") {
                assertEquals(4, s.line.size)
                assertTrue(s.lastCell in s.line, "the winning line goes through the last piece")
                assertEquals(s.winner, if (s.turn == "1") ann else bob)
                assertTrue(s.sharedTraits.isNotEmpty())
                for (word in s.sharedTraits) {
                    val bit = Quarto.TRAITS.indexOfFirst { word == it.first || word == it.second }
                    val set = Quarto.TRAITS[bit].first == word
                    assertTrue(s.line.all { (s.board[it] shr bit and 1 == 1) == set }, "game $game: $word on ${s.line}")
                }
            } else {
                assertEquals("draw", s.status)
                assertTrue(s.board.all { it >= 0 })
            }
        }
    }
}
