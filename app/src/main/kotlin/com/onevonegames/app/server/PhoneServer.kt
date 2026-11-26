package com.onevonegames.app.server

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

/** What the game server running inside this phone is doing. */
sealed interface PhoneServerState {
    data object Stopped : PhoneServerState
    data object Starting : PhoneServerState

    /** Listening. [addresses] are this phone's addresses on the local network (empty without Wi-Fi). */
    data class Running(val port: Int, val addresses: List<String>) : PhoneServerState

    data class Failed(val message: String) : PhoneServerState
}

/** Start/stop switch and live state of the game server that runs inside this phone (see [ServerService]). */
object PhoneServer {
    const val PORT = 8080

    private val _state = MutableStateFlow<PhoneServerState>(PhoneServerState.Stopped)
    val state: StateFlow<PhoneServerState> = _state.asStateFlow()

    fun start(context: Context) {
        if (_state.value is PhoneServerState.Running || _state.value is PhoneServerState.Starting) return
        _state.value = PhoneServerState.Starting
        ContextCompat.startForegroundService(context, Intent(context, ServerService::class.java))
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, ServerService::class.java))
        if (_state.value !is PhoneServerState.Failed) _state.value = PhoneServerState.Stopped
    }

    internal fun setState(state: PhoneServerState) {
        _state.value = state
    }

    /**
     * This phone's IPv4 addresses on a local network (Wi-Fi, or its own hotspot), best guess first.
     * These are what a friend types into the app to reach this phone.
     */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { network ->
                network.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { it.isSiteLocalAddress } // 192.168.x.x, 10.x.x.x, 172.16-31.x.x
                    .map { network.name to it.hostAddress.orEmpty() }
            }
            .sortedBy { (name, _) ->
                when {
                    name.startsWith("wlan") -> 0 // Wi-Fi first
                    name.startsWith("ap") || name.startsWith("swlan") -> 1 // this phone's own hotspot
                    else -> 2
                }
            }
            .map { it.second }
            .filter { it.isNotEmpty() }
            .distinct()
    }.getOrDefault(emptyList())
}
