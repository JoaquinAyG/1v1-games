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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classicgames.app.data.CheckersState
import kotlin.math.abs

private const val SIZE = 8

val CheckersDarkSquare = Color(0xFF6D4C41)
val CheckersLightSquare = Color(0xFFF1D9B0)
val CheckersRed = Color(0xFFD32F2F)
val CheckersBlack = Color(0xFF263238)
val CheckersGold = Color(0xFFFFC107)

/**
 * The Checkers board. Each player sees their own pieces at the bottom (the second player's view is rotated).
 * Tap one of your pieces that can move, then tap a highlighted square. During a multi-jump the jumping piece
 * is selected for you and you keep tapping the next landing square.
 */
@Composable
fun CheckersPanel(
    game: CheckersState,
    myName: String,
    notice: String?,
    onMove: (from: Int, to: Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iAmRed = game.redPlayer == myName
    val opponent = if (iAmRed) game.blackPlayer else game.redPlayer
    val playing = game.status == "playing"
    val myTurn = playing && game.turn == (if (iAmRed) "R" else "B")

    var selected by remember { mutableIntStateOf(-1) }
    // Any new position from the server clears a stale selection.
    LaunchedEffect(game.board, game.turn) { selected = -1 }

    // While a multi-jump is running only that piece can move, so it counts as selected.
    val active = if (myTurn && game.chain >= 0) game.chain else selected
    val movable = if (myTurn) game.legalMoves.map { it.from }.toSet() else emptySet()
    val targets = if (myTurn && active >= 0) {
        game.legalMoves.filter { it.from == active }.map { it.to }.toSet()
    } else {
        emptySet()
    }
    val mustJump = myTurn && game.legalMoves.isNotEmpty() &&
        game.legalMoves.all { abs(it.to / SIZE - it.from / SIZE) == 2 }

    fun onSquare(cell: Int) {
        when {
            !myTurn -> return
            cell in targets -> {
                onMove(active, cell)
                selected = -1
            }
            cell in movable -> selected = cell
            else -> selected = -1
        }
    }

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
                myTurn && game.chain >= 0 -> "Keep jumping!"
                myTurn -> "Your turn"
                else -> "$opponent's turn..."
            },
            style = MaterialTheme.typography.titleMedium,
        )
        // This line is always there (blank when not needed) so the board doesn't jump down when it appears.
        Text(
            if (mustJump && game.chain < 0) "You must capture" else " ",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            PieceScore(CheckersRed, game.redPlayer, game.redCount, mine = iAmRed)
            PieceScore(CheckersBlack, game.blackPlayer, game.blackCount, mine = !iAmRed)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CheckersDarkSquare)
                .padding(4.dp),
        ) {
            for (displayRow in 0 until SIZE) {
                Row(Modifier.fillMaxWidth()) {
                    for (displayColumn in 0 until SIZE) {
                        // The black player's board is turned around so their pieces are at the bottom.
                        val row = if (iAmRed) displayRow else SIZE - 1 - displayRow
                        val column = if (iAmRed) displayColumn else SIZE - 1 - displayColumn
                        val cell = row * SIZE + column
                        val dark = (row + column) % 2 == 1
                        Square(
                            dark = dark,
                            piece = game.board[cell],
                            selected = cell == active,
                            target = cell in targets,
                            last = cell == game.lastFrom || cell == game.lastTo,
                            enabled = myTurn && dark,
                            onClick = { onSquare(cell) },
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
private fun PieceScore(color: Color, name: String, count: Int, mine: Boolean) {
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
    dark: Boolean,
    piece: Char,
    selected: Boolean,
    target: Boolean,
    last: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val base = if (dark) CheckersDarkSquare else CheckersLightSquare
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(if (last && dark) lerp(base, CheckersGold, 0.35f) else base)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (piece != '.') {
            val red = piece == 'r' || piece == 'R'
            val king = piece == 'R' || piece == 'B'
            Box(
                modifier = Modifier
                    .fillMaxSize(0.8f)
                    .clip(CircleShape)
                    .background(if (red) CheckersRed else CheckersBlack)
                    .border(
                        if (selected) 3.dp else 1.5.dp,
                        if (selected) CheckersGold else Color.White.copy(alpha = 0.35f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // A gold ring marks a king.
                if (king) Box(Modifier.fillMaxSize(0.52f).border(2.dp, CheckersGold, CircleShape))
            }
        }
        if (target) {
            Box(
                Modifier
                    .fillMaxSize(0.32f)
                    .clip(CircleShape)
                    .background(CheckersGold.copy(alpha = 0.9f)),
            )
        }
    }
}
