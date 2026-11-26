package com.classicgames.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.classicgames.core.Brisca
import com.classicgames.core.BriscaState

/** Suit colours by suit number, as on a Spanish deck: Oros gold, Copas red, Espadas blue, Bastos green. */
val BriscaSuitColors = listOf(Color(0xFFC08A00), Color(0xFFC62828), Color(0xFF1E5AA8), Color(0xFF2E7D32))
val BriscaFace = Color(0xFFFFFDF6)
val BriscaBack = Color(0xFF9E2A2B)

/** The number printed in a Spanish card's corner, by rank number: 1 to 7, then 10, 11 and 12 for Sota, Caballo, Rey. */
private val CornerNumbers = listOf("1", "2", "3", "4", "5", "6", "7", "10", "11", "12")

/**
 * The Brisca table as this player sees it: the opponent's hand face down, the deck with the face-up trump, the card
 * waiting on the table, the last trick, both scores and your hand. On your turn tap a card in your hand to play it;
 * "Swap 7" appears when you may swap the 7 of trumps for the face-up card.
 */
@Composable
fun BriscaPanel(
    game: BriscaState,
    notice: String?,
    onCard: (Int) -> Unit,
    onSwapSeven: () -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = game.status == "playing"
    val lead = game.trick.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when {
                game.status == "won" -> if (game.winner == game.you) "You won!" else "${game.winner} won"
                game.status == "draw" -> "It's a draw"
                game.yourTurn -> "Your turn"
                else -> "${game.opponent}'s turn..."
            },
            style = MaterialTheme.typography.titleMedium,
        )

        if (playing) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("You  ${game.yourPoints}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("${game.opponent}  ${game.opponentPoints}", style = MaterialTheme.typography.bodyMedium)
            }

            // The opponent's hand, face down: only how many cards they hold.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(min = 46.dp),
            ) {
                Text(game.opponent, style = MaterialTheme.typography.bodySmall)
                repeat(game.opponentCards) { SpanishCard(null, Modifier.width(30.dp)) }
            }

            Table(game, lead?.card)

            // What just happened, or what to do: one short line, so the hand doesn't jump.
            Text(
                when {
                    game.event.isNotEmpty() -> game.event
                    game.yourTurn && lead != null -> "Answer the ${Brisca.nameOf(lead.card)}"
                    game.yourTurn -> "Lead a card"
                    else -> " "
                },
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )

            // Your hand: big cards, tap one to play it.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(min = 130.dp)) {
                for (card in game.hand) {
                    SpanishCard(
                        card,
                        Modifier
                            .width(86.dp)
                            .clickable(enabled = game.yourTurn) { onCard(card) },
                        ring = if (game.yourTurn) MaterialTheme.colorScheme.primary else null,
                    )
                }
            }
            if (game.canSwap) {
                OutlinedButton(onClick = onSwapSeven) { Text("Swap 7 for the ${Brisca.nameOf(game.trumpCard)}") }
            }
        } else {
            Text(
                "You ${game.yourPoints} - ${game.opponentPoints} ${game.opponent}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text("61 points win; there are 120 in all", style = MaterialTheme.typography.bodySmall)
        }

        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        if (!playing) {
            Button(onClick = onPlayAgain) { Text("Play again") }
        }
    }
}

/** The deck with the face-up trump beside it, the card waiting on the table, and the last trick with who took it. */
@Composable
private fun Table(game: BriscaState, lead: Int?) {
    val cardWidth = 56.dp
    // Aligned at the bottom so the labels line up although the last trick's cards are smaller.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        Labelled("Deck ${game.deckSize}") {
            if (game.deckSize > 0) SpanishCard(null, Modifier.width(cardWidth)) else EmptySlot(cardWidth)
        }
        // Once drawn, the turned-up card stays here faded: its suit is still trumps.
        Labelled("Trumps") {
            SpanishCard(game.trumpCard, Modifier.width(cardWidth).alpha(if (game.deckSize > 0) 1f else 0.4f))
        }
        Labelled("Table") {
            if (lead != null) SpanishCard(lead, Modifier.width(cardWidth)) else EmptySlot(cardWidth)
        }
        val taker = game.lastTrickWinner
        Labelled(
            when {
                taker == null -> "Last trick"
                else -> "${if (taker == game.you) "You" else taker} +${game.lastTrick.sumOf { Brisca.pointsOf(it.card) }}"
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (game.lastTrick.isEmpty()) {
                    EmptySlot(42.dp)
                    EmptySlot(42.dp)
                }
                for (play in game.lastTrick) SpanishCard(play.card, Modifier.width(42.dp))
            }
        }
    }
}

@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content()
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptySlot(width: Dp) {
    val shape = RoundedCornerShape(width * 0.12f)
    Box(
        Modifier
            .width(width)
            .aspectRatio(CARD_RATIO)
            .clip(shape)
            .border(1.dp, Color.Black.copy(alpha = 0.18f), shape),
    )
}

/** Width over height of a card. */
private const val CARD_RATIO = 0.66f

/**
 * One Spanish card, sized by the width in [modifier]: [card] face up, or its back when null. The face has the number
 * of a real Spanish deck in the corner, the suit drawn in the middle in its colour, and for the As, Sota, Caballo and
 * Rey their name at the bottom. [ring] outlines it (playable cards).
 */
