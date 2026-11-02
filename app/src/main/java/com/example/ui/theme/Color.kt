package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ====================================================================
// Luxury Premium UI - Core Brand Palette
// ====================================================================
val NavyPrimary = Color(0xFF0B192C)      // سرمه‌ای عمیق
val LuxuryBlack = Color(0xFF101114)      // مشکی لوکس
val GoldAccent = Color(0xFFD4AF37)       // طلایی اصلی (Metallic Gold)
val GoldLight = Color(0xFFF5D77A)        // طلایی روشن (Shimmer Gold)
val SilverAccent = Color(0xFFC0C0C0)     // نقره‌ای
val WarmWhite = Color(0xFFF5F2E9)        // سفید گرم

// Legacy & Supporting Navy Shades
val NavySecondary = Color(0xFF132238)
val NavyLight = Color(0xFF1E3554)
val SlateBackground = Color(0xFF0E1218)   // Deep Luxury Dark Background

// Luxury Surfaces & Cards
val CardSurface = Color(0xFF161B24)      // Elevated Dark Luxury Card
val CardSurfaceElevated = Color(0xFF1D2330)
val DarkBackground = Color(0xFF101114)
val DarkSurface = Color(0xFF161A22)
val DarkSurfaceElevated = Color(0xFF1F2533)

// Gold Palette Variations
val GoldDark = Color(0xFFAA8010)
val BronzeTrophy = Color(0xFFCD7F32)

// Functional & Feedback Colors (Tuned for Luxury Dark UI)
val SuccessGreen = Color(0xFF10B981)
val SuccessGreenLight = Color(0x2A10B981)
val WarningOrange = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)
val ErrorRedLight = Color(0x2AEF4444)

// Luxury Text Colors
val TextPrimary = Color(0xFFF5F2E9)      // Warm White for primary readable text
val TextSecondary = Color(0xFFB0B7C3)    // Soft Silver-Slate for secondary text
val TextMuted = Color(0xFF7E8899)        // Muted Slate for captions
val TextOnDark = Color(0xFFF5F2E9)
val TextGold = Color(0xFFF5D77A)

// Glassmorphism & Borders
val LuxuryBorder = Color(0x33D4AF37)     // 20% Alpha Gold border
val LuxuryBorderStrong = Color(0x66D4AF37) // 40% Alpha Gold border
val SilverBorder = Color(0x33C0C0C0)     // 20% Alpha Silver border
val GlassSurface = Color(0x1AFFFFFF)

// Gradients
val GoldGradientBrush = Brush.linearGradient(
    listOf(Color(0xFFD4AF37), Color(0xFFF5D77A), Color(0xFFAA8010))
)

val GoldHorizontalBrush = Brush.horizontalGradient(
    listOf(Color(0xFFD4AF37), Color(0xFFF5D77A))
)

val SilverGradientBrush = Brush.linearGradient(
    listOf(Color(0xFFE2E8F0), Color(0xFFC0C0C0), Color(0xFF94A3B8))
)

val LuxuryDarkGradient = Brush.verticalGradient(
    listOf(LuxuryBlack, NavyPrimary)
)

val LuxuryCardGradient = Brush.verticalGradient(
    listOf(Color(0xFF1B2230), Color(0xFF131722))
)
