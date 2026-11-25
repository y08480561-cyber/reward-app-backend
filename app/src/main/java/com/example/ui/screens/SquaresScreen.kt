package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AdSquare
import com.example.model.DailyCycle
import com.example.model.HomeAnnouncement
import com.example.model.SquareStatus
import com.example.ui.components.FeedbackTone
import com.example.ui.components.LuxuryFeedbackBanner
import com.example.ui.components.LuxuryHairlineDivider
import com.example.ui.components.LuxuryScreenCanvas
import com.example.ui.components.formatRemainingTimePersian
import com.example.ui.components.toPersianDigits
import com.example.ui.theme.*

@Composable
fun SquaresScreen(
    squares: List<AdSquare>,
    cycle: DailyCycle?,
    announcement: HomeAnnouncement? = null,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    onSquareClick: (Activity, AdSquare) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val displaySquares = remember(squares) {
        if (squares.size >= 100) {
            squares
        } else {
            (1..100).map { num ->
                squares.find { it.squareNumber == num } ?: AdSquare(
                    squareNumber = num,
                    status = SquareStatus.READY
                )
            }
        }
    }

    val completedCount = displaySquares.count { it.status == SquareStatus.COMPLETED }
    val remainingCount = (100 - completedCount).coerceAtLeast(0)
    val progressFraction = (completedCount.toFloat() / 100f).coerceIn(0f, 1f)

    LuxuryScreenCanvas {
        Column(modifier = Modifier.fillMaxSize()) {
            // Streamlined Sticky Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ObsidianBlack.copy(alpha = 0.96f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
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
                            Text(
                                text = "۱۰۰ مربع روزانه",
                                style = MaterialTheme.typography.titleLarge,
                                color = WarmWhite
                            )

                            if (cycle != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Timer,
                                        contentDescription = null,
                                        tint = GoldLight,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = formatRemainingTimePersian(cycle.remainingMs),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = GoldLight
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "${completedCount.toPersianDigits()} تکمیل  •  ${remainingCount.toPersianDigits()} باقی‌مانده",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )

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
                    }

                    LuxuryHairlineDivider(useGoldAccent = true)
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                LuxuryFeedbackBanner(
                    message = errorMessage,
                    tone = FeedbackTone.ERROR,
                    onDismiss = onDismissError,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Clean, Uncluttered 100-Squares Grid
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
            ) {
                val columns = if (maxWidth >= 600.dp) 8 else 5

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 108.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("grid_100_squares")
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
    }
}

@Composable
fun SquareCard(
    square: AdSquare,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isCompleted = square.status == SquareStatus.COMPLETED
    val isLoading = square.status == SquareStatus.LOADING
    val isPlaying = square.status == SquareStatus.PLAYING
    val isFailed = square.status == SquareStatus.FAILED
    val isClickable = !isCompleted && !isLoading && !isPlaying

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && isClickable) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "square_press_scale"
    )

    val cardBackground = when {
        isCompleted -> Color(0xFF0E1D17)
        isLoading || isPlaying -> Color(0xFF1F1A0F)
        isFailed -> Color(0xFF1E1213)
        else -> CharcoalSurface
    }

    val borderColor = when {
        isCompleted -> SuccessGreen.copy(alpha = 0.55f)
        isLoading || isPlaying -> GoldAccent
        isFailed -> ErrorRed.copy(alpha = 0.6f)
        else -> LuxuryBorder
    }

    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                enabled = isClickable,
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = GoldLight)
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .testTag("square_item_${square.squareNumber}"),
        color = cardBackground,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = GoldLight
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = square.squareNumber.toPersianDigits(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = GoldLight
                        )
                    }
                }
                isPlaying -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "در حال پخش",
                            tint = GoldLight,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = square.squareNumber.toPersianDigits(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = GoldLight
                        )
                    }
                }
                isCompleted -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "تکمیل‌شده",
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = square.squareNumber.toPersianDigits(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFF6EE7B7)
                        )
                    }
                }
                isFailed -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "تلاش مجدد",
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = square.squareNumber.toPersianDigits(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = ErrorRed
                        )
                    }
                }
                else -> {
                    // Subtle 4dp gold dot indicator at top-start + bold clean numeral
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(7.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(GoldAccent.copy(alpha = 0.7f))
                    )
                    Text(
                        text = square.squareNumber.toPersianDigits(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = WarmWhite,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
