package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ====================================================================
// Luxury Obsidian & Champagne Gold Design System Palette
// ====================================================================

// Primary Canvas & Surface Hierarchy
val ObsidianBlack = Color(0xFF080808)        // مشکی عمیق (پس‌زمینه اصلی)
val CharcoalSurface = Color(0xFF121212)      // مشکی زغالی (سطح اول کارت‌ها)
val DarkGraySurface = Color(0xFF1B1B1B)      // خاکستری تیره (سطح دوم / عناصر برجسته)
val ElevatedObsidian = Color(0xFF232323)     // سطح تعاملی / حالت فشرده

// Gold Accent Spectrum (Controlled Luxury Use)
val GoldAccent = Color(0xFFD4AF37)           // طلایی اصلی
val GoldLight = Color(0xFFE8D5A3)            // طلایی شامپاینی
val GoldDark = Color(0xFFB99745)             // طلایی مات

// Typography Colors
val WarmWhite = Color(0xFFF5F1E8)            // سفید گرم (متن اصلی)
val TextPrimary = WarmWhite
val TextSecondary = Color(0xFFA7A39A)        // خاکستری متن ثانویه
val TextMuted = Color(0xFF77736B)            // خاکستری کم‌رنگ (راهنما و غیرفعال)
val TextOnDark = WarmWhite
val TextGold = GoldLight

// Legacy Aliases Remapped to Exact Obsidian/Charcoal Tokens for Seamless Compatibility
val LuxuryBlack = ObsidianBlack
val NavyPrimary = CharcoalSurface
val NavySecondary = DarkGraySurface
val NavyLight = ElevatedObsidian
val SlateBackground = ObsidianBlack
val CardSurface = CharcoalSurface
val CardSurfaceElevated = DarkGraySurface
val DarkBackground = ObsidianBlack
val DarkSurface = CharcoalSurface
val DarkSurfaceElevated = DarkGraySurface
val SilverAccent = Color(0xFFC8C4BC)
val BronzeTrophy = Color(0xFFC08A54)

// Refined Muted Semantic Feedback Colors (No Neon, High Legibility on Obsidian)
val SuccessGreen = Color(0xFF2EA070)
val SuccessGreenLight = Color(0x1E2EA070)
val WarningOrange = Color(0xFFD89A3C)
val WarningOrangeLight = Color(0x1ED89A3C)
val ErrorRed = Color(0xFFD65A5A)
val ErrorRedLight = Color(0x1ED65A5A)

// Subtle Hairline Borders & Overlays
val CharcoalBorder = Color(0xFF282828)
val LuxuryBorder = Color(0x33D4AF37)         // 20% Gold hairline
val LuxuryBorderStrong = Color(0x66D4AF37)   // 40% Gold accent hairline
val SilverBorder = Color(0x2EC8C4BC)
val GlassSurface = Color(0x14F5F1E8)
val DockSurfaceColor = Color(0xF5121212)

// Subtle Editorial Gradients
val ObsidianCanvasGradient = Brush.verticalGradient(
    listOf(ObsidianBlack, Color(0xFF0E0E0E), ObsidianBlack)
)

val LuxuryDarkGradient = ObsidianCanvasGradient

val LuxuryCardGradient = Brush.verticalGradient(
    listOf(DarkGraySurface, CharcoalSurface)
)

val LuxuryElevatedCardGradient = Brush.verticalGradient(
    listOf(Color(0xFF201E19), CharcoalSurface)
)

val GoldGradientBrush = Brush.linearGradient(
    listOf(GoldLight, GoldAccent, GoldDark)
)

val GoldHorizontalBrush = Brush.horizontalGradient(
    listOf(GoldAccent, GoldLight)
)

val GoldHairlineBrush = Brush.horizontalGradient(
    listOf(Color.Transparent, GoldAccent.copy(alpha = 0.55f), GoldLight.copy(alpha = 0.75f), GoldAccent.copy(alpha = 0.55f), Color.Transparent)
)

val SilverGradientBrush = Brush.linearGradient(
    listOf(Color(0xFFE5E2DA), SilverAccent, Color(0xFF949088))
)
