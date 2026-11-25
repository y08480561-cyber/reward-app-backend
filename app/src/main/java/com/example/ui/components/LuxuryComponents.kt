package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Formats integer/long values with Persian thousand separators and Persian digits.
 */
fun formatNumberPersian(value: Long): String {
    val formatted = NumberFormat.getNumberInstance(Locale.US).format(value).replace(',', '٬')
    return formatted.toPersianDigits()
}

fun formatNumberPersian(value: Int): String = formatNumberPersian(value.toLong())

fun formatTomanPersian(amount: Long): String = "${formatNumberPersian(amount)} تومان"

/**
 * Polite time-aware Persian greeting based on Tehran local hour.
 */
fun getTehranGreeting(): String {
    val hour = try {
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).get(Calendar.HOUR_OF_DAY)
    } catch (_: Exception) {
        12
    }
    return when (hour) {
        in 5..11 -> "صبح بخیر"
        in 12..16 -> "روز بخیر"
        in 17..20 -> "عصر بخیر"
        else -> "شب بخیر"
    }
}

/**
 * Base full-screen obsidian canvas with an ultra-subtle editorial gold atmospheric glow at top.
 */
@Composable
fun LuxuryScreenCanvas(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // Subtle editorial gold radial sheen behind top area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GoldAccent.copy(alpha = 0.065f),
                            GoldDark.copy(alpha = 0.02f),
                            Color.Transparent
                        ),
                        radius = 750f
                    )
                )
        )
        content()
    }
}

/**
 * Unified top header with clean statusBarsPadding(), optional back navigation,
 * crisp title/subtitle hierarchy, and optional trailing score or action slot.
 */
@Composable
fun LuxuryTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backTestTag: String? = null,
    scoreValue: Int? = null,
    onScoreClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ObsidianBlack.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .let { mod ->
                                if (backTestTag != null) mod.testTag(backTestTag) else mod
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DarkGraySurface)
                                .border(1.dp, CharcoalBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "بازگشت",
                                tint = GoldLight,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = WarmWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (scoreValue != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    LuxuryScorePill(
                        score = scoreValue,
                        onClick = onScoreClick
                    )
                }

                if (trailingContent != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingContent()
                }
            }

            LuxuryHairlineDivider(useGoldAccent = true)
        }
    }
}

@Composable
fun LuxuryScorePill(
    score: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(50))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(50),
        color = DarkGraySurface,
        border = BorderStroke(1.dp, LuxuryBorderStrong)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "${formatNumberPersian(score)} امتیاز",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = GoldLight
            )
        }
    }
}

@Composable
fun LuxuryHairlineDivider(
    modifier: Modifier = Modifier,
    useGoldAccent: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                if (useGoldAccent) {
                    GoldHairlineBrush
                } else {
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, CharcoalBorder, CharcoalBorder, Color.Transparent)
                    )
                }
            )
    )
}

/**
 * Standard refined Charcoal Surface card with 1dp hairline border and subtle press feedback when clickable.
 */
@Composable
fun LuxuryCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = CharcoalSurface,
    borderColor: Color = CharcoalBorder,
    elevation: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_press_scale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation, shape, spotColor = Color.Black.copy(alpha = 0.6f))
            .clip(shape)
            .let { mod ->
                if (onClick != null) {
                    mod.clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.material3.ripple(color = GoldAccent),
                        onClick = onClick
                    )
                } else mod
            },
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Featured Luxury Card for hero sections (Score, Active Competition, Gift Banner, VIP Tier).
 */
