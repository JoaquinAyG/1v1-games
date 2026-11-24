package com.classicgames.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

/**
 * Calls [onAvailable] whenever the phone gets a network (Wi-Fi came back, switched to mobile data, ...),
 * so a waiting reconnect can try again immediately instead of sleeping out its back-off.
 */
class NetworkWatcher(context: Context, private val onAvailable: () -> Unit) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onAvailable()
    }

    fun start() {
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
    }

    fun stop() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }
}
