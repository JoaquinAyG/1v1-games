package com.classicgames.core

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What happens when a phone drops off the network and comes back. */
class ReconnectTest {
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

    /** A real pause (the test clock is virtual, but the server's timers and sockets are not). */
    private suspend fun realDelay(millis: Long) = withContext(Dispatchers.Default) { delay(millis) }

    private suspend fun DefaultClientWebSocketSession.closedWith(): CloseReason? = withTimeout(5_000) { closeReason.await() }

    private val startTicTacToe = ClientMessage(type = "startGame", game = "tictactoe")
    private fun move(cell: Int) = ClientMessage(type = "move", cell = cell)
    private fun chat(text: String) = ClientMessage(type = "chat", text = text)

    @Test
    fun aDroppedPlayerKeepsTheSeatAndTheGameAndCanContinue() = testApplication {
        application { module(graceMillis = 10_000) }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=BACK") {
            val ann = this
            ann.handshake()

            var bobToken = ""
            var lastVersion = 0L
            client.webSocket("/ws?name=Bob&room=BACK") {
                bobToken = handshake()
                assertEquals("Bob joined", ann.next().text)
                ann.say(startTicTacToe); ann.next(); next()
                ann.say(move(4)); ann.next() // Ann (X) takes the centre; it is Bob's turn
                lastVersion = next().version
                // ...and Bob's connection ends here, without a goodbye.
            }

            // Ann is told, and nothing else happens: the game is still there.
            assertEquals("Bob lost connection", ann.next().text)
            val away = ann.next()
            assertEquals("presence", away.type)
            assertEquals("Bob", away.from)
            assertEquals("offline", away.text)

            // Bob comes back with his token and finds everything as he left it.
            client.webSocket("/ws?name=Bob&room=BACK&token=$bobToken") {
                val session = next()
                assertEquals("session", session.type)
                assertEquals(bobToken, session.token) // same seat, same token
                val history = next()
                assertEquals("history", history.type)
                assertTrue(history.history!!.any { it.text == "Bob joined" })
                val game = next()
                assertEquals("game", game.type)
                assertEquals("....X....", game.state!!.board)
                assertEquals("O", game.state!!.turn)
                assertEquals(lastVersion, game.version) // the same state again, not a newer one

                assertEquals("Bob is back", ann.next().text)
                val back = ann.next()
                assertEquals("presence", back.type)
                assertEquals("online", back.text)

                // The game carries on from where it was.
                say(move(0))
                val annView = ann.next().state!!
                val bobView = next().state!!
                assertEquals(annView, bobView)
                assertEquals("O...X....", annView.board)
                assertEquals("X", annView.turn)
            }
        }
    }

    @Test
    fun aSeatCanBeTakenOverWhileTheOldConnectionIsStillOpen() = testApplication {
        application { module(graceMillis = 10_000) }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=TAKE") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=TAKE") {
                val oldConnection = this
                val token = handshake()
                ann.next() // "Bob joined"

                // The phone reconnects before the server noticed the old connection died.
                client.webSocket("/ws?name=Bob&room=TAKE&token=$token") {
                    assertEquals("session", next().type)
                    assertEquals("history", next().type)

                    // The stale connection is closed by the server...
                    assertEquals("Replaced by a new connection", oldConnection.closedWith()?.message)
                    // ...and Ann hears that Bob is back, without a "lost connection" in between.
                    assertEquals("Bob is back", ann.next().text)
                    assertEquals("online", ann.next().text)
                }
            }
        }
    }

    @Test
    fun aWrongTokenAnUnknownRoomOrAStolenNameIsRejected() = testApplication {
        application { module(graceMillis = 10_000) }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=TOKENS") {
            handshake()

            client.webSocket("/ws?name=Ann&room=TOKENS&token=not-her-token") {
                assertEquals("Session expired", closedWith()?.message)
            }
            client.webSocket("/ws?name=Ann&room=TOKENS") { // same name, no token
                assertEquals("Name already in use in this room", closedWith()?.message)
            }
            client.webSocket("/ws?name=Zed&room=NOSUCHROOM&token=whatever") {
                assertEquals("Session expired", closedWith()?.message)
            }
        }
    }

    @Test
    fun theSeatIsGivenUpWhenTheGracePeriodRunsOut() = testApplication {
        application { module(graceMillis = 300) }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=LATE") {
            val ann = this
            ann.handshake()

            var bobToken = ""
            client.webSocket("/ws?name=Bob&room=LATE") {
                bobToken = handshake()
                ann.next() // "Bob joined"
                ann.say(startTicTacToe); ann.next(); next()
            }

            assertEquals("Bob lost connection", ann.next().text)
            assertEquals("offline", ann.next().text)

            // Nobody comes back in time: the seat is freed and the game is cancelled.
            assertEquals("Bob left", ann.next().text)
            val cancelled = ann.next()
            assertEquals("game", cancelled.type)
            assertNull(cancelled.state)

            // Too late for Bob now.
            client.webSocket("/ws?name=Bob&room=LATE&token=$bobToken") {
                assertEquals("Session expired", closedWith()?.message)
            }
        }
    }

    @Test
    fun leavingOnPurposeFreesTheSeatImmediately() = testApplication {
        application { module(graceMillis = 60_000) }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=BYE") {
            val ann = this
            ann.handshake()
            client.webSocket("/ws?name=Bob&room=BYE") {
                handshake()
                ann.next() // "Bob joined"
                say(ClientMessage(type = "leave"))
                assertEquals("Left", closedWith()?.message)
            }

            // No "lost connection", no waiting: Bob is simply gone.
            assertEquals("Bob left", ann.next().text)

            // The seat is free again for somebody else.
            client.webSocket("/ws?name=Carol&room=BYE") {
                handshake()
                assertEquals("Carol joined", ann.next().text)
            }
        }
    }

    @Test
    fun chatHistoryIsReplayedIncludingWhatWasMissedWhileAway() = testApplication {
        application { module(graceMillis = 10_000) }
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws?name=Ann&room=CHATTY") {
            val ann = this
            ann.handshake()

            var bobToken = ""
            client.webSocket("/ws?name=Bob&room=CHATTY") {
                bobToken = handshake()
                ann.next() // "Bob joined"
                ann.say(chat("hello"))
                assertEquals("hello", next().text)
            }
            ann.next(); ann.next() // "Bob lost connection" and the presence message
            ann.say(chat("are you still there?"))
            realDelay(300) // give the server time to handle it before Bob comes back

            client.webSocket("/ws?name=Bob&room=CHATTY&token=$bobToken") {
                assertEquals("session", next().type)
                val history = next().history!!
                assertEquals(
                    listOf("Ann joined", "Bob joined", "hello", "Bob lost connection", "are you still there?", "Bob is back"),
                    history.map { it.text },
                )
                assertEquals(listOf("system", "system", "chat", "system", "chat", "system"), history.map { it.type })
                assertEquals("Ann", history[2].from) // who said it is kept
            }
        }
    }

    @Test
    fun aReconnectingPlayerIsToldWhoIsStillOffline() = testApplication {
        application { module(graceMillis = 10_000) }
        val client = createClient { install(WebSockets) }

        var annToken = ""
        client.webSocket("/ws?name=Ann&room=BOTH") {
            val ann = this
            annToken = ann.handshake()
            client.webSocket("/ws?name=Bob&room=BOTH") {
                handshake()
                ann.next() // "Bob joined"
            } // Bob drops
            ann.next(); ann.next() // "Bob lost connection" + presence

            // Ann's own phone reconnects on a new connection (takes over while the old one is still open).
            client.webSocket("/ws?name=Ann&room=BOTH&token=$annToken") {
                assertEquals("session", next().type)
                assertEquals("history", next().type)
                val presence = next()
                assertEquals("presence", presence.type)
                assertEquals("Bob", presence.from)
                assertEquals("offline", presence.text)
            }
        }
        assertNotEquals("", annToken)
    }
}
