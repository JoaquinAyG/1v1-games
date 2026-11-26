package com.onevonegames.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onevonegames.core.ConnectState

private const val COLUMNS = 7
private const val ROWS = 6

val ConnectBoardBlue = Color(0xFF1D4ED8)
val ConnectRed = Color(0xFFE5484D)
val ConnectYellow = Color(0xFFFFC107)
private val EmptySlot = Color(0xFFEAF1FF)

/** The Connect Four board with a status line. Tap any slot in a column to drop a disc there. */
@Composable
fun ConnectFourPanel(
    game: ConnectState,
    myName: String,
    notice: String?,
    onColumn: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val myMark = if (game.redPlayer == myName) "R" else "Y"
    val opponent = if (game.redPlayer == myName) game.yellowPlayer else game.redPlayer
    val playing = game.status == "playing"
    val myTurn = playing && game.turn == myMark

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when {
                game.status == "won" -> if (game.winner == myName) "You won!" else "${game.winner} won"
                game.status == "draw" -> "It's a draw"
                myTurn -> "Your turn"
                else -> "$opponent's turn..."
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(if (myMark == "R") ConnectRed else ConnectYellow))
            Text(
                "You are ${if (myMark == "R") "red" else "yellow"}  vs  $opponent",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ConnectBoardBlue)
                .padding(6.dp),
        ) {
            for (row in 0 until ROWS) {
                Row(Modifier.fillMaxWidth()) {
                    for (column in 0 until COLUMNS) {
                        val cell = row * COLUMNS + column
                        Slot(
                            mark = game.board[cell],
                            winning = cell in game.line,
                            last = cell == game.lastMove,
                            // A column can take a disc while its top slot is still empty.
                            enabled = myTurn && game.board[column] == '.',
                            onClick = { onColumn(column) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        if (!playing) {
            Button(onClick = onPlayAgain) { Text("Play again") }
        }
    }
}

@Composable
private fun Slot(
    mark: Char,
    winning: Boolean,
    last: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    when (mark) {
                        'R' -> ConnectRed
                        'Y' -> ConnectYellow
                        else -> EmptySlot
                    },
                )
                .then(
                    when {
                        winning -> Modifier.border(3.dp, Color.White, CircleShape)
                        last -> Modifier.border(2.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
                        else -> Modifier
                    },
                ),
        )
    }
}
