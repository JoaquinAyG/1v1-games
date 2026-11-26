package com.classicgames.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ChatScreen(
    room: RoomUi,
    myName: String,
    onSend: (String) -> Unit,
    onStartGame: (String) -> Unit,
    onCell: (Int) -> Unit,
    onPlaceFleet: (List<List<Int>>) -> Unit,
    onMovePiece: (from: Int, to: Int) -> Unit,
    onSetCode: (List<Int>) -> Unit,
    onGuessCode: (List<Int>) -> Unit,
    onSwap: () -> Unit,
    onGivePiece: (Int) -> Unit,
    onPlayCard: (Int) -> Unit,
    onSwapSeven: () -> Unit,
    onMoveWalker: (String) -> Unit,
    onHint: (String) -> Unit,
    onHanabi: (action: String, card: Int?, color: Int?, number: Int?) -> Unit,
    onLeave: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val connected = room.state == ConnectionState.Connected

    LaunchedEffect(room.lines.size) {
        if (room.lines.isNotEmpty()) listState.animateScrollToItem(room.lines.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
    ) {
        // Header sits directly on the gradient, so it uses white text.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onLeave,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) { Text("Leave", fontWeight = FontWeight.SemiBold) }
            Column(Modifier.weight(1f)) {
                Text(
                    "Room ${room.code}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    when (room.state) {
                        ConnectionState.Connecting -> "Connecting..."
                        ConnectionState.Connected -> "Share this code with your friend"
                        ConnectionState.Reconnecting -> "Connection lost. Reconnecting..."
                        ConnectionState.Closed -> "Disconnected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }

        if (room.state == ConnectionState.Closed) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = CardShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    room.closeReason ?: "Connection lost",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // The other player's connection dropped: the server holds their seat and the game for a while.
        room.opponentOffline?.takeIf { connected }?.let { name ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = CardShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "$name lost their connection. Waiting for them to come back...",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        val tictactoe = room.game
        val battle = room.battle
        val connect = room.connect
        val othello = room.othello
        val checkers = room.checkers
        val dots = room.dots
        val oware = room.oware
        val mastermind = room.mastermind
        val hex = room.hex
        val memory = room.memory
        val quarto = room.quarto
        val brisca = room.brisca
        val maze = room.maze
        val hanabi = room.hanabi
        // Battleship needs lots of room, so its card takes what it needs and the chat shrinks to a short strip.
        val battleActive = battle != null
        // Mastermind grows with every guess, so its card stops at about 60% of the window and scrolls inside, and
        // the chat keeps the rest.
        val mastermindMaxHeight =
            with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.6f).toDp() }

        // Keep showing the game while reconnecting: it is still there, we just can't play until we are back.
        if (connected || room.state == ConnectionState.Reconnecting) {
            AppCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .then(
                        when {
                            battleActive -> Modifier.weight(1f, fill = false)
                            mastermind != null -> Modifier.heightIn(max = mastermindMaxHeight)
                            else -> Modifier
                        },
                    ),
            ) {
                when {
                    tictactoe != null -> TicTacToePanel(
                        game = tictactoe,
                        myName = myName,
                        notice = room.notice,
                        onCell = onCell,
                        onPlayAgain = { onStartGame("tictactoe") },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    battle != null -> BattleshipPanel(
                        battle = battle,
                        notice = room.notice,
                        onPlaceFleet = onPlaceFleet,
                        onFire = onCell,
                        onPlayAgain = { onStartGame("battleship") },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    connect != null -> ConnectFourPanel(
                        game = connect,
                        myName = myName,
                        notice = room.notice,
                        onColumn = onCell,
                        onPlayAgain = { onStartGame("connectfour") },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    othello != null -> OthelloPanel(
                        game = othello,
                        myName = myName,
                        notice = room.notice,
                        onCell = onCell,
                        onPlayAgain = { onStartGame("othello") },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    checkers != null -> CheckersPanel(
                        game = checkers,
                        myName = myName,
                        notice = room.notice,
                        onMove = onMovePiece,
                        onPlayAgain = { onStartGame("checkers") },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    dots != null -> DotsAndBoxesPanel(
                        game = dots,
                        myName = myName,
                        notice = room.notice,
                        onLine = onCell,
                        onPlayAgain = { onStartGame("dotsandboxes") },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    oware != null -> OwarePanel(
                        game = oware,
                        myName = myName,
                        notice = room.notice,
                        onPit = onCell,
                        onPlayAgain = { onStartGame("oware") },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    mastermind != null -> MastermindPanel(
                        game = mastermind,
                        notice = room.notice,
                        onSetCode = onSetCode,
                        onGuess = onGuessCode,
                        onPlayAgain = { onStartGame("mastermind") },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    hex != null -> HexPanel(
                        game = hex,
                        myName = myName,
                        notice = room.notice,
                        onCell = onCell,
                        onSwap = onSwap,
                        onPlayAgain = { onStartGame("hex") },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    memory != null -> MemoryPanel(
                        game = memory,
                        myName = myName,
                        notice = room.notice,
                        onCard = onCell,
                        onPlayAgain = { onStartGame("memory") },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    quarto != null -> QuartoPanel(
                        game = quarto,
                        myName = myName,
                        notice = room.notice,
                        onCell = onCell,
                        onGive = onGivePiece,
                        onPlayAgain = { onStartGame("quarto") },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    brisca != null -> BriscaPanel(
                        game = brisca,
                        notice = room.notice,
                        onCard = onPlayCard,
                        onSwapSeven = onSwapSeven,
                        onPlayAgain = { onStartGame("brisca") },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    maze != null -> MazePanel(
                        game = maze,
                        notice = room.notice,
                        onMove = onMoveWalker,
                        onHint = onHint,
                        onPlayAgain = { onStartGame("maze") },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    hanabi != null -> HanabiPanel(
                        game = hanabi,
                        notice = room.notice,
                        onAction = onHanabi,
                        onPlayAgain = { onStartGame("hanabi") },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    else -> GamePicker(room.preferredGame, room.notice, onStartGame)
                }
            }
        }

        // Chat history on a white card so it stays readable on the gradient.
        Surface(
            shape = CardShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = (if (battleActive) Modifier.height(110.dp) else Modifier.weight(1f))
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(room.lines) { line -> MessageRow(line) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message") },
                enabled = connected,
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.6f),
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onSend(draft); draft = "" },
                enabled = connected && draft.isNotBlank(),
                // Sits directly on the gradient, so give it colors that stay visible when disabled too.
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = Color.White.copy(alpha = 0.3f),
                    disabledContentColor = Color.White.copy(alpha = 0.85f),
                ),
            ) { Text("Send") }
        }
    }
}

/**
 * Shown when no game is running: the game hosted from the lobby (or the first one) as the main button, then the
 * others. They wrap onto as few lines as fit, so even with a dozen games the chat still keeps most of the screen.
 */
@Composable
private fun GamePicker(preferred: String?, notice: String?, onStart: (String) -> Unit) {
    val playable = GAMES.filter { it.icon != null }.sortedByDescending { it.id == preferred }
    val main = playable.first()
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Pick a game", style = MaterialTheme.typography.titleSmall)
        Button(onClick = { onStart(main.id) }) { Text("Play ${main.title}") }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (game in playable.drop(1)) {
                OutlinedButton(
                    onClick = { onStart(game.id) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(game.title) }
            }
        }
        notice?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MessageRow(line: ChatLine) {
    when {
        line.system -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                line.text,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> Box(
            Modifier.fillMaxWidth(),
            contentAlignment = if (line.mine) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (line.mine) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (!line.mine) Text(line.from, style = MaterialTheme.typography.labelSmall)
                    Text(line.text)
                }
            }
        }
    }
}
