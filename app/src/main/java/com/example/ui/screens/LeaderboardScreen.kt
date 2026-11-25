package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.LeaderboardEntry
import com.example.ui.components.LuxuryBadge
import com.example.ui.components.LuxuryBadgeTone
import com.example.ui.components.LuxuryCard
import com.example.ui.components.LuxuryEmptyState
import com.example.ui.components.LuxuryHighlightCard
import com.example.ui.components.LuxuryLoadingState
import com.example.ui.components.LuxuryPrimaryButton
import com.example.ui.components.LuxuryScreenCanvas
import com.example.ui.components.LuxuryTopBar
import com.example.ui.components.formatNumberPersian
import com.example.ui.components.toPersianDigits
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState

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

    val showPodium = searchQuery.isBlank() && filteredList.size >= 3
    val listEntries = remember(filteredList, showPodium) {
        if (showPodium) filteredList.drop(3) else filteredList
    }

    LuxuryScreenCanvas {
        Column(modifier = Modifier.fillMaxSize()) {
            LuxuryTopBar(
                title = "جدول رتبه‌بندی",
                onBack = onBack,
                backTestTag = "leaderboard_back_button",
                trailingContent = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !uiState.isLeaderboardLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("leaderboard_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "به‌روزرسانی رتبه‌بندی",
                            tint = if (uiState.isLeaderboardLoading) TextMuted else GoldLight,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Compact Current User Highlight Strip
                if (currentUserEntry != null && !uiState.isLeaderboardLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .padding(horizontal = 16.dp)
                            .testTag("current_user_rank_card"),
                        shape = RoundedCornerShape(16.dp),
                        color = DarkGraySurface,
                        border = BorderStroke(1.dp, LuxuryBorderStrong)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "#${currentUserEntry.rank.toPersianDigits()}",
                                    color = GoldLight,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = currentUserEntry.displayName,
                                    color = WarmWhite,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                LuxuryBadge(
                                    text = "شما",
                                    tone = LuxuryBadgeTone.GOLD
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "${formatNumberPersian(currentUserEntry.totalScore)} امتیاز",
                                color = GoldLight,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                // Search Field
                if (uiState.leaderboard.isNotEmpty() && !uiState.isLeaderboardLoading) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .padding(horizontal = 16.dp)
                            .testTag("leaderboard_search_input"),
                        placeholder = {
                            Text(
                                text = "جستجوی شرکت‌کننده…",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "جستجو",
                                tint = GoldLight,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "پاک کردن جستجو",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = WarmWhite),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = WarmWhite,
                            unfocusedTextColor = WarmWhite,
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedContainerColor = CharcoalSurface,
                            unfocusedContainerColor = CharcoalSurface,
                            cursorColor = GoldAccent
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 88.dp)
                ) {
                    when {
                        uiState.isLeaderboardLoading -> {
                            LuxuryLoadingState(message = "در حال دریافت رتبه‌بندی…")
                        }

                        uiState.leaderboardErrorMessage != null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LuxuryCard(
                                    borderColor = ErrorRed.copy(alpha = 0.45f),
                                    modifier = Modifier.testTag("leaderboard_error_card")
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(ErrorRedLight)
                                                .border(1.dp, ErrorRed.copy(alpha = 0.4f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.WarningAmber,
                                                contentDescription = null,
                                                tint = ErrorRed,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = if (uiState.leaderboardErrorCode != null) {
                                                "خطا در دریافت اطلاعات (کد ${uiState.leaderboardErrorCode.toPersianDigits()})"
                                            } else {
                                                "اختلال در دریافت جدول رتبه‌بندی"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = WarmWhite,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = uiState.leaderboardErrorMessage ?: "اتصال به سرور برقرار نشد.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(18.dp))
                                        LuxuryPrimaryButton(
                                            text = "تلاش مجدد",
                                            icon = Icons.Rounded.Refresh,
                                            onClick = onRefresh,
                                            modifier = Modifier.testTag("leaderboard_retry_button")
                                        )
                                    }
                                }
                            }
                        }

                        filteredList.isEmpty() -> {
                            LuxuryEmptyState(
                                icon = Icons.Rounded.EmojiEvents,
                                title = if (searchQuery.isNotEmpty()) {
                                    "نتیجه‌ای یافت نشد"
                                } else {
                                    "رتبه‌بندی هنوز ثبت نشده است"
                                },
                                description = if (searchQuery.isNotEmpty()) {
                                    "شرکت‌کننده‌ای با نام «$searchQuery» پیدا نشد."
                                } else {
                                    "با تکمیل نخستین مربع‌ها، رتبه‌ها نمایش داده می‌شوند."
                                }
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (showPodium) {
                                    item {
                                        TopThreePodiumSection(topThree = filteredList.take(3))
                                        if (listEntries.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                    }
                                }

                                items(listEntries, key = { it.rank }) { entry ->
                                    LeaderboardItemRow(entry = entry)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopThreePodiumSection(topThree: List<LeaderboardEntry>) {
    LuxuryHighlightCard(
        modifier = Modifier.testTag("leaderboard_podium_card"),
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "نفرات برتر دوره",
                style = MaterialTheme.typography.titleSmall,
                color = GoldLight
            )
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                if (topThree.size > 1) {
                    PodiumPillar(
                        entry = topThree[1],
                        rankColor = SilverAccent,
                        pillarHeight = 60.dp,
                        isCrown = false
                    )
                }

                if (topThree.isNotEmpty()) {
                    PodiumPillar(
                        entry = topThree[0],
                        rankColor = GoldAccent,
                        pillarHeight = 82.dp,
                        isCrown = true
                    )
                }

                if (topThree.size > 2) {
                    PodiumPillar(
                        entry = topThree[2],
                        rankColor = BronzeTrophy,
                        pillarHeight = 48.dp,
                        isCrown = false
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
    pillarHeight: Dp,
    isCrown: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp)
    ) {
        Icon(
            imageVector = if (isCrown) Icons.Rounded.WorkspacePremium else Icons.Rounded.EmojiEvents,
            contentDescription = "رتبه ${entry.rank.toPersianDigits()}",
            tint = rankColor,
            modifier = Modifier.size(if (isCrown) 24.dp else 20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = entry.displayName,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = formatNumberPersian(entry.totalScore),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = rankColor,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pillarHeight)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(rankColor.copy(alpha = 0.22f), ObsidianBlack.copy(alpha = 0.45f))
                    )
                )
                .border(
                    1.dp,
                    rankColor.copy(alpha = 0.45f),
                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#${entry.rank.toPersianDigits()}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = rankColor
            )
        }
    }
}

@Composable
private fun LeaderboardItemRow(entry: LeaderboardEntry) {
    val isTop3 = entry.rank in 1..3
    val rankColor = when (entry.rank) {
        1 -> GoldAccent
        2 -> SilverAccent
        3 -> BronzeTrophy
        else -> TextSecondary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("leaderboard_row_${entry.rank}"),
        shape = RoundedCornerShape(14.dp),
        color = if (entry.isCurrentUser) DarkGraySurface else CharcoalSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (entry.isCurrentUser) GoldAccent.copy(alpha = 0.7f) else CharcoalBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#${entry.rank.toPersianDigits()}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = rankColor,
                modifier = Modifier.width(38.dp)
            )

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (entry.isCurrentUser || isTop3) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = WarmWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (entry.isCurrentUser) {
                    Spacer(modifier = Modifier.width(6.dp))
                    LuxuryBadge(
                        text = "شما",
                        tone = LuxuryBadgeTone.GOLD
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${formatNumberPersian(entry.totalScore)} امتیاز",
                color = if (isTop3 || entry.isCurrentUser) GoldLight else WarmWhite,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
