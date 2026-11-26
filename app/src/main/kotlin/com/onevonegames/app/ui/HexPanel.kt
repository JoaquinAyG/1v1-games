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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onevonegames.core.HexState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

val HexRed = Color(0xFFE5484D)
val HexBlue = Color(0xFF2563EB)
val HexEmpty = Color(0xFFF3ECDD)
val HexLine = Color(0xFF9C8B74)
private val HexWinGlow = Color(0xFFFFC107)

/** Space around the board for the coloured sides, in cell widths: just enough for half their stroke and its cap. */
private const val MARGIN = 0.2f

/**
 * Where everything sits on a Hex board of [n] x [n] cells drawn [width] pixels wide: pointy-top hexagons, every row
 * half a cell further right than the one above, as in the protocol.
 */
class HexGeometry(width: Float, private val n: Int) {
    /** Width of a cell, flat side to flat side. */
    val cell = width / (1.5f * n - 0.5f + 2 * MARGIN)

    /** Distance from a cell's centre to its corners. */
    val radius = cell / sqrt(3f)
    private val margin = cell * MARGIN

    val height = 2 * margin + radius * (2 + 1.5f * (n - 1))

    fun center(index: Int): Offset {
        val row = index / n
        val column = index % n
        return Offset(margin + cell / 2 + column * cell + row * cell / 2, margin + radius + row * 1.5f * radius)
    }

    /** Corner [k] of a cell: 0 is the top one, then clockwise (upper right, lower right, bottom, lower left, upper left). */
    fun corner(center: Offset, k: Int, scale: Float = 1f): Offset {
        val angle = PI / 3 * k - PI / 2
        return center + Offset((radius * scale * cos(angle)).toFloat(), (radius * scale * sin(angle)).toFloat())
    }

    fun hexagon(index: Int, scale: Float = 1f): Path {
        val center = center(index)
        return Path().apply {
            moveTo(corner(center, 0, scale).x, corner(center, 0, scale).y)
            for (k in 1..5) corner(center, k, scale).let { lineTo(it.x, it.y) }
            close()
        }
    }

    /** The cell under [point], or null if it is off the board. */
    fun cellAt(point: Offset): Int? {
        val nearest = (0 until n * n).minBy { (center(it) - point).getDistance() }
        return nearest.takeIf { (center(it) - point).getDistance() <= radius }
    }

    /**
     * The outer sides of the board as (corner, corner) pairs of one cell, with the colour of the player who owns that
     * side: red along the top and bottom rows, blue along the left and right columns.
     */
    fun sides(): List<Triple<Int, Pair<Int, Int>, Color>> = buildList {
        for (i in 0 until n) {
            val top = i
            val bottom = (n - 1) * n + i
            val left = i * n
            val right = i * n + n - 1
            add(Triple(top, 5 to 0, HexRed))
            add(Triple(top, 0 to 1, HexRed))
            add(Triple(bottom, 2 to 3, HexRed))
            add(Triple(bottom, 3 to 4, HexRed))
            add(Triple(left, 4 to 5, HexBlue))
            if (i < n - 1) add(Triple(left, 3 to 4, HexBlue))
            add(Triple(right, 1 to 2, HexBlue))
            if (i > 0) add(Triple(right, 0 to 1, HexBlue))
        }
    }
}

/**
 * The Hex board with both players' sides. On your turn tap an empty cell to put your stone there; the board takes the
 * card's full width so the cells are big enough for one tap. When the swap is allowed, "Swap" takes over the
 * opponent's first stone. The last stone played has a dot, and the winner's chain is highlighted.
 */
