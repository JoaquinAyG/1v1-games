package com.onevonegames.app.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.onevonegames.app.MainActivity
import com.onevonegames.app.R
import com.onevonegames.core.awaitFreePort
import com.onevonegames.core.createGameServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.BindException

/**
 * Runs the game server inside the phone, as a foreground service so Android keeps it alive while the screen is
 * off or the app is in the background. Friends on the same Wi-Fi connect to this phone's address (see [PhoneServer]).
 */
class ServerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startRequested = false

    /**
     * Guards [destroyed] and the resources below. The start coroutine sets them up on a background thread while
     * [onDestroy] may run at any moment on the main thread, even while the engine is still starting.
     */
    private val lock = Any()
    private var destroyed = false
    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "Game server", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Shown while this phone hosts the game server for friends."
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Must happen right away after startForegroundService().
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(emptyList()), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (!startRequested) {
            startRequested = true
            startServer()
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        PhoneServer.setState(PhoneServerState.Starting)
        scope.launch {
            try {
                // start(wait = false) can't report a busy port: it would crash the app instead.
                if (!awaitFreePort(PhoneServer.PORT)) throw BindException()
                val started =createGameServer(port = PhoneServer.PORT, host = "0.0.0.0").start(wait = false)
                val alive = synchronized(lock) {
                    if (!destroyed) {
                        server = started
                        holdWifiAwake()
                        watchNetwork()
                    }
                    !destroyed
                }
                if (!alive) {
                    // onDestroy ran while start() was busy and had no engine to stop yet: free the port here.
                    runCatching { started.stop(300, 1_000) }
                    return@launch
                }
                publishAddresses()
            } catch (e: Throwable) {
                if (synchronized(lock) { destroyed }) return@launch
                val reason = if (e is BindException || e.cause is BindException) {
                    "Port ${PhoneServer.PORT} is already in use on this phone."
                } else {
                    e.message ?: e::class.simpleName ?: "unknown error"
                }
                PhoneServer.setState(PhoneServerState.Failed(reason))
                stopSelf()
            }
        }
    }

    /** Publishes the current addresses to the app and to the notification. */
    private fun publishAddresses() {
        val addresses = PhoneServer.localAddresses()
        synchronized(lock) {
            // A network callback can still fire after onDestroy; it must not bring back Running or the notification.
            if (destroyed) return
            PhoneServer.setState(PhoneServerState.Running(PhoneServer.PORT, addresses))
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(addresses))
        }
    }

    /**
     * Wi-Fi can change while hosting (reconnects, a different network); keep the shown address current.
     * Call under [lock].
     */
    private fun watchNetwork() {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publishAddresses()
            override fun onLost(network: Network) = publishAddresses()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = publishAddresses()
        }
        networkCallback = callback
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
    }

    /**
     * Stops Wi-Fi from going to sleep while the screen is off, so friends can still reach this phone.
     * Call under [lock].
     */
    @Suppress("DEPRECATION")
    private fun holdWifiAwake() {
        runCatching {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifi.createWifiLock(mode, "1v1Games:server").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun notification(addresses: List<String>): Notification {
        val text = if (addresses.isEmpty()) {
            "Waiting for a Wi-Fi network..."
        } else {
            "Friends connect to ${addresses.first()}:${PhoneServer.PORT}"
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_server)
            .setContentTitle("1v1 Games server is running")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        // From here on the start coroutine sets nothing up any more. If the engine is still starting, server is null
        // below and the coroutine stops the engine itself once start() returns.
        synchronized(lock) { destroyed = true }
        networkCallback?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        wifiLock?.let { runCatching { if (it.isHeld) it.release() } }
        val running = server
        server = null
        // Stopping waits for open connections to wind down, so keep that off the main thread.
        if (running != null) {
            CoroutineScope(Dispatchers.IO).launch { runCatching { running.stop(300, 1_000) } }
        }
        scope.cancel()
        if (PhoneServer.state.value !is PhoneServerState.Failed) PhoneServer.setState(PhoneServerState.Stopped)
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "server"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.onevonegames.app.STOP_SERVER"
    }
}
