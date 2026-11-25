package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.remote.ApiConfig
import com.example.model.RewardAppearanceConfig
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
import com.example.ui.components.LuxuryTextField
import com.example.ui.components.LuxuryTopBar
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

    LaunchedEffect(Unit) {
        viewModel.loadRewardsData()
    }

    BackHandler(enabled = onBack != null) {
        onBack?.invoke()
    }

    LuxuryScreenCanvas {
        Column(modifier = Modifier.fillMaxSize()) {
            LuxuryTopBar(
                title = "جایزه دوره",
                onBack = onBack
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (uiState.isRewardsLoading) {
                    LuxuryLoadingState(message = "در حال دریافت جایزه دوره…")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LuxuryFeedbackBanner(
                            message = uiState.rewardsErrorMessage,
                            tone = FeedbackTone.ERROR,
                            onRetry = { viewModel.loadRewardsData() },
                            onDismiss = { viewModel.clearRewardsMessages() }
                        )

                        LuxuryFeedbackBanner(
                            message = uiState.claimSuccessMessage,
                            tone = FeedbackTone.SUCCESS,
                            onDismiss = { viewModel.clearRewardsMessages() }
                        )

                        val banner = uiState.rewardAppearance

                        if (uiState.currentUser?.isAdmin == true) {
                            AdminGiftBannerEditCard(
                                appearance = banner,
                                onSave = { title, desc, url, active, terms ->
                                    viewModel.updateGiftBanner(title, desc, url, active, terms)
                                }
                            )
                        }

                        SingleGiftBannerCard(
                            heroTitle = banner.heroTitle.ifBlank { "اینترنت ۲۰ گیگابایتی هدیه دوره جاری" },
                            heroSubtitle = banner.heroSubtitle.ifBlank {
                                "هدیه ویژه برای نفرات برتر جدول رتبه‌بندی"
                            },
                            heroBannerUrl = banner.heroBannerUrl,
                            termsText = banner.termsText.ifBlank {
                                "اهدای هدیه در پایان دوره بدون کسر امتیاز انجام می‌شود."
                            },
                            isActive = banner.isActive
                        )

                        // Concise 3-Step Overview
                        LuxuryCard(
                            borderColor = CharcoalBorder,
                            contentPadding = PaddingValues(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                PrizeGuideStepRow(
                                    stepNumber = "۱",
                                    title = "تکمیل مربع‌های روزانه",
                                    description = "هر روز امتیاز بیشتر جمع‌آوری کنید."
                                )
                                LuxuryHairlineDivider()
                                PrizeGuideStepRow(
                                    stepNumber = "۲",
                                    title = "صعود در جدول رتبه‌بندی",
                                    description = "رتبه نهایی پایان دوره ملاک انتخاب برنده است."
                                )
                                LuxuryHairlineDivider()
                                PrizeGuideStepRow(
                                    stepNumber = "۳",
                                    title = "دریافت بدون کسر امتیاز",
                                    description = "تیم پشتیبانی مستقیماً با برندگان هماهنگ می‌کند."
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(104.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrizeGuideStepRow(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${stepNumber.toPersianDigits()}.",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = GoldLight,
            modifier = Modifier.width(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = WarmWhite
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
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
        val resolved = ApiConfig.resolveBackendAssetUrl(heroBannerUrl)
        if (resolved.isNotBlank()) resolved else R.drawable.banner_rewards_20gb
    }

    LuxuryHighlightCard(
        modifier = Modifier.testTag("gift_banner_card"),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1536f / 1024f)
                .background(ObsidianBlack),
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
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, CharcoalSurface.copy(alpha = 0.88f))
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
            ) {
                LuxuryBadge(
                    text = if (isActive) "جایزه فعال" else "در حال به‌روزرسانی",
                    icon = if (isActive) Icons.Rounded.CardGiftcard else Icons.Rounded.HourglassEmpty,
                    tone = if (isActive) LuxuryBadgeTone.GOLD else LuxuryBadgeTone.NEUTRAL
                )
            }
        }

        LuxuryHairlineDivider(useGoldAccent = true)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = heroTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = WarmWhite
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = heroSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            if (termsText.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                LuxuryHairlineDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Verified,
                        contentDescription = null,
                        tint = GoldLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = termsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarmWhite.copy(alpha = 0.88f)
                    )
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

    LuxuryCard(
        modifier = Modifier.testTag("admin_banner_edit_card"),
        borderColor = LuxuryBorderStrong,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(GoldAccent.copy(alpha = 0.14f))
                        .border(1.dp, LuxuryBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = GoldLight,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ویرایش بنر جایزه (مدیریت)",
                    style = MaterialTheme.typography.titleSmall,
                    color = GoldLight
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (isExpanded) "بستن" else "باز کردن",
                tint = GoldLight
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LuxuryTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "عنوان بنر جایزه"
                )
                LuxuryTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = "توضیح کوتاه",
                    singleLine = false,
                    maxLines = 3
                )
                LuxuryTextField(
                    value = bannerUrl,
                    onValueChange = { bannerUrl = it },
                    label = "نشانی تصویر بنر",
                    isLtrContent = true
                )
                LuxuryTextField(
                    value = terms,
                    onValueChange = { terms = it },
                    label = "شرایط و توضیحات تکمیلی",
                    singleLine = false,
                    maxLines = 3
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { isActive = !isActive },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "نمایش فعال بنر برای کاربران",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarmWhite
                        )
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBlack,
                            checkedTrackColor = GoldAccent,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkGraySurface
                        )
                    )
                }

                LuxuryPrimaryButton(
                    text = "ذخیره تغییرات بنر",
                    icon = Icons.Rounded.Save,
                    onClick = { onSave(title, subtitle, bannerUrl, isActive, terms) }
                )
            }
        }
    }
}
