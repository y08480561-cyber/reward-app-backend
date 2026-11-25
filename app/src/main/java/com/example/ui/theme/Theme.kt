package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LuxuryColorScheme = darkColorScheme(
    primary = GoldAccent,
    onPrimary = ObsidianBlack,
    primaryContainer = DarkGraySurface,
    onPrimaryContainer = WarmWhite,
    secondary = GoldLight,
    onSecondary = ObsidianBlack,
    secondaryContainer = Color(0xFF231E12),
    onSecondaryContainer = GoldLight,
    tertiary = GoldDark,
    onTertiary = ObsidianBlack,
    tertiaryContainer = DarkGraySurface,
    onTertiaryContainer = WarmWhite,
    background = ObsidianBlack,
    onBackground = WarmWhite,
    surface = CharcoalSurface,
    onSurface = WarmWhite,
    surfaceVariant = DarkGraySurface,
    onSurfaceVariant = TextSecondary,
    outline = LuxuryBorder,
    outlineVariant = CharcoalBorder,
    error = ErrorRed,
    onError = WarmWhite
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = LuxuryColorScheme,
            typography = Typography,
            shapes = LuxuryShapes,
            content = content
        )
    }
}
