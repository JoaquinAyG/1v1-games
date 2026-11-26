package com.onevonegames.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onevonegames.core.GameState

/** The tic-tac-toe board with a status line. The server decides everything; this only shows it and reports taps. */
@Composable
fun TicTacToePanel(
    game: GameState,
    myName: String,
    notice: String?,
    onCell: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val myMark = if (game.xPlayer == myName) "X" else "O"
    val opponent = if (game.xPlayer == myName) game.oPlayer else game.xPlayer
    val playing = game.status == "playing"
    val myTurn = playing && game.turn == myMark

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when {
                game.status == "won" -> if (game.winner == myName) "You won!" else "${game.winner} won"
                game.status == "draw" -> "It's a draw"
                myTurn -> "Your turn ($myMark)"
                else -> "$opponent's turn..."
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text("You are $myMark  vs  $opponent", style = MaterialTheme.typography.bodySmall)

        Column {
            for (row in 0 until 3) {
                Row {
                    for (col in 0 until 3) {
                        val index = row * 3 + col
                        Cell(
                            mark = game.board[index],
                            highlighted = index in game.line,
                            enabled = myTurn && game.board[index] == '.',
                            onClick = { onCell(index) },
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
private fun Cell(mark: Char, highlighted: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (mark != '.') {
            Text(
                text = mark.toString(),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = if (mark == 'X') MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}
