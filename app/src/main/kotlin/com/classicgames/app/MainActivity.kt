package com.classicgames.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classicgames.app.ui.AppBackground
import com.classicgames.app.ui.AppViewModel
import com.classicgames.app.ui.ChatScreen
import com.classicgames.app.ui.ClassicGamesTheme
import com.classicgames.app.ui.LobbyScreen

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the server runs either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The gradient is dark enough that status/navigation bar icons must be light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            ClassicGamesTheme {
                AppBackground {
                    AppRoot(viewModel, onRequestNotifications = ::askForNotificationPermission)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Back on screen: if the connection broke while we were away, don't wait for the next retry.
        viewModel.onForeground()
    }

    /** Android 13+ asks before an app may show notifications; hosting the server shows one while it runs. */
    private fun askForNotificationPermission() {
        val needed = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needed) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun AppRoot(viewModel: AppViewModel, onRequestNotifications: () -> Unit) {
    val room by viewModel.room.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Simple two-screen app for now: lobby, or inside a room.
    val current = room
    if (current == null) {
        LobbyScreen(viewModel, onRequestNotifications)
    } else {
        BackHandler { viewModel.leaveRoom() }
        ChatScreen(
            room = current,
            myName = settings?.name.orEmpty(),
            onSend = viewModel::sendMessage,
            onStartGame = viewModel::startGame,
            onCell = viewModel::playCell,
            onPlaceFleet = viewModel::placeFleet,
            onMovePiece = viewModel::movePiece,
            onSetCode = viewModel::setSecretCode,
            onGuessCode = viewModel::guessCode,
            onSwap = viewModel::swapHex,
            onLeave = viewModel::leaveRoom,
        )
    }
}