@Composable
fun LuxuryHighlightCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    borderBrush: Brush = Brush.linearGradient(
        listOf(GoldAccent.copy(alpha = 0.65f), GoldLight.copy(alpha = 0.25f), GoldDark.copy(alpha = 0.55f))
    ),
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "highlight_card_scale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(14.dp, shape, spotColor = GoldAccent.copy(alpha = 0.16f))
            .clip(shape)
            .let { mod ->
                if (onClick != null) {
                    mod.clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.material3.ripple(color = GoldLight),
                        onClick = onClick
                    )
                } else mod
            },
        shape = shape,
        color = CharcoalSurface,
        border = BorderStroke(1.dp, borderBrush)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(LuxuryElevatedCardGradient)
        ) {
            // Top subtle gold halo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(GoldAccent.copy(alpha = 0.08f), Color.Transparent)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    }
}

/**
 * Primary champagne-to-gold metallic button with tactile press feedback & loading state.
 */
@Composable
fun LuxuryPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    height: Dp = 52.dp
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.975f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "primary_btn_scale"
    )

    Button(
        onClick = {
            if (!isLoading) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        },
        enabled = enabled && !isLoading,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = DarkGraySurface,
            contentColor = ObsidianBlack,
            disabledContentColor = TextMuted
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (enabled && !isLoading) 10.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = GoldAccent.copy(alpha = 0.35f)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .background(
                    brush = if (enabled && !isLoading) {
                        GoldGradientBrush
                    } else {
                        Brush.horizontalGradient(listOf(DarkGraySurface, DarkGraySurface))
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (enabled && !isLoading) GoldLight.copy(alpha = 0.55f) else CharcoalBorder,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = GoldLight,
                        strokeWidth = 2.2.dp,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "لطفاً شکیبا باشید…",
                        style = MaterialTheme.typography.labelLarge,
                        color = GoldLight
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (enabled) ObsidianBlack else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (enabled) ObsidianBlack else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Refined secondary outlined button on dark obsidian surface.
 */
@Composable
fun LuxurySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accentColor: Color = GoldLight
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "secondary_btn_scale"
    )

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (enabled) accentColor.copy(alpha = 0.45f) else CharcoalBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = DarkGraySurface.copy(alpha = 0.6f),
            contentColor = accentColor,
            disabledContentColor = TextMuted
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) accentColor else TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) accentColor else TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Refined Obsidian TextField with consistent focus borders, supporting text, and optional LTR input.
 */
@Composable
fun LuxuryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    isLtrContent: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val fieldContent = @Composable {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            label = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            placeholder = if (placeholder.isNotBlank()) {
                {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            } else null,
            supportingText = if (!supportingText.isNullOrBlank()) {
                {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            } else null,
            leadingIcon = if (leadingIcon != null) {
                {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = GoldLight,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else null,
            trailingIcon = trailingIcon,
            singleLine = singleLine,
            maxLines = maxLines,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = WarmWhite,
                textAlign = if (isLtrContent) TextAlign.Left else TextAlign.Start
            ),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = WarmWhite,
                unfocusedTextColor = WarmWhite,
                focusedBorderColor = GoldAccent,
                unfocusedBorderColor = CharcoalBorder,
                focusedLabelColor = GoldLight,
                unfocusedLabelColor = TextSecondary,
                focusedContainerColor = Color(0xFF151515),
                unfocusedContainerColor = Color(0xFF101010),
                cursorColor = GoldAccent
            )
        )
    }

    if (isLtrContent) {
        // Keep RTL outer alignment for labels while letting phone/OTP text read naturally
        fieldContent()
    } else {
        fieldContent()
    }
}

enum class LuxuryBadgeTone {
    GOLD,
    CHAMPAGNE,
    SILVER,
    SUCCESS,
    WARNING,
    ERROR,
    NEUTRAL
}

