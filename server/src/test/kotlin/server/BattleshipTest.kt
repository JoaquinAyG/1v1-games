package server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BattleshipTest {
    private val ann = "Ann"
    private val bob = "Bob"

    /** Five straight ships on rows 0, 2, 4, 6 and 8, all starting in column 0. */
    private fun fleet() = listOf(
        ShipPlacement(listOf(0, 1, 2, 3, 4)),     // Carrier
        ShipPlacement(listOf(20, 21, 22, 23)),    // Battleship
        ShipPlacement(listOf(40, 41, 42)),        // Cruiser
        ShipPlacement(listOf(60, 61, 62)),        // Submarine
        ShipPlacement(listOf(80, 81)),            // Destroyer
    )

    private val shipCells = fleet().flatMap { it.cells }
    private val waterCells = (0 until 100).filter { it !in shipCells }

    private fun Battleship.place(player: String, ships: List<ShipPlacement>? = fleet()) =
        handle(player, ClientMessage(type = "place", ships = ships))

    private fun Battleship.fire(player: String, cell: Int) =
        handle(player, ClientMessage(type = "move", cell = cell))

    private fun Battleship.view(player: String) = messageFor(player).battle!!

    /** A game with both fleets placed; Ann fires first. */
    private fun readyGame() = Battleship(ann, bob).also {
        assertNull(it.place(ann))
        assertNull(it.place(bob))
    }

    @Test
    fun rejectsInvalidFleets() {
        val g = Battleship(ann, bob)
        val f = fleet()
        assertTrue(g.place(ann, f.take(4))!!.contains("sizes"))
        assertTrue(g.place(ann, f.dropLast(1) + ShipPlacement(listOf(80, 81, 82)))!!.contains("sizes"))
        assertEquals("Ships cannot overlap", g.place(ann, f.dropLast(1) + ShipPlacement(listOf(4, 14))))
        assertEquals("A ship is outside the board", g.place(ann, f.dropLast(1) + ShipPlacement(listOf(98, 99 + 1))))
        assertEquals("Ships must be straight and unbroken", g.place(ann, f.dropLast(1) + ShipPlacement(listOf(80, 82))))
        assertEquals("Ships must be straight and unbroken", g.place(ann, f.dropLast(1) + ShipPlacement(listOf(80, 91))))
        // Wrapping from the end of one row to the start of the next is not a straight line.
        assertEquals("Ships must be straight and unbroken", g.place(ann, f.dropLast(1) + ShipPlacement(listOf(89, 90))))
        assertEquals("Send your ships with the move", g.place(ann, null))
        // None of the rejected attempts counted: a valid fleet is still accepted.
        assertNull(g.place(ann))
    }

    @Test
    fun acceptsVerticalAndTouchingShips() {
        val g = Battleship(ann, bob)
        val touching = listOf(
            ShipPlacement(listOf(0, 10, 20, 30, 40)), // vertical
            ShipPlacement(listOf(1, 11, 21, 31)),     // right next to it
            ShipPlacement(listOf(2, 12, 22)),
            ShipPlacement(listOf(3, 13, 23)),
            ShipPlacement(listOf(4, 14)),
        )
        assertNull(g.place(ann, touching))
    }

    @Test
    fun cannotPlaceTwiceOrFireBeforeBothAreReady() {
        val g = Battleship(ann, bob)
        assertEquals("Place your fleet first", g.fire(ann, 0))
        assertNull(g.place(ann))
        assertEquals("You already placed your fleet", g.place(ann))
        assertEquals("Place your fleet first", g.fire(ann, 0))
        assertEquals("placing", g.view(ann).phase)
        assertTrue(g.view(ann).youReady)
        assertFalse(g.view(ann).opponentReady)
        assertTrue(g.view(bob).opponentReady)
        assertEquals("Waiting for Bob to place their fleet", g.view(ann).event)
        assertEquals("Ann is ready. Place your fleet!", g.view(bob).event)
    }

    @Test
    fun firstPlayerFiresFirstAndTurnsAlternate() {
        val g = readyGame()
        assertEquals("playing", g.view(ann).phase)
        assertTrue(g.view(ann).yourTurn)
        assertFalse(g.view(bob).yourTurn)
        assertEquals("Both fleets are ready. You fire first!", g.view(ann).event)
        assertEquals("Both fleets are ready. Ann fires first", g.view(bob).event)

        assertEquals("Not your turn", g.fire(bob, 0))
        assertEquals("Invalid cell", g.fire(ann, 100))
        assertEquals("Invalid cell", g.fire(ann, -1))
        assertNull(g.fire(ann, 55))          // a miss still passes the turn
        assertTrue(g.view(bob).yourTurn)
        assertEquals("Not your turn", g.fire(ann, 56))
        assertNull(g.fire(bob, 0))           // a hit passes the turn too
        assertTrue(g.view(ann).yourTurn)
    }

    @Test
    fun hitsAndMissesAreShownAndShipsStayHidden() {
        val g = readyGame()
        assertNull(g.fire(ann, 0))   // hit
        assertNull(g.fire(bob, 99))  // miss
        assertNull(g.fire(ann, 55))  // miss

        val annView = g.view(ann)
        assertEquals('H', annView.shots[0])
        assertEquals('M', annView.shots[55])
        assertEquals("Miss at F6", annView.event)
        assertFalse('S' in annView.shots, "the enemy fleet must stay hidden while playing")

        val bobView = g.view(bob)
        assertEquals('H', bobView.myBoard[0])   // Ann's shot hit Bob's carrier
        assertEquals('M', bobView.myBoard[55])
        assertEquals('S', bobView.myBoard[1])   // my own ships are visible to me
        assertEquals("Ann missed at F6", bobView.event)
        assertFalse('S' in bobView.shots)
    }

    @Test
    fun rejectsFiringTwiceAtTheSameCell() {
        val g = readyGame()
        g.fire(ann, 0)
        g.fire(bob, 99)
        assertEquals("You already fired there", g.fire(ann, 0))
    }

    @Test
    fun sinkingIsAnnouncedAndMarked() {
        val g = readyGame()
        // Ann sinks the Destroyer (80, 81) while Bob misses.
        assertNull(g.fire(ann, 80))
        assertEquals("Hit at A9!", g.view(ann).event)
        assertEquals("Ann hit your ship at A9", g.view(bob).event)
        assertNull(g.fire(bob, waterCells[0]))
        assertNull(g.fire(ann, 81))
        assertEquals("You sank their Destroyer!", g.view(ann).event)
        assertEquals("Ann sank your Destroyer!", g.view(bob).event)
        assertEquals('X', g.view(ann).shots[80])
        assertEquals('X', g.view(ann).shots[81])
        assertEquals(listOf(5, 4, 3, 3), g.view(ann).enemyShipsLeft)
        assertEquals(listOf(5, 4, 3, 3), g.view(bob).myShipsLeft)
        assertEquals(listOf(5, 4, 3, 3, 2), g.view(ann).myShipsLeft)
    }

    @Test
    fun sinkingTheWholeFleetWinsAndRevealsTheRest() {
        val g = readyGame()
        for ((i, cell) in shipCells.withIndex()) {
            assertNull(g.fire(ann, cell))
            if (i == shipCells.lastIndex) break
            assertFalse(g.finished)
            assertNull(g.fire(bob, waterCells[i]))
        }
        assertTrue(g.finished)
        assertEquals(ann, g.winner)

        val annView = g.view(ann)
        assertEquals("finished", annView.phase)
        assertEquals(ann, annView.winner)
        assertTrue(annView.enemyShipsLeft.isEmpty())
        assertFalse(annView.yourTurn)

        // Bob never found Ann's ships, so they are revealed to him now that the game is over.
        val bobView = g.view(bob)
        assertTrue(shipCells.all { bobView.shots[it] == 'S' })
        assertEquals("The game is over", g.fire(bob, 5))
        assertEquals("The game is over", g.fire(ann, 5))
    }

    @Test
    fun rejectsStrangersAndUnknownActions() {
        val g = readyGame()
        assertEquals("You are not in this game", g.fire("Eve", 0))
        assertTrue(g.handle(ann, ClientMessage(type = "chat", text = "hi"))!!.contains("place"))
    }
}
