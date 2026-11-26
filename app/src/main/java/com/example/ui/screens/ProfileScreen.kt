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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CompetitionInfo
import com.example.model.PromoAdBanner
import com.example.model.PromoBannerUrlValidator
import com.example.model.User
import com.example.ui.components.LuxuryCard
import com.example.ui.components.LuxuryHairlineDivider
import com.example.ui.components.LuxuryHighlightCard
import com.example.ui.components.LuxuryScreenCanvas
import com.example.ui.components.PromoAdBannerSection
import com.example.ui.components.LuxurySecondaryButton
import com.example.ui.components.LuxuryTierBadge
import com.example.ui.components.LuxuryTopBar
import com.example.ui.components.formatNumberPersian
import com.example.ui.components.formatPhonePersian
import com.example.ui.components.toPersianDigits
import com.example.ui.theme.*

@Composable
fun ProfileScreen(
    user: User,
    activeCompetition: CompetitionInfo? = null,
    promoAdBanners: List<PromoAdBanner> = emptyList(),
    promoAdBannersClockOffsetMs: Long = 0L,
    onPromoBannerVisible: (String) -> Unit = {},
    onPromoBannerClick: (PromoAdBanner) -> Unit = {},
    onLogout: () -> Unit,
    onNavigateToStore: () -> Unit,
    onNavigateToSupport: () -> Unit = {},
    onNavigateToMembership: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    isTestAdsEnabled: Boolean = false,
    onToggleTestAds: (Boolean) -> Unit = {},
    onToggleNotifications: (Boolean) -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    BackHandler(enabled = onBack != null) {
        onBack?.invoke()
    }

    val effectiveNowMs = remember(promoAdBanners, promoAdBannersClockOffsetMs) {
        System.currentTimeMillis() + promoAdBannersClockOffsetMs
    }
    val profileBanners = remember(promoAdBanners, effectiveNowMs) {
        PromoBannerUrlValidator.filterAndSortActive(promoAdBanners, effectiveNowMs, "profile")
    }

    LuxuryScreenCanvas {
        Column(modifier = Modifier.fillMaxSize()) {
            LuxuryTopBar(
                title = "پروفایل",
                onBack = onBack
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (profileBanners.isNotEmpty()) {
                    PromoAdBannerSection(
                        banners = profileBanners,
                        clockOffsetMs = promoAdBannersClockOffsetMs,
                        onBannerVisible = onPromoBannerVisible,
                        onBannerClick = onPromoBannerClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 1. Clean Identity & Key Stats Card
                LuxuryHighlightCard(contentPadding = PaddingValues(22.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(GoldGradientBrush)
                                .border(1.5.dp, GoldLight.copy(alpha = 0.75f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "تصویر پروفایل",
                                tint = ObsidianBlack,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = user.fullName.ifBlank { "عضو باشگاه رقابت" },
                            style = MaterialTheme.typography.headlineSmall,
                            color = WarmWhite
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = formatPhonePersian(user.phoneNumber),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        LuxuryTierBadge(
                            panelType = user.panelType,
                            multiplier = user.multiplier
                        )

                        Spacer(modifier = Modifier.height(18.dp))
                        LuxuryHairlineDivider(useGoldAccent = true)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "امتیاز دوره",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatNumberPersian(user.totalScore),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = GoldLight
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "ضریب فعلی",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${user.multiplier.toPersianDigits()} برابر",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = WarmWhite
                                )
                            }
                        }

                        if (activeCompetition != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "دوره ${activeCompetition.competitionNumber.toPersianDigits()} • ${activeCompetition.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 2. Unified Account Menu & Settings Card
                LuxuryCard(
                    borderColor = CharcoalBorder,
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    ProfileMenuRow(
                        title = "فروشگاه و ارتقای حساب",
                        icon = Icons.Outlined.Storefront,
                        onClick = onNavigateToStore,
                        modifier = Modifier.testTag("btn_goto_store")
                    )
                    LuxuryHairlineDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ProfileMenuRow(
                        title = "سطوح عضویت و ضریب امتیاز",
                        icon = Icons.Outlined.WorkspacePremium,
                        onClick = onNavigateToMembership
                    )
                    LuxuryHairlineDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ProfileMenuRow(
                        title = "مرکز پشتیبانی و پیام‌ها",
                        icon = Icons.Outlined.SupportAgent,
                        onClick = onNavigateToSupport,
                        modifier = Modifier.testTag("btn_profile_support")
                    )
                    LuxuryHairlineDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Notifications Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp)
                            .clickable { onToggleNotifications(!user.notificationsEnabled) }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "دریافت اعلان‌ها و یادآورها",
                                style = MaterialTheme.typography.titleSmall,
                                color = WarmWhite
                            )
                        }
                        Switch(
                            checked = user.notificationsEnabled,
                            onCheckedChange = onToggleNotifications,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBlack,
                                checkedTrackColor = GoldAccent,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = DarkGraySurface
                            )
                        )
                    }

                    LuxuryHairlineDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Collapsible Rules Row (collapsed by default to keep screen calm)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp)
                            .clickable { showRules = !showRules }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "راهنما و قوانین باشگاه",
                                style = MaterialTheme.typography.titleSmall,
                                color = WarmWhite
                            )
                        }
                        Icon(
                            imageVector = if (showRules) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (showRules) "بستن" else "باز کردن",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(visible = showRules) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 18.dp, bottom = 16.dp)
                        ) {
                            Text(
                                text = "• با مشاهده کامل هر مربع، امتیاز آن بر اساس ضریب سطح عضویت شما ثبت می‌شود.\n" +
                                    "• برندگان هر دوره بر پایه رتبه‌بندی پایانی انتخاب شده و بدون کسر امتیاز هدیه دریافت می‌کنند.\n" +
                                    "• خرید بسته‌ها فقط از طریق درگاه امن مایکت انجام می‌شود.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // 3. Logout Action
                LuxurySecondaryButton(
                    text = "خروج از حساب کاربری",
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    onClick = { showLogoutDialog = true },
                    accentColor = ErrorRed,
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .testTag("btn_logout")
                )

                Spacer(modifier = Modifier.height(104.dp))
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = "خروج از حساب کاربری",
                    style = MaterialTheme.typography.titleLarge,
                    color = WarmWhite
                )
            },
            text = {
                Text(
                    text = "آیا مطمئن هستید که می‌خواهید از حساب فعلی خارج شوید؟ امتیازهای شما روی سرور محفوظ خواهد ماند.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "خروج از حساب",
                        style = MaterialTheme.typography.labelLarge,
                        color = WarmWhite
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "انصراف",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                }
            }
        )
    }
}

@Composable
private fun ProfileMenuRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
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
                tint = GoldLight,
                modifier = Modifier.size(21.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = WarmWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}
