package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DailyCycle
import com.example.ui.components.formatRemainingTimePersian
import com.example.ui.components.toPersianDigits
import com.example.ui.theme.*
import org.json.JSONObject

@Composable
fun MembershipScreen(
    panels: List<JSONObject>,
    userMembership: JSONObject?,
    isLoading: Boolean,
    cycle: DailyCycle?,
    errorMessage: String?,
    infoMessage: String?,
    onRefresh: () -> Unit,
    onPurchase: (productId: String) -> Unit,
    onDismissMessage: () -> Unit
) {
    val currentPanelType = userMembership?.optJSONObject("membership")?.optString("panelType") 
        ?: userMembership?.optString("panelType") 
        ?: "free"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LuxuryDarkGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Luxury Banner Header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = GoldAccent.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(24.dp),
                color = CardSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, LuxuryBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1B2332), CardSurface)
                            )
                        )
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(GoldGradientBrush)
                            .shadow(8.dp, CircleShape, spotColor = GoldAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = LuxuryBlack,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "سطوح عضویت و ارتقای امتیاز مسابقه",
                        color = WarmWhite,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "با فعال‌سازی عضویت نقره‌ای یا طلایی، امتیاز هر مربع را ۳ یا ۶ برابر کنید!",
                        color = GoldLight,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    if (cycle != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x20D4AF37))
                                .border(1.dp, LuxuryBorder, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ریست مسابقه تا: ${formatRemainingTimePersian(cycle.remainingMs)}",
                                color = WarmWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Error / Info Alerts
            if (errorMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ErrorRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    color = ErrorRed.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ErrorRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = errorMessage, color = WarmWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismissMessage) {
                            Text("بستن", color = WarmWhite)
                        }
                    }
                }
            }

            if (infoMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SuccessGreen.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    color = SuccessGreen.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = infoMessage, color = WarmWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismissMessage) {
                            Text("بستن", color = WarmWhite)
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GoldAccent)
                }
            }

            // Membership Panel Cards
            // 1. Free Panel (عادی)
            PanelCard(
                title = "پنل پایه (عادی)",
                priceStr = "رایگان",
                multiplierStr = "ضریب ۱ برابری (۱ امتیاز به ازای هر مربع)",
                description = "پیش‌فرض برای همه کاربران جدید. هر مربع تکمیل‌شده معادل ۱ امتیاز در جدول رتبه‌بندی ثبت می‌شود.",
                isCurrent = currentPanelType == "free",
                buttonText = "پنل فعلی شما",
                buttonEnabled = false,
                accentColor = SilverAccent,
                isVipGold = false,
                onAction = {}
            )

            // 2. Silver Panel (ارتقای نقره‌ای)
            PanelCard(
                title = "ارتقای نقره‌ای",
                priceStr = "۷۰,۰۰۰ تومان",
                multiplierStr = "ضریب ۳ برابری امتیاز (۳ امتیاز به ازای هر مربع)",
                description = "سه برابر شدن امتیاز تمامی ۱۰۰ مربع بعد از فعال‌سازی ارتقا با ماندگاری دائمی روی حساب.",
                isCurrent = currentPanelType == "silver",
                buttonText = if (currentPanelType == "silver") "سطح نقره‌ای فعال شما" else "خرید ارتقای نقره‌ای (۷۰,۰۰۰ تومان)",
                buttonEnabled = currentPanelType != "silver" && currentPanelType != "gold",
                accentColor = SilverAccent,
                isVipGold = false,
                onAction = { onPurchase("pkg_upgrade_silver") }
            )

            // 3. Gold Panel (ارتقای طلایی)
            val goldButtonText = when (currentPanelType) {
                "gold" -> "سطح طلایی فعال شما"
                "silver" -> "ارتقا به طلایی (۱۲۰,۰۰۰ تومان)"
                else -> "خرید ارتقای طلایی (۱۲۰,۰۰۰ تومان)"
            }
            PanelCard(
                title = "ارتقای طلایی (VIP)",
                priceStr = "۱۲۰,۰۰۰ تومان",
                multiplierStr = "ضریب ۶ برابری امتیاز (۶ امتیاز به ازای هر مربع)",
                description = "شش برابر شدن امتیاز هر مربع (۶ امتیاز به ازای هر مربع). بالاترین سطح شانس کسب رتبه اول و هدیه ویژه.",
                isCurrent = currentPanelType == "gold",
                buttonText = goldButtonText,
                buttonEnabled = currentPanelType != "gold",
                accentColor = GoldAccent,
                isVipGold = true,
                onAction = { onPurchase("pkg_upgrade_gold") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Billing Disclosure Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, LuxuryBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "راهنمای پرداخت درون‌برنامه‌ای مایکت",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = GoldLight
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "تمامی پرداخت‌ها از طریق درگاه امن پرداخت درون‌برنامه‌ای مایکت انجام شده و پس از اعتبارسنجی رسید خرید در سرور امن Supabase، ضریب امتیاز بلافاصله فعال می‌گردد.",
                        fontSize = 11.sp,
                        color = SilverAccent,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun PanelCard(
    title: String,
    priceStr: String,
    multiplierStr: String,
    description: String,
    isCurrent: Boolean,
    buttonText: String,
    buttonEnabled: Boolean,
    accentColor: Color,
    isVipGold: Boolean,
    onAction: () -> Unit
) {
    val borderColor = when {
        isCurrent -> accentColor
        isVipGold -> GoldAccent.copy(alpha = 0.5f)
        else -> LuxuryBorder
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isVipGold || isCurrent) 14.dp else 6.dp,
                shape = RoundedCornerShape(26.dp),
                spotColor = accentColor.copy(alpha = 0.35f)
            )
            .border(
                width = if (isCurrent) 2.5.dp else 1.2.dp,
                color = borderColor,
                shape = RoundedCornerShape(26.dp)
            ),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isVipGold) {
                        Brush.verticalGradient(
                            listOf(Color(0xFF231D12), CardSurface)
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color(0xFF19202E), CardSurface)
                        )
                    }
                )
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isVipGold) {
                        Text(text = "👑 ", fontSize = 18.sp)
                    }
                    Text(
                        text = title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isVipGold) GoldLight else WarmWhite
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accentColor.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = priceStr,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = if (isVipGold) GoldLight else accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.2f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = multiplierStr,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WarmWhite
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = description,
                fontSize = 13.sp,
                color = SilverAccent,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = onAction,
                enabled = buttonEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(if (buttonEnabled) 8.dp else 0.dp, RoundedCornerShape(16.dp), spotColor = accentColor),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isVipGold) GoldAccent else if (buttonEnabled) accentColor else Color(0xFF1E293B),
                    disabledContainerColor = Color(0x331E293B),
                    disabledContentColor = Color(0xFF64748B)
                )
            ) {
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = if (buttonEnabled) LuxuryBlack else Color(0xFF94A3B8)
                )
            }
        }
    }
}
