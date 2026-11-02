package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LeaderboardEntry
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    BackHandler(enabled = true) {
        onBack()
    }

    val filteredList = remember(uiState.leaderboard, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.leaderboard
        } else {
            val query = searchQuery.trim().lowercase()
            uiState.leaderboard.filter { entry ->
                entry.displayName.lowercase().contains(query)
            }
        }
    }

    val currentUserEntry = remember(uiState.leaderboard, uiState.currentUser) {
        uiState.leaderboard.firstOrNull { it.isCurrentUser }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(LuxuryBlack, NavyPrimary, LuxuryBlack)
                )
            )
    ) {
        // --- Top Bar ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp),
            color = NavyPrimary,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.verticalGradient(listOf(Color(0x33D4AF37), Color(0x11D4AF37)))
            )
        ) {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("leaderboard_back_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x1AD4AF37)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "بازگشت",
                                tint = GoldLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "جدول رتبه‌بندی مسابقه",
                            color = WarmWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "برترین شرکت‌کنندگان و امتیازات کل",
                            color = GoldLight.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = onRefresh,
                        enabled = !uiState.isLeaderboardLoading,
                        modifier = Modifier.testTag("leaderboard_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "تازه‌سازی",
                            tint = if (uiState.isLeaderboardLoading) TextMuted else GoldAccent
                        )
                    }
                }
            }
        }

        // --- Current User Floating Highlight Card (Luxury VIP Trim) ---
        if (currentUserEntry != null && !uiState.isLeaderboardLoading) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("current_user_rank_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    Brush.horizontalGradient(
                        listOf(GoldAccent, GoldLight, GoldAccent)
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1B2332), Color(0xFF121722))
                            )
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(GoldLight, GoldAccent, GoldDark)
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                .shadow(8.dp, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#${currentUserEntry.rank}",
                                color = LuxuryBlack,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentUserEntry.displayName,
                                    color = WarmWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GoldAccent.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "شما",
                                        color = GoldLight,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "وضعیت رتبه شما در مسابقه جاری",
                                color = Color(0xFFB0B7C3),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF131722),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66D4AF37))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${currentUserEntry.totalScore} امتیاز",
                                color = GoldLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // --- Search Bar (Luxury Obsidian Input) ---
        if (uiState.leaderboard.isNotEmpty() && !uiState.isLeaderboardLoading) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("leaderboard_search_input"),
                placeholder = {
                    Text("جستجوی شرکت‌کننده بر اساس نام...", fontSize = 13.sp, color = TextMuted)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "جستجو",
                        tint = GoldLight
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "پاک کردن",
                                tint = TextMuted
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = WarmWhite,
                    unfocusedTextColor = WarmWhite,
                    focusedBorderColor = GoldAccent,
                    unfocusedBorderColor = Color(0x33D4AF37),
                    focusedContainerColor = CardSurface,
                    unfocusedContainerColor = CardSurface
                )
            )
        }

        // --- Content Area ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp) // Clearance for floating Bottom Navigation
        ) {
            when {
                // 1. Loading State
                uiState.isLeaderboardLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = GoldAccent,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "در حال دریافت آخرین رتبه‌بندی مسابقه...",
                            color = GoldLight,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 2. Error State
                uiState.leaderboardErrorMessage != null -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .align(Alignment.Center)
                            .testTag("leaderboard_error_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44EF4444)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x2AEF4444)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (uiState.leaderboardErrorCode != null) {
                                    "خطای سرور (کد وضعیت ${uiState.leaderboardErrorCode})"
                                } else {
                                    "خطا در دریافت جدول رتبه‌بندی"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = WarmWhite
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.leaderboardErrorMessage ?: "امکان برقراری ارتباط با سرور فراهم نشد.",
                                color = Color(0xFFB0B7C3),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onRefresh,
                                modifier = Modifier.testTag("leaderboard_retry_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = LuxuryBlack,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تلاش مجدد", color = LuxuryBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 3. Empty Search or Empty Leaderboard
                filteredList.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0x44D4AF37),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "شرکت‌کننده‌ای با این نام یافت نشد."
                            } else {
                                "هنوز رتبه‌بندی در مسابقه ثبت نشده است."
                            },
                            color = Color(0xFFB0B7C3),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }

                // 4. Success List
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top 3 Podium Cards if full list
                        if (searchQuery.isBlank() && filteredList.size >= 3) {
                            item {
                                TopThreePodiumSection(topThree = filteredList.take(3))
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // List of Entries
                        items(filteredList, key = { it.rank }) { entry ->
                            LeaderboardItemRow(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopThreePodiumSection(topThree: List<LeaderboardEntry>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_podium_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33D4AF37)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "سکوهای برتر دوره مسابقه",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GoldLight
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                // 2nd Place (Silver)
                if (topThree.size > 1) {
                    PodiumPillar(
                        entry = topThree[1],
                        rankColor = SilverAccent,
                        borderColor = Color(0x66C0C0C0),
                        pillarHeight = 70.dp,
                        trophyIcon = "🥈"
                    )
                }

                // 1st Place (Gold)
                if (topThree.isNotEmpty()) {
                    PodiumPillar(
                        entry = topThree[0],
                        rankColor = GoldAccent,
                        borderColor = GoldAccent,
                        pillarHeight = 95.dp,
                        trophyIcon = "🥇"
                    )
                }

                // 3rd Place (Bronze)
                if (topThree.size > 2) {
                    PodiumPillar(
                        entry = topThree[2],
                        rankColor = BronzeTrophy,
                        borderColor = Color(0x66CD7F32),
                        pillarHeight = 55.dp,
                        trophyIcon = "🥉"
                    )
                }
            }
        }
    }
}

@Composable
private fun PodiumPillar(
    entry: LeaderboardEntry,
    rankColor: Color,
    borderColor: Color,
    pillarHeight: androidx.compose.ui.unit.Dp,
    trophyIcon: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp)
    ) {
        Text(text = trophyIcon, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = entry.displayName,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = WarmWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "${entry.totalScore} امتیاز",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = rankColor
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pillarHeight)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(rankColor.copy(alpha = 0.15f))
                .border(1.5.dp, borderColor, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#${entry.rank}",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = rankColor
            )
        }
    }
}

@Composable
private fun LeaderboardItemRow(entry: LeaderboardEntry) {
    val isTop3 = entry.rank in 1..3
    val rankBadgeColor = when (entry.rank) {
        1 -> GoldAccent
        2 -> SilverAccent
        3 -> BronzeTrophy
        else -> Color(0xFF8E98A8)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_row_${entry.rank}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isCurrentUser) Color(0xFF1B2332) else CardSurface
        ),
        border = if (entry.isCurrentUser) {
            androidx.compose.foundation.BorderStroke(1.5.dp, GoldAccent)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0x22D4AF37))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Badge
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(rankBadgeColor.copy(alpha = if (isTop3) 0.25f else 0.15f))
                    .border(1.dp, rankBadgeColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${entry.rank}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = rankBadgeColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Display Name (Masked for privacy)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.displayName,
                        fontWeight = if (entry.isCurrentUser || isTop3) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                        color = WarmWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.isCurrentUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = GoldAccent.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "شما",
                                color = GoldLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Score Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isTop3) GoldAccent.copy(alpha = 0.2f) else Color(0xFF1F2634),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isTop3) GoldAccent.copy(alpha = 0.5f) else Color(0x22FFFFFF)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isTop3) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = GoldLight,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "${entry.totalScore} امتیاز",
                        color = if (isTop3) GoldLight else WarmWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
