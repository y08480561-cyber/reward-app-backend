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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.ui.components.formatRemainingTimePersian
import com.example.ui.components.toPersianDigits
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.ui.theme.NavyLight
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.NavySecondary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SquaresScreen(
    squares: List<AdSquare>,
    cycle: DailyCycle?,
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
        // Top Sticky Banner: 50 Squares Progress
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp),
            color = CardSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "۵۰ مربع جایزه‌دار",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${completedCount.toPersianDigits()} تکمیل شده • ${remainingCount.toPersianDigits()} باقیمانده",
                            fontSize = 12.sp,
                            color = SuccessGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(NavyPrimary)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "۵۰ مربع",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val progressFraction = (completedCount.toFloat() / 50f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = SuccessGreen,
                    trackColor = Color(0xFFE2E8F0)
                )

                Spacer(modifier = Modifier.height(8.dp))

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

        // 50 Squares Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                .size(8.dp)
                .clip(CircleShape)
                .background(textColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 10.sp, color = Color(0xFF64748B))
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
        else -> CardSurface
    }

    val borderColor = when {
        isCompleted -> SuccessGreen
        isLoading || isPlaying -> GoldAccent
        isFailed -> ErrorRed
        else -> Color(0xFFCBD5E1)
    }

    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .shadow(if (isCompleted) 1.dp else 2.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = !isCompleted && !isLoading && !isPlaying) { onClick() }
            .testTag("square_item_${square.squareNumber}"),
        color = cardBackground
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
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFB45309)
                    )
                }
                isPlaying -> {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                isCompleted -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                isFailed -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = NavyPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Square Name and Number
            Text(
                text = "مربع ${square.squareNumber.toPersianDigits()}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
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
                fontSize = 8.sp,
                fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.SemiBold,
                color = when {
                    isCompleted -> SuccessGreen
                    isLoading || isPlaying -> Color(0xFFB45309)
                    isFailed -> ErrorRed
                    else -> Color(0xFF64748B)
                },
                textAlign = TextAlign.Center
            )
        }
    }
}
