package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.HomeAnnouncement
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HomeAnnouncementBanner(
    announcement: HomeAnnouncement?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = announcement != null && announcement.isActive,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (announcement == null || !announcement.isActive) return@AnimatedVisibility

        var currentEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(announcement.endsAtMs) {
            while (announcement.endsAtMs != null && announcement.endsAtMs > currentEpochMs) {
                delay(1000L)
                currentEpochMs = System.currentTimeMillis()
            }
        }

        val typeConfig = when (announcement.type) {
            "COMPETITION_START" -> AnnouncementTypeUiConfig(
                label = "شروع دوره جدید",
                icon = Icons.Rounded.PlayCircle,
                accentColor = SuccessGreen,
                borderColor = SuccessGreen.copy(alpha = 0.45f),
                bgGradient = listOf(Color(0xFF101915), CharcoalSurface)
            )
            "COMPETITION_END" -> AnnouncementTypeUiConfig(
                label = "ساعات پایانی دوره",
                icon = Icons.Rounded.Timer,
                accentColor = ErrorRed,
                borderColor = ErrorRed.copy(alpha = 0.45f),
                bgGradient = listOf(Color(0xFF1C1112), CharcoalSurface)
            )
            "WINNERS" -> AnnouncementTypeUiConfig(
                label = "اعلام برندگان",
                icon = Icons.Rounded.EmojiEvents,
                accentColor = GoldAccent,
                borderColor = LuxuryBorderStrong,
                bgGradient = listOf(Color(0xFF1E1A10), CharcoalSurface)
            )
            "CUSTOM" -> AnnouncementTypeUiConfig(
                label = "پیام ویژه",
                icon = Icons.Rounded.Star,
                accentColor = GoldLight,
                borderColor = LuxuryBorderStrong,
                bgGradient = listOf(Color(0xFF191712), CharcoalSurface)
            )
            else -> AnnouncementTypeUiConfig(
                label = "اطلاعیه رسمی",
                icon = Icons.Rounded.Campaign,
                accentColor = GoldLight,
                borderColor = LuxuryBorder,
                bgGradient = listOf(DarkGraySurface, CharcoalSurface)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .shadow(10.dp, RoundedCornerShape(18.dp), spotColor = Color.Black)
                .border(1.dp, typeConfig.borderColor, RoundedCornerShape(18.dp))
                .testTag("home_announcement_banner"),
            shape = RoundedCornerShape(18.dp),
            color = CharcoalSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(typeConfig.bgGradient))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(typeConfig.accentColor.copy(alpha = 0.14f))
                            .border(1.dp, typeConfig.accentColor.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = typeConfig.icon,
                            contentDescription = null,
                            tint = typeConfig.accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = typeConfig.label,
                            color = WarmWhite,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (announcement.endsAtMs != null) {
                        val diffMs = (announcement.endsAtMs - currentEpochMs).coerceAtLeast(0L)
                        if (diffMs > 0) {
                            val days = diffMs / (24 * 3600 * 1000L)
                            val hours = (diffMs % (24 * 3600 * 1000L)) / (3600 * 1000L)
                            val minutes = (diffMs % (3600 * 1000L)) / (60 * 1000L)
                            val seconds = (diffMs % (60 * 1000L)) / 1000L

                            val timeText = if (days > 0) {
                                "${days.toPersianDigits()} روز و ${hours.toPersianDigits()} ساعت"
                            } else {
                                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds).toPersianDigits()
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = DarkGraySurface,
                                border = BorderStroke(1.dp, LuxuryBorder)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Timer,
                                        contentDescription = null,
                                        tint = GoldLight,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "فرصت باقی‌مانده: $timeText",
                                        color = GoldLight,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = DarkGraySurface,
                                border = BorderStroke(1.dp, CharcoalBorder)
                            ) {
                                Text(
                                    text = "پایان مهلت دوره",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = announcement.title,
                    color = GoldLight,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = announcement.content,
                    color = WarmWhite.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private data class AnnouncementTypeUiConfig(
    val label: String,
    val icon: ImageVector,
    val accentColor: Color,
    val borderColor: Color,
    val bgGradient: List<Color>
)
