package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.RewardAppearanceConfig
import com.example.ui.components.toPersianDigits
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState
import com.example.viewmodel.MainViewModel

@Composable
fun PrizesScreen(
    viewModel: MainViewModel,
    uiState: MainUiState,
    onBack: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()

    // On initial load or retry
    LaunchedEffect(Unit) {
        viewModel.loadRewardsData()
    }

    BackHandler(enabled = true) {
        onBack?.invoke()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LuxuryDarkGradient)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // --- Top Luxury Header Bar ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp),
                color = LuxuryBlack
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(LuxuryBlack, Color(0xFF131926))
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, LuxuryBorder, Color.Transparent)
                            ),
                            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(GoldAccent.copy(alpha = 0.25f), Color(0x10D4AF37))
                                        )
                                    )
                                    .border(1.dp, LuxuryBorder, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CardGiftcard,
                                    contentDescription = null,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "ویترین جوایز و هدایای ویژه",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarmWhite
                                )
                                Text(
                                    text = "معرفی جایزه نفیس دوره جاری مسابقه",
                                    fontSize = 11.sp,
                                    color = SilverAccent
                                )
                            }
                        }

                        // User Score Badge - Luxury Gold Pill
                        Box(
                            modifier = Modifier
                                .shadow(6.dp, RoundedCornerShape(20.dp), spotColor = GoldAccent)
                                .background(GoldGradientBrush, shape = RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Stars,
                                    contentDescription = null,
                                    tint = LuxuryBlack,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${uiState.currentUser?.totalScore?.toPersianDigits() ?: "۰"} امتیاز",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = LuxuryBlack
                                )
                            }
                        }
                    }
                }
            }

            // --- Status Banners (Error or Refresh Messages) ---
            AnimatedVisibility(visible = uiState.rewardsErrorMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    color = ErrorRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.rewardsErrorMessage ?: "",
                            fontSize = 12.sp,
                            color = WarmWhite,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearRewardsMessages() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = WarmWhite)
                        }
                    }
                }
            }

            if (uiState.isRewardsLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = GoldAccent)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "در حال بارگذاری ویترین لوکس هدایا...",
                            fontSize = 13.sp,
                            color = SilverAccent
                        )
                    }
                }
            } else {
                // === SINGLE GIFT BANNER SHOWCASE ===
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val banner = uiState.rewardAppearance

                    // Admin Gift Banner Editor card
                    if (uiState.currentUser?.isAdmin == true) {
                        AdminGiftBannerEditCard(
                            appearance = banner,
                            onSave = { title, desc, url, active, terms ->
                                viewModel.updateGiftBanner(title, desc, url, active, terms)
                            }
                        )
                    }

                    // Main Single Gift Banner Card
                    SingleGiftBannerCard(
                        heroTitle = banner.heroTitle.ifBlank { "اینترنت ۲۰ گیگ برنده شو!" },
                        heroSubtitle = banner.heroSubtitle.ifBlank { "هدیه ویژه دوره جاری مسابقه برای نفرات برتر و شرکت‌کنندگان فعال" },
                        heroBannerUrl = banner.heroBannerUrl,
                        termsText = banner.termsText.ifBlank { "این بخش صرفاً برای معرفی هدیه است و نیازی به خرید یا ثبت درخواست ندارد." },
                        isActive = banner.isActive
                    )

                    // Informational Notice Card - Luxury Styling
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 680.dp)
                            .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = GoldAccent.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(20.dp),
                        color = CardSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, LuxuryBorder)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(GoldAccent.copy(alpha = 0.15f))
                                        .border(1.dp, LuxuryBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = GoldLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "راهنمای شرکت در رقابت هدیه",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "۱. با مشاهده روزانه ۱۰۰ مربع تبلیغاتی در بخش «۱۰۰ مربع»، امتیاز حساب خود را افزایش دهید.\n" +
                                       "۲. جدول رتبه‌بندی به صورت زنده بروزرسانی می‌شود و شرکت‌کنندگان برتر در پایان دوره مشخص می‌گردند.\n" +
                                       "۳. اهدای هدیه بدون نیاز به کسر امتیاز یا فرایند خرید بوده و مستقیماً توسط تیم مدیریت به برندگان اهدا می‌شود.",
                                fontSize = 12.sp,
                                color = WarmWhite.copy(alpha = 0.85f),
                                lineHeight = 22.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(100.dp)) // Clearance for bottom nav bar
                }
            }
        }
    }
}

