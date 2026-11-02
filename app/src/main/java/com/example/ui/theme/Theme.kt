package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Unified Luxury Premium Color Scheme
private val LuxuryColorScheme = darkColorScheme(
    primary = GoldAccent,
    onPrimary = LuxuryBlack,
    primaryContainer = NavyPrimary,
    onPrimaryContainer = WarmWhite,
    secondary = GoldLight,
    onSecondary = LuxuryBlack,
    secondaryContainer = Color(0xFF2A2412),
    onSecondaryContainer = GoldLight,
    tertiary = SilverAccent,
    onTertiary = LuxuryBlack,
    tertiaryContainer = Color(0xFF1E2430),
    onTertiaryContainer = WarmWhite,
    background = LuxuryBlack,
    onBackground = WarmWhite,
    surface = CardSurface,
    onSurface = WarmWhite,
    surfaceVariant = CardSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = LuxuryBorder,
    outlineVariant = Color(0x22FFFFFF),
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force consistent Luxury Dark & Gold aesthetic
    dynamicColor: Boolean = false, // Keep consistent Luxury Gold & Deep Navy brand identity
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LuxuryColorScheme,
        typography = Typography,
        content = content
    )
}
