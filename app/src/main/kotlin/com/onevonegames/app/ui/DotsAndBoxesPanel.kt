package com.onevonegames.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onevonegames.core.DotsAndBoxes
import com.onevonegames.core.DotsState

val DotsRed = Color(0xFFE5484D)
val DotsBlue = Color(0xFF2563EB)
val DotsInk = Color(0xFF263238)
val DotsLastLineGlow = Color(0xFFFFC107)
private val DotsPaper = Color(0xFFF4F7FB)

/** Space around the outer dots, in cells, so they are not cut off and taps just outside still count. */
private const val MARGIN = 0.35f

/** How far from a line (in cells) a tap still draws it; a tap in the middle of a box draws nothing. */
private const val TAP_REACH = 0.4f

/**
 * The Dots and Boxes board with both scores. On your turn, tap near a line between two dots to draw it.
 * Closed boxes are filled in their owner's colour; the line drawn last is highlighted, and so are the boxes it
 * closed.
 */
@Composable
fun DotsAndBoxesPanel(
    game: DotsState,
    myName: String,
    notice: String?,
    onLine: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iAmRed = game.redPlayer == myName
    val opponent = if (iAmRed) game.bluePlayer else game.redPlayer
    val playing = game.status == "playing"
    val myTurn = playing && game.turn == (if (iAmRed) "R" else "B")

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
        // Always present (blank when nothing was closed) so the board doesn't jump down when a box is closed.
        // While the game runs, whoever closed a box is the one whose turn it still is.
        val boxes = if (game.closed.size == 1) "a box" else "${game.closed.size} boxes"
        Text(
            when {
                !playing || game.closed.isEmpty() -> " "
                myTurn -> "You closed $boxes: draw again"
                else -> "$opponent closed $boxes and draws again"
            },
            style = MaterialTheme.typography.bodySmall,
        )

        // Scores: red on the left, blue on the right; "You" marks which side is yours.
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Score(DotsRed, game.redPlayer, game.redScore, mine = iAmRed)
            Score(DotsBlue, game.bluePlayer, game.blueScore, mine = !iAmRed)
        }

        Board(game = game, enabled = myTurn, onLine = onLine, modifier = Modifier.fillMaxWidth())

        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        if (!playing) {
            Button(onClick = onPlayAgain) { Text("Play again") }
        }
    }
}

@Composable
private fun Score(color: Color, name: String, count: Int, mine: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(
            "${if (mine) "You" else name}  $count",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Where everything sits on a square board [side] pixels wide with [n] x [n] boxes. */
private class Grid(side: Float, private val n: Int) {
    val cell = side / (n + 2 * MARGIN)
    private val margin = cell * MARGIN

    /** Dot ([row], [column]), both 0..n from the top-left. */
    fun dot(row: Int, column: Int) = Offset(margin + column * cell, margin + row * cell)

    /** The two ends of every line, indexed by line number (the numbering comes from core). */
    val lines: Array<Pair<Offset, Offset>> = arrayOfNulls<Pair<Offset, Offset>>(DotsAndBoxes.lineCount(n)).also {
        for (row in 0..n) for (column in 0 until n) {
            it[DotsAndBoxes.horizontalLine(n, row, column)] = dot(row, column) to dot(row, column + 1)
        }
        for (row in 0 until n) for (column in 0..n) {
            it[DotsAndBoxes.verticalLine(n, row, column)] = dot(row, column) to dot(row + 1, column)
        }
    }.requireNoNulls()

    /** Top-left corner of box [box] (row * n + column). */
    fun boxCorner(box: Int) = dot(box / n, box % n)

    /** The line closest to [point], or null if none is within reach. */
    fun lineAt(point: Offset): Int? {
        val nearest = lines.indices.minBy { distance(point, lines[it]) }
        return nearest.takeIf { distance(point, lines[it]) <= TAP_REACH * cell }
    }

    private fun distance(p: Offset, line: Pair<Offset, Offset>): Float {
        val (a, b) = line
        val ab = b - a
        val t = (((p - a).x * ab.x + (p - a).y * ab.y) / (ab.x * ab.x + ab.y * ab.y)).coerceIn(0f, 1f)
        return (p - (a + ab * t)).getDistance()
    }
}

@Composable
private fun Board(game: DotsState, enabled: Boolean, onLine: (Int) -> Unit, modifier: Modifier) {
    val n = game.size
    val faint = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(DotsPaper)
            .pointerInput(game, enabled, onLine) {
                if (!enabled) return@pointerInput
                detectTapGestures { tap ->
                    val line = Grid(size.width.toFloat(), n).lineAt(tap) ?: return@detectTapGestures
                    if (game.lines[line] == '.') onLine(line)
                }
            },
    ) {
        val grid = Grid(size.width, n)
        val cell = grid.cell
        fun colorOf(mark: Char) = if (mark == 'R') DotsRed else DotsBlue

        // Closed boxes in their owner's colour; the ones the last line closed stand out.
        for ((box, owner) in game.boxes.withIndex()) {
            if (owner == '.') continue
            val alpha = if (box in game.closed) 0.6f else 0.3f
            drawRect(colorOf(owner).copy(alpha = alpha), topLeft = grid.boxCorner(box), size = Size(cell, cell))
        }

        // Lines not drawn yet are faint guides; drawn ones are ink.
        for ((line, ends) in grid.lines.withIndex()) {
            val drawn = game.lines[line] != '.'
            drawLine(
                color = if (drawn) DotsInk else faint,
                start = ends.first,
                end = ends.second,
                strokeWidth = cell * if (drawn) 0.08f else 0.04f,
                cap = StrokeCap.Round,
            )
        }

        // The last line glows, in the colour of whoever drew it.
        if (game.lastLine in grid.lines.indices) {
            val (start, end) = grid.lines[game.lastLine]
            drawLine(DotsLastLineGlow.copy(alpha = 0.7f), start, end, cell * 0.2f, StrokeCap.Round)
            drawLine(colorOf(game.lines[game.lastLine]), start, end, cell * 0.09f, StrokeCap.Round)
        }

        for (row in 0..n) for (column in 0..n) {
            drawCircle(DotsInk, radius = cell * 0.075f, center = grid.dot(row, column))
        }
    }
}
