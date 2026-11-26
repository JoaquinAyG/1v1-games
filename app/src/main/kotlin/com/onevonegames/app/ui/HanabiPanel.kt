package com.onevonegames.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onevonegames.core.HanabiCard
import com.onevonegames.core.HanabiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The card colours by colour number: red, yellow, green, blue, white. */
val HanabiColors = listOf(Color(0xFFD93A3A), Color(0xFFF2C53D), Color(0xFF3E9E4F), Color(0xFF2F6FD6), Color(0xFFF4F4F2))

/** Text on a card of each colour: light on the dark colours, dark on yellow and white. */
private val HanabiInk = listOf(Color.White, Color(0xFF3A2E00), Color.White, Color.White, Color(0xFF333333))

/** One letter per colour, shown on every card next to its shape so colours never depend on colour vision alone. */
private const val LETTERS = "RYGBW"
private val ColorNames = listOf("red", "yellow", "green", "blue", "white")

private val HanabiBack = Color(0xFF37474F)
private val HanabiRing = Color(0xFFFF8F00)

/**
 * Hanabi as this player sees it: the hint tokens, lives and deck, the five fireworks, the last action, the partner's
 * hand face up with what they have been told about each card, your own hand face down with what you have been told,
 * and the discard pile grouped by colour.
 *
 * On your turn, tap a card in your hand and then "Play" or "Discard", or tap the colour (top half) or the number (bottom
 * half) of one of your partner's cards and then "Give hint". Every colour has its own shape and letter as well as its
 * colour: red a circle, yellow a star, green a triangle, blue a diamond and white a cross.
 */
