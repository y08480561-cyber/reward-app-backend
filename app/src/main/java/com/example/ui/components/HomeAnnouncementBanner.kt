package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
                icon = Icons.Rounded.PlayCircle,
                accentColor = SuccessGreen,
                borderColor = SuccessGreen.copy(alpha = 0.35f)
            )
            "COMPETITION_END" -> AnnouncementTypeUiConfig(
                icon = Icons.Rounded.Timer,
                accentColor = ErrorRed,
                borderColor = ErrorRed.copy(alpha = 0.35f)
            )
            "WINNERS" -> AnnouncementTypeUiConfig(
                icon = Icons.Rounded.EmojiEvents,
                accentColor = GoldAccent,
                borderColor = LuxuryBorderStrong
            )
            "CUSTOM" -> AnnouncementTypeUiConfig(
                icon = Icons.Rounded.Star,
                accentColor = GoldLight,
                borderColor = LuxuryBorder
            )
            else -> AnnouncementTypeUiConfig(
                icon = Icons.Rounded.Campaign,
                accentColor = GoldLight,
                borderColor = CharcoalBorder
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .testTag("home_announcement_banner"),
            shape = RoundedCornerShape(16.dp),
            color = CharcoalSurface,
            border = BorderStroke(1.dp, typeConfig.borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(typeConfig.accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeConfig.icon,
                        contentDescription = null,
                        tint = typeConfig.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = announcement.title,
                            color = WarmWhite,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (announcement.endsAtMs != null) {
                            val diffMs = (announcement.endsAtMs - currentEpochMs).coerceAtLeast(0L)
                            if (diffMs > 0) {
                                val days = diffMs / (24 * 3600 * 1000L)
                                val hours = (diffMs % (24 * 3600 * 1000L)) / (3600 * 1000L)
                                val minutes = (diffMs % (3600 * 1000L)) / (60 * 1000L)
                                val seconds = (diffMs % (60 * 1000L)) / 1000L
                                val timeText = if (days > 0) {
                                    "${days.toPersianDigits()} روز"
                                } else {
                                    String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds).toPersianDigits()
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = timeText,
                                    color = GoldLight,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    if (announcement.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = announcement.content,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private data class AnnouncementTypeUiConfig(
    val icon: ImageVector,
    val accentColor: Color,
    val borderColor: Color
)
