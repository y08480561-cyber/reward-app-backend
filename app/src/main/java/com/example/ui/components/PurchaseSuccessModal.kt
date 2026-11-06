package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.PurchaseSuccessModalData
import com.example.ui.theme.*

/**
 * High-Trust, Animated, Luxury Purchase Success Dialog
 * Triggered after Myket purchase verification on server.
 */
@Composable
fun PurchaseSuccessModal(
    data: PurchaseSuccessModalData,
    onDismiss: () -> Unit
) {
    // Pulse animation for the glowing checkmark badge
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Entrance scale animation
    var startAnimation by remember { mutableStateOf(false) }
    val modalScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "modal_scale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(24.dp)
                .testTag("purchase_success_modal"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(modalScale)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = GoldAccent
                    ),
                shape = RoundedCornerShape(28.dp),
                color = CardSurface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(GoldAccent, LuxuryBorder, GoldAccent.copy(alpha = 0.4f))
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF162235),
                                    CardSurface,
                                    Color(0xFF0F172A)
                                )
                            )
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Sparkles / Decorative stars
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = GoldLight.copy(alpha = glowAlpha),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تراکنش موفق مایکت",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = GoldLight.copy(alpha = glowAlpha),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Animated Glowing Success Checkmark Badge
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .scale(pulseScale),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer glowing aura
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            SuccessGreen.copy(alpha = glowAlpha * 0.5f),
                                            GoldAccent.copy(alpha = 0.2f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        // Inner circle container
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(SuccessGreen, Color(0xFF059669))
                                    )
                                )
                                .border(2.dp, GoldLight, CircleShape)
                                .shadow(8.dp, CircleShape, spotColor = SuccessGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "پرداخت موفق",
                                tint = WarmWhite,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Title
                    Text(
                        text = "پرداخت با موفقیت انجام شد!",
                        color = WarmWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Subtitle / Trust Statement
                    Text(
                        text = "اطلاعات خرید شما روی سرور تایید و اعتبارات مربوطه عینا به حساب کاربر اضافه گردید.",
                        color = SilverAccent,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Purchase Details Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0x220B192C),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            GoldAccent.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Product Title
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "محصول خریداری‌شده:",
                                    fontSize = 12.sp,
                                    color = SilverAccent
                                )
                                Text(
                                    text = data.productTitle,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarmWhite
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = LuxuryBorder.copy(alpha = 0.4f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            // Benefits granted
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "مزایای اعطا شده:",
                                    fontSize = 12.sp,
                                    color = SilverAccent
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SuccessGreen.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = data.unitsOrMultiplierText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            if (data.priceToman > 0) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = LuxuryBorder.copy(alpha = 0.4f), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "مبلغ پرداختی:",
                                        fontSize = 12.sp,
                                        color = SilverAccent
                                    )
                                    Text(
                                        text = "${data.priceToman.toString().toPersianDigits()} تومان",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = GoldLight
                                    )
                                }
                            }

                            if (data.orderId.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = LuxuryBorder.copy(alpha = 0.4f), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "شناسه تراکنش:",
                                        fontSize = 11.sp,
                                        color = SilverAccent
                                    )
                                    Text(
                                        text = data.orderId,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SilverAccent
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Trust Badge Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تاییدشده در سیستم پرداخت مایکت و پایگاه داده",
                            fontSize = 11.sp,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Primary Button: "تایید و بازگشت به مسابقه"
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .shadow(10.dp, RoundedCornerShape(16.dp), spotColor = GoldAccent)
                            .testTag("confirm_purchase_success_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = LuxuryBlack,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تایید و دریافت اعتبارات",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = LuxuryBlack
                            )
                        }
                    }
                }
            }
        }
    }
}
