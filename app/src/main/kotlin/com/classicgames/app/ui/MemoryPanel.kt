package com.classicgames.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classicgames.core.MemoryState

val MemoryFirst = Color(0xFFE5484D)
val MemorySecond = Color(0xFF2563EB)
val MemoryBack = Color(0xFF0F766E)
val MemoryFaceUp = Color(0xFFFFFBF2)
val MemoryJustTurned = Color(0xFFFFC107)

/**
 * The card faces, by face number: apple, cat, rocket, star, moon, balloon, fish, cactus, mushroom, die, bell, ball.
 * Written as escapes so the source stays plain ASCII.
 */
val MemoryFaces = listOf(
    "🍎", "🐱", "🚀", "⭐", "🌙", "🎈",
    "🐟", "🌵", "🍄", "🎲", "🔔", "⚽",
)

/** What face number [face] shows: its emoji, or a plain number if there are more faces than emoji. */
fun memoryFace(face: Int) = MemoryFaces.getOrElse(face) { (face + 1).toString() }

/**
 * The Memory grid with both scores. On your turn tap a face-down card to flip it. Cards you take get your colour; the
 * two cards just turned over are ringed in amber, and a pair that didn't match stays face up until the next card is
 * flipped. The server only ever sends the faces of cards that are face up.
 */
@Composable
fun MemoryPanel(
    game: MemoryState,
    myName: String,
    notice: String?,
    onCard: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iAmFirst = game.firstPlayer == myName
    val me = if (iAmFirst) "1" else "2"
    val opponent = if (iAmFirst) game.secondPlayer else game.firstPlayer
    val playing = game.status == "playing"
    val myTurn = playing && game.turn == me
    val pair = game.lastPair.map { memoryFace(game.faces[it]) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
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

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Score(MemoryFirst, if (iAmFirst) "You" else game.firstPlayer, game.firstScore, mine = iAmFirst)
            Score(MemorySecond, if (iAmFirst) game.secondPlayer else "You", game.secondScore, mine = !iAmFirst)
        }

        // What just happened, always one line so the grid doesn't jump. The pair just turned over is named by its faces.
        Text(
            when {
                !playing -> "All pairs found"
                game.lastPair.isNotEmpty() && game.lastMatched -> {
                    val face = pair.first()
                    if (myTurn) "You found a pair of $face: flip again" else "$opponent found a pair of $face and flips again"
                }
                game.lastPair.isNotEmpty() -> {
                    val faces = pair.joinToString(" and ")
                    if (myTurn) "$opponent turned over $faces: no match. Your turn" else "No match: $faces"
                }
                game.flipped.isNotEmpty() -> if (myTurn) "Flip a second card" else "$opponent is flipping..."
                myTurn -> "Flip a card"
                else -> " "
            },
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in 0 until game.rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (column in 0 until game.columns) {
                        val card = row * game.columns + column
                        val owner = when (game.taken[card]) {
                            '1' -> MemoryFirst
                            '2' -> MemorySecond
                            else -> null
                        }
                        Card(
                            face = game.faces[card].takeIf { it >= 0 }?.let(::memoryFace),
                            owner = owner,
                            justTurned = card in game.lastPair || card in game.flipped,
                            enabled = myTurn && owner == null && card !in game.flipped,
                            onClick = { onCard(card) },
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
private fun Score(color: Color, name: String, pairs: Int, mine: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(
            "$name  $pairs",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * One card: its back while [face] is null, otherwise the face, tinted with the [owner]'s colour once taken. Cards
 * turned over in the current or the last try are ringed in amber.
 */
@Composable
private fun Card(face: String?, owner: Color?, justTurned: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .aspectRatio(0.78f)
            .clip(shape)
            .background(
                when {
                    face == null -> MemoryBack
                    owner != null -> owner.copy(alpha = 0.18f).compositeOver(MemoryFaceUp)
                    else -> MemoryFaceUp
                },
            )
            .border(
                width = if (justTurned || owner != null) 3.dp else 1.dp,
                color = when {
                    justTurned -> MemoryJustTurned
                    owner != null -> owner
                    else -> Color.Black.copy(alpha = 0.15f)
                },
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (face == null) MemoryCardBack(Modifier.fillMaxSize().padding(6.dp)) else Text(face, fontSize = 26.sp)
    }
}

/** The back of a card: a light diamond inside a thin frame. */
@Composable
fun MemoryCardBack(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            Color.White.copy(alpha = 0.35f),
            style = Stroke(w * 0.05f),
            cornerRadius = CornerRadius(w * 0.12f),
        )
        val diamond = Path().apply {
            moveTo(w / 2, h * 0.22f)
            lineTo(w * 0.75f, h / 2)
            lineTo(w / 2, h * 0.78f)
            lineTo(w * 0.25f, h / 2)
            close()
        }
        drawPath(diamond, Color.White.copy(alpha = 0.3f))
        drawCircle(Color.White.copy(alpha = 0.5f), radius = w * 0.06f, center = Offset(w / 2, h / 2))
    }
}
