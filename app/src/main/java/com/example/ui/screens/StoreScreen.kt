package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StoreProduct
import com.example.model.UserEntitlements
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
    onRestorePurchases: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LuxuryDarkGradient)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            // Header Section
            item {
                StoreHeader(entitlements)
            }

            // Error Message
            if (errorMessage != null) {
                item {
                    ErrorMessage(errorMessage, onDismissError)
                }
            }

            // Store Title Section
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(GoldAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "بسته‌های ویژه و ارتقای مسابقه",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WarmWhite
                    )
                }
            }

            if (isLoading && products.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = GoldAccent)
                    }
                }
            } else {
                items(products) { product ->
                    StoreProductCard(product, onPurchase)
                }
            }

            // Store Footer / Info
            item {
                StoreFooter(onRestorePurchases = onRestorePurchases)
            }
        }
    }
}

@Composable
fun StoreHeader(entitlements: UserEntitlements) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = GoldAccent.copy(alpha = 0.25f)),
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
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(GoldAccent.copy(alpha = 0.25f), Color(0x10D4AF37))
                            )
                        )
                        .border(1.dp, LuxuryBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = GoldLight,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "فروشگاه لوکس و ارتقای حساب",
                        color = WarmWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "سرعت پیشرفت و ضریب امتیازات خود را چند برابر کنید",
                        color = SilverAccent,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EntitlementBadge(
                    icon = Icons.Default.FlashOn,
                    label = "اعتبار بازگشایی آنی",
                    value = "${entitlements.instantUnlockCredits.toPersianDigits()} مربع",
                    accentColor = GoldLight,
                    modifier = Modifier.weight(1f)
                )
                val statusText = when (entitlements.accountTier.uppercase()) {
                    "GOLD" -> "👑 طلایی (۶X)"
                    "SILVER" -> "🥈 نقره‌ای (۳X)"
                    else -> "عادی (۱X)"
                }
                val statusColor = when (entitlements.accountTier.uppercase()) {
                    "GOLD" -> GoldAccent
                    "SILVER" -> SilverAccent
                    else -> Color(0xFF94A3B8)
                }
                EntitlementBadge(
                    icon = Icons.Default.Verified,
                    label = "سطح و ضریب امتیاز",
                    value = statusText,
                    accentColor = statusColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun EntitlementBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accentColor: Color = GoldAccent,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0x220B192C),
        border = androidx.compose.foundation.BorderStroke(1.dp, LuxuryBorder.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = label, color = SilverAccent, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, color = WarmWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StoreProductCard(product: StoreProduct, onPurchase: (String) -> Unit) {
    val isGoldTier = product.productType == "ACCOUNT_UPGRADE" && (product.id.contains("gold", ignoreCase = true) || product.priceToman >= 100000)
    val accentColor = if (isGoldTier) GoldAccent else if (product.productType == "ACCOUNT_UPGRADE") SilverAccent else GoldLight
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(22.dp), spotColor = accentColor.copy(alpha = 0.25f))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(accentColor.copy(alpha = 0.6f), Color(0x22D4AF37), accentColor.copy(alpha = 0.3f))
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = product.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarmWhite
                        )
                        if (product.badge.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accentColor.copy(alpha = 0.18f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = product.badge,
                                    color = accentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = product.description,
                        fontSize = 12.sp,
                        color = SilverAccent,
                        lineHeight = 18.sp
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (product.productType == "ACCOUNT_UPGRADE") Icons.Default.WorkspacePremium else Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            HorizontalDivider(color = LuxuryBorder.copy(alpha = 0.4f), thickness = 1.dp)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "مبلغ قابل پرداخت:", fontSize = 11.sp, color = SilverAccent)
                    Text(
                        text = "${product.priceToman.toPersianDigits()} تومان",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isGoldTier) GoldLight else WarmWhite
                    )
                }
                
                Button(
                    onClick = { onPurchase(product.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGoldTier) GoldAccent else GoldLight
                    ),
                    modifier = Modifier.shadow(6.dp, RoundedCornerShape(14.dp), spotColor = GoldAccent)
                ) {
                    Text(
                        text = "خرید آنلاین",
                        fontWeight = FontWeight.ExtraBold,
                        color = LuxuryBlack,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = LuxuryBlack
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorMessage(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ErrorRed.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = ErrorRed)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = message, color = WarmWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = WarmWhite)
            }
        }
    }
}

@Composable
fun StoreFooter(onRestorePurchases: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, LuxuryBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "شرایط و قوانین خرید", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GoldLight)
                }
                if (onRestorePurchases != null) {
                    TextButton(onClick = onRestorePurchases) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "بازیابی خریدها", fontSize = 11.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "بسته‌های بازگشایی آنی بلافاصله پس از خرید در چرخه ۲۴ ساعته فعلی اعمال می‌شوند. ارتقای سطح نقره‌ای (۳X) و طلایی (۶X) به صورت دائمی به حساب کاربری افزوده شده و شامل مزایای چند برابری امتیازات تمام مربع‌ها است.",
                fontSize = 11.sp,
                color = SilverAccent,
                lineHeight = 18.sp
            )
        }
    }
}
