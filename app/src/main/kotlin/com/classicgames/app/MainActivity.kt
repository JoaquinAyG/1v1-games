package com.classicgames.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
                    AppRoot(viewModel)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(viewModel: AppViewModel) {
    val room by viewModel.room.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Simple two-screen app for now: lobby, or inside a room.
    val current = room
    if (current == null) {
        LobbyScreen(viewModel)
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
            onLeave = viewModel::leaveRoom,
        )
    }
}
