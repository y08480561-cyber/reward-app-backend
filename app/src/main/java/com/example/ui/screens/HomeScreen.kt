package com.example.ui.screens

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CompetitionInfo
import com.example.model.DailyCycle
import com.example.model.HomeAnnouncement
import com.example.model.User
import com.example.ui.components.HomeAnnouncementBanner
import com.example.ui.components.LuxuryCard
import com.example.ui.components.LuxuryHairlineDivider
import com.example.ui.components.LuxuryHighlightCard
import com.example.ui.components.LuxuryPrimaryButton
import com.example.ui.components.LuxuryScreenCanvas
import com.example.ui.components.LuxuryTierBadge
import com.example.ui.components.formatNumberPersian
import com.example.ui.components.formatRemainingTimePersian
import com.example.ui.components.getTehranGreeting
import com.example.ui.components.toPersianDigits
import com.example.ui.theme.*
import com.example.viewmodel.AppScreen

@Composable
fun HomeScreen(
    user: User,
    cycle: DailyCycle?,
    competition: CompetitionInfo? = null,
    announcement: HomeAnnouncement? = null,
    userRank: Int? = null,
    completedSquaresToday: Int? = null,
    onNavigate: (AppScreen) -> Unit
) {
    val scrollState = rememberScrollState()
    val animatedScore by animateIntAsState(
        targetValue = user.totalScore,
        animationSpec = tween(durationMillis = 500),
        label = "home_score_animation"
    )
    val completedCount = completedSquaresToday ?: cycle?.completedSquaresCount ?: 0
    val totalSquares = cycle?.totalSquares ?: 100
    val progressFraction = (completedCount.toFloat() / totalSquares.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    LuxuryScreenCanvas {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Calm Editorial Greeting Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${getTehranGreeting()}، ${user.firstName.ifBlank { "همراه گرامی" }}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = WarmWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (userRank != null && userRank > 0) {
                            "رتبه فعلی شما: #${userRank.toPersianDigits()}"
                        } else {
                            "باشگاه رقابت و جوایز"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (userRank != null && userRank > 0) GoldLight else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { onNavigate(AppScreen.MEMBERSHIP) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LuxuryTierBadge(
                        panelType = user.panelType,
                        multiplier = user.multiplier
                    )
                }
            }

            // 2. Single Hero Focal Card: Score + Competition Progress + Main CTA
            LuxuryHighlightCard(
                contentPadding = PaddingValues(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (competition != null) {
                            "دوره ${competition.competitionNumber.toPersianDigits()} • ${competition.title}"
                        } else {
                            "رقابت روزانه ۱۰۰ مربع"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "فعال",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatNumberPersian(animatedScore),
                            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = GoldLight
                        )
                        Text(
                            text = "امتیاز",
                            style = MaterialTheme.typography.titleMedium,
                            color = WarmWhite.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Text(
                        text = "ضریب ${user.multiplier.toPersianDigits()}x",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldAccent,
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                LuxuryHairlineDivider(useGoldAccent = true)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${completedCount.toPersianDigits()} از ${totalSquares.toPersianDigits()} مربع امروز",
                        style = MaterialTheme.typography.labelMedium,
                        color = WarmWhite
                    )
                    if (cycle != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "بازنشانی ${formatRemainingTimePersian(cycle.remainingMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = GoldAccent,
                    trackColor = DarkGraySurface,
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(20.dp))

                LuxuryPrimaryButton(
                    text = "شروع رقابت و ۱۰۰ مربع",
                    icon = Icons.Rounded.GridView,
                    onClick = { onNavigate(AppScreen.SQUARES) },
                    modifier = Modifier.testTag("btn_home_start_squares")
                )
            }

            // 3. Optional Quiet Announcement Strip (only when server has an active message)
            if (announcement != null && announcement.isActive) {
                HomeAnnouncementBanner(
                    announcement = announcement,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. Unified Quick Services Card (only destinations not already in bottom navigation)
            LuxuryCard(
                borderColor = CharcoalBorder,
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                HomeMenuRow(
                    title = "فروشگاه و ارتقای حساب",
                    subtitle = "بسته‌های بازگشایی آنی و افزایش ضریب",
                    icon = Icons.Outlined.Storefront,
                    onClick = { onNavigate(AppScreen.STORE) },
                    modifier = Modifier.testTag("home_card_store")
                )
                LuxuryHairlineDivider(modifier = Modifier.padding(horizontal = 16.dp))
                HomeMenuRow(
                    title = "سطوح عضویت",
                    subtitle = "مقایسه پنل پایه، نقره‌ای و طلایی",
                    icon = Icons.Outlined.WorkspacePremium,
                    onClick = { onNavigate(AppScreen.MEMBERSHIP) },
                    modifier = Modifier.testTag("home_card_membership")
                )
                LuxuryHairlineDivider(modifier = Modifier.padding(horizontal = 16.dp))
                HomeMenuRow(
                    title = "پشتیبانی و پیگیری",
                    subtitle = "ارتباط مستقیم و ثبت درخواست",
                    icon = Icons.Outlined.SupportAgent,
                    onClick = { onNavigate(AppScreen.SUPPORT) },
                    modifier = Modifier.testTag("home_card_support")
                )
            }

            Spacer(modifier = Modifier.height(104.dp))
        }
    }
}

@Composable
private fun HomeMenuRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = GoldLight,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = WarmWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}
