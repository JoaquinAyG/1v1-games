package com.classicgames.core

import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
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
