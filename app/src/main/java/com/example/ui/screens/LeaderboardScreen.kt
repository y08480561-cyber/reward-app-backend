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
            .background(SlateBackground)
    ) {
        // --- Top Bar ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp),
            color = NavyPrimary
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                            tint = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "جدول رتبه‌بندی مسابقه",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "برترین شرکت‌کنندگان و امتیازات کل",
                            color = TextMuted,
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

        // --- Current User Floating Highlight Card ---
        if (currentUserEntry != null && !uiState.isLeaderboardLoading) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("current_user_rank_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavySecondary),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(GoldAccent, GoldDark))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#${currentUserEntry.rank}",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentUserEntry.displayName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GoldAccent.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "شما",
                                        color = GoldAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "وضعیت رتبه شما در مسابقه جاری",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NavyPrimary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${currentUserEntry.totalScore} امتیاز",
                                color = GoldAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // --- Search Bar ---
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
                        tint = NavyLight
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
                    focusedBorderColor = NavySecondary,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
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
                            color = TextSecondary,
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
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
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
                                    .background(ErrorRedLight),
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
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.leaderboardErrorMessage ?: "امکان برقراری ارتباط با سرور فراهم نشد.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onRefresh,
                                modifier = Modifier.testTag("leaderboard_retry_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = NavySecondary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تلاش مجدد")
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
                            tint = TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "شرکت‌کننده‌ای با این نام یافت نشد."
                            } else {
                                "هنوز رتبه‌بندی در مسابقه ثبت نشده است."
                            },
                            color = TextSecondary,
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                color = NavyPrimary
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
                        rankColor = Color(0xFF94A3B8),
                        borderColor = Color(0xFFCBD5E1),
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
                        borderColor = Color(0xFFE2E8F0),
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
            color = TextPrimary,
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
                .border(1.dp, borderColor, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
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
        2 -> Color(0xFF94A3B8)
        3 -> BronzeTrophy
        else -> NavyLight
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_row_${entry.rank}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isCurrentUser) Color(0xFFEFF6FF) else CardSurface
        ),
        border = if (entry.isCurrentUser) {
            androidx.compose.foundation.BorderStroke(1.5.dp, NavySecondary)
        } else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                    .background(rankBadgeColor.copy(alpha = if (isTop3) 0.2f else 0.1f)),
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
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.isCurrentUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NavySecondary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "شما",
                                color = NavySecondary,
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
                color = if (isTop3) GoldAccent.copy(alpha = 0.15f) else Color(0xFFF1F5F9)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isTop3) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "${entry.totalScore} امتیاز",
                        color = if (isTop3) GoldDark else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
