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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.StoreProduct
import com.example.model.UserEntitlements
import com.example.ui.components.FeedbackTone
import com.example.ui.components.LuxuryBadge
import com.example.ui.components.LuxuryBadgeTone
import com.example.ui.components.LuxuryCard
import com.example.ui.components.LuxuryEmptyState
import com.example.ui.components.LuxuryFeedbackBanner
import com.example.ui.components.LuxuryHairlineDivider
import com.example.ui.components.LuxuryHighlightCard
import com.example.ui.components.LuxuryLoadingState
import com.example.ui.components.LuxuryPrimaryButton
import com.example.ui.components.LuxuryScreenCanvas
import com.example.ui.components.LuxurySectionHeader
import com.example.ui.components.LuxuryTopBar
import com.example.ui.components.formatNumberPersian
import com.example.ui.components.formatTomanPersian
import com.example.ui.components.toPersianDigits
import com.example.ui.theme.*

@Composable
fun StoreScreen(
    products: List<StoreProduct>,
    entitlements: UserEntitlements,
    isLoading: Boolean,
    errorMessage: String?,
    onPurchase: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onRestorePurchases: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    BackHandler(enabled = onBack != null) {
        onBack?.invoke()
    }

    LuxuryScreenCanvas {
        Column(modifier = Modifier.fillMaxSize()) {
            LuxuryTopBar(
                title = "فروشگاه و ارتقای حساب",
                subtitle = "بسته‌های بازگشایی آنی و افزایش ضریب",
                onBack = onBack,
                trailingContent = {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "به‌روزرسانی فروشگاه",
                            tint = GoldLight
                        )
                    }
                }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 108.dp)
            ) {
                item {
                    StoreHeader(entitlements = entitlements)
                }

                if (errorMessage != null) {
                    item {
                        LuxuryFeedbackBanner(
                            message = errorMessage,
                            tone = FeedbackTone.ERROR,
                            onDismiss = onDismissError
                        )
                    }
                }

                item {
                    LuxurySectionHeader(
                        title = "بسته‌های ویژه و ارتقای حساب",
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }

                if (isLoading && products.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LuxuryLoadingState(message = "در حال دریافت بسته‌های فروشگاه…")
                        }
                    }
                } else if (products.isEmpty()) {
                    item {
                        LuxuryEmptyState(
                            icon = Icons.Rounded.ShoppingBag,
                            title = "در حال حاضر بسته‌ای فعال نیست",
                            description = "بسته‌های فروشگاه به‌زودی در دسترس قرار می‌گیرند یا می‌توانید فهرست را دوباره بررسی کنید.",
                            actionLabel = "به‌روزرسانی",
                            onAction = onRefresh
                        )
                    }
                } else {
                    items(products, key = { it.id }) { product ->
                        StoreProductCard(product = product, onPurchase = onPurchase)
                    }
                }

                item {
                    StoreFooter(onRestorePurchases = onRestorePurchases)
                }
            }
        }
    }
}

@Composable
fun StoreHeader(entitlements: UserEntitlements) {
    LuxuryHighlightCard(
        contentPadding = PaddingValues(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GoldAccent.copy(alpha = 0.14f))
                    .border(1.dp, LuxuryBorderStrong, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = GoldLight,
                    modifier = Modifier.size(25.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "دارایی‌ها و سطح حساب شما",
                    style = MaterialTheme.typography.titleMedium,
                    color = WarmWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "سرعت پیشرفت و ضریب امتیازهای خود را افزایش دهید",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EntitlementBadge(
                icon = Icons.Rounded.Bolt,
                label = "اعتبار بازگشایی آنی",
                value = "${formatNumberPersian(entitlements.instantUnlockCredits)} مربع",
                accentColor = GoldLight,
                modifier = Modifier.weight(1f)
            )

            val tier = entitlements.accountTier.uppercase()
            val (tierLabel, tierIcon, tierColor) = when (tier) {
                "GOLD" -> Triple("طلایی (${entitlements.multiplier.coerceAtLeast(6).toPersianDigits()}x)", Icons.Rounded.WorkspacePremium, GoldAccent)
                "SILVER" -> Triple("نقره‌ای (${entitlements.multiplier.coerceAtLeast(3).toPersianDigits()}x)", Icons.Rounded.Verified, SilverAccent)
                else -> Triple("پایه (${entitlements.multiplier.coerceAtLeast(1).toPersianDigits()}x)", Icons.Rounded.Star, TextSecondary)
            }

            EntitlementBadge(
                icon = tierIcon,
                label = "سطح و ضریب امتیاز",
                value = tierLabel,
                accentColor = tierColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun EntitlementBadge(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color = GoldAccent,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = ObsidianBlack.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, CharcoalBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite
            )
        }
    }
}

@Composable
fun StoreProductCard(product: StoreProduct, onPurchase: (String) -> Unit) {
    val isUpgrade = product.productType == "ACCOUNT_UPGRADE"
    val isGoldTier = isUpgrade && (product.id.contains("gold", ignoreCase = true) || product.priceToman >= 100000)
    val accentColor = when {
        isGoldTier -> GoldAccent
        isUpgrade -> SilverAccent
        else -> GoldLight
    }

    LuxuryCard(
        modifier = Modifier.testTag("product_card_${product.id}"),
        borderColor = if (isGoldTier) LuxuryBorderStrong else CharcoalBorder,
        contentPadding = PaddingValues(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = product.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = WarmWhite
                    )
                    if (product.badge.isNotBlank()) {
                        LuxuryBadge(
                            text = product.badge,
                            tone = if (isGoldTier) LuxuryBadgeTone.GOLD else LuxuryBadgeTone.CHAMPAGNE
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = product.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.14f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isUpgrade) Icons.Rounded.WorkspacePremium else Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        LuxuryHairlineDivider(useGoldAccent = isGoldTier)
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "مبلغ قابل پرداخت",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatTomanPersian(product.priceToman),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (isGoldTier) GoldLight else WarmWhite
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            LuxuryPrimaryButton(
                text = "خرید از مایکت",
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                onClick = { onPurchase(product.id) },
                height = 48.dp,
                modifier = Modifier.widthIn(min = 148.dp, max = 180.dp)
            )
        }
    }
}

@Composable
fun StoreFooter(onRestorePurchases: (() -> Unit)? = null) {
    LuxuryCard(
        borderColor = CharcoalBorder,
        contentPadding = PaddingValues(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = GoldLight,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "شرایط و قوانین خرید",
                    style = MaterialTheme.typography.titleSmall,
                    color = GoldLight
                )
            }
            if (onRestorePurchases != null) {
                TextButton(
                    onClick = onRestorePurchases,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = GoldLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "بازیابی خریدها",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldLight
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "بسته‌های بازگشایی آنی بلافاصله پس از خرید در چرخه ۲۴ ساعته جاری اعمال می‌شوند. ارتقای سطح نقره‌ای (۳x) و طلایی (۶x) به‌صورت دائمی به حساب شما افزوده شده و ضریب امتیاز همه مربع‌ها را افزایش می‌دهد.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
