package com.onevonegames.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onevonegames.core.QuartoState

val QuartoBoard = Color(0xFFB9C8CE)
val QuartoCell = Color(0xFFE6EDF0)
val QuartoLight = Color(0xFFF6E2B3)
val QuartoDark = Color(0xFF6B3A1E)
private val QuartoOutline = Color(0xFF3B2414)
private val QuartoWin = Color(0xFFFFC107)
private val QuartoLast = Color(0xFF0F766E)

/**
 * The Quarto board, the piece in hand and the pool. When it is your turn to place, tap an empty cell to put the piece
 * you were given there; when it is your turn to choose, tap a piece in the pool and then "Give". The pool keeps every
 * piece in the same slot all game, so it is easy to see which ones are left. The winning line is highlighted.
 */
@Composable
fun QuartoPanel(
    game: QuartoState,
    myName: String,
    notice: String?,
    onCell: (Int) -> Unit,
    onGive: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iAmFirst = game.firstPlayer == myName
    val opponent = if (iAmFirst) game.secondPlayer else game.firstPlayer
    val playing = game.status == "playing"
    val myTurn = playing && game.turn == (if (iAmFirst) "1" else "2")
    val placing = myTurn && game.phase == "place"
    val choosing = myTurn && game.phase == "choose"
    // The piece picked in the pool but not given yet; forgotten whenever the game moves on.
    var selected by remember(game.pool, game.phase, game.turn) { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
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

        // What to do now, with the piece in hand beside it. Always the same height so the board doesn't jump.
        Row(
            modifier = Modifier.heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                when {
                    game.status == "won" -> "Four ${game.sharedTraits.joinToString(", ")} pieces in a line"
                    game.status == "draw" -> "The board is full and no line shares a trait"
                    placing -> "Place the piece $opponent gave you"
                    game.phase == "place" -> "$opponent is placing the piece you gave them"
                    choosing -> "Choose a piece for $opponent"
                    else -> "$opponent is choosing a piece for you..."
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (game.piece >= 0) {
                PieceSlot(game.piece, Modifier.size(44.dp), ring = if (placing) QuartoLast else null)
            }
        }

        Board(
            game = game,
            enabled = placing,
            onCell = onCell,
            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
        )

        Pool(
            game = game,
            selected = selected,
            enabled = choosing,
            onPick = { selected = it },
            modifier = Modifier.fillMaxWidth(),
        )

        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        if (choosing) {
            val piece = selected
            Button(onClick = { piece?.let(onGive) }, enabled = piece != null) { Text("Give to $opponent") }
        }
        if (!playing) {
            Button(onClick = onPlayAgain) { Text("Play again") }
        }
    }
}

@Composable
private fun Board(game: QuartoState, enabled: Boolean, onCell: (Int) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(QuartoBoard)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in 0 until 4) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (column in 0 until 4) {
                    val cell = row * 4 + column
                    val piece = game.board[cell]
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(shape)
                            .background(QuartoCell)
                            .then(
                                when {
                                    cell in game.line -> Modifier.border(4.dp, QuartoWin, shape)
                                    cell == game.lastCell -> Modifier.border(2.dp, QuartoLast, shape)
                                    else -> Modifier
                                },
                            )
                            .clickable(enabled = enabled && piece < 0) { onCell(cell) },
                    ) {
                        if (piece >= 0) {
                            Canvas(Modifier.fillMaxSize()) { drawQuartoPiece(piece, center, size.minDimension, QuartoCell) }
                        }
                    }
                }
            }
        }
    }
}

/** All 16 pieces in two rows of eight; a piece that is on the board or in hand leaves an empty slot. */
@Composable
private fun Pool(game: QuartoState, selected: Int?, enabled: Boolean, onPick: (Int) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (column in 0 until 8) {
                    val piece = row * 8 + column
                    val available = piece in game.pool
                    PieceSlot(
                        piece = piece.takeIf { available },
                        ring = if (piece == selected) QuartoWin else null,
                        // Never dimmed when it isn't your turn: a faded dark piece would look like a light one.
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = enabled && available) { onPick(piece) },
                    )
                }
            }
        }
    }
}

/** A square slot holding [piece] (or nothing), optionally ringed in [ring]. */
@Composable
private fun PieceSlot(piece: Int?, modifier: Modifier, ring: Color? = null) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (piece == null) QuartoCell.copy(alpha = 0.45f) else QuartoCell)
            .then(if (ring != null) Modifier.border(3.dp, ring, shape) else Modifier),
    ) {
        if (piece != null) Canvas(Modifier.fillMaxSize()) { drawQuartoPiece(piece, center, size.minDimension, QuartoCell) }
    }
}

/**
 * Draws Quarto piece [piece] seen from above, in a square [box] pixels wide around [center], on [background]. Each
 * trait has its own look: tall pieces fill most of the box and cast a longer shadow, short ones are about half as
 * wide; dark pieces are brown, light ones cream; square pieces are squares and round ones circles; hollow pieces have
 * a hole in the middle that shows the [background]. Shared with the lobby icon.
 */
fun DrawScope.drawQuartoPiece(piece: Int, center: Offset, box: Float, background: Color) {
    val tall = piece and 1 != 0
    val dark = piece and 2 != 0
    val square = piece and 4 != 0
    val hollow = piece and 8 != 0

    val half = box * (if (tall) 0.42f else 0.25f)
    val stroke = box * 0.035f
    fun shape(at: Offset, color: Color, style: DrawStyle) {
        if (square) {
            drawRoundRect(
                color,
                topLeft = at - Offset(half, half),
                size = Size(2 * half, 2 * half),
                cornerRadius = CornerRadius(half * 0.2f),
                style = style,
            )
        } else {
            drawCircle(color, radius = half, center = at, style = style)
        }
    }

    val shadow = box * (if (tall) 0.06f else 0.025f)
    shape(center + Offset(shadow, shadow), Color.Black.copy(alpha = 0.25f), Fill)
    shape(center, if (dark) QuartoDark else QuartoLight, Fill)
    shape(center, QuartoOutline, Stroke(stroke))
    if (hollow) {
        val hole = half * 0.48f
        drawCircle(background, radius = hole, center = center)
        drawCircle(QuartoOutline, radius = hole, center = center, style = Stroke(stroke))
    }
}
