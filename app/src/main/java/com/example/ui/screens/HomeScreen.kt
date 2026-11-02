package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CompetitionInfo
import com.example.model.DailyCycle
import com.example.model.HomeAnnouncement
import com.example.model.User
import com.example.ui.components.HomeAnnouncementBanner
import com.example.ui.components.formatRemainingTimePersian
import com.example.ui.components.toPersianDigits
import com.example.ui.theme.*
import com.example.viewmodel.AppScreen

@Composable
fun HomeScreen(
    user: User,
    cycle: DailyCycle?,
    competition: CompetitionInfo? = null,
    announcement: HomeAnnouncement? = null,
    userRank: Int = 1,
    onNavigate: (AppScreen) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(LuxuryBlack, NavyPrimary, LuxuryBlack)
                )
            )
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Home Announcement Banner (if active)
        if (announcement != null && announcement.isActive) {
            HomeAnnouncementBanner(
                announcement = announcement,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. Active Competition Banner Card (Luxury Metallic Trim)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = CardSurface,
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                Brush.horizontalGradient(
                    listOf(GoldAccent, Color(0x33D4AF37), GoldLight)
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF161E2E), Color(0xFF0F1622))
                        )
                    )
                    .padding(18.dp)
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
                                .background(
                                    Brush.linearGradient(
                                        listOf(GoldLight, GoldAccent, GoldDark)
                                    )
                                )
                                .shadow(6.dp, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = LuxuryBlack,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (competition != null) "مسابقه دوره ${competition.competitionNumber.toPersianDigits()}" else "مسابقه جاری",
                            color = GoldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SuccessGreen.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen)
                    ) {
                        Text(
                            text = "در حال برگزاری",
                            color = Color(0xFF6EE7B7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = competition?.title ?: "رقابت کن، جایزه ببر (جوایز ارزنده)",
                    color = WarmWhite,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Score pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "امتیاز شما در این دوره:",
                        color = Color(0xFFB0B7C3),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${user.totalScore.toPersianDigits()} امتیاز",
                        color = GoldLight,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. Quick Action Button to 100 Squares (Metallic Gold CTA)
        Button(
            onClick = { onNavigate(AppScreen.SQUARES) },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .shadow(10.dp, RoundedCornerShape(16.dp))
                .testTag("btn_home_start_squares"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = null,
                    tint = LuxuryBlack,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ورود به جدول ۱۰۰ مربع جایزه‌دار",
                    color = LuxuryBlack,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }
        }

        // 4. Quick Access Grid (Luxury Obsidian & Gold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Leaderboard Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate(AppScreen.LEADERBOARD) }
                    .testTag("home_card_leaderboard"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33D4AF37))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x1AD4AF37)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "رتبه‌بندی",
                            tint = GoldLight,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "جدول رتبه‌بندی",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = WarmWhite
                    )
                }
            }

            // Prizes Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate(AppScreen.PRIZES) }
                    .testTag("home_card_prizes"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33C0C0C0))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x1AC0C0C0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = "جوایز",
                            tint = SilverAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "جوایز دوره",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = WarmWhite
                    )
                }
            }
        }

        // 5. Quick Support Banner Card (Luxury Concierge Desk)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(16.dp))
                .clickable { onNavigate(AppScreen.SUPPORT) }
                .testTag("home_card_support"),
            shape = RoundedCornerShape(16.dp),
            color = CardSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33D4AF37))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x1AD4AF37)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SupportAgent,
                            contentDescription = null,
                            tint = GoldLight,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "مرکز پشتیبانی و پیام‌ها",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = WarmWhite
                        )
                        Text(
                            text = "پاسخ مستقیم مدیر به سوالات و مشکلات شما",
                            fontSize = 11.sp,
                            color = Color(0xFFB0B7C3)
                        )
                    }
                }
                Text(
                    text = "ارسال تیکت ←",
                    color = GoldLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

