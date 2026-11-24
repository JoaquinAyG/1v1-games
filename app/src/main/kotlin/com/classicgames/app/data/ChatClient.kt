package com.classicgames.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

sealed interface ChatEvent {
    data class Received(val message: ServerMessage) : ChatEvent
    /** The connection ended. [reason] is the server's explanation (e.g. "Room is full"), if any. */
    data class Closed(val reason: String?) : ChatEvent
}

/** Talks to the game server: creates room codes and keeps one room connection open. */
class ChatClient {
    private val http = HttpClient(OkHttp) {
        install(WebSockets) { pingIntervalMillis = 15_000 }
        install(HttpTimeout) { connectTimeoutMillis = 5_000 }
    }

    /** Asks the server for a fresh random room code. */
    suspend fun newRoomCode(server: String): String {
        val response = http.get("http://${normalize(server)}/rooms/new")
        if (!response.status.isSuccess()) error("Server answered ${response.status}")
        return response.bodyAsText().trim()
    }

    /**
     * Joins [room] and runs until the connection closes or the calling coroutine is cancelled.
     * Everything received from [outgoing] is sent to the room.
     */
    suspend fun runSession(
        server: String,
        name: String,
        room: String,
        outgoing: ReceiveChannel<ClientMessage>,
        onEvent: (ChatEvent) -> Unit,
    ) {
        try {
            http.webSocket(
                urlString = "ws://${normalize(server)}/ws",
                request = {
                    url {
                        parameters.append("name", name)
                        parameters.append("room", room)
                    }
                },
            ) {
                val sender = launch {
                    for (message in outgoing) {
                        send(Frame.Text(protocolJson.encodeToString(ClientMessage.serializer(), message)))
                    }
                }
                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        runCatching {
                            protocolJson.decodeFromString(ServerMessage.serializer(), frame.readText())
                        }.onSuccess { onEvent(ChatEvent.Received(it)) }
                    }
                } finally {
                    sender.cancel()
                }
                onEvent(ChatEvent.Closed(closeReason.await()?.message?.takeIf { it.isNotBlank() }))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onEvent(ChatEvent.Closed(e.message ?: "Connection failed"))
        }
    }

    fun close() = http.close()

    /** "192.168.1.20" -> "192.168.1.20:8080"; also tolerates a pasted http:// or ws:// prefix. */
    private fun normalize(server: String): String {
        val s = server.trim().removePrefix("http://").removePrefix("ws://").trimEnd('/')
        return if (':' in s) s else "$s:8080"
    }
}
