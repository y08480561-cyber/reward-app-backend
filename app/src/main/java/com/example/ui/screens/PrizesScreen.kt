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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyPrimary)
    ) {
        // --- Top Header Bar ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp),
            color = NavySecondary
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(GoldAccent.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
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
                                text = "ویترین جوایز و هدیه مسابقه",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldLight
                            )
                            Text(
                                text = "معرفی هدیه ویژه دوره جاری",
                                fontSize = 11.sp,
                                color = Color.LightGray
                            )
                        }
                    }

                    // User Score Badge
                    Box(
                        modifier = Modifier
                            .background(
                                brush = Brush.horizontalGradient(listOf(GoldAccent, GoldLight)),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = null,
                                tint = NavyPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${uiState.currentUser?.totalScore ?: 0} امتیاز",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
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
                color = ErrorRed.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed)
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
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearRewardsMessages() }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.White)
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "در حال دریافت بنر هدیه از سرور...",
                        fontSize = 13.sp,
                        color = Color.LightGray
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

                // Informational Notice Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .shadow(2.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = NavySecondary,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "راهنمای شرکت در رقابت هدیه",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldLight
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "۱. با مشاهده روزانه ۱۰۰ مربع تبلیغاتی در بخش «۱۰۰ مربع»، امتیاز حساب خود را افزایش دهید.\n" +
                                   "۲. جدول رتبه‌بندی به صورت زنده بروزرسانی می‌شود و شرکت‌کنندگان برتر در پایان دوره مشخص می‌گردند.\n" +
                                   "۳. اهدای هدیه بدون نیاز به کسر امتیاز یا فرایند خرید بوده و مستقیماً توسط تیم مدیریت به برندگان اهدا می‌شود.",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(80.dp)) // Padding for bottom nav bar
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
        if (heroBannerUrl.isNotBlank() && (heroBannerUrl.startsWith("http://") || heroBannerUrl.startsWith("https://"))) {
            heroBannerUrl
        } else {
            R.drawable.banner_rewards_20gb
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(GoldAccent, GoldLight, Color(0xFF00B4D8).copy(alpha = 0.6f), GoldAccent)
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("gift_banner_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavySecondary)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // --- Banner Image Section ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1536f / 1024f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF0F172A), NavyPrimary)
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

                // Top Badge Overlay - aligned to TopEnd so it never obscures centered/right-side text
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        color = if (isActive) GoldAccent else Color.Gray,
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isActive) Icons.Default.CardGiftcard else Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = NavyPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isActive) "هدیه ویژه دوره جاری" else "در حال به‌روزرسانی",
                                color = NavyPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Subtle gold divider between banner graphic and description
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                GoldAccent.copy(alpha = 0.6f),
                                GoldLight,
                                GoldAccent.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // --- Banner Content Section ---
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = heroTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    lineHeight = 26.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = heroSubtitle,
                    fontSize = 13.sp,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 22.sp
                )

                if (termsText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = Color(0xFF00B4D8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = termsText,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                lineHeight = 18.sp
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
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .border(1.dp, GoldAccent, RoundedCornerShape(16.dp))
            .testTag("admin_banner_edit_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavySecondary)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان بنر هدیه") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = GoldAccent,
                            unfocusedLabelColor = Color.LightGray
                        )
                    )

                    OutlinedTextField(
                        value = subtitle,
                        onValueChange = { subtitle = it },
                        label = { Text("توضیحات کوتاه هدیه") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = GoldAccent,
                            unfocusedLabelColor = Color.LightGray
                        )
                    )

                    OutlinedTextField(
                        value = bannerUrl,
                        onValueChange = { bannerUrl = it },
                        label = { Text("آدرس تصویر بنر (URL)") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = GoldAccent,
                            unfocusedLabelColor = Color.LightGray
                        )
                    )

                    OutlinedTextField(
                        value = terms,
                        onValueChange = { terms = it },
                        label = { Text("متن راهنما و قوانین دریافت") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = GoldAccent,
                            unfocusedLabelColor = Color.LightGray
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
                            color = Color.White
                        )
                        Switch(
                            checked = isActive,
                            onCheckedChange = { isActive = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NavyPrimary,
                                checkedTrackColor = GoldAccent
                            )
                        )
                    }

                    Button(
                        onClick = { onSave(title, subtitle, bannerUrl, isActive, terms) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = NavyPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ذخیره بنر هدیه در دیتابیس",
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                    }
                }
            }
        }
    }
}
