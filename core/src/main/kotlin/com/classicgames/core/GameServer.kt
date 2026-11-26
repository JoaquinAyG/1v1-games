package com.classicgames.core

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.*
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

/** Longest chat message passed on to the room; anything longer is cut. */
const val MAX_CHAT_LENGTH = 500

/** Largest frame a client may send. Real messages are far smaller; a bigger frame closes the connection. */
private const val MAX_FRAME_BYTES = 64 * 1024L

/**
 * Creates (but does not start) the game server. Call `.start(wait = true)` on a PC, or `.start(wait = false)`
 * from the Android service. [extra] can add more routes, e.g. the PC launcher's test page.
 * [graceMillis] is how long a dropped player's seat is held (see [Hub]).
 */
fun createGameServer(
    port: Int = 8080,
    host: String = "0.0.0.0",
    graceMillis: Long = DEFAULT_GRACE_MILLIS,
    extra: Application.() -> Unit = {},
) = embeddedServer(CIO, port = port, host = host) {
    module(graceMillis)
    extra()
}

fun Application.module(graceMillis: Long = DEFAULT_GRACE_MILLIS) {
    install(WebSockets) {
        // Dead connections (a phone that lost Wi-Fi) are noticed within about 30 seconds.
        pingPeriod = 10.seconds
        timeout = 20.seconds
        maxFrameSize = MAX_FRAME_BYTES
    }

    val hub = Hub(this, graceMillis)

    fun system(text: String) = ServerMessage("system", "server", text)

    routing {
        // Host flow: GET /rooms/new -> a fresh random code (plain text, e.g. "K7QM").
        // Then the host connects to /ws with that code and shares it with the friend.
        get("/rooms/new") { call.respondText(hub.newRoomCode()) }

        // ws://<ip>:8080/ws?name=Alice&room=ABCD            joins (room is created on first join, max 2 players)
        // ws://<ip>:8080/ws?name=Alice&room=ABCD&token=...  takes the same seat back after a dropped connection
        webSocket("/ws") {
            val name = call.request.queryParameters["name"]?.trim().orEmpty()
            val room = call.request.queryParameters["room"]?.trim()?.uppercase().orEmpty()
            val token = call.request.queryParameters["token"]?.trim()?.takeIf { it.isNotEmpty() }
            if (name.isEmpty() || name.length > 24 || room.isEmpty() || room.length > 16) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Provide ?name= (1-24 chars) and ?room= (1-16 chars)"))
                return@webSocket
            }

            val joined = when (val outcome = hub.join(room, name, token, this)) {
                is JoinOutcome.Rejected -> {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, outcome.reason))
                    return@webSocket
                }
                is JoinOutcome.Joined -> outcome
            }
            // Taking a seat over while the old connection is still open (not noticed as dead yet): drop the old one.
            joined.replaced?.let { old ->
                runCatching { old.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Replaced by a new connection")) }
            }

            /** Game updates go to both players (each with their own view); rejections only to the player who asked. */
            suspend fun respond(outcome: GameOutcome) = when (outcome) {
                is GameOutcome.Update -> hub.deliver(room, outcome.messages)
                is GameOutcome.Rejected -> send(ServerMessage("error", "server", outcome.reason).toFrame())
            }

            var leftOnPurpose = false
            try {
                send(ServerMessage("session", "server", "", token = joined.token).toFrame())
                if (joined.resumed) {
                    hub.say(room, system("$name is back"), except = name)
                    hub.announcePresence(room, name, online = true)
                } else {
                    hub.say(room, system("$name joined"), except = name)
                }
                // Catch up: recent chat, who is offline, and the game as it stands.
                hub.snapshotFor(room, name)?.let { snapshot ->
                    send(ServerMessage("history", "server", "", history = snapshot.history).toFrame())
                    snapshot.offlinePeers.forEach { send(ServerMessage("presence", it, "offline").toFrame()) }
                    snapshot.gameMessage?.let { send(it.toFrame()) }
                }

                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val raw = frame.readText()
                    val msg = runCatching { protocolJson.decodeFromString(ClientMessage.serializer(), raw) }
                        .getOrElse { ClientMessage(text = raw) }
                    when (msg.type) {
                        "chat" -> if (msg.text.isNotBlank()) {
                            hub.say(room, ServerMessage("chat", name, capChat(msg.text)), except = name)
                        }
                        "startGame" -> respond(hub.startGame(room, msg.game.orEmpty(), name))
                        "move", "place", "secret", "swap" -> respond(hub.act(room, name, msg))
                        "leave" -> {
                            leftOnPurpose = true
                            break
                        }
                        else -> send(ServerMessage("error", "server", "Unknown message type: ${msg.type}").toFrame())
                    }
                }
            } catch (e: Throwable) {
                // Without this, a bug in message handling would just silently drop the player.
                if (e !is CancellationException && e !is ClosedReceiveChannelException) {
                    call.application.log.error("WebSocket handler for $name in room $room failed", e)
                }
                throw e
            } finally {
                // Runs even when the connection was cancelled underneath us, so the bookkeeping is always done.
                withContext(NonCancellable) {
                    if (leftOnPurpose) {
                        hub.leave(room, name, this@webSocket)
                        runCatching { this@webSocket.close(CloseReason(CloseReason.Codes.NORMAL, "Left")) }
                    } else if (hub.disconnect(room, name, this@webSocket)) {
                        // The connection just dropped: keep the seat and the game, and let the other player know.
                        hub.say(room, system("$name lost connection"))
                        hub.announcePresence(room, name, online = false)
                    }
                }
            }
        }
    }
}

/** [text] cut to [MAX_CHAT_LENGTH] characters, without splitting an emoji's surrogate pair. */
private fun capChat(text: String): String {
    if (text.length <= MAX_CHAT_LENGTH) return text
    val end = if (text[MAX_CHAT_LENGTH - 1].isHighSurrogate()) MAX_CHAT_LENGTH - 1 else MAX_CHAT_LENGTH
    return text.substring(0, end)
}
