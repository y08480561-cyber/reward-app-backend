package com.example.ui.screens

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.example.ui.components.LuxuryBadge
import com.example.ui.components.LuxuryBadgeTone
import com.example.ui.components.LuxuryCard
import com.example.ui.components.LuxuryHairlineDivider
import com.example.ui.components.LuxuryHighlightCard
import com.example.ui.components.LuxuryPrimaryButton
import com.example.ui.components.LuxuryScreenCanvas
import com.example.ui.components.LuxurySectionHeader
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
        animationSpec = tween(durationMillis = 550),
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Luxury Greeting Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${getTehranGreeting()}، ${user.firstName.ifBlank { "همراه گرامی" }} عزیز",
                        style = MaterialTheme.typography.headlineSmall,
                        color = WarmWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "به باشگاه رقابت و جوایز خوش آمدید",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { onNavigate(AppScreen.STORE) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_home_store")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(DarkGraySurface)
                                .border(1.dp, LuxuryBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storefront,
                                contentDescription = "فروشگاه",
                                tint = GoldLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { onNavigate(AppScreen.PROFILE) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_header_profile")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GoldGradientBrush)
                                .border(1.dp, GoldLight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "پروفایل کاربری",
                                tint = ObsidianBlack,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 2. Hero Score & VIP Status Showcase Card
            LuxuryHighlightCard(
                contentPadding = PaddingValues(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LuxuryTierBadge(
                        panelType = user.panelType,
                        multiplier = user.multiplier
                    )

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onNavigate(AppScreen.LEADERBOARD) },
                        shape = RoundedCornerShape(50),
                        color = ObsidianBlack.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, LuxuryBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EmojiEvents,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (userRank != null && userRank > 0) {
                                    "رتبه فعلی: #${userRank.toPersianDigits()}"
                                } else {
                                    "مشاهده رتبه‌بندی"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = GoldLight
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "مجموع امتیازهای شما در دوره جاری",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

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

                Spacer(modifier = Modifier.height(14.dp))
                LuxuryHairlineDivider(useGoldAccent = true)
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = user.fullName.ifBlank { "شرکت‌کننده باشگاه" },
                        style = MaterialTheme.typography.labelMedium,
                        color = WarmWhite
                    )
                    Text(
                        text = "ضریب امتیاز هر مربع: ${user.multiplier.toPersianDigits()} برابر",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldLight.copy(alpha = 0.9f)
                    )
                }
            }

            // 3. Home Announcement Banner (if active from server)
            if (announcement != null && announcement.isActive) {
                HomeAnnouncementBanner(
                    announcement = announcement,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. Active Competition & Daily 100-Square Progress Card
            LuxuryCard(
                borderColor = LuxuryBorder,
                contentPadding = PaddingValues(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GoldAccent.copy(alpha = 0.14f))
                                .border(1.dp, LuxuryBorderStrong, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EmojiEvents,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (competition != null) {
                                "مسابقه دوره ${competition.competitionNumber.toPersianDigits()}"
                            } else {
                                "مسابقه جاری"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = GoldLight
                        )
                    }

                    LuxuryBadge(
                        text = "در حال برگزاری",
                        tone = LuxuryBadgeTone.SUCCESS
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = competition?.title ?: "رقابت روزانه ۱۰۰ مربع جایزه‌دار",
                    style = MaterialTheme.typography.titleMedium,
                    color = WarmWhite
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Progress & Midnight Reset Countdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "پیشرفت امروز: ${completedCount.toPersianDigits()} از ${totalSquares.toPersianDigits()} مربع",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    if (cycle != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "بازنشانی: ${formatRemainingTimePersian(cycle.remainingMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldLight
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(50)),
                    color = GoldAccent,
                    trackColor = DarkGraySurface,
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(18.dp))

                LuxuryPrimaryButton(
                    text = "ورود به جدول ۱۰۰ مربع",
                    icon = Icons.Rounded.GridView,
                    onClick = { onNavigate(AppScreen.SQUARES) },
                    modifier = Modifier.testTag("btn_home_start_squares")
                )
            }

            // 5. Prize Showcase Preview Card
            LuxuryCard(
                onClick = { onNavigate(AppScreen.PRIZES) },
                borderColor = LuxuryBorder,
                containerColor = DarkGraySurface,
                modifier = Modifier.testTag("home_card_prizes")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(GoldAccent.copy(alpha = 0.14f))
                                .border(1.dp, LuxuryBorderStrong, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CardGiftcard,
                                contentDescription = "ویترین جوایز",
                                tint = GoldLight,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "ویترین جوایز و هدایای ویژه",
                                style = MaterialTheme.typography.titleSmall,
                                color = WarmWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "مشاهده جایزه دوره جاری و شرایط دریافت",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = GoldLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 6. Organized Quick Access Section
            LuxurySectionHeader(
                title = "دسترسی سریع",
                modifier = Modifier.widthIn(max = 600.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeQuickActionTile(
                    title = "جدول رتبه‌بندی",
                    subtitle = "برترین‌های دوره",
                    icon = Icons.Outlined.EmojiEvents,
                    onClick = { onNavigate(AppScreen.LEADERBOARD) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_card_leaderboard")
                )
                HomeQuickActionTile(
                    title = "فروشگاه و ارتقا",
                    subtitle = "بازگشایی آنی و ضریب",
                    icon = Icons.Outlined.Storefront,
                    onClick = { onNavigate(AppScreen.STORE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_card_store")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeQuickActionTile(
                    title = "سطوح عضویت",
                    subtitle = "پنل نقره‌ای و طلایی",
                    icon = Icons.Outlined.WorkspacePremium,
                    onClick = { onNavigate(AppScreen.MEMBERSHIP) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_card_membership")
                )
                HomeQuickActionTile(
                    title = "پشتیبانی اختصاصی",
                    subtitle = "ثبت و پیگیری تیکت",
                    icon = Icons.Outlined.SupportAgent,
                    onClick = { onNavigate(AppScreen.SUPPORT) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_card_support")
                )
            }

            Spacer(modifier = Modifier.height(104.dp))
        }
    }
}

@Composable
private fun HomeQuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LuxuryCard(
        modifier = modifier.heightIn(min = 108.dp),
        onClick = onClick,
        borderColor = CharcoalBorder,
        contentPadding = PaddingValues(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkGraySurface)
                .border(1.dp, LuxuryBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = GoldLight,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
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
}
