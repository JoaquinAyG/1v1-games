package com.classicgames.app.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LobbyScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val saved = settings ?: return // still loading the saved profile

    // Editable copies of the saved values; reset whenever the saved values change.
    var name by rememberSaveable(saved) { mutableStateOf(saved.name) }
    var server by rememberSaveable(saved) { mutableStateOf(saved.server) }
    var code by rememberSaveable { mutableStateOf("") }

    val dirty = name.trim() != saved.name || server.trim() != saved.server
    val profileReady = saved.name.isNotBlank() && saved.server.isNotBlank() && !dirty
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
                "Classic Games",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                "1v1 games with your friends",
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
                Button(
                    onClick = { viewModel.saveSettings(name, server) },
                    enabled = dirty && name.isNotBlank() && server.isNotBlank(),
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
                if (profileReady) "Tap a game to host a room, then start it once your friend joins."
                else "Save your name and the server address to play.",
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
