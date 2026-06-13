package com.aihomecloud.ahcplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AhcColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = TextPrimary,
    secondary = AccentDim,
    onSecondary = TextPrimary,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    surfaceVariant = BgCardFocused,
    onSurfaceVariant = TextSecondary,
    error = androidx.compose.ui.graphics.Color(0xFFCF6679)
)

@Composable
fun AhcPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AhcColorScheme,
        typography = AppTypography,
        content = content
    )
}
