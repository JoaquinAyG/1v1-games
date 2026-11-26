package com.onevonegames.app.data

import com.onevonegames.core.ClientMessage
import com.onevonegames.core.ServerMessage
import com.onevonegames.core.protocolJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

/** How one connection to a room ended. */
sealed interface SessionEnd {
    /** We said goodbye on purpose and the server confirmed. */
    data object Left : SessionEnd

    /** The server refused us (bad room or name, full room, expired seat): trying again won't help. */
    data class Rejected(val reason: String) : SessionEnd

    /** The connection broke or could not be made: worth trying again. */
    data class Dropped(val reason: String?) : SessionEnd
}

/** Talks to the game server: creates room codes and keeps one room connection open. */
class ChatClient {
    private val http = HttpClient(OkHttp) {
        // Pings let us notice a dead connection (a lost Wi-Fi) within seconds instead of minutes.
        install(WebSockets) { pingIntervalMillis = 10_000 }
        install(HttpTimeout) { connectTimeoutMillis = 5_000 }
    }

    /** Asks the server for a fresh random room code. */
    suspend fun newRoomCode(server: String): String {
        val response = http.get("http://${normalize(server)}/rooms/new")
        if (!response.status.isSuccess()) error("Server answered ${response.status}")
        return response.bodyAsText().trim()
    }

    /**
     * Connects to [room] once and runs until that connection ends or the calling coroutine is cancelled.
     * Pass the seat [token] to take an existing seat back after a dropped connection.
     * Everything received from [outgoing] is sent to the room; everything the server sends goes to [onMessage].
     */
    suspend fun runSession(
        server: String,
        name: String,
        room: String,
        token: String?,
        outgoing: ReceiveChannel<ClientMessage>,
        onMessage: (ServerMessage) -> Unit,
    ): SessionEnd {
        var end: SessionEnd = SessionEnd.Dropped(null)
        try {
            http.webSocket(
                urlString = "ws://${normalize(server)}/ws",
                request = {
                    url {
                        parameters.append("name", name)
                        parameters.append("room", room)
                        if (token != null) parameters.append("token", token)
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
                        }.onSuccess(onMessage)
                    }
                } finally {
                    sender.cancel()
                }
                val reason = runCatching { closeReason.await() }.getOrNull()
                end = when {
                    reason?.code == CloseReason.Codes.VIOLATED_POLICY.code ->
                        SessionEnd.Rejected(reason.message.ifBlank { "The server refused the connection" })
                    reason?.code == CloseReason.Codes.NORMAL.code && reason.message == "Left" -> SessionEnd.Left
                    else -> SessionEnd.Dropped(reason?.message?.takeIf { it.isNotBlank() })
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            end = SessionEnd.Dropped(e.message ?: "Connection failed")
        }
        return end
    }

    fun close() = http.close()

    /** "192.168.1.20" -> "192.168.1.20:8080"; also tolerates a pasted http:// or ws:// prefix. */
    private fun normalize(server: String): String {
        val s = server.trim().removePrefix("http://").removePrefix("ws://").trimEnd('/')
        return if (':' in s) s else "$s:8080"
    }
}
