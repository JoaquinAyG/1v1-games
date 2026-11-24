package com.classicgames.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A game in the lobby. [icon] is null for "coming soon" placeholders. */
class GameEntry(
    val id: String,
    val title: String,
    val icon: (@Composable () -> Unit)? = null,
)

/** Add new games here; the lobby grid picks them up. */
val GAMES = listOf(
    GameEntry(id = "tictactoe", title = "Tic-Tac-Toe", icon = { TicTacToeIcon() }),
    GameEntry(id = "battleship", title = "Battleship", icon = { BattleshipIcon() }),
    GameEntry(id = "connectfour", title = "Connect Four", icon = { ConnectFourIcon() }),
    GameEntry(id = "othello", title = "Othello", icon = { OthelloIcon() }),
    GameEntry(id = "checkers", title = "Checkers", icon = { CheckersIcon() }),
)

/** Lays the games out in rows of [columns] tiles. Gaps in the last row stay empty so tiles keep their size. */
@Composable
fun GameGrid(
    games: List<GameEntry>,
    enabled: Boolean,
    onGameClick: (GameEntry) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (rowGames in games.chunked(columns)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (game in rowGames) {
                    Box(Modifier.weight(1f)) {
                        GameTile(game, enabled = enabled, onClick = { onGameClick(game) })
                    }
                }
                repeat(columns - rowGames.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Rounded square with the game's icon and its name underneath. */
@Composable
private fun GameTile(game: GameEntry, enabled: Boolean, onClick: () -> Unit) {
    val playable = game.icon != null
    Column(
        modifier = Modifier.fillMaxWidth().alpha(if (playable && !enabled) 0.55f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(if (playable) Color.White else Color.White.copy(alpha = 0.18f))
                .clickable(enabled = playable && enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (game.icon != null) {
                game.icon.invoke()
            } else {
                Text("?", color = Color.White.copy(alpha = 0.7f), fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = game.title,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** Little # board with an X, an O and another X, drawn from scratch so it scales with the tile. */
@Composable
fun TicTacToeIcon(modifier: Modifier = Modifier) {
    val xColor = MaterialTheme.colorScheme.primary
    val oColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)

    Canvas(modifier = modifier.fillMaxSize().padding(16.dp)) {
        val side = size.minDimension
        val cell = side / 3f
        val stroke = side * 0.055f

        for (i in 1..2) {
            drawLine(gridColor, Offset(cell * i, 0f), Offset(cell * i, side), stroke, StrokeCap.Round)
            drawLine(gridColor, Offset(0f, cell * i), Offset(side, cell * i), stroke, StrokeCap.Round)
        }

        fun center(col: Int, row: Int) = Offset(cell * (col + 0.5f), cell * (row + 0.5f))
        val r = cell * 0.27f
        drawX(center(0, 0), r, xColor, stroke)
        drawO(center(1, 1), r, oColor, stroke)
        drawX(center(2, 2), r, xColor, stroke)
    }
}

/** A blue board with a few red and yellow discs, drawn from scratch. */
@Composable
fun ConnectFourIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize().padding(14.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = ConnectBoardBlue,
            topLeft = Offset(0.02f * w, 0.10f * h),
            size = Size(0.96f * w, 0.80f * h),
            cornerRadius = CornerRadius(0.12f * w, 0.12f * w),
        )

        // 4 columns x 3 rows of slots; 'R' red, 'Y' yellow, '.' empty (row by row from the top).
        val slots = "..Y." + ".RY." + "RRYR"
        val slotW = 0.96f * w / 4f
        val slotH = 0.80f * h / 3f
        val radius = minOf(slotW, slotH) * 0.36f
        for ((i, mark) in slots.withIndex()) {
            val color = when (mark) {
                'R' -> ConnectRed
                'Y' -> ConnectYellow
                else -> Color(0xFFEAF1FF)
            }
            val center = Offset(0.02f * w + slotW * (i % 4 + 0.5f), 0.10f * h + slotH * (i / 4 + 0.5f))
            drawCircle(color, radius = radius, center = center)
        }
    }
}

/** A small checkered board with red and black pieces (one of them a king), drawn from scratch. */
@Composable
fun CheckersIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize().padding(14.dp)) {
        val side = minOf(size.width, size.height) * 0.96f
        val left = size.width * 0.02f
        val top = size.height * 0.02f
        val cell = side / 4f

        val boardShape = Path().apply {
            addRoundRect(RoundRect(left, top, left + side, top + side, CornerRadius(0.12f * side, 0.12f * side)))
        }
        clipPath(boardShape) {
            drawRect(CheckersLightSquare, topLeft = Offset(left, top), size = Size(side, side))
            for (r in 0 until 4) for (c in 0 until 4) {
                if ((r + c) % 2 == 1) {
                    drawRect(CheckersDarkSquare, topLeft = Offset(left + c * cell, top + r * cell), size = Size(cell, cell))
                }
            }
        }

        // (row, column, isRed, isKing) on dark squares.
        val pieces = listOf(
            listOf(0, 1, 0, 0), listOf(0, 3, 0, 0), listOf(1, 2, 0, 0),
            listOf(3, 0, 1, 0), listOf(3, 2, 1, 0), listOf(2, 1, 1, 1),
        )
        for ((r, c, red, king) in pieces) {
            val center = Offset(left + (c + 0.5f) * cell, top + (r + 0.5f) * cell)
            drawCircle(if (red == 1) CheckersRed else CheckersBlack, radius = cell * 0.38f, center = center)
            if (king == 1) drawCircle(CheckersGold, radius = cell * 0.2f, center = center, style = Stroke(cell * 0.07f))
        }
    }
}

/** A green board with black and white discs, drawn from scratch. */
@Composable
fun OthelloIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize().padding(14.dp)) {
        val w = size.width
        val h = size.height
        val left = 0.02f * w
        val top = 0.02f * h
        val side = minOf(0.96f * w, 0.96f * h)
        drawRoundRect(
            color = OthelloBoardGreen,
            topLeft = Offset(left, top),
            size = Size(side, side),
            cornerRadius = CornerRadius(0.12f * side, 0.12f * side),
        )

        val cell = side / 3f
        val line = Color(0xFF1B5E20)
        for (i in 1..2) {
            drawLine(line, Offset(left + cell * i, top + 0.04f * side), Offset(left + cell * i, top + 0.96f * side), side * 0.02f)
            drawLine(line, Offset(left + 0.04f * side, top + cell * i), Offset(left + 0.96f * side, top + cell * i), side * 0.02f)
        }

        // 3 x 3 slots, row by row: 'B' black disc, 'W' white disc, '.' empty.
        val slots = "BW." + "WBW" + ".WB"
        for ((i, mark) in slots.withIndex()) {
            if (mark == '.') continue
            val center = Offset(left + cell * (i % 3 + 0.5f), top + cell * (i / 3 + 0.5f))
            drawCircle(if (mark == 'B') OthelloBlack else OthelloWhite, radius = cell * 0.36f, center = center)
        }
    }
}

/** A little warship on the waves. */
@Composable
fun BattleshipIcon(modifier: Modifier = Modifier) {
    val hullColor = Color(0xFF37474F)
    val cabinColor = MaterialTheme.colorScheme.secondary
    val funnelColor = MaterialTheme.colorScheme.tertiary
    val waterColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)

    Canvas(modifier = modifier.fillMaxSize().padding(14.dp)) {
        val w = size.width
        val h = size.height

        val hull = Path().apply {
            moveTo(0.04f * w, 0.50f * h)
            lineTo(0.96f * w, 0.50f * h)
            lineTo(0.80f * w, 0.72f * h)
            lineTo(0.20f * w, 0.72f * h)
            close()
        }
        drawPath(hull, hullColor)
        drawRect(cabinColor, topLeft = Offset(0.32f * w, 0.30f * h), size = Size(0.34f * w, 0.20f * h))
        drawRect(funnelColor, topLeft = Offset(0.42f * w, 0.14f * h), size = Size(0.12f * w, 0.16f * h))
        drawLine(hullColor, Offset(0.66f * w, 0.42f * h), Offset(0.90f * w, 0.35f * h), w * 0.045f, StrokeCap.Round)
        drawLine(waterColor, Offset(0.04f * w, 0.84f * h), Offset(0.96f * w, 0.84f * h), w * 0.05f, StrokeCap.Round)
        drawLine(waterColor, Offset(0.16f * w, 0.94f * h), Offset(0.84f * w, 0.94f * h), w * 0.05f, StrokeCap.Round)
    }
}

private fun DrawScope.drawX(center: Offset, r: Float, color: Color, stroke: Float) {
    drawLine(color, center + Offset(-r, -r), center + Offset(r, r), stroke, StrokeCap.Round)
    drawLine(color, center + Offset(-r, r), center + Offset(r, -r), stroke, StrokeCap.Round)
}

private fun DrawScope.drawO(center: Offset, r: Float, color: Color, stroke: Float) {
    drawCircle(color, radius = r, center = center, style = Stroke(width = stroke))
}
