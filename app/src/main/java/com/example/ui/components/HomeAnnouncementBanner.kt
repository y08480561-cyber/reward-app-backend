package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HomeAnnouncement
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.ui.theme.NavyLight
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.NavySecondary
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

        // Live ticking for end-date countdown if applicable
        var currentEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(announcement.endsAtMs) {
            while (announcement.endsAtMs != null && announcement.endsAtMs > currentEpochMs) {
                delay(1000L)
                currentEpochMs = System.currentTimeMillis()
            }
        }

        val typeConfig = when (announcement.type) {
            "COMPETITION_START" -> AnnouncementTypeUiConfig(
                label = "شروع مسابقه",
                icon = Icons.Default.PlayCircle,
                badgeColor = Color(0xFF10B981),
                borderColor = Color(0xFF10B981),
                bgGradient = listOf(NavyPrimary, Color(0xFF13364B))
            )
            "COMPETITION_END" -> AnnouncementTypeUiConfig(
                label = "پایان مسابقه",
                icon = Icons.Default.Timer,
                badgeColor = Color(0xFFEF4444),
                borderColor = Color(0xFFF87171),
                bgGradient = listOf(Color(0xFF3B1E28), NavyPrimary)
            )
            "WINNERS" -> AnnouncementTypeUiConfig(
                label = "اعلام برندگان",
                icon = Icons.Default.EmojiEvents,
                badgeColor = GoldAccent,
                borderColor = GoldAccent,
                bgGradient = listOf(Color(0xFF2C2411), NavyPrimary)
            )
            "CUSTOM" -> AnnouncementTypeUiConfig(
                label = "پیام ویژه",
                icon = Icons.Default.Star,
                badgeColor = Color(0xFFA855F7),
                borderColor = Color(0xFFC084FC),
                bgGradient = listOf(Color(0xFF25193B), NavyPrimary)
            )
            else -> AnnouncementTypeUiConfig(
                label = "اطلاعیه مهم",
                icon = Icons.Default.Campaign,
                badgeColor = Color(0xFF38BDF8),
                borderColor = Color(0xFF38BDF8),
                bgGradient = listOf(NavyPrimary, NavySecondary)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(18.dp))
                .border(1.5.dp, typeConfig.borderColor, RoundedCornerShape(18.dp))
                .testTag("home_announcement_banner"),
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(typeConfig.bgGradient))
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header Row: Type Badge + Icon + Remaining Time Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Badge with Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(typeConfig.badgeColor.copy(alpha = 0.2f))
                                .border(1.dp, typeConfig.badgeColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = typeConfig.icon,
                                contentDescription = null,
                                tint = typeConfig.badgeColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = typeConfig.label,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Remaining Countdown Badge (if end date specified)
                        if (announcement.endsAtMs != null) {
                            val diffMs = (announcement.endsAtMs - currentEpochMs).coerceAtLeast(0L)
                            if (diffMs > 0) {
                                val days = diffMs / (24 * 3600 * 1000L)
                                val hours = (diffMs % (24 * 3600 * 1000L)) / (3600 * 1000L)
                                val minutes = (diffMs % (3600 * 1000L)) / (60 * 1000L)
                                val seconds = (diffMs % (60 * 1000L)) / 1000L

                                val timeText = if (days > 0) {
                                    "مهلت: ${days.toPersianDigits()} روز و ${hours.toPersianDigits()} ساعت باقیمانده"
                                } else {
                                    "مهلت: " + String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds).toPersianDigits()
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFEF4444).copy(alpha = 0.25f),
                                    modifier = Modifier.border(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = Color(0xFFFCA5A5),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = timeText,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF64748B).copy(alpha = 0.3f)
                                ) {
                                    Text(
                                        text = "مهلت مسابقه پایان یافت",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Title
                    Text(
                        text = announcement.title,
                        color = GoldAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Content
                    Text(
                        text = announcement.content,
                        color = Color(0xFFF1F5F9),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

private data class AnnouncementTypeUiConfig(
    val label: String,
    val icon: ImageVector,
    val badgeColor: Color,
    val borderColor: Color,
    val bgGradient: List<Color>
)