@Composable
fun SingleGiftBannerCard(
    heroTitle: String,
    heroSubtitle: String,
    heroBannerUrl: String,
    termsText: String,
    isActive: Boolean
) {
    val context = LocalContext.current
    val imageModel: Any = remember(heroBannerUrl) {
        val trimmed = heroBannerUrl.trim()
        when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("/") -> "https://reward-app-backend-a9ze.onrender.com$trimmed"
            trimmed.isNotBlank() -> "https://reward-app-backend-a9ze.onrender.com/$trimmed"
            else -> R.drawable.banner_rewards_20gb
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = GoldAccent.copy(alpha = 0.35f))
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(GoldAccent, GoldLight, SilverAccent.copy(alpha = 0.5f), GoldAccent)
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .testTag("gift_banner_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // --- Banner Image Section ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1536f / 1024f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF070B12), NavyPrimary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(true)
                        .placeholder(R.drawable.banner_rewards_20gb)
                        .error(R.drawable.banner_rewards_20gb)
                        .fallback(R.drawable.banner_rewards_20gb)
                        .build(),
                    contentDescription = heroTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillWidth
                )

                // Top Badge Overlay - aligned to TopEnd
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopEnd)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        color = if (isActive) GoldAccent else Color(0xFF334155),
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isActive) Icons.Default.CardGiftcard else Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = if (isActive) LuxuryBlack else WarmWhite,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isActive) "هدیه ویژه دوره جاری" else "در حال به‌روزرسانی",
                                color = if (isActive) LuxuryBlack else WarmWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            // Subtle gold metallic divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                GoldAccent.copy(alpha = 0.5f),
                                GoldLight,
                                GoldAccent.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // --- Banner Content Section ---
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = heroTitle,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WarmWhite,
                    lineHeight = 28.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = heroSubtitle,
                    fontSize = 13.sp,
                    color = SilverAccent,
                    lineHeight = 22.sp
                )

                if (termsText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0x18F5F2E9),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LuxuryBorder.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = termsText,
                                fontSize = 11.sp,
                                color = WarmWhite.copy(alpha = 0.8f),
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminGiftBannerEditCard(
    appearance: RewardAppearanceConfig,
    onSave: (String, String, String, Boolean, String) -> Unit
) {
    var title by remember(appearance.heroTitle) { mutableStateOf(appearance.heroTitle) }
    var subtitle by remember(appearance.heroSubtitle) { mutableStateOf(appearance.heroSubtitle) }
    var bannerUrl by remember(appearance.heroBannerUrl) { mutableStateOf(appearance.heroBannerUrl) }
    var terms by remember(appearance.termsText) { mutableStateOf(appearance.termsText) }
    var isActive by remember(appearance.isActive) { mutableStateOf(appearance.isActive) }
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .border(1.dp, LuxuryBorder, RoundedCornerShape(20.dp))
            .testTag("admin_banner_edit_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GoldAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ویرایش بنر هدیه (پنل مدیریت)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = GoldAccent
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان بنر هدیه") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = WarmWhite),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = LuxuryBorder,
                            focusedLabelColor = GoldLight,
                            unfocusedLabelColor = SilverAccent
                        )
                    )

                    OutlinedTextField(
                        value = subtitle,
                        onValueChange = { subtitle = it },
                        label = { Text("توضیحات کوتاه هدیه") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = WarmWhite),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = LuxuryBorder,
                            focusedLabelColor = GoldLight,
                            unfocusedLabelColor = SilverAccent
                        )
                    )

                    OutlinedTextField(
                        value = bannerUrl,
                        onValueChange = { bannerUrl = it },
                        label = { Text("آدرس تصویر بنر (URL)") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = WarmWhite),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = LuxuryBorder,
                            focusedLabelColor = GoldLight,
                            unfocusedLabelColor = SilverAccent
                        )
                    )

                    OutlinedTextField(
                        value = terms,
                        onValueChange = { terms = it },
                        label = { Text("متن راهنما و قوانین دریافت") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = WarmWhite),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = LuxuryBorder,
                            focusedLabelColor = GoldLight,
                            unfocusedLabelColor = SilverAccent
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "وضعیت بنر هدیه (فعال/غیرفعال):",
                            fontSize = 12.sp,
                            color = WarmWhite
                        )
                        Switch(
                            checked = isActive,
                            onCheckedChange = { isActive = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = LuxuryBlack,
                                checkedTrackColor = GoldAccent
                            )
                        )
                    }

                    Button(
                        onClick = { onSave(title, subtitle, bannerUrl, isActive, terms) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = GoldAccent),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = LuxuryBlack,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ذخیره بنر هدیه در دیتابیس",
                            fontWeight = FontWeight.ExtraBold,
                            color = LuxuryBlack
                        )
                    }
                }
            }
        }
    }
}
