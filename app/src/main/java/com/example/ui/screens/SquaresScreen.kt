package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AdSquare
import com.example.model.DailyCycle
import com.example.model.SquareStatus
import com.example.ui.components.toPersianDigits
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.NavySecondary
import com.example.ui.theme.SuccessGreen

import com.example.ui.components.HomeAnnouncementBanner
import com.example.model.HomeAnnouncement

@Composable
fun SquaresScreen(
    squares: List<AdSquare>,
    cycle: DailyCycle?,
    announcement: HomeAnnouncement? = null,
    onSquareClick: (Activity, AdSquare) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val displaySquares = if (squares.size >= 50) {
        squares
    } else {
        (1..50).map { num ->
            squares.find { it.squareNumber == num } ?: AdSquare(
                squareNumber = num,
                status = SquareStatus.READY
            )
        }
    }

    val completedCount = displaySquares.count { it.status == SquareStatus.COMPLETED }
    val remainingCount = 50 - completedCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Top Sticky Banner: 50 Squares Progress & Midnight Iran Countdown
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
            color = CardSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "۵۰ مربع جایزه‌دار",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NavyPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${completedCount.toPersianDigits()} تکمیل شده • ${remainingCount.toPersianDigits()} باقیمانده",
                            fontSize = 12.sp,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val remainingMs = cycle?.remainingMs ?: 0L
                    val hours = (remainingMs / (1000 * 60 * 60))
                    val minutes = (remainingMs / (1000 * 60)) % 60
                    val seconds = (remainingMs / 1000) % 60
                    val countdownStr = String.format("%02d:%02d:%02d", hours, minutes, seconds).toPersianDigits()

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(NavyPrimary, NavySecondary)
                                )
                            )
                            .border(1.5.dp, GoldAccent.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "ریست ساعت ۱۲ شب",
                                color = GoldLight,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = countdownStr,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val progressFraction = (completedCount.toFloat() / 50f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = SuccessGreen,
                    trackColor = Color(0xFFE2E8F0)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Legend row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LegendItem("آماده", Color(0xFFE2E8F0), NavyPrimary)
                    LegendItem("در حال بارگذاری", Color(0xFFFEF3C7), Color(0xFFB45309))
                    LegendItem("تکمیل (قفل)", Color(0xFFD1FAE5), SuccessGreen)
                    LegendItem("تلاش مجدد", Color(0xFFFEE2E2), ErrorRed)
                }
            }
        }

        // Active Announcement Banner at top of page (Before main grid content)
        if (announcement != null && announcement.isActive) {
            HomeAnnouncementBanner(
                announcement = announcement,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        // 50 Squares Professional Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .testTag("grid_50_squares")
        ) {
            items(displaySquares, key = { it.squareNumber }) { square ->
                SquareCard(
                    square = square,
                    onClick = {
                        activity?.let { onSquareClick(it, square) }
                    }
                )
            }
        }
    }
}

@Composable
fun LegendItem(label: String, bgColor: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(textColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SquareCard(
    square: AdSquare,
    onClick: () -> Unit
) {
    val isCompleted = square.status == SquareStatus.COMPLETED
    val isLoading = square.status == SquareStatus.LOADING
    val isPlaying = square.status == SquareStatus.PLAYING
    val isFailed = square.status == SquareStatus.FAILED

    val cardBackground = when {
        isCompleted -> Color(0xFFECFDF5)
        isLoading || isPlaying -> Color(0xFFFEF3C7)
        isFailed -> Color(0xFFFEF2F2)
        else -> Color.White
    }

    val borderColor = when {
        isCompleted -> SuccessGreen
        isLoading || isPlaying -> GoldAccent
        isFailed -> ErrorRed
        else -> NavySecondary.copy(alpha = 0.35f)
    }

    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .shadow(
                elevation = if (isCompleted) 2.dp else 5.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = borderColor
            )
            .clip(RoundedCornerShape(16.dp))
            .border(2.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = !isCompleted && !isLoading && !isPlaying) { onClick() }
            .testTag("square_item_${square.squareNumber}"),
        color = cardBackground,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Status Icon / Animation
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = Color(0xFFB45309)
                    )
                }
                isPlaying -> {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                isCompleted -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                isFailed -> {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(NavyPrimary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = NavyPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Square Name and Number
            Text(
                text = "مربع ${square.squareNumber.toPersianDigits()}",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isCompleted) SuccessGreen else NavyPrimary,
                textAlign = TextAlign.Center
            )

            // Subtitle Badge
            Text(
                text = when {
                    isCompleted -> "+۱ امتیاز"
                    isLoading -> "بارگذاری"
                    isPlaying -> "در حال پخش"
                    isFailed -> "تلاش مجدد"
                    else -> "۱ امتیاز"
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    isCompleted -> SuccessGreen
                    isLoading || isPlaying -> Color(0xFFB45309)
                    isFailed -> ErrorRed
                    else -> NavySecondary
                },
                textAlign = TextAlign.Center
            )
        }
    }
}