@Composable
fun HexPanel(
    game: HexState,
    myName: String,
    notice: String?,
    onCell: (Int) -> Unit,
    onSwap: () -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iAmRed = game.redPlayer == myName
    val opponent = if (iAmRed) game.bluePlayer else game.redPlayer
    val playing = game.status == "playing"
    val myTurn = playing && game.turn == (if (iAmRed) "R" else "B")
    val stones = game.board.count { it != '.' }

    // No side padding: every dp of width goes to the board's cells.
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when {
                !playing -> if (game.winner == myName) "You won!" else "${game.winner} won"
                myTurn -> "Your turn"
                else -> "$opponent's turn..."
            },
            style = MaterialTheme.typography.titleMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Side(HexRed, if (iAmRed) "You" else game.redPlayer, "top to bottom", mine = iAmRed)
            Side(HexBlue, if (iAmRed) game.bluePlayer else "You", "left to right", mine = !iAmRed)
        }

        // One line of news, always there so the board doesn't jump.
        Text(
            when {
                !playing -> if (game.winner == myName) "You joined your sides!" else "${game.winner} joined their sides"
                game.swapped && stones == 1 ->
                    if (iAmRed) "$opponent swapped: your first stone is now theirs, mirrored" else "You took over the first stone"
                myTurn && game.canSwap -> "Place a stone, or swap to take over $opponent's first stone"
                myTurn -> "Tap a cell to place your stone"
                else -> " "
            },
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Board(game = game, enabled = myTurn, onTap = onCell, modifier = Modifier.fillMaxWidth())

        // Below the board, so the board doesn't move when the button goes away after blue's first turn.
        if (myTurn && game.canSwap) OutlinedButton(onClick = onSwap) { Text("Swap") }

        notice?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        if (!playing) {
            Button(onClick = onPlayAgain) { Text("Play again") }
        }
    }
}

@Composable
private fun Side(color: Color, name: String, sides: String, mine: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(
            "$name: $sides",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Board(game: HexState, enabled: Boolean, onTap: (Int) -> Unit, modifier: Modifier) {
    val n = game.size
    // The board's shape only depends on the number of cells, so any width gives the height.
    val ratio = HexGeometry(1f, n).let { 1f / it.height }
    val currentOnTap by rememberUpdatedState(onTap)

    Canvas(
        modifier = modifier
            .aspectRatio(ratio)
            .pointerInput(game, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { tap ->
                    val cell = HexGeometry(size.width.toFloat(), n).cellAt(tap) ?: return@detectTapGestures
                    if (game.board[cell] == '.') currentOnTap(cell)
                }
            },
    ) {
        drawHexBoard(HexGeometry(size.width, n), n, game.board, lastMove = game.lastMove, path = game.winningPath)
    }
}

/** Draws the sides, the cells and their stones, the last move's dot and the winning chain. Shared with the icon. */
fun DrawScope.drawHexBoard(geometry: HexGeometry, n: Int, board: String, lastMove: Int = -1, path: List<Int> = emptyList()) {
    // The sides first, thick, so the cells drawn on top leave only their outer half showing.
    for ((cell, corners, color) in geometry.sides()) {
        val center = geometry.center(cell)
        drawLine(
            color,
            geometry.corner(center, corners.first),
            geometry.corner(center, corners.second),
            strokeWidth = geometry.cell * 0.32f,
            cap = StrokeCap.Round,
        )
    }

    for (cell in 0 until n * n) {
        val hexagon = geometry.hexagon(cell)
        val fill = when (board[cell]) {
            'R' -> HexRed
            'B' -> HexBlue
            else -> HexEmpty
        }
        drawPath(hexagon, fill)
        drawPath(hexagon, HexLine, style = Stroke(geometry.cell * 0.04f, join = StrokeJoin.Round))
    }

    if (path.isNotEmpty()) {
        val points = path.map { geometry.center(it) }
        for ((a, b) in points.zipWithNext()) drawLine(HexWinGlow, a, b, geometry.cell * 0.16f, StrokeCap.Round)
        for (p in points) drawCircle(HexWinGlow, radius = geometry.cell * 0.14f, center = p)
    }

    if (lastMove in 0 until n * n) {
        drawCircle(Color.White, radius = geometry.cell * 0.16f, center = geometry.center(lastMove))
    }
}