@Composable
fun HanabiPanel(
    game: HanabiState,
    notice: String?,
    onAction: (action: String, card: Int?, color: Int?, number: Int?) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = game.status == "playing"
    // What is picked but not sent yet; forgotten whenever the game moves on.
    var pickedCard by remember(game.yourHand, game.yourTurn, game.deckSize) { mutableStateOf<Int?>(null) }
    var pickedColor by remember(game.partnerHand, game.yourTurn) { mutableStateOf<Int?>(null) }
    var pickedNumber by remember(game.partnerHand, game.yourTurn) { mutableStateOf<Int?>(null) }
    val hinting = pickedColor != null || pickedNumber != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = when (game.status) {
                "won" -> "A perfect show: 25!"
                "finished" -> "Show's over: ${game.score} out of 25"
                "lost" -> "Out of lives: you both lost"
                else -> (if (game.yourTurn) "Your turn" else "${game.partner}'s turn") + when (game.turnsLeft) {
                    -1 -> ""
                    1 -> ": the very last one"
                    else -> ": last round, ${game.turnsLeft} turns left"
                }
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (playing && game.turnsLeft >= 0) MaterialTheme.colorScheme.error else Color.Unspecified,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Hints ${game.hints}/8", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Lives ${game.lives}/3", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Deck ${game.deckSize}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Score ${game.score}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }

        Fireworks(game.fireworks)
        Text(
            game.lastAction.ifEmpty { " " },
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )

        // The partner's hand, face up, with what they know under each card.
        Text(
            if (playing && game.yourTurn) "${game.partner}'s hand: tap a colour or a number to hint it" else "${game.partner}'s hand",
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((i, card) in game.partnerHand.withIndex()) {
                val pointed = (pickedColor != null && card.color == pickedColor) || (pickedNumber != null && card.number == pickedNumber)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    FaceUpCard(
                        card = card,
                        name = "${game.partner}'s card ${i + 1}",
                        ring = when {
                            pointed -> HanabiRing
                            i in game.hintedPartners -> MaterialTheme.colorScheme.primary
                            else -> null
                        },
                        onColor = if (playing && game.yourTurn) ({ pickedCard = null; pickedNumber = null; pickedColor = card.color }) else null,
                        onNumber = if (playing && game.yourTurn) ({ pickedCard = null; pickedColor = null; pickedNumber = card.number }) else null,
                    )
                    KnowledgeTag(card)
                }
            }
        }

        // Your own hand, face down: only what the hints told you.
        Text(
            if (playing && game.yourTurn) "Your hand: tap a card to play or discard it" else "Your hand",
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((i, card) in game.yourHand.withIndex()) {
                FaceDownCard(
                    card = card,
                    name = "Your card ${i + 1}",
                    ring = when {
                        pickedCard == i -> HanabiRing
                        i in game.hintedYours -> MaterialTheme.colorScheme.primary
                        else -> null
                    },
                    onClick = if (playing && game.yourTurn) ({ pickedColor = null; pickedNumber = null; pickedCard = i }) else null,
                )
            }
        }

        if (playing) {
            // What to do with what was picked. Always the same height so the hands don't jump.
            Row(
                modifier = Modifier.heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val card = pickedCard
                when {
                    !game.yourTurn -> Text("Waiting for ${game.partner}...", style = MaterialTheme.typography.bodySmall)
                    hinting -> {
                        val what = pickedColor?.let { "${ColorNames[it]} cards" } ?: "${pickedNumber}s"
                        val count = game.partnerHand.count { it.color == pickedColor || it.number == pickedNumber }
                        Button(
                            onClick = { onAction("hint", null, pickedColor, pickedNumber) },
                            enabled = game.hints > 0,
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Text(if (game.hints > 0) "Hint ${game.partner}'s $what ($count)" else "No hint tokens left")
                        }
                        TextButton(onClick = { pickedColor = null; pickedNumber = null }) { Text("Cancel") }
                    }
                    card != null -> {
                        Button(onClick = { onAction("play", card, null, null) }) { Text("Play") }
                        OutlinedButton(onClick = { onAction("discard", card, null, null) }, enabled = game.hints < 8) {
                            Text(if (game.hints < 8) "Discard" else "Discard (8 hints)")
                        }
                        TextButton(onClick = { pickedCard = null }) { Text("Cancel") }
                    }
                    else -> Text(
                        "Play or discard one of your cards, or give ${game.partner} a hint",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Text(game.result, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }

        Discards(game.discards)

        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        if (!playing) {
            Button(onClick = onPlayAgain) { Text("Play again") }
        }
    }
}

/** The five fireworks: each colour's top card, or an empty slot with its shape while nothing is played. */
@Composable
private fun Fireworks(fireworks: List<Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((color, top) in fireworks.withIndex()) {
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .size(width = 46.dp, height = 56.dp)
                    .clip(shape)
                    .background(if (top > 0) HanabiColors[color] else HanabiColors[color].copy(alpha = 0.18f))
                    .border(1.dp, Color.Black.copy(alpha = 0.25f), shape),
                contentAlignment = Alignment.Center,
            ) {
                CornerMark(color, ink = if (top > 0) HanabiInk[color] else Color.Black.copy(alpha = 0.55f), Modifier.align(Alignment.TopStart))
                Text(
                    if (top > 0) "$top" else "-",
                    color = if (top > 0) HanabiInk[color] else Color.Black.copy(alpha = 0.45f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/**
 * Room for a ring of [ring]'s colour around a card, drawn on the panel rather than on the card so it shows on every card
 * colour. The room is kept when there is no ring, so cards don't move when one appears.
 */
@Composable
private fun Ringed(ring: Color?, content: @Composable () -> Unit) {
    Box(
        Modifier
            .border(3.dp, ring ?: Color.Transparent, RoundedCornerShape(13.dp))
            .padding(4.dp),
    ) { content() }
}

/** A card of the partner's: the top half is its colour (tap to hint the colour), the bottom half its number. */
@Composable
private fun FaceUpCard(card: HanabiCard, name: String, ring: Color?, onColor: (() -> Unit)?, onNumber: (() -> Unit)?) = Ringed(ring) {
    val shape = RoundedCornerShape(10.dp)
    val ink = HanabiInk[card.color]
    Column(
        Modifier
            .size(width = CARD_WIDTH, height = CARD_HEIGHT)
            .clip(shape)
            .background(HanabiColors[card.color])
            .border(1.dp, Color.Black.copy(alpha = 0.3f), shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(enabled = onColor != null) { onColor?.invoke() }
                .semantics { contentDescription = "$name, ${ColorNames[card.color]}" },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Symbol(card.color, ink, Modifier.size(18.dp))
                Text(LETTERS[card.color].toString(), color = ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.25f)))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(enabled = onNumber != null) { onNumber?.invoke() }
                .semantics { contentDescription = "$name, number ${card.number}" },
            contentAlignment = Alignment.Center,
        ) {
            Text("${card.number}", color = ink, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        }
    }
}

/** Under a partner's card: what they know about it ("R?" and what they have ruled out). */
@Composable
private fun KnowledgeTag(card: HanabiCard) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(CARD_WIDTH).height(30.dp)) {
        val known = (if (card.knownColor >= 0) LETTERS[card.knownColor].toString() else "?") +
            (if (card.knownNumber > 0) "${card.knownNumber}" else "?")
        Text("knows $known", style = MaterialTheme.typography.labelSmall, maxLines = 1)
        ruledOut(card)?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, fontSize = 10.sp) }
    }
}

/** What its holder knows about a card, in words: "red, not 1 or 3", "a 4", "nothing known". */
private fun describeKnown(card: HanabiCard): String {
    val known = listOfNotNull(
        card.knownColor.takeIf { it >= 0 }?.let { ColorNames[it] },
        card.knownNumber.takeIf { it > 0 }?.let { "number $it" },
    )
    val not = ruledOut(card)?.removePrefix("not ")?.split(" ")?.map { it.toIntOrNull()?.toString() ?: ColorNames[LETTERS.indexOf(it)] }
    return (known + listOfNotNull(not?.joinToString(" or ", prefix = "not "))).joinToString(", ").ifEmpty { "nothing known" }
}

/** "not Y G 1 3": the colours and numbers a card has been ruled out of, or null when none (or both are known). */
private fun ruledOut(card: HanabiCard): String? {
    val colors = if (card.knownColor >= 0) emptyList() else card.notColors.map { LETTERS[it].toString() }
    val numbers = if (card.knownNumber > 0) emptyList() else card.notNumbers.map { it.toString() }
    return (colors + numbers).takeIf { it.isNotEmpty() }?.joinToString(" ", prefix = "not ")
}

/**
 * One of your own cards, face down: tinted and marked with its colour if a hint told you, its number in the middle if
 * you know it (otherwise "?"), and what it has been ruled out of along the bottom.
 */
@Composable
private fun FaceDownCard(card: HanabiCard, name: String, ring: Color?, onClick: (() -> Unit)?) = Ringed(ring) {
    val shape = RoundedCornerShape(10.dp)
    val color = card.knownColor
    val background = if (color >= 0) HanabiColors[color] else HanabiBack
    val ink = if (color >= 0) HanabiInk[color] else Color.White
    Box(
        Modifier
            .size(width = CARD_WIDTH, height = CARD_HEIGHT + 14.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, Color.Black.copy(alpha = 0.3f), shape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .semantics { contentDescription = "$name: ${describeKnown(card)}" },
        contentAlignment = Alignment.Center,
    ) {
        if (color >= 0) {
            CornerMark(color, ink, Modifier.align(Alignment.TopStart))
        }
        Text(
            if (card.knownNumber > 0) "${card.knownNumber}" else "?",
            color = ink.copy(alpha = if (card.knownNumber > 0) 1f else 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
        )
        ruledOut(card)?.let {
            Text(
                it,
                color = ink,
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            )
        }
    }
}

/** A colour's shape and letter, small, for a card's corner. */
@Composable
private fun CornerMark(color: Int, ink: Color, modifier: Modifier) {
    Row(
        modifier.padding(start = 4.dp, top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Symbol(color, ink, Modifier.size(11.dp))
        Text(LETTERS[color].toString(), color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
    }
}

/** The discard pile, grouped by colour and sorted by number; the groups wrap onto as few lines as fit. */
@Composable
private fun Discards(discards: List<HanabiCard>) {
    if (discards.isEmpty()) {
        Text("Discards: none yet", style = MaterialTheme.typography.bodySmall)
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Discards", style = MaterialTheme.typography.labelMedium)
        for ((color, cards) in discards.groupBy { it.color }.toSortedMap()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Symbol(color, if (color == 4) Color(0xFF777777) else HanabiColors[color], Modifier.size(14.dp))
                Text(
                    "${LETTERS[color]} ${cards.map { it.number }.sorted().joinToString(" ")}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** A colour's shape in [ink]: red a circle, yellow a star, green a triangle, blue a diamond, white a cross. */
@Composable
private fun Symbol(color: Int, ink: Color, modifier: Modifier) {
    Canvas(modifier) { drawHanabiSymbol(color, ink) }
}

/** Draws a colour's shape (see [Symbol]) to fill the drawing area. */
fun DrawScope.drawHanabiSymbol(color: Int, ink: Color) {
    val s = size.minDimension
    val c = Offset(size.width / 2, size.height / 2)
    when (color) {
        0 -> drawCircle(ink, radius = s * 0.42f, center = c)
        1 -> drawPath(star(c, s * 0.5f, s * 0.21f), ink)
        2 -> drawPath(
            Path().apply {
                moveTo(c.x, c.y - s * 0.44f)
                lineTo(c.x + s * 0.48f, c.y + s * 0.38f)
                lineTo(c.x - s * 0.48f, c.y + s * 0.38f)
                close()
            },
            ink,
        )
        3 -> drawPath(
            Path().apply {
                moveTo(c.x, c.y - s * 0.5f)
                lineTo(c.x + s * 0.38f, c.y)
                lineTo(c.x, c.y + s * 0.5f)
                lineTo(c.x - s * 0.38f, c.y)
                close()
            },
            ink,
        )
        else -> {
            val arm = s * 0.17f
            drawRect(ink, topLeft = Offset(c.x - arm, c.y - s * 0.46f), size = Size(2 * arm, s * 0.92f))
            drawRect(ink, topLeft = Offset(c.x - s * 0.46f, c.y - arm), size = Size(s * 0.92f, 2 * arm))
        }
    }
}

/** A five-pointed star centred on [c]. */
private fun star(c: Offset, outer: Float, inner: Float) = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val angle = -PI / 2 + i * PI / 5
        val x = c.x + (r * cos(angle)).toFloat()
        val y = c.y + (r * sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A firework burst in the five card colours, for the lobby icon. */
fun DrawScope.drawHanabiBurst() {
    val s = size.minDimension
    val c = Offset(size.width / 2, size.height / 2)
    for (i in 0 until 10) {
        val angle = i * PI / 5 - PI / 2
        val ink = HanabiColors[i % 5].let { if (i % 5 == 4) Color(0xFF9E9E9E) else it }
        val from = c + Offset((s * 0.12f * cos(angle)).toFloat(), (s * 0.12f * sin(angle)).toFloat())
        val to = c + Offset((s * 0.4f * cos(angle)).toFloat(), (s * 0.4f * sin(angle)).toFloat())
        drawLine(ink, from, to, s * 0.045f, StrokeCap.Round)
        drawCircle(ink, radius = s * 0.045f, center = c + Offset((s * 0.47f * cos(angle)).toFloat(), (s * 0.47f * sin(angle)).toFloat()))
    }
    drawCircle(HanabiColors[1], radius = s * 0.07f, center = c)
    drawCircle(Color(0xFF9E9E9E), radius = s * 0.07f, center = c, style = Stroke(s * 0.015f))
}

private val CARD_WIDTH: Dp = 58.dp
private val CARD_HEIGHT: Dp = 84.dp
