package server

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlin.time.Duration.Companion.seconds

fun main() {
    // 0.0.0.0 so other devices on the LAN can reach it.
    embeddedServer(CIO, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
    }

    val hub = Hub()

    routing {
        // Tiny browser test page at http://<server-ip>:8080/
        staticResources("/", "web")

        // Host flow: GET /rooms/new -> a fresh random code (plain text, e.g. "K7QM").
        // Then the host connects to /ws with that code and shares it with the friend.
        get("/rooms/new") { call.respondText(hub.newRoomCode()) }

        // ws://<ip>:8080/ws?name=Alice&room=ABCD  (room is created on first join, max 2 players)
        webSocket("/ws") {
            val name = call.request.queryParameters["name"]?.trim().orEmpty()
            val room = call.request.queryParameters["room"]?.trim()?.uppercase().orEmpty()
            if (name.isEmpty() || name.length > 24 || room.isEmpty() || room.length > 16) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Provide ?name= (1-24 chars) and ?room= (1-16 chars)"))
                return@webSocket
            }
            when (hub.join(room, name, this)) {
                JoinResult.OK -> {}
                JoinResult.NAME_TAKEN -> {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Name already in use in this room"))
                    return@webSocket
                }
                JoinResult.ROOM_FULL -> {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Room is full"))
                    return@webSocket
                }
            }

            /** Game updates go to both players (each with their own view); rejections only to the player who asked. */
            suspend fun respond(outcome: GameOutcome) = when (outcome) {
                is GameOutcome.Update -> hub.deliver(room, outcome.messages)
                is GameOutcome.Rejected -> send(ServerMessage("error", "server", outcome.reason).toFrame())
            }

            try {
                send(ServerMessage("system", "server",
                    "Joined room $room. Players: ${hub.online(room).joinToString()}").toFrame())
                hub.broadcast(room, ServerMessage("system", "server", "$name joined"), except = name)

                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val raw = frame.readText()
                    val msg = runCatching { protocolJson.decodeFromString(ClientMessage.serializer(), raw) }
                        .getOrElse { ClientMessage(text = raw) }
                    when (msg.type) {
                        "chat" -> if (msg.text.isNotBlank()) {
                            hub.broadcast(room, ServerMessage("chat", name, msg.text), except = name)
                        }
                        "startGame" -> respond(hub.startGame(room, msg.game.orEmpty(), name))
                        "move", "place" -> respond(hub.act(room, name, msg))
                        else -> send(ServerMessage("error", "server", "Unknown message type: ${msg.type}").toFrame())
                    }
                }
            } catch (e: Throwable) {
                // Without this, a bug in message handling would just silently drop the player.
                if (e !is kotlinx.coroutines.CancellationException && e !is ClosedReceiveChannelException) {
                    call.application.log.error("WebSocket handler for $name in room $room failed", e)
                }
                throw e
            } finally {
                val cancelledGame = hub.leave(room, name, this)
                hub.broadcast(room, ServerMessage("system", "server", "$name left"))
                if (cancelledGame) {
                    hub.broadcast(room, ServerMessage("game", "server", "Game ended because $name left"))
                }
            }
        }
    }
}
