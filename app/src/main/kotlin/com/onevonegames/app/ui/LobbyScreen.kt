package com.onevonegames.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onevonegames.app.server.PhoneServerState

@Composable
fun LobbyScreen(viewModel: AppViewModel, onRequestNotifications: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val phoneServer by viewModel.phoneServer.collectAsStateWithLifecycle()
    val saved = settings ?: return // still loading the saved profile

    // Editable copies of the saved values. Each is reset only when its own saved value changes, so that flipping the
    // hosting switch doesn't throw away what has been typed but not saved yet.
    var name by rememberSaveable(saved.name) { mutableStateOf(saved.name) }
    var server by rememberSaveable(saved.server) { mutableStateOf(saved.server) }
    var code by rememberSaveable { mutableStateOf("") }

    // With the server on this phone there is no address to type; we just wait for the server to be up.
    val serverReady = if (saved.hostOnPhone) phoneServer is PhoneServerState.Running else saved.server.isNotBlank()
    val dirty = name.trim() != saved.name || (!saved.hostOnPhone && server.trim() != saved.server)
    val profileReady = saved.name.isNotBlank() && serverReady && !dirty
    val canPlay = profileReady && !viewModel.busy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            Text(
                "1v1 Games",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                "Games for two: against each other or together",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }

        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Your name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Host the server on this phone", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "No PC needed: friends on the same Wi-Fi connect to this phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = saved.hostOnPhone,
                        onCheckedChange = { on ->
                            if (on) onRequestNotifications() // the server shows a notification while it runs
                            viewModel.setHostOnPhone(on)
                        },
                    )
                }

                if (saved.hostOnPhone) {
                    PhoneServerStatus(phoneServer)
                } else {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = { Text("Server address") },
                        placeholder = { Text("192.168.1.20:8080") },
                        supportingText = { Text("The PC or phone running the server. Port 8080 if omitted.") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Button(
                    onClick = { viewModel.saveSettings(name, server) },
                    enabled = dirty && name.isNotBlank() && (saved.hostOnPhone || server.isNotBlank()),
                ) { Text(if (dirty || saved.name.isBlank()) "Save" else "Saved") }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Games",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            GameGrid(
                games = GAMES,
                enabled = canPlay,
                onGameClick = { viewModel.hostRoom(it.id) },
            )
            Text(
                when {
                    profileReady -> "Tap a game to host a room, then start it once your friend joins."
                    saved.hostOnPhone && !serverReady && saved.name.isNotBlank() -> "Starting the server on this phone..."
                    saved.hostOnPhone -> "Save your name to play."
                    else -> "Save your name and the server address to play."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }

        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Play", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Button(
                    onClick = { viewModel.hostRoom() },
                    enabled = canPlay,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Host a room") }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().take(16) },
                        label = { Text("Room code") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { viewModel.joinRoom(code) },
                        enabled = canPlay && code.isNotBlank(),
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Join") }
                }

                viewModel.lobbyError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** What the server on this phone is doing, and the address friends have to type to reach it. */
@Composable
private fun PhoneServerStatus(state: PhoneServerState) {
    val context = LocalContext.current
    var copied by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (state) {
            PhoneServerState.Stopped, PhoneServerState.Starting ->
                Text("Starting the server...", style = MaterialTheme.typography.bodyMedium)

            is PhoneServerState.Running -> {
                if (state.addresses.isEmpty()) {
                    Text(
                        "The server is running, but this phone is not on a Wi-Fi network. Connect to Wi-Fi " +
                            "(or turn on this phone's hotspot) so your friends can reach it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    val address = "${state.addresses.first()}:${state.port}"
                    Text("Server running. Friends type this address in their app:", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            address,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("Server address", address))
                            copied = true
                        }) { Text(if (copied) "Copied" else "Copy") }
                    }
                    if (state.addresses.size > 1) {
                        Text(
                            "Other addresses of this phone: " + state.addresses.drop(1).joinToString { "$it:${state.port}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is PhoneServerState.Failed ->
                Text(
                    "Couldn't start the server: ${state.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
        }
    }
}
