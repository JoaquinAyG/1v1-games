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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classicgames.core.OwareState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

val OwareWood = Color(0xFFB9783F)
val OwarePit = Color(0xFF6B3E1E)
val OwareSeed = Color(0xFFF4E4C4)
private val OwareSeedEdge = Color(0xFF7A5230)
private val OwareLastPit = Color(0xFFFFC107)
private val OwareCaptured = Color(0xFFE5484D)

/** Pits on each side of the board. */
private const val SIDE = 6

/** Up to this many seeds a pit draws every seed; above it the pit shows the number instead. */
private const val MAX_DRAWN_SEEDS = 15

/**
 * The Oware board: two rows of six pits with their seeds, and each player's captured seeds. Your own pits are always
 * the bottom row, so seeds travel counter-clockwise from your point of view (left to right along your row). On your
 * turn the pits you may sow from are ringed; tap one to sow it. The pit sown last is ringed in amber, and the pits it
 * captured from in red.
 */
@Composable
fun OwarePanel(
    game: OwareState,
    myName: String,
    notice: String?,
    onPit: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iAmNorth = game.northPlayer == myName
    val opponent = if (iAmNorth) game.southPlayer else game.northPlayer
    val playing = game.status == "playing"
    val myTurn = playing && game.turn == (if (iAmNorth) "N" else "S")

    // South owns pits 0..5 and north 6..11, both numbered counter-clockwise. Your row runs left to right along the
    // bottom and the opponent's continues right to left along the top.
    val myFirst = if (iAmNorth) SIDE else 0
    val theirFirst = SIDE - myFirst
    val bottom = List(SIDE) { myFirst + it }
    val top = List(SIDE) { theirFirst + SIDE - 1 - it }

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
        // Always two lines tall (blank when nothing happened) so the board doesn't jump when there is news.
        Text(
            game.event,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Captured(name = opponent, count = if (iAmNorth) game.southCaptured else game.northCaptured, mine = false)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(OwareWood)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((row, countAbove) in listOf(top to true, bottom to false)) {
                Row(Modifier.fillMaxWidth()) {
                    for (pit in row) {
                        val playable = myTurn && pit in game.legalMoves
                        Pit(
                            seeds = game.pits[pit],
                            countAbove = countAbove,
                            playable = playable,
                            last = pit == game.lastPit,
                            captured = pit in game.capturedPits,
                            onClick = { onPit(pit) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Captured(name = myName, count = if (iAmNorth) game.northCaptured else game.southCaptured, mine = true)

        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        if (!playing) {
            Button(onClick = onPlayAgain) { Text("Play again") }
        }
    }
}

@Composable
private fun Captured(name: String, count: Int, mine: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(OwareSeed).border(1.dp, OwareSeedEdge, CircleShape))
        Text(
            "${if (mine) "You" else name}: $count captured",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** One pit with its seeds, and its seed count written above it (top row) or below it (bottom row). */
@Composable
private fun Pit(
    seeds: Int,
    countAbove: Boolean,
    playable: Boolean,
    last: Boolean,
    captured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val count: @Composable () -> Unit = {
        Text(
            seeds.toString(),
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (countAbove) count()
        val ring = when {
            captured -> OwareCaptured
            last -> OwareLastPit
            playable -> Color.White.copy(alpha = 0.85f)
            else -> Color.Black.copy(alpha = 0.2f)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(3.dp)
                .clip(CircleShape)
                .background(if (captured) OwareCaptured.copy(alpha = 0.35f).compositeOver(OwarePit) else OwarePit)
                .border(if (captured || last || playable) 3.dp else 1.dp, ring, CircleShape)
                .clickable(enabled = playable, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (seeds > MAX_DRAWN_SEEDS) {
                Text(seeds.toString(), color = OwareSeed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            } else if (seeds > 0) {
                Canvas(Modifier.fillMaxSize().padding(7.dp)) { drawSeeds(seeds) }
            }
        }
        if (!countAbove) count()
    }
}

/**
 * Draws [n] seeds inside a circle filling the canvas: up to 6 on a ring (a pair, a triangle, a square...), more
 * spread out like a sunflower head so the pile looks natural and every seed can still be counted.
 */
private fun DrawScope.drawSeeds(n: Int) {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val seed: Float
    val spots: List<Offset>
    if (n <= 6) {
        seed = radius * if (n <= 4) 0.27f else 0.24f
        val ring = radius * when (n) {
            1 -> 0f
            2 -> 0.34f
            3 -> 0.4f
            4 -> 0.46f
            else -> 0.54f
        }
        spots = List(n) { i ->
            val angle = 2 * PI * i / n - PI / 2
            center + Offset((ring * cos(angle)).toFloat(), (ring * sin(angle)).toFloat())
        }
    } else {
        // Each seed gets an equal share of the circle: about reach * sqrt(PI / n) between neighbours.
        val share = 0.9f * sqrt(PI.toFloat() / n)
        seed = radius * share / (2 + share)
        val reach = radius - seed
        spots = List(n) { i ->
            val distance = reach * sqrt((i + 0.5f) / n)
            val angle = i * GOLDEN_ANGLE
            center + Offset((distance * cos(angle)).toFloat(), (distance * sin(angle)).toFloat())
        }
    }
    for (spot in spots) {
        drawCircle(OwareSeed, radius = seed, center = spot)
        drawCircle(OwareSeedEdge, radius = seed, center = spot, style = Stroke(seed * 0.22f))
    }
}

/** The angle between consecutive seeds of a sunflower spiral, in radians. */
private val GOLDEN_ANGLE = PI * (3 - sqrt(5.0))
