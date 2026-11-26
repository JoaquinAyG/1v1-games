package com.onevonegames.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FreePortTest {
    private fun occupy() = ServerSocket().apply { bind(InetSocketAddress("0.0.0.0", 0)) }

    @Test
    fun busyPortIsReported() = runBlocking {
        occupy().use { assertFalse(awaitFreePort(it.localPort, waitMillis = 200)) }
    }

    @Test
    fun releasedPortIsFree() = runBlocking {
        val port = occupy().use { it.localPort }
        assertTrue(awaitFreePort(port, waitMillis = 0))
    }

    @Test
    fun waitsForThePortToBeReleased() = runBlocking {
        val socket = occupy()
        launch {
            delay(300)
            socket.close()
        }
        assertTrue(awaitFreePort(socket.localPort, waitMillis = 3_000))
    }
}
