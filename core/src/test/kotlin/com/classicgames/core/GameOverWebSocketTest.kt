package com.classicgames.core

import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameOverWebSocketTest {
    private suspend fun DefaultClientWebSocketSession.next(): ServerMessage = withTimeout(5_000) {
        val result = incoming.receiveCatching()
        val frame = result.getOrNull() as? Frame.Text
            ?: error("socket closed: ${closeReason.await()} / ${result.exceptionOrNull()}")
        protocolJson.decodeFromString(ServerMessage.serializer(), frame.readText())
    }

    private suspend fun DefaultClientWebSocketSession.say(m: ClientMessage) =
        send(Frame.Text(protocolJson.encodeToString(ClientMessage.serializer(), m)))

    /** Reads the join handshake (the "session" message, then the chat "history") and returns the seat token. */
    private suspend fun DefaultClientWebSocketSession.handshake(): String {
        val session = next()
        assertEquals("session", session.type)
        assertEquals("history", next().type)
        return session.token!!
    }

    private val start = ClientMessage(type = "startGame", game = "tictactoe")
    private fun move(cell: Int) = ClientMessage(type = "move", cell = cell)

    @Test
    fun cannotStartAloneButCanChat() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }
        client.webSocket("/ws?name=Ann&room=SOLO") {
            handshake()
            say(start)
            val reply = next()
            assertEquals("error", reply.type)
            assertEquals("Wait for a second player to join", reply.text)
        }
    }

    @Test
    fun playFullGameThenRematchThenLeave() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=GAME") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=GAME") {
                val bob = this
                bob.handshake()
                assertEquals("Bob joined", ann.next().text)

                // Ann (first in the room) starts and is X.
                ann.say(start)
                val started = ann.next().state!!
                bob.next()
                assertEquals("Ann", started.xPlayer)
                assertEquals("Bob", started.oPlayer)
                assertEquals(".........", started.board)

                // Bob moving first is rejected, and only Bob hears about it.
                bob.say(move(0))
                val rejected = bob.next()
                assertEquals("error", rejected.type)
                assertEquals("Not your turn", rejected.text)

                // Starting a second game while one is running is rejected.
                ann.say(start)
                assertEquals("A game is already in progress", ann.next().text)

                suspend fun play(who: DefaultClientWebSocketSession, cell: Int): GameState {
                    who.say(move(cell))
                    val a = ann.next().state
                    val b = bob.next().state
                    assertEquals(a, b) // both players always see the same state
                    return a!!
                }

                play(ann, 0); play(bob, 3); play(ann, 1); play(bob, 4)
                val won = play(ann, 2)
                assertEquals("won", won.status)
                assertEquals("Ann", won.winner)
                assertEquals(listOf(0, 1, 2), won.line)

                // Moves after the end are rejected.
                bob.say(move(5))
                assertEquals("The game is over", bob.next().text)

                // Rematch: Bob is X this time.
                ann.say(start)
                val rematch = ann.next().state!!
                bob.next()
                assertEquals("Bob", rematch.xPlayer)
                assertEquals("playing", rematch.status)

                bob.say(ClientMessage(type = "leave")) // leaves for good in the middle of the rematch
            }

            // Bob's socket closed in the middle of the rematch: Ann is told, and the game is gone.
            assertEquals("Bob left", ann.next().text)
            val ended = ann.next()
            assertEquals("game", ended.type)
            assertNull(ended.state)
            assertNotNull(ended.text)
        }
    }

    @Test
    fun leavingAfterAFinishedGameKeepsTheFinalBoard() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=DONE") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=DONE") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                ann.say(start)
                ann.next(); bob.next()
                for ((who, cell) in listOf(ann to 0, bob to 3, ann to 1, bob to 4, ann to 2)) {
                    who.say(move(cell))
                    ann.next(); bob.next()
                }

                bob.say(ClientMessage(type = "leave")) // leaves after Ann won
            }

            // Ann hears that Bob left, but no "game" message clears her final board.
            assertEquals("Bob left", ann.next().text)
            ann.say(start)
            val reply = ann.next()
            assertEquals("error", reply.type)
            assertEquals("Wait for a second player to join", reply.text)
        }
    }

    @Test
    fun everyGameChangeGetsAHigherVersionSoLateUpdatesCanBeIgnored() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=ORDER") {
            val ann = this
            ann.handshake()
            var lastMove = 0L
            client.webSocket("/ws?name=Bob&room=ORDER") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                suspend fun change(who: DefaultClientWebSocketSession, msg: ClientMessage): Long {
                    who.say(msg)
                    val a = ann.next()
                    val b = bob.next()
                    assertEquals(a.version, b.version) // one change, one version for everybody
                    return a.version
                }

                val started = change(ann, start)
                val first = change(ann, move(0))
                bob.say(move(0)) // rejected: changes nothing, so no version is used up
                assertEquals("error", bob.next().type)
                val second = change(bob, move(4))
                assertTrue(started in 1 until first)
                assertEquals(first + 1, second)
                lastMove = second

                bob.say(ClientMessage(type = "leave"))
            }

            // Cancelling the game is a change too, so a move delivered after it can't bring the game back.
            assertEquals("Bob left", ann.next().text)
            val ended = ann.next()
            assertNull(ended.state)
            assertTrue(ended.version > lastMove)
        }
    }

    @Test
    fun battleshipOverWebSocketGivesEachPlayerTheirOwnView() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }
        val fleet = listOf(
            ShipPlacement(listOf(0, 1, 2, 3, 4)),
            ShipPlacement(listOf(20, 21, 22, 23)),
            ShipPlacement(listOf(40, 41, 42)),
            ShipPlacement(listOf(60, 61, 62)),
            ShipPlacement(listOf(80, 81)),
        )

        client.webSocket("/ws?name=Ann&room=SHIPS") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=SHIPS") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                ann.say(ClientMessage(type = "startGame", game = "battleship"))
                val annStart = ann.next().battle!!
                val bobStart = bob.next().battle!!
                assertEquals("placing", annStart.phase)
                assertEquals("Ann", annStart.you)
                assertEquals("Bob", bobStart.you)

                // Firing before placing is rejected and only the sender hears about it.
                bob.say(move(0))
                assertEquals("Place your fleet first", bob.next().text)

                ann.say(ClientMessage(type = "place", ships = fleet))
                assertEquals(true, ann.next().battle!!.youReady)
                assertEquals(true, bob.next().battle!!.opponentReady)

                bob.say(ClientMessage(type = "place", ships = fleet))
                val annPlaying = ann.next().battle!!
                val bobPlaying = bob.next().battle!!
                assertEquals("playing", annPlaying.phase)
                assertEquals(true, annPlaying.yourTurn)
                assertEquals(false, bobPlaying.yourTurn)
                assertEquals('S', annPlaying.myBoard[0]) // I can see my own ships...
                assertEquals('.', annPlaying.shots[0])   // ...but not the enemy's

                ann.say(move(0)) // hits Bob's carrier
                val annAfter = ann.next().battle!!
                val bobAfter = bob.next().battle!!
                assertEquals('H', annAfter.shots[0])
                assertEquals('H', bobAfter.myBoard[0])
                assertEquals(false, 'S' in annAfter.shots)
                assertEquals("Hit at A1!", annAfter.event)
                assertEquals("Ann hit your ship at A1", bobAfter.event)
                assertEquals(true, bobAfter.yourTurn)

                bob.say(ClientMessage(type = "leave"))
            }

            // Bob left mid-game: the game is cancelled for Ann.
            assertEquals("Bob left", ann.next().text)
            val ended = ann.next()
            assertEquals("game", ended.type)
            assertNull(ended.battle)
            assertNull(ended.state)
        }
    }

    @Test
    fun connectFourOverWebSocketBothPlayersSeeTheSameBoard() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=FOUR") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=FOUR") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                ann.say(ClientMessage(type = "startGame", game = "connectfour"))
                val started = ann.next().connect!!
                bob.next()
                assertEquals("Ann", started.redPlayer)
                assertEquals("Bob", started.yellowPlayer)
                assertEquals("R", started.turn)
                assertEquals(".".repeat(42), started.board)

                // Yellow can't open, and only Bob hears about it.
                bob.say(move(3))
                assertEquals("Not your turn", bob.next().text)

                // Red drops into column 3: it lands on the bottom row (cell 35 + 3).
                ann.say(move(3))
                val annView = ann.next().connect!!
                val bobView = bob.next().connect!!
                assertEquals(annView, bobView)
                assertEquals('R', annView.board[38])
                assertEquals("Y", annView.turn)
                assertEquals(38, annView.lastMove)

                // Yellow stacks on top of it.
                bob.say(move(3))
                assertEquals('Y', ann.next().connect!!.board[31])
                bob.next()
            }
        }
    }

    @Test
    fun othelloOverWebSocketBothPlayersSeeTheSameBoard() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=REVERSI") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=REVERSI") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                ann.say(ClientMessage(type = "startGame", game = "othello"))
                val started = ann.next().othello!!
                bob.next()
                assertEquals("Ann", started.blackPlayer)
                assertEquals("Bob", started.whitePlayer)
                assertEquals(listOf(19, 26, 37, 44), started.legalMoves)

                // White can't open; an illegal cell is refused as well. Only the sender hears about it.
                bob.say(move(19))
                assertEquals("Not your turn", bob.next().text)
                ann.say(move(0))
                assertEquals("That move doesn't flip any discs", ann.next().text)

                ann.say(move(19))
                val annView = ann.next().othello!!
                val bobView = bob.next().othello!!
                assertEquals(annView, bobView)
                assertEquals("W", annView.turn)
                assertEquals(listOf(27), annView.flipped)
                assertEquals(listOf(18, 20, 34), annView.legalMoves)
            }
        }
    }

    @Test
    fun checkersOverWebSocketBothPlayersSeeTheSameBoard() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=DRAUGHTS") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=DRAUGHTS") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                ann.say(ClientMessage(type = "startGame", game = "checkers"))
                val started = ann.next().checkers!!
                bob.next()
                assertEquals("Ann", started.redPlayer)
                assertEquals("Bob", started.blackPlayer)
                assertEquals(7, started.legalMoves.size)

                // Black can't open, and a move for a square that isn't theirs is refused. Only the sender hears it.
                bob.say(ClientMessage(type = "move", from = 17, to = 24))
                assertEquals("Not your turn", bob.next().text)
                ann.say(ClientMessage(type = "move", from = 17, to = 24))
                assertEquals("Pick one of your own pieces", ann.next().text)

                ann.say(ClientMessage(type = "move", from = 40, to = 33))
                val annView = ann.next().checkers!!
                val bobView = bob.next().checkers!!
                assertEquals(annView, bobView)
                assertEquals("B", annView.turn)
                assertEquals(40, annView.lastFrom)
                assertEquals(33, annView.lastTo)
                assertEquals('r', annView.board[33])
                assertEquals(true, CheckersMove(17, 24) in annView.legalMoves)
            }
        }
    }

    @Test
    fun dotsAndBoxesOverWebSocketPlaysAWholeGame() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=BOXES") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=BOXES") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                ann.say(ClientMessage(type = "startGame", game = "dotsandboxes"))
                val start = ann.next()
                bob.next()
                var state = start.dots!!
                var version = start.version
                val n = state.size
                assertEquals("Ann", state.redPlayer)
                assertEquals("Bob", state.bluePlayer)
                assertEquals("R", state.turn)
                assertEquals(DotsAndBoxes.lineCount(n), state.lines.length)

                // Blue can't open, and only Bob hears about it.
                bob.say(move(0))
                assertEquals("Not your turn", bob.next().text)

                // Every line in order, each drawn by whoever's turn it is. The horizontal lines close nothing.
                // Then each row of boxes goes to the player who draws its second vertical line: they keep the
                // turn to the end of the row and pass it on with the next row's first line. Bob gets row 0.
                for (line in 0 until DotsAndBoxes.lineCount(n)) {
                    (if (state.turn == "R") ann else bob).say(move(line))
                    val annMessage = ann.next()
                    val bobMessage = bob.next()
                    assertEquals(annMessage.dots, bobMessage.dots)
                    assertEquals(annMessage.version, bobMessage.version)
                    assertTrue(annMessage.version > version)
                    version = annMessage.version
                    state = annMessage.dots!!
                    assertEquals(line, state.lastLine)

                    if (line == DotsAndBoxes.verticalLine(n, 0, 1)) {
                        assertEquals(listOf(0), state.closed) // the first box...
                        assertEquals("B", state.turn)         // ...and Bob draws again
                    }
                }

                val blueRows = (n + 1) / 2 // rows 0, 2, 4, ...
                assertEquals(blueRows * n, state.blueScore)
                assertEquals((n - blueRows) * n, state.redScore)
                assertEquals(if (n % 2 == 1) "won" else "draw", state.status)
                if (n % 2 == 1) assertEquals("Bob", state.winner)

                ann.say(move(0))
                assertEquals("The game is over", ann.next().text)
            }
        }
    }

    @Test
    fun owareOverWebSocketPlaysAWholeGame() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=SEEDS") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=SEEDS") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                ann.say(ClientMessage(type = "startGame", game = "oware"))
                val start = ann.next()
                bob.next()
                var state = start.oware!!
                var version = start.version
                assertEquals("Ann", state.southPlayer)
                assertEquals("Bob", state.northPlayer)
                assertEquals(List(12) { 4 }, state.pits)
                assertEquals((0..5).toList(), state.legalMoves)

                // North can't open, and south can't sow from north's pits. Only the sender hears about it.
                bob.say(move(6))
                assertEquals("Not your turn", bob.next().text)
                ann.say(move(6))
                assertEquals("Pick one of your own pits", ann.next().text)

                // Each side always sows from its first legal pit until the game ends (the 100-move rule makes
                // sure it does).
                var plies = 0
                while (state.status == "playing") {
                    val pit = state.legalMoves.first()
                    (if (state.turn == "S") ann else bob).say(move(pit))
                    val annMessage = ann.next()
                    val bobMessage = bob.next()
                    assertEquals(annMessage.oware, bobMessage.oware)
                    assertEquals(annMessage.version, bobMessage.version)
                    assertTrue(annMessage.version > version)
                    version = annMessage.version
                    state = annMessage.oware!!
                    assertEquals(pit, state.lastPit)
                    assertEquals(Oware.TOTAL_SEEDS, state.pits.sum() + state.southCaptured + state.northCaptured)
                    assertTrue(++plies < 2_000, "the game never ended")
                }

                assertTrue(state.status == "won" || state.status == "draw")
                assertTrue(state.legalMoves.isEmpty())
                if (state.status == "won") {
                    val southWon = state.southCaptured > state.northCaptured
                    assertEquals(if (southWon) "Ann" else "Bob", state.winner)
                }

                ann.say(move(0))
                assertEquals("The game is over", ann.next().text)
            }
        }
    }

    @Test
    fun mastermindOverWebSocketHidesEachCodeUntilTheEnd() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }
        val annCode = listOf(0, 0, 1, 1)
        val bobCode = listOf(5, 4, 3, 2)
        val wrong = listOf(1, 2, 3, 4)
        fun secret(code: List<Int>) = ClientMessage(type = "secret", code = code)
        fun guess(code: List<Int>) = ClientMessage(type = "move", code = code)

        client.webSocket("/ws?name=Ann&room=CODES") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=CODES") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                var version = 0L

                /** Reads the update both players get for one change and checks it is a newer version. */
                suspend fun update(): Pair<MastermindState, MastermindState> {
                    val a = ann.next()
                    val b = bob.next()
                    assertEquals(a.version, b.version)
                    assertTrue(a.version > version)
                    version = a.version
                    // Neither player is ever sent the other's code before the game is over.
                    if (a.mastermind!!.phase != "finished") {
                        assertTrue(a.mastermind!!.opponentSecret.isEmpty())
                        assertTrue(b.mastermind!!.opponentSecret.isEmpty())
                        val bobJson = protocolJson.encodeToString(ServerMessage.serializer(), b)
                        assertTrue("[0,0,1,1]" !in bobJson, bobJson)
                    }
                    return a.mastermind!! to b.mastermind!!
                }

                ann.say(ClientMessage(type = "startGame", game = "mastermind"))
                var (a, b) = update()
                assertEquals("secret", a.phase)
                assertEquals("Ann", a.firstPlayer)
                assertEquals("Bob", a.opponent)
                assertEquals("Ann", b.opponent)

                // Bad codes and early guesses are rejected, only for the sender.
                bob.say(guess(wrong))
                assertEquals("Set your code first", bob.next().text)
                ann.say(secret(listOf(0, 1)))
                assertEquals("A code is 4 pegs, each one of the 6 colours", ann.next().text)

                ann.say(secret(annCode))
                update().let { (x, y) -> a = x; b = y }
                assertEquals(annCode, a.yourSecret)
                assertTrue(b.opponentReady)
                assertTrue(b.yourSecret.isEmpty())

                bob.say(secret(bobCode))
                update().let { (x, y) -> a = x; b = y }
                assertEquals("guessing", a.phase)
                assertTrue(a.yourTurn)
                assertEquals(bobCode, b.yourSecret)

                bob.say(guess(wrong))
                assertEquals("Not your turn", bob.next().text)

                // Round 1: both miss. 1 2 3 4 against 5 4 3 2 is one black (the 3) and two white (the 2 and 4).
                ann.say(guess(wrong))
                update().let { (x, y) -> a = x; b = y }
                assertEquals(listOf(MastermindGuess(wrong, 1, 2)), a.yourGuesses)
                assertEquals(a.yourGuesses, b.opponentGuesses)
                assertTrue(b.yourTurn)
                bob.say(guess(wrong))
                update().let { (x, y) -> a = x; b = y }
                assertEquals(listOf(MastermindGuess(wrong, 0, 1)), b.yourGuesses)

                // Round 2: Ann cracks it, Bob still gets his guess, misses, and Ann wins.
                ann.say(guess(bobCode))
                update().let { (x, y) -> a = x; b = y }
                assertEquals("guessing", a.phase)
                assertTrue(b.yourTurn)
                bob.say(guess(listOf(0, 0, 1, 2)))
                update().let { (x, y) -> a = x; b = y }
                assertEquals("finished", a.phase)
                assertEquals("Ann", a.winner)
                assertEquals("Ann", b.winner)
                assertEquals(bobCode, a.opponentSecret)
                assertEquals(annCode, b.opponentSecret)
                assertEquals(MastermindGuess(listOf(0, 0, 1, 2), 3, 0), b.yourGuesses.last())

                ann.say(guess(wrong))
                assertEquals("The game is over", ann.next().text)
            }
        }
    }

    @Test
    fun hexOverWebSocketSwapsAndPlaysToAWin() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }
        val swap = ClientMessage(type = "swap")

        client.webSocket("/ws?name=Ann&room=HEXES") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=HEXES") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                var version = 0L

                /** Reads the update both players get for one change: the same board, with a newer version. */
                suspend fun update(): HexState {
                    val a = ann.next()
                    val b = bob.next()
                    assertEquals(a.hex, b.hex)
                    assertEquals(a.version, b.version)
                    assertTrue(a.version > version)
                    version = a.version
                    return a.hex!!
                }

                ann.say(ClientMessage(type = "startGame", game = "hex"))
                var state = update()
                assertEquals("Ann", state.redPlayer)
                assertEquals("Bob", state.bluePlayer)
                assertEquals(Hex.SIZE, state.size)
                val n = state.size
                val centre = n / 2 * n + n / 2

                // Bob can't open or swap yet, Ann can't swap at all. Only the sender hears about it.
                bob.say(move(centre))
                assertEquals("Not your turn", bob.next().text)
                bob.say(swap)
                assertEquals("Not your turn", bob.next().text)

                // Ann opens in the centre, which is on the long diagonal: Bob's swap keeps it there, in blue.
                ann.say(move(centre))
                state = update()
                assertTrue(state.canSwap)
                ann.say(swap)
                assertEquals("Not your turn", ann.next().text)
                bob.say(swap)
                state = update()
                assertEquals('B', state.board[centre])
                assertTrue(state.swapped)
                assertFalse(state.canSwap)
                assertEquals("R", state.turn)

                // Ann walks down the first column while Bob plays down the last one, which never joins his sides.
                for (row in 0 until n) {
                    ann.say(move(row * n))
                    state = update()
                    if (row == n - 1) break
                    bob.say(move(row * n + n - 1))
                    state = update()
                }
                assertEquals("won", state.status)
                assertEquals("Ann", state.winner)
                assertEquals((0 until n).map { it * n }, state.winningPath)

                bob.say(move(1))
                assertEquals("The game is over", bob.next().text)
            }
        }
    }

    @Test
    fun memoryOverWebSocketPlaysAWholeGameWithoutShowingFaceDownCards() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=PAIRS") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=PAIRS") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                var version = 0L
                val known = HashMap<Int, Int>() // card -> face, as seen by both players

                /** Reads the update both players get for one change and checks what it may show. */
                suspend fun update(): MemoryState {
                    val a = ann.next()
                    val b = bob.next()
                    assertEquals(a.memory, b.memory)
                    assertEquals(a.version, b.version)
                    assertTrue(a.version > version)
                    version = a.version
                    val s = a.memory!!
                    for (card in s.faces.indices) {
                        val faceUp = s.taken[card] != '.' || card in s.flipped || card in s.lastPair
                        assertEquals(faceUp, s.faces[card] >= 0, "card $card in $s")
                        if (faceUp) known[card] = s.faces[card]
                    }
                    return s
                }

                ann.say(ClientMessage(type = "startGame", game = "memory"))
                var state = update()
                assertEquals("Ann", state.firstPlayer)
                assertEquals(List(24) { -1 }, state.faces)

                bob.say(move(0))
                assertEquals("Not your turn", bob.next().text)
                ann.say(move(24))
                assertEquals("Invalid card", ann.next().text)

                // Both play with a perfect memory: a known pair if there is one, else an unknown card and then its
                // known partner or another unknown card.
                suspend fun flip(card: Int) {
                    (if (state.turn == "1") ann else bob).say(move(card))
                    state = update()
                }
                var turns = 0
                while (state.status == "playing") {
                    val open = state.taken.indices.filter { state.taken[it] == '.' }
                    val pair = open.filter { it in known }.groupBy { known.getValue(it) }.values.firstOrNull { it.size == 2 }
                    val a = pair?.get(0) ?: open.first { it !in known }
                    flip(a)
                    val b = pair?.get(1)
                        ?: open.firstOrNull { it != a && known[it] == known[a] }
                        ?: open.first { it != a && it !in known }
                    flip(b)
                    assertTrue(++turns < 100, "the game never ended")
                }

                assertEquals(12, state.firstScore + state.secondScore)
                assertEquals((0 until 12).flatMap { listOf(it, it) }, state.faces.sorted())
                when {
                    state.firstScore > state.secondScore -> assertEquals("Ann", state.winner)
                    state.secondScore > state.firstScore -> assertEquals("Bob", state.winner)
                    else -> assertEquals("draw", state.status)
                }

                ann.say(move(0))
                assertEquals("The game is over", ann.next().text)
            }
        }
    }

    @Test
    fun quartoOverWebSocketPlaysADrawThenARematchToAWin() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }
        fun give(piece: Int) = ClientMessage(type = "move", piece = piece)

        client.webSocket("/ws?name=Ann&room=QUARTO") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=QUARTO") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                var version = 0L

                /** Reads the update both players get for one change: the same state, with a newer version. */
                suspend fun update(): QuartoState {
                    val a = ann.next()
                    val b = bob.next()
                    assertEquals(a.quarto, b.quarto)
                    assertEquals(a.version, b.version)
                    assertTrue(a.version > version)
                    version = a.version
                    return a.quarto!!
                }

                ann.say(ClientMessage(type = "startGame", game = "quarto"))
                var state = update()
                assertEquals("Ann", state.firstPlayer)
                assertEquals("choose", state.phase)

                // Out of turn, out of phase and malformed moves only reach the sender.
                bob.say(give(0))
                assertEquals("Not your turn", bob.next().text)
                ann.say(move(0))
                assertEquals("Choose a piece for Bob first", ann.next().text)
                ann.say(ClientMessage(type = "move", cell = 0, piece = 0))
                assertEquals("Send a cell to place your piece, or a piece to give", ann.next().text)

                // Each piece of a full board without a line goes on its own cell, in order: nobody ever wins.
                val drawn = listOf(12, 8, 2, 7, 5, 0, 10, 4, 11, 14, 9, 3, 1, 13, 6, 15)
                for ((cell, piece) in drawn.withIndex()) {
                    val chooser = if (state.turn == "1") ann else bob
                    chooser.say(give(piece))
                    state = update()
                    assertEquals(piece, state.piece)
                    assertEquals("place", state.phase)
                    if (cell == 0) {
                        bob.say(give(1))
                        assertEquals("Place the piece you were given first", bob.next().text)
                    }
                    (if (state.turn == "1") ann else bob).say(move(cell))
                    state = update()
                    assertEquals(-1, state.piece)
                }
                assertEquals(drawn, state.board)
                assertEquals("draw", state.status)
                assertEquals("finished", state.phase)
                assertTrue(state.pool.isEmpty())
                ann.say(give(0))
                assertEquals("The game is over", ann.next().text)

                // Rematch: Bob chooses first now. Pieces 0..3 go on the top row in order; they are all round and
                // solid, and Bob, who places the 2nd and the 4th piece, wins.
                bob.say(ClientMessage(type = "startGame", game = "quarto"))
                state = update()
                assertEquals("Bob", state.firstPlayer)
                assertEquals("Ann", state.secondPlayer)
                assertEquals("1", state.turn)
                for (piece in 0 until 4) {
                    (if (state.turn == "1") bob else ann).say(give(piece))
                    state = update()
                    (if (state.turn == "1") bob else ann).say(move(piece))
                    state = update()
                }
                assertEquals("won", state.status)
                assertEquals("Bob", state.winner)
                assertEquals(listOf(0, 1, 2, 3), state.line)
                assertEquals(listOf("round", "solid"), state.sharedTraits)
                assertEquals(12, state.pool.size)

                ann.say(move(4))
                assertEquals("The game is over", ann.next().text)
            }
        }
    }

    @Test
    fun briscaOverWebSocketPlaysAWholeGameWithoutShowingTheOtherHand() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }
        fun card(card: Int) = ClientMessage(type = "move", card = card)
        val swap7 = ClientMessage(type = "swap7")

        client.webSocket("/ws?name=Ann&room=BRISCA") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=BRISCA") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                var version = 0L

                /**
                 * Reads the update both players get for one change: each their own view, with the same newer version.
                 * Checks that the views agree on everything public and that neither shows a card of the other hand.
                 */
                suspend fun update(): Pair<BriscaState, BriscaState> {
                    val a = ann.next()
                    val b = bob.next()
                    assertEquals(a.version, b.version)
                    assertTrue(a.version > version)
                    version = a.version
                    val sa = a.brisca!!
                    val sb = b.brisca!!
                    assertEquals("Ann", sa.you)
                    assertEquals("Bob", sb.you)
                    assertEquals(sa.trick, sb.trick)
                    assertEquals(sa.lastTrick, sb.lastTrick)
                    assertEquals(sa.deckSize, sb.deckSize)
                    assertEquals(sa.trumpCard, sb.trumpCard)
                    assertEquals(sa.yourPoints, sb.opponentPoints)
                    assertEquals(sa.hand.size, sb.opponentCards)
                    assertEquals(sb.hand.size, sa.opponentCards)
                    for ((mine, theirs) in listOf(sa to sb.hand, sb to sa.hand)) {
                        val seen = mine.hand + mine.trick.map { it.card } + mine.lastTrick.map { it.card } + mine.trumpCard
                        // The face-up trump is the one card of the other hand you may know: you saw it being drawn.
                        assertTrue(seen.none { it in theirs && it != mine.trumpCard }, "${mine.you} sees $seen, other hand $theirs")
                    }
                    return sa to sb
                }

                ann.say(ClientMessage(type = "startGame", game = "brisca"))
                var (a, b) = update()
                assertEquals("Ann", a.firstPlayer)
                assertTrue(a.yourTurn)
                assertEquals(3, a.hand.size)
                assertEquals(34, a.deckSize)

                bob.say(card(b.hand.first()))
                assertEquals("Not your turn", bob.next().text)
                bob.say(swap7)
                assertEquals("Not your turn", bob.next().text)
                ann.say(card(b.hand.first()))
                assertEquals("That card is not in your hand", ann.next().text)

                // Both always play their first card, swapping the 7 of trumps first whenever they may.
                var plays = 0
                while (a.status == "playing") {
                    var (me, state) = if (a.yourTurn) ann to a else bob to b
                    if (state.canSwap) {
                        val faceUp = state.trumpCard
                        me.say(swap7)
                        update().let { (x, y) -> a = x; b = y }
                        state = if (me === ann) a else b
                        assertEquals(Brisca.cardOf(Brisca.suitOf(faceUp), 6), state.trumpCard)
                        assertTrue(faceUp in state.hand)
                    }
                    me.say(card(state.hand.first()))
                    update().let { (x, y) -> a = x; b = y }
                    assertTrue(++plays <= 40, "the game never ended")
                }
                assertEquals(40, plays)
                assertEquals(120, a.yourPoints + a.opponentPoints)
                when {
                    a.yourPoints >= 61 -> assertEquals("Ann", a.winner)
                    b.yourPoints >= 61 -> assertEquals("Bob", b.winner)
                    else -> assertEquals("draw", a.status)
                }
                assertEquals(a.winner, b.winner)

                ann.say(card(0))
                assertEquals("The game is over", ann.next().text)
            }
        }
    }

    @Test
    fun mazeOverWebSocketGuidesTheWalkerOutThenSwapsRolesAndEndsWhenOneLeaves() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }
        fun step(direction: String) = ClientMessage(type = "move", direction = direction)
        fun hint(direction: String) = ClientMessage(type = "hint", direction = direction)
        val bits = mapOf("up" to 1, "right" to 2, "down" to 4, "left" to 8)

        client.webSocket("/ws?name=Ann&room=MAZE") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=MAZE") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                var version = 0L

                /** Reads the update both players get for one change, with the same newer version; guide's view first. */
                suspend fun update(guide: DefaultClientWebSocketSession): Pair<MazeState, MazeState> {
                    val a = ann.next()
                    val b = bob.next()
                    assertEquals(a.version, b.version)
                    assertTrue(a.version > version)
                    version = a.version
                    val (g, w) = if (guide === ann) a.maze!! to b.maze!! else b.maze!! to a.maze!!
                    assertEquals("guide", g.role)
                    assertEquals("walker", w.role)
                    assertEquals(g.window, w.window)
                    assertEquals(g.movesUsed, w.movesUsed)
                    assertEquals(g.hints, w.hints)
                    if (w.status == "playing") {
                        // The walker only gets the window: no walls, cells or trail.
                        assertTrue(w.walls.isEmpty() && w.trail.isEmpty())
                        assertEquals(listOf(-1, -1, -1), listOf(w.start, w.exit, w.position))
                    }
                    return g to w
                }

                ann.say(ClientMessage(type = "startGame", game = "maze"))
                var (g, w) = update(ann)
                assertEquals("Ann", g.guide)
                assertEquals("Bob", g.walker)
                assertEquals(8, g.size)

                // The guide finds the way out on the map and hints every step; the walker follows.
                val dist = Maze.distances(g.size, g.walls, g.exit)
                val limit = g.moveLimit
                bob.say(hint("up"))
                assertEquals("Only Ann gives hints", bob.next().text)
                ann.say(step("up"))
                assertEquals("Only Bob moves: send them hints", ann.next().text)
                while (g.status == "playing") {
                    val here = g.position
                    val direction = Maze.DIRECTIONS.first { d ->
                        val next = Maze.neighbour(g.size, here, Maze.DIRECTIONS.indexOf(d))
                        g.walls[here] and bits.getValue(d) == 0 && dist[next] == dist[here] - 1
                    }
                    ann.say(hint(direction))
                    update(ann).let { (x, y) -> g = x; w = y }
                    assertEquals(direction, w.hint)
                    // Before moving on, the walker bumps into a wall of its window if it has one: refused, not counted.
                    Maze.DIRECTIONS.firstOrNull { w.window[4] and bits.getValue(it) != 0 }?.let {
                        bob.say(step(it))
                        assertEquals("There's a wall that way", bob.next().text)
                    }
                    bob.say(step(w.hint))
                    update(ann).let { (x, y) -> g = x; w = y }
                }
                assertEquals("won", w.status)
                assertEquals(g.moveLimit, limit)
                assertEquals(g.shortest, w.movesUsed)
                assertEquals("Out in ${g.shortest} moves: the shortest way, a perfect run", w.result)
                assertEquals(g.result, w.result)
                // Now the walker sees the whole maze too.
                assertEquals(g.walls, w.walls)
                assertEquals(g.trail, w.trail)
                bob.say(step("up"))
                assertEquals("The game is over", bob.next().text)

                // Play again: Bob guides and Ann walks.
                ann.say(ClientMessage(type = "startGame", game = "maze"))
                update(bob).let { (x, y) -> g = x; w = y }
                assertEquals("Bob", g.guide)
                assertEquals("Ann", g.walker)
                assertEquals(0, w.movesUsed)
                val open = Maze.DIRECTIONS.first { w.window[4] and bits.getValue(it) == 0 }
                ann.say(step(open))
                update(bob).let { (x, y) -> g = x; w = y }
                assertEquals(1, w.movesUsed)

                bob.say(ClientMessage(type = "leave")) // the guide leaves in the middle of the game
            }

            // The walker is told, and the game is gone for both: nobody won or lost it.
            assertEquals("Bob left", ann.next().text)
            val ended = ann.next()
            assertEquals("game", ended.type)
            assertNull(ended.maze)
            assertEquals("Game ended because Bob left", ended.text)
            ann.say(step("up"))
            assertEquals("No game in progress", ann.next().text)
        }
    }

    @Test
    fun longChatMessagesAreCut() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }
        client.webSocket("/ws?name=Ann&room=LONG") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=LONG") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"

                ann.say(ClientMessage(text = "a".repeat(MAX_CHAT_LENGTH + 100)))
                assertEquals("a".repeat(MAX_CHAT_LENGTH), bob.next().text)

                // An emoji (two UTF-16 chars) straddling the limit is dropped whole, not cut in half.
                val emoji = "\uD83D\uDE00"
                ann.say(ClientMessage(text = "b".repeat(MAX_CHAT_LENGTH - 1) + emoji))
                assertEquals("b".repeat(MAX_CHAT_LENGTH - 1), bob.next().text)
            }
        }
    }

    @Test
    fun anOversizedFrameClosesTheConnection() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }
        client.webSocket("/ws?name=Ann&room=HUGE") {
            handshake()
            send(Frame.Text("x".repeat(65 * 1024)))
            val reason = withTimeout(5_000) { closeReason.await() }
            assertEquals(CloseReason.Codes.TOO_BIG, reason?.knownReason)
        }
    }

    @Test
    fun oldChatFormatStillWorks() = testApplication {
        application { module() }
        val client = createClient { install(WebSockets) }
        client.webSocket("/ws?name=Ann&room=CHAT") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=CHAT") {
                val bob = this
                bob.handshake()
                ann.next() // "Bob joined"
                ann.send(Frame.Text("""{"text":"hello"}"""))
                assertEquals("hello", bob.next().text)
                bob.send(Frame.Text("plain text, not json"))
                assertEquals("plain text, not json", ann.next().text)
            }
        }
    }
}
