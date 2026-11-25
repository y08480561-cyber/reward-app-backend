package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
                title = "سطوح عضویت",
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "با ارتقای سطح عضویت، امتیاز هر مربع به‌صورت دائمی ۳ یا ۶ برابر محاسبه می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(horizontal = 4.dp)
                )

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
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LuxuryLoadingState(message = "در حال بررسی سطح عضویت…")
                    }
                }

                PanelCard(
                    title = "عضویت پایه",
                    priceStr = "رایگان",
                    multiplierStr = "ضریب ۱ برابری",
                    description = "۱ امتیاز به ازای هر مربع تکمیل‌شده.",
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
                    multiplierStr = "ضریب ۳ برابری",
                    description = "۳ امتیاز دائمی به ازای هر مربع تکمیل‌شده.",
                    isCurrent = currentPanelType == "silver",
                    buttonText = if (currentPanelType == "silver") "فعال است" else "ارتقا به نقره‌ای",
                    buttonEnabled = currentPanelType != "silver" && currentPanelType != "gold",
                    accentColor = SilverAccent,
                    icon = Icons.Rounded.Verified,
                    isVipGold = false,
                    onAction = { onPurchase("pkg_upgrade_silver") }
                )

                val goldButtonText = when (currentPanelType) {
                    "gold" -> "فعال است"
                    "silver" -> "ارتقا به طلایی"
                    else -> "خرید عضویت طلایی"
                }

                PanelCard(
                    title = "عضویت طلایی ویژه",
                    priceStr = "۱۲۰٬۰۰۰ تومان",
                    multiplierStr = "ضریب ۶ برابری",
                    description = "بالاترین سرعت صعود با ۶ امتیاز دائمی به ازای هر مربع.",
                    isCurrent = currentPanelType == "gold",
                    buttonText = goldButtonText,
                    buttonEnabled = currentPanelType != "gold",
                    accentColor = GoldAccent,
                    icon = Icons.Rounded.WorkspacePremium,
                    isVipGold = true,
                    onAction = { onPurchase("pkg_upgrade_gold") }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = GoldLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "پرداخت امن درون‌برنامه‌ای مایکت و فعال‌سازی آنی ضریب.",
                        style = MaterialTheme.typography.labelSmall,
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isVipGold) GoldLight else accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isVipGold) GoldLight else WarmWhite
                )
                if (isCurrent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    LuxuryBadge(
                        text = "فعال",
                        icon = Icons.Rounded.CheckCircle,
                        tone = LuxuryBadgeTone.SUCCESS
                    )
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

        Spacer(modifier = Modifier.height(12.dp))
        LuxuryHairlineDivider(useGoldAccent = isVipGold)
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = multiplierStr,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

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
        LuxuryHighlightCard(contentPadding = PaddingValues(18.dp)) {
            cardContent()
        }
    } else {
        LuxuryCard(
            borderColor = borderColor,
            contentPadding = PaddingValues(18.dp)
        ) {
            cardContent()
        }
    }
}
