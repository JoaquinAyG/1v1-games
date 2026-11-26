package com.onevonegames.server

import com.onevonegames.core.createGameServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing

/** PC launcher: runs the game server on port 8080, plus a tiny browser chat page at http://localhost:8080/. */
fun main() {
    createGameServer(port = 8080, host = "0.0.0.0") {
        routing { staticResources("/", "web") }
    }.start(wait = true)
}
