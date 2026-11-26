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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onevonegames.core.MastermindGuess
import com.onevonegames.core.MastermindState

/** Peg colours, indexed by the colour numbers of the protocol. */
val MastermindColors = listOf(
    Color(0xFFE53935), // red
    Color(0xFFFDD835), // yellow
    Color(0xFF43A047), // green
    Color(0xFF1E88E5), // blue
    Color(0xFF8E24AA), // purple
    Color(0xFFFAFAFA), // white
)
val MastermindBoard = Color(0xFF8D6E63)
private val PegEdge = Color(0xFF37474F)
private val EmptyHole = Color(0xFFE3E7EA)
private val CrackedRow = Color(0xFFDCF5E3)

private fun pegColor(color: Int) = MastermindColors.getOrElse(color) { Color.Gray }

/**
 * Everything for one Mastermind duel: choosing your secret code, then guessing the opponent's. Your guesses with
 * their scores are on the left and the opponent's guesses at your code on the right; both codes are shown once the
 * game is over. The server keeps the opponent's code until then, so this only shows its state and reports taps.
 */
@Composable
fun MastermindPanel(
    game: MastermindState,
    notice: String?,
    onSetCode: (List<Int>) -> Unit,
    onGuess: (List<Int>) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finished = game.phase == "finished"
    // The code being put together: a fresh one for every guess (and for a new game).
    var draft by remember(game.phase, game.yourGuesses.size) { mutableStateOf(List<Int?>(game.pegs) { null }) }
    var selected by remember(game.phase, game.yourGuesses.size) { mutableIntStateOf(0) }
    val complete = draft.all { it != null }

    /** Puts [color] in the selected hole and moves on to the next empty one. */
    fun pick(color: Int) {
        val next = draft.toMutableList().also { it[selected] = color }
        draft = next
        selected = (1..game.pegs).map { (selected + it) % game.pegs }.firstOrNull { next[it] == null } ?: selected
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val title = when {
            finished -> when (game.winner) {
                null -> "It's a draw"
                game.you -> "You won!"
                else -> "${game.winner} won"
            }
            game.phase == "secret" -> if (game.youReady) "Waiting for ${game.opponent}..." else "Set your secret code"
            game.yourTurn -> "Your turn"
            else -> "${game.opponent}'s turn..."
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (game.event != title) {
            Text(game.event, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }

        // What you can do comes first (choosing a code, the next guess, the result), so it stays in view while the
        // guess lists below it grow.
        when {
            game.phase == "secret" && !game.youReady -> {
                Text(
                    "Pick ${game.pegs} colours for ${game.opponent} to crack. Colours can repeat.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                CodeEditor(draft, selected, game.colors, onHole = { selected = it }, onColor = ::pick)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { draft = List(game.pegs) { null }; selected = 0 }) { Text("Clear") }
                    Button(onClick = { onSetCode(draft.map { it!! }) }, enabled = complete) { Text("Set code") }
                }
            }
            game.phase == "guessing" -> {
                // You can put the next guess together while the opponent is still thinking.
                CodeEditor(draft, selected, game.colors, onHole = { selected = it }, onColor = ::pick)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { draft = List(game.pegs) { null }; selected = 0 }) { Text("Clear") }
                    Button(onClick = { onGuess(draft.map { it!! }) }, enabled = complete && game.yourTurn) {
                        Text("Guess")
                    }
                }
            }
            finished -> CodeLine("${game.opponent}'s code", game.opponentSecret)
        }

        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        if (game.youReady) CodeLine("Your code", game.yourSecret)

        if (finished) {
            Button(onClick = onPlayAgain) { Text("Play again") }
        }

        if (game.phase != "secret") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuessList("You → ${game.opponent}", game.yourGuesses, game.maxGuesses, game.pegs, Modifier.weight(1f))
                GuessList("${game.opponent} → you", game.opponentGuesses, game.maxGuesses, game.pegs, Modifier.weight(1f))
            }
            Text(
                "●  right colour, right place     ○  right colour, wrong place",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The holes of the code being put together (tap one to choose it) and the colours to fill them with. */
@Composable
private fun CodeEditor(draft: List<Int?>, selected: Int, colors: Int, onHole: (Int) -> Unit, onColor: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            draft.forEachIndexed { i, color -> Peg(color, 44.dp, selected = i == selected, onClick = { onHole(i) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (color in 0 until colors) Peg(color, 38.dp, onClick = { onColor(color) })
        }
    }
}

/** A peg of [color] (an empty hole when null), ringed when [selected]. */
@Composable
private fun Peg(color: Int?, size: Dp, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color?.let(::pegColor) ?: EmptyHole)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else PegEdge.copy(alpha = if (color == null) 0.3f else 0.6f),
                CircleShape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

/** A whole code in a row, after a [label]. */
@Composable
private fun CodeLine(label: String, code: List<Int>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 4.dp))
        code.forEach { Peg(it, 24.dp) }
    }
}

/** One player's guesses at the other's code, each with its score, under a [title] and a count. */
@Composable
private fun GuessList(title: String, guesses: List<MastermindGuess>, max: Int, pegs: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${guesses.size} of $max guesses",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        guesses.forEachIndexed { i, guess ->
            val cracked = guess.black == pegs
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (cracked) CrackedRow else Color.Transparent)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(16.dp),
                )
                guess.code.forEach { Peg(it, 18.dp) }
                Score(guess.black, guess.white, pegs, Modifier.padding(start = 4.dp))
            }
        }
    }
}

/** The score of a guess as small key pegs, two per row: black ones first, then white ones, then empty holes. */
@Composable
private fun Score(black: Int, white: Int, pegs: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in 0 until (pegs + 1) / 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in row * 2 until minOf(row * 2 + 2, pegs)) {
                    val color = when {
                        i < black -> Color.Black
                        i < black + white -> Color.White
                        else -> EmptyHole
                    }
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.dp, PegEdge.copy(alpha = if (i < black + white) 0.8f else 0.25f), CircleShape),
                    )
                }
            }
        }
    }
}