@Composable
fun LuxuryBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: LuxuryBadgeTone = LuxuryBadgeTone.GOLD
) {
    val (bg, border, fg) = when (tone) {
        LuxuryBadgeTone.GOLD -> Triple(GoldAccent.copy(alpha = 0.16f), GoldAccent.copy(alpha = 0.5f), GoldLight)
        LuxuryBadgeTone.CHAMPAGNE -> Triple(GoldLight.copy(alpha = 0.14f), GoldLight.copy(alpha = 0.4f), GoldLight)
        LuxuryBadgeTone.SILVER -> Triple(SilverAccent.copy(alpha = 0.14f), SilverAccent.copy(alpha = 0.4f), SilverAccent)
        LuxuryBadgeTone.SUCCESS -> Triple(SuccessGreenLight, SuccessGreen.copy(alpha = 0.45f), Color(0xFF6EE7B7))
        LuxuryBadgeTone.WARNING -> Triple(WarningOrangeLight, WarningOrange.copy(alpha = 0.45f), Color(0xFFF3C27A))
        LuxuryBadgeTone.ERROR -> Triple(ErrorRedLight, ErrorRed.copy(alpha = 0.45f), Color(0xFFF49A9A))
        LuxuryBadgeTone.NEUTRAL -> Triple(DarkGraySurface, CharcoalBorder, TextSecondary)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LuxuryTierBadge(
    panelType: String,
    multiplier: Int? = null,
    modifier: Modifier = Modifier
) {
    val normalized = panelType.lowercase()
    when {
        normalized == "gold" -> {
            val multText = (multiplier ?: 6).toPersianDigits()
            LuxuryBadge(
                text = "سطح طلایی • ضریب ${multText}x",
                icon = Icons.Rounded.WorkspacePremium,
                tone = LuxuryBadgeTone.GOLD,
                modifier = modifier
            )
        }
        normalized == "silver" -> {
            val multText = (multiplier ?: 3).toPersianDigits()
            LuxuryBadge(
                text = "سطح نقره‌ای • ضریب ${multText}x",
                icon = Icons.Rounded.Verified,
                tone = LuxuryBadgeTone.SILVER,
                modifier = modifier
            )
        }
        else -> {
            val multText = (multiplier ?: 1).toPersianDigits()
            LuxuryBadge(
                text = "عضویت پایه • ضریب ${multText}x",
                icon = Icons.Rounded.Star,
                tone = LuxuryBadgeTone.NEUTRAL,
                modifier = modifier
            )
        }
    }
}

enum class FeedbackTone {
    ERROR,
    SUCCESS,
    INFO
}

@Composable
fun LuxuryFeedbackBanner(
    message: String?,
    tone: FeedbackTone,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val (bg, border, iconTint, icon) = when (tone) {
            FeedbackTone.ERROR -> Quadruple(
                Color(0xFF1D1213),
                ErrorRed.copy(alpha = 0.45f),
                ErrorRed,
                Icons.Rounded.ErrorOutline
            )
            FeedbackTone.SUCCESS -> Quadruple(
                Color(0xFF101C17),
                SuccessGreen.copy(alpha = 0.45f),
                SuccessGreen,
                Icons.Rounded.CheckCircle
            )
            FeedbackTone.INFO -> Quadruple(
                Color(0xFF191610),
                LuxuryBorderStrong,
                GoldLight,
                Icons.Rounded.Info
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            color = bg,
            border = BorderStroke(1.dp, border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmWhite,
                    modifier = Modifier.weight(1f)
                )
                if (onRetry != null) {
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "تلاش مجدد",
                            tint = GoldLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "بستن پیام",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun LuxurySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GoldGradientBrush)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = WarmWhite
            )
        }
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelMedium,
                color = GoldLight,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun LuxuryLoadingState(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(CharcoalSurface)
                .border(1.dp, LuxuryBorderStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = GoldAccent,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LuxuryEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(DarkGraySurface)
                .border(1.dp, LuxuryBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GoldLight,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = WarmWhite,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(22.dp))
            LuxuryPrimaryButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.widthIn(max = 260.dp)
            )
        }
    }
}

/**
 * Refined Iranian mobile number formatting in Persian digits (keeps natural reading order).
 */
fun formatPhonePersian(phone: String): String {
    return phone.trim().toPersianDigits()
}
