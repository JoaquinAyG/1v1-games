package com.classicgames.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.classicgames.core.MazeState

val MazeFloor = Color(0xFFF6F2E7)
val MazeWall = Color(0xFF2F3E4E)
val MazeWalker = Color(0xFFE5484D)
val MazeExit = Color(0xFF2E9E5B)
private val MazeTrail = Color(0x66E5484D)
private val MazeSeen = Color(0xFFFFE7A3)
private val MazeOutside = Color(0xFFB8C2CC)

/** The wall bit of each direction, as on MazeState. */
private val WallBits = mapOf("up" to 1, "right" to 2, "down" to 4, "left" to 8)

/** Arrows by direction. */
private val Arrows = mapOf("up" to "↑", "right" to "→", "down" to "↓", "left" to "←")

/**
 * Maze Guide, as the guide or as the walker. The guide gets the whole maze, with the walker, their trail, the exit and
 * the cells the walker can see shaded, and four arrows that send a hint. The walker gets the guide's latest hint and the
 * 3 x 3 cells around them, drawn large, with a move button on each side (a side with a wall is disabled). Both see the
 * moves used against the limit, and once the game is over the whole maze with the walker's trail.
 */
@Composable
fun MazePanel(
    game: MazeState,
    notice: String?,
    onMove: (String) -> Unit,
    onHint: (String) -> Unit,
    onPlayAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = game.status == "playing"
    val guiding = game.role == "guide"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when {
                game.status == "won" -> "You made it out together!"
                game.status == "lost" -> "Out of moves: you both lost"
                guiding -> "Guide ${game.walker} to the exit"
                else -> "Find the exit: ${game.guide} can see the way"
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            if (playing) "Moves ${game.movesUsed} / ${game.moveLimit}, ${game.moveLimit - game.movesUsed} left"
            else "Moves ${game.movesUsed} / ${game.moveLimit}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )

        when {
            !playing -> {
                WholeMaze(game, shadeWindow = false)
                Text(game.result, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            guiding -> {
                WholeMaze(game, shadeWindow = true)
                HintButtons(game, onHint)
            }
            else -> {
                LatestHint(game)
                Window(game, onMove)
            }
        }

        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        if (!playing) {
            Button(onClick = onPlayAgain) { Text("Play again") }
            Text(
                "Roles swap: you'll be the ${if (guiding) "walker" else "guide"}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** The whole maze, cells about 40 dp wide (smaller on a narrow screen). [shadeWindow] shades what the walker sees. */
@Composable
private fun WholeMaze(game: MazeState, shadeWindow: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = min(40.dp * game.size, maxWidth)
        Canvas(Modifier.size(side)) {
            drawMaze(
                cells = game.size,
                walls = game.walls,
                exit = game.exit,
                walker = game.position,
                trail = game.trail,
                start = game.start,
                shadeWindow = shadeWindow,
            )
        }
    }
}

/** The guide's four arrows, and what they sent last. */
@Composable
private fun HintButtons(game: MazeState, onHint: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (direction in listOf("left", "up", "down", "right")) {
            FilledTonalButton(
                onClick = { onHint(direction) },
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                DirectionArrow(direction, Modifier.size(20.dp))
                Text(" ${direction.replaceFirstChar { it.uppercase() }}", maxLines = 1)
            }
        }
    }
    Text(
        when {
            game.hints == 0 -> "Tap an arrow to tell ${game.walker} which way to go. The shaded cells are all they see"
            else -> "Your last hint: ${Arrows.getValue(game.hint)} ${game.hint}" + (movesAgo(game)?.let { ", $it" } ?: "")
        },
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
}

/** "2 moves ago" when the walker has moved since the last hint, or null. */
private fun movesAgo(game: MazeState): String? = when (val moves = game.movesUsed - game.hintAt) {
    0 -> null
    1 -> "1 move ago"
    else -> "$moves moves ago"
}

/** The walker's banner with the guide's latest hint: bright until the walker moves, and it pulses when a hint comes. */
@Composable
private fun LatestHint(game: MazeState) {
    val fresh = game.hints > 0 && game.hintAt == game.movesUsed
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(game.hints) {
        if (game.hints > 0) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(700))
        }
    }
    Surface(
        color = if (fresh) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        ) {
            if (game.hints == 0) {
                Text(
                    "${game.guide} sees the whole maze. Wait for a hint, or ask in the chat",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                DirectionArrow(
                    game.hint,
                    Modifier.size(48.dp).scale(1f + 0.35f * pulse.value),
                    color = if (fresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column {
                    Text("${game.guide} says: go ${game.hint}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    movesAgo(game)?.let { Text("Sent $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

/** The 3 x 3 cells around the walker, drawn large, with a move button on each side. */
@Composable
private fun Window(game: MazeState, onMove: (String) -> Unit) {
    val here = game.window.getOrElse(4) { 0 }

    @Composable
    fun Arrow(direction: String, modifier: Modifier) {
        FilledTonalButton(
            onClick = { onMove(direction) },
            enabled = here and WallBits.getValue(direction) == 0,
            contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(14.dp),
            modifier = modifier,
        ) { DirectionArrow(direction, Modifier.size(26.dp)) }
    }

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val button = 52.dp
        val gap = 6.dp
        val side = min(216.dp, maxWidth - (button + gap) * 2)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
            Arrow("up", Modifier.size(width = side * 0.55f, height = button))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                Arrow("left", Modifier.size(width = button, height = side * 0.55f))
                Canvas(Modifier.size(side)) {
                    drawMaze(cells = 3, walls = game.window, exit = game.windowExit, walker = 4)
                }
                Arrow("right", Modifier.size(width = button, height = side * 0.55f))
            }
            Arrow("down", Modifier.size(width = side * 0.55f, height = button))
        }
    }
}

/**
 * An arrow pointing "up", "right", "down" or "left", drawn rather than typed so that every direction sits in the middle
 * of its space (the arrow characters of a font sit at different heights). It takes the content colour by default.
 */
@Composable
private fun DirectionArrow(direction: String, modifier: Modifier, color: Color = LocalContentColor.current) {
    val degrees = 90f * listOf("up", "right", "down", "left").indexOf(direction).coerceAtLeast(0)
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = s * 0.13f
        fun at(x: Float, y: Float) = Offset(x * s, y * s)
        rotate(degrees) {
            drawLine(color, at(0.5f, 0.86f), at(0.5f, 0.16f), stroke, StrokeCap.Round)
            drawLine(color, at(0.5f, 0.14f), at(0.2f, 0.44f), stroke, StrokeCap.Round)
            drawLine(color, at(0.5f, 0.14f), at(0.8f, 0.44f), stroke, StrokeCap.Round)
        }
    }
}

/**
 * Draws a square maze of [cells] x [cells] over the whole drawing area: [walls] one mask per cell, row by row (bits 1 up,
 * 2 right, 4 down, 8 left), or -1 for a cell past the edge of the maze, drawn grey. Then the exit, the walker's [trail]
 * and the walker, each left out when -1 or empty. [start] gets a small ring, and [shadeWindow] shades the 3 x 3 cells
 * around the walker.
 */
fun DrawScope.drawMaze(
    cells: Int,
    walls: List<Int>,
    exit: Int = -1,
    walker: Int = -1,
    trail: List<Int> = emptyList(),
    start: Int = -1,
    shadeWindow: Boolean = false,
) {
    if (walls.size != cells * cells) return
    val stroke = maxOf(2.dp.toPx(), size.minDimension / cells * 0.08f)
    val origin = stroke / 2
    val side = (size.minDimension - stroke) / cells
    fun topLeft(cell: Int) = Offset(origin + cell % cells * side, origin + cell / cells * side)
    fun center(cell: Int) = topLeft(cell) + Offset(side / 2, side / 2)

    drawRect(MazeFloor, topLeft = Offset(origin, origin), size = Size(side * cells, side * cells))
    for (cell in walls.indices) {
        if (walls[cell] < 0) drawRect(MazeOutside, topLeft = topLeft(cell), size = Size(side, side))
    }
    if (shadeWindow && walker >= 0) {
        val row = walker / cells
        val column = walker % cells
        val top = maxOf(row - 1, 0)
        val left = maxOf(column - 1, 0)
        drawRect(
            MazeSeen,
            topLeft = Offset(origin + left * side, origin + top * side),
            size = Size((minOf(column + 1, cells - 1) - left + 1) * side, (minOf(row + 1, cells - 1) - top + 1) * side),
        )
    }
    if (exit >= 0) drawExit(topLeft(exit), side)
    if (start >= 0) drawCircle(MazeWall.copy(alpha = 0.45f), radius = side * 0.16f, center = center(start), style = Stroke(side * 0.06f))
    if (trail.size > 1) {
        val path = Path().apply {
            moveTo(center(trail.first()).x, center(trail.first()).y)
            for (cell in trail.drop(1)) lineTo(center(cell).x, center(cell).y)
        }
        drawPath(path, MazeTrail, style = Stroke(side * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    if (walker >= 0) {
        drawCircle(MazeWalker, radius = side * 0.3f, center = center(walker))
        drawCircle(Color.White, radius = side * 0.3f, center = center(walker), style = Stroke(side * 0.06f))
    }
    for (cell in walls.indices) {
        val mask = walls[cell]
        if (mask <= 0) continue
        val a = topLeft(cell)
        val b = a + Offset(side, 0f)
        val c = a + Offset(side, side)
        val d = a + Offset(0f, side)
        if (mask and 1 != 0) drawLine(MazeWall, a, b, stroke, StrokeCap.Round)
        if (mask and 2 != 0) drawLine(MazeWall, b, c, stroke, StrokeCap.Round)
        if (mask and 4 != 0) drawLine(MazeWall, d, c, stroke, StrokeCap.Round)
        if (mask and 8 != 0) drawLine(MazeWall, a, d, stroke, StrokeCap.Round)
    }
}

/** The exit: a green square with a white flag. */
private fun DrawScope.drawExit(topLeft: Offset, side: Float) {
    val inset = side * 0.12f
    drawRoundRect(
        MazeExit,
        topLeft = topLeft + Offset(inset, inset),
        size = Size(side - 2 * inset, side - 2 * inset),
        cornerRadius = CornerRadius(side * 0.14f),
    )
    fun at(x: Float, y: Float) = topLeft + Offset(x * side, y * side)
    drawLine(Color.White, at(0.36f, 0.26f), at(0.36f, 0.76f), side * 0.06f, StrokeCap.Round)
    val flag = Path().apply {
        moveTo(at(0.38f, 0.26f).x, at(0.38f, 0.26f).y)
        lineTo(at(0.7f, 0.37f).x, at(0.7f, 0.37f).y)
        lineTo(at(0.38f, 0.48f).x, at(0.38f, 0.48f).y)
        close()
    }
    drawPath(flag, Color.White)
}
