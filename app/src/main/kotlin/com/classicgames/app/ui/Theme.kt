package com.classicgames.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AppColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDEFE6),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF1D4ED8),
    tertiary = Color(0xFFD97706), // used for "O" so it stands out from the teal "X"
    background = Color(0xFFF3F8F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFE3ECEF),
    onSurfaceVariant = Color(0xFF3F4A4D),
    outline = Color(0xFF6F8185),
)

/** Green-to-blue backdrop used behind every screen. */
private val BackgroundBrush = Brush.verticalGradient(listOf(Color(0xFF0FA38F), Color(0xFF1F5FBF)))

val CardShape = RoundedCornerShape(20.dp)

@Composable
fun ClassicGamesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, content = content)
}

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(BackgroundBrush)) { content() }
}

/** White rounded card that sits on top of the gradient. */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        content = content,
    )
}