@Composable
fun SpanishCard(card: Int?, modifier: Modifier, ring: Color? = null) {
    BoxWithConstraints(modifier.aspectRatio(CARD_RATIO)) {
        val width = maxWidth
        val shape = RoundedCornerShape(width * 0.12f)
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(if (card == null) BriscaBack else BriscaFace)
                .border(if (ring != null) 2.dp else 1.dp, ring ?: Color.Black.copy(alpha = 0.25f), shape),
        ) {
            if (card == null) {
                MemoryCardBack(Modifier.fillMaxSize().padding(width * 0.1f))
                return@Box
            }
            val suit = Brisca.suitOf(card)
            val rank = Brisca.rankOf(card)
            val color = BriscaSuitColors[suit]
            val sp = with(LocalDensity.current) { width.toSp() }
            Text(
                CornerNumbers[rank],
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = sp * 0.26f,
                lineHeight = sp * 0.28f,
                modifier = Modifier.padding(start = width * 0.08f, top = width * 0.04f),
            )
            Canvas(Modifier.fillMaxSize().padding(horizontal = width * 0.2f, vertical = width * 0.42f)) {
                drawSuit(suit, size.minDimension)
            }
            if (rank == 0 || rank >= 7) {
                Text(
                    Brisca.RANKS[rank],
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = sp * 0.16f,
                    lineHeight = sp * 0.18f,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = width * 0.06f),
                )
            }
        }
    }
}

/**
 * Draws a simple version of a Spanish suit, centred in the drawing area, in a square [side] pixels wide: Oros a gold
 * coin, Copas a red cup, Espadas a blue sword, Bastos a green club.
 */
fun DrawScope.drawSuit(suit: Int, side: Float) {
    val color = BriscaSuitColors[suit]
    val left = (size.width - side) / 2f
    val top = (size.height - side) / 2f
    fun at(x: Float, y: Float) = Offset(left + x * side, top + y * side)

    when (suit) {
        0 -> { // Oros: a coin with a rim and an inner ring
            val center = at(0.5f, 0.5f)
            drawCircle(Color(0xFFF2C230), radius = side * 0.44f, center = center)
            drawCircle(color, radius = side * 0.44f, center = center, style = Stroke(side * 0.07f))
            drawCircle(color, radius = side * 0.24f, center = center, style = Stroke(side * 0.05f))
            drawCircle(color, radius = side * 0.07f, center = center)
        }
        1 -> { // Copas: a bowl on a stem and a foot
            val bowl = Path().apply {
                moveTo(at(0.14f, 0.1f).x, at(0.14f, 0.1f).y)
                lineTo(at(0.86f, 0.1f).x, at(0.86f, 0.1f).y)
                quadraticTo(at(0.86f, 0.56f).x, at(0.86f, 0.56f).y, at(0.5f, 0.58f).x, at(0.5f, 0.58f).y)
                quadraticTo(at(0.14f, 0.56f).x, at(0.14f, 0.56f).y, at(0.14f, 0.1f).x, at(0.14f, 0.1f).y)
                close()
            }
            drawPath(bowl, color)
            drawRect(color, topLeft = at(0.44f, 0.56f), size = Size(side * 0.12f, side * 0.24f))
            drawRoundRect(
                color,
                topLeft = at(0.26f, 0.78f),
                size = Size(side * 0.48f, side * 0.12f),
                cornerRadius = CornerRadius(side * 0.06f),
            )
            drawLine(Color.White.copy(alpha = 0.6f), at(0.22f, 0.18f), at(0.78f, 0.18f), side * 0.04f, StrokeCap.Round)
        }
        2 -> { // Espadas: a blade, a cross guard, a grip and a pommel
            val blade = Path().apply {
                moveTo(at(0.5f, 0.02f).x, at(0.5f, 0.02f).y)
                lineTo(at(0.59f, 0.16f).x, at(0.59f, 0.16f).y)
                lineTo(at(0.59f, 0.64f).x, at(0.59f, 0.64f).y)
                lineTo(at(0.41f, 0.64f).x, at(0.41f, 0.64f).y)
                lineTo(at(0.41f, 0.16f).x, at(0.41f, 0.16f).y)
                close()
            }
            drawPath(blade, Color(0xFFB9CDEA))
            drawPath(blade, color, style = Stroke(side * 0.04f))
            drawRoundRect(
                color,
                topLeft = at(0.2f, 0.64f),
                size = Size(side * 0.6f, side * 0.08f),
                cornerRadius = CornerRadius(side * 0.04f),
            )
            drawRect(color, topLeft = at(0.45f, 0.72f), size = Size(side * 0.1f, side * 0.16f))
            drawCircle(color, radius = side * 0.07f, center = at(0.5f, 0.92f))
        }
        else -> { // Bastos: a knobbly club, thicker at the top
            drawLine(color, at(0.34f, 0.9f), at(0.62f, 0.12f), side * 0.16f, StrokeCap.Round)
            drawLine(color, at(0.5f, 0.45f), at(0.66f, 0.12f), side * 0.24f, StrokeCap.Round)
            val knot = Color(0xFF1B5E20)
            drawCircle(knot, radius = side * 0.06f, center = at(0.66f, 0.3f))
            drawCircle(knot, radius = side * 0.05f, center = at(0.4f, 0.52f))
            drawCircle(knot, radius = side * 0.045f, center = at(0.5f, 0.7f))
        }
    }
}
