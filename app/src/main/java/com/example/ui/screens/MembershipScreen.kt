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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Banner Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = NavyPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(GoldAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = NavyPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "پنل‌های عضویت و ارتقای مسابقه",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "با خرید پنل‌های نقره‌ای یا طلایی، امتیاز هر مربع را ۲ تا ۴ برابر کنید!",
                    color = GoldLight,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                if (cycle != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ریست مسابقه تا: ${formatRemainingTimePersian(cycle.remainingMs)}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
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
                    .border(1.dp, ErrorRed, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = ErrorRed.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ErrorRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = errorMessage, color = ErrorRed, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissMessage) {
                        Text("بستن", color = ErrorRed)
                    }
                }
            }
        }

        if (infoMessage != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SuccessGreen, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = SuccessGreen.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = infoMessage, color = SuccessGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissMessage) {
                        Text("بستن", color = SuccessGreen)
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
        // 1. Free Panel
        PanelCard(
            title = "پنل پایه (عادی)",
            priceStr = "رایگان",
            multiplierStr = "ضریب ۱ برابری امتیاز",
            description = "پیش‌فرض برای همه کاربران جدید. هر مربع تکمیل‌شده معادل ۱ امتیاز ثبت می‌شود.",
            isCurrent = currentPanelType == "free",
            buttonText = "پنل فعلی شما",
            buttonEnabled = false,
            accentColor = Color(0xFF64748B),
            onAction = {}
        )

        // 2. Silver Panel
        PanelCard(
            title = "پنل نقره‌ای",
            priceStr = "۵۵,۰۰۰ تومان",
            multiplierStr = "ضریب ۲ برابری امتیاز",
            description = "دو برابر شدن امتیاز تمامی مربع‌های تکمیل‌شده در دوره جاری مسابقه.",
            isCurrent = currentPanelType == "silver",
            buttonText = if (currentPanelType == "silver") "پنل فعال شما" else "خرید پنل نقره‌ای",
            buttonEnabled = currentPanelType != "silver" && currentPanelType != "gold",
            accentColor = Color(0xFF0284C7),
            onAction = { onPurchase("silver_panel") }
        )

        // 3. Gold Panel / Upgrade
        val goldButtonText = when (currentPanelType) {
            "gold" -> "پنل طلایی فعال"
            "silver" -> "ارتقا به طلایی (۱۰۵,۰۰۰ تومان)"
            else -> "خرید پنل طلایی (۱۰۵,۰۰۰ تومان)"
        }
        PanelCard(
            title = "پنل طلایی",
            priceStr = "۱۰۵,۰۰۰ تومان",
            multiplierStr = "ضریب ۴ برابری امتیاز",
            description = "چهار برابر شدن امتیاز هر مربع (۴ امتیاز به ازای هر مربع). امکان ارتقای مستقیم از نقره‌ای با حفظ امتیازات قبلی.",
            isCurrent = currentPanelType == "gold",
            buttonText = goldButtonText,
            buttonEnabled = currentPanelType != "gold",
            accentColor = GoldAccent,
            onAction = {
                val productId = if (currentPanelType == "silver") "gold_upgrade" else "gold_panel"
                onPurchase(productId)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Myket Billing Disclosure Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "راهنمای پرداخت درون‌برنامه‌ای مایکت",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = NavyPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "تمامی پرداخت‌ها از طریق درگاه امن پرداخت درون‌برنامه‌ای مایکت انجام شده و پس از اعتبارسنجی رسید خرید در سرور امن Supabase، ضریب امتیاز بلافاصله فعال می‌گردد.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
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
    onAction: () -> Unit
) {
    // Professional Gradient for high-end look
    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            Color.White,
            if (isCurrent) accentColor.copy(alpha = 0.05f) else Color(0xFFF8FAFC)
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isCurrent) 12.dp else 6.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = accentColor
            )
            .border(
                width = if (isCurrent) 3.5.dp else 1.5.dp,
                color = if (isCurrent) accentColor else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(28.dp)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradient)
                .padding(28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NavyPrimary
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = priceStr,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.25f)),
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NavyPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = description,
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onAction,
                enabled = buttonEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(if (buttonEnabled) 4.dp else 0.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrent) NavySecondary else accentColor,
                    disabledContainerColor = Color(0xFFF1F5F9),
                    disabledContentColor = Color(0xFF94A3B8)
                )
            ) {
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = if (buttonEnabled) Color.White else Color(0xFF64748B)
                )
            }
        }
    }
}
