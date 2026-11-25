package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.DailyCycle
import com.example.ui.components.FeedbackTone
import com.example.ui.components.LuxuryBadge
import com.example.ui.components.LuxuryBadgeTone
import com.example.ui.components.LuxuryCard
import com.example.ui.components.LuxuryFeedbackBanner
import com.example.ui.components.LuxuryHairlineDivider
import com.example.ui.components.LuxuryHighlightCard
import com.example.ui.components.LuxuryLoadingState
import com.example.ui.components.LuxuryPrimaryButton
import com.example.ui.components.LuxuryScreenCanvas
import com.example.ui.components.LuxurySecondaryButton
import com.example.ui.components.LuxuryTopBar
import com.example.ui.components.formatRemainingTimePersian
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
    onDismissMessage: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val currentPanelType = userMembership?.optJSONObject("membership")?.optString("panelType")
        ?: userMembership?.optString("panelType")
        ?: "free"

    BackHandler(enabled = onBack != null) {
        onBack?.invoke()
    }

    LuxuryScreenCanvas {
        Column(modifier = Modifier.fillMaxSize()) {
            LuxuryTopBar(
                title = "سطوح عضویت ویژه",
                subtitle = "افزایش ضریب امتیاز در تمام مربع‌ها",
                onBack = onBack,
                trailingContent = {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "به‌روزرسانی عضویت",
                            tint = GoldLight
                        )
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Overview Card
                LuxuryHighlightCard(contentPadding = PaddingValues(22.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(GoldGradientBrush)
                                .border(1.5.dp, GoldLight.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WorkspacePremium,
                                contentDescription = null,
                                tint = ObsidianBlack,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "سطوح عضویت و ضریب امتیاز",
                            style = MaterialTheme.typography.headlineSmall,
                            color = WarmWhite,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "با فعال‌سازی پنل نقره‌ای یا طلایی، امتیاز هر مربع تکمیل‌شده به‌صورت دائمی ۳ یا ۶ برابر محاسبه می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 360.dp)
                        )

                        if (cycle != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = ObsidianBlack.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, LuxuryBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Timer,
                                        contentDescription = null,
                                        tint = GoldLight,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "بازنشانی چرخه روزانه: ${formatRemainingTimePersian(cycle.remainingMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = WarmWhite
                                    )
                                }
                            }
                        }
                    }
                }

                LuxuryFeedbackBanner(
                    message = errorMessage,
                    tone = FeedbackTone.ERROR,
                    onDismiss = onDismissMessage
                )

                LuxuryFeedbackBanner(
                    message = infoMessage,
                    tone = FeedbackTone.SUCCESS,
                    onDismiss = onDismissMessage
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LuxuryLoadingState(message = "در حال بررسی وضعیت عضویت…")
                    }
                }

                PanelCard(
                    title = "عضویت پایه",
                    priceStr = "رایگان",
                    multiplierStr = "ضریب ۱ برابری (۱ امتیاز به ازای هر مربع)",
                    description = "سطح پیش‌فرض برای تمامی کاربران. هر مربع تکمیل‌شده در این سطح ۱ امتیاز در جدول رتبه‌بندی ثبت می‌کند.",
                    isCurrent = currentPanelType == "free",
                    buttonText = "سطح فعلی شما",
                    buttonEnabled = false,
                    accentColor = TextSecondary,
                    icon = Icons.Rounded.Star,
                    isVipGold = false,
                    onAction = {}
                )

                PanelCard(
                    title = "عضویت نقره‌ای",
                    priceStr = "۷۰٬۰۰۰ تومان",
                    multiplierStr = "ضریب ۳ برابری (۳ امتیاز به ازای هر مربع)",
                    description = "سه برابر شدن امتیاز تمامی ۱۰۰ مربع پس از فعال‌سازی، به‌صورت دائمی روی حساب کاربری شما.",
                    isCurrent = currentPanelType == "silver",
                    buttonText = if (currentPanelType == "silver") "سطح نقره‌ای فعال است" else "ارتقا به سطح نقره‌ای",
                    buttonEnabled = currentPanelType != "silver" && currentPanelType != "gold",
                    accentColor = SilverAccent,
                    icon = Icons.Rounded.Verified,
                    isVipGold = false,
                    onAction = { onPurchase("pkg_upgrade_silver") }
                )

                val goldButtonText = when (currentPanelType) {
                    "gold" -> "سطح طلایی فعال است"
                    "silver" -> "ارتقا به طلایی (۱۲۰٬۰۰۰ تومان)"
                    else -> "خرید عضویت طلایی (۱۲۰٬۰۰۰ تومان)"
                }

                PanelCard(
                    title = "عضویت طلایی ویژه",
                    priceStr = "۱۲۰٬۰۰۰ تومان",
                    multiplierStr = "ضریب ۶ برابری (۶ امتیاز به ازای هر مربع)",
                    description = "بالاترین ضریب امتیاز در باشگاه رقابت. هر مربع تکمیل‌شده ۶ امتیاز ثبت می‌کند و شانس شما را برای جایگاه نخست به حداکثر می‌رساند.",
                    isCurrent = currentPanelType == "gold",
                    buttonText = goldButtonText,
                    buttonEnabled = currentPanelType != "gold",
                    accentColor = GoldAccent,
                    icon = Icons.Rounded.WorkspacePremium,
                    isVipGold = true,
                    onAction = { onPurchase("pkg_upgrade_gold") }
                )

                LuxuryCard(
                    borderColor = CharcoalBorder,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = GoldLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تضمین پرداخت امن مایکت",
                            style = MaterialTheme.typography.titleSmall,
                            color = GoldLight
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "تمامی تراکنش‌ها از طریق درگاه پرداخت درون‌برنامه‌ای مایکت انجام شده و پس از تأیید خودکار رسید در سرور، ضریب جدید بلافاصله فعال می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(104.dp))
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
    icon: ImageVector = Icons.Rounded.Star,
    isVipGold: Boolean,
    onAction: () -> Unit
) {
    val borderColor = when {
        isCurrent -> accentColor
        isVipGold -> LuxuryBorderStrong
        else -> CharcoalBorder
    }

    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.14f))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isVipGold) GoldLight else accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isVipGold) GoldLight else WarmWhite
                    )
                    if (isCurrent) {
                        Spacer(modifier = Modifier.height(3.dp))
                        LuxuryBadge(
                            text = "فعال روی حساب",
                            icon = Icons.Rounded.CheckCircle,
                            tone = LuxuryBadgeTone.SUCCESS
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            LuxuryBadge(
                text = priceStr,
                tone = when {
                    isVipGold -> LuxuryBadgeTone.GOLD
                    accentColor == SilverAccent -> LuxuryBadgeTone.SILVER
                    else -> LuxuryBadgeTone.NEUTRAL
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        LuxuryHairlineDivider(useGoldAccent = isVipGold)
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = multiplierStr,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (isVipGold && buttonEnabled) {
            LuxuryPrimaryButton(
                text = buttonText,
                onClick = onAction
            )
        } else {
            LuxurySecondaryButton(
                text = buttonText,
                onClick = onAction,
                enabled = buttonEnabled,
                accentColor = if (buttonEnabled) accentColor else TextMuted
            )
        }
    }

    if (isVipGold) {
        LuxuryHighlightCard(contentPadding = PaddingValues(20.dp)) {
            cardContent()
        }
    } else {
        LuxuryCard(
            borderColor = borderColor,
            contentPadding = PaddingValues(20.dp)
        ) {
            cardContent()
        }
    }
}
