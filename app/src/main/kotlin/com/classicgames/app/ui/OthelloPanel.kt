package com.classicgames.app.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classicgames.core.OthelloState

private const val SIZE = 8

val OthelloBoardGreen = Color(0xFF2E7D32)
val OthelloSquareGreen = Color(0xFF388E3C)
val OthelloBlack = Color(0xFF212121)
val OthelloWhite = Color(0xFFFAFAFA)

/** The Othello board with scores. On your turn, the cells you may play show a dot; tap one to place a disc. */
@Composable
fun OthelloPanel(
    game: OthelloState,
    myName: String,
    notice: String?,
    onCell: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iAmBlack = game.blackPlayer == myName
    val opponent = if (iAmBlack) game.whitePlayer else game.blackPlayer
    val playing = game.status == "playing"
    val myTurn = playing && game.turn == (if (iAmBlack) "B" else "W")

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
        // Always present (blank when nobody passed) so the board doesn't jump down when a pass is announced.
        Text(
            game.passed?.let { if (it == myName) "You had no moves and passed" else "$it had no moves and passed" } ?: " ",
            style = MaterialTheme.typography.bodySmall,
        )

        // Scores: black on the left, white on the right; "(you)" marks which side is yours.
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Score(OthelloBlack, game.blackPlayer, game.blackCount, mine = iAmBlack)
            Score(OthelloWhite, game.whitePlayer, game.whiteCount, mine = !iAmBlack)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(OthelloBoardGreen)
                .padding(4.dp),
        ) {
            for (row in 0 until SIZE) {
                Row(Modifier.fillMaxWidth()) {
                    for (column in 0 until SIZE) {
                        val cell = row * SIZE + column
                        val playable = myTurn && cell in game.legalMoves
                        Square(
                            mark = game.board[cell],
                            hint = playable,
                            last = cell == game.lastMove,
                            onClick = { onCell(cell) },
                            enabled = playable,
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
private fun Score(color: Color, name: String, count: Int, mine: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.Gray, CircleShape),
        )
        Text(
            "${if (mine) "You" else name}  $count",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Square(
    mark: Char,
    hint: Boolean,
    last: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .background(OthelloSquareGreen)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            mark != '.' -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(if (mark == 'B') OthelloBlack else OthelloWhite)
                    .border(
                        if (last) 2.dp else 1.dp,
                        if (last) Color(0xFFFFC107) else Color.Black.copy(alpha = 0.25f),
                        CircleShape,
                    ),
            )
            hint -> Box(
                Modifier
                    .fillMaxSize(0.3f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.55f)),
            )
        }
    }
}
