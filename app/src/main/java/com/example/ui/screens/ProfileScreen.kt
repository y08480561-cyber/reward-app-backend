package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CompetitionInfo
import com.example.model.User
import com.example.ui.components.LuxuryCard
import com.example.ui.components.LuxuryHairlineDivider
import com.example.ui.components.LuxuryHighlightCard
import com.example.ui.components.LuxuryPrimaryButton
import com.example.ui.components.LuxuryScreenCanvas
import com.example.ui.components.LuxurySecondaryButton
import com.example.ui.components.LuxurySectionHeader
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
    val scrollState = rememberScrollState()

    BackHandler(enabled = onBack != null) {
        onBack?.invoke()
    }

    LuxuryScreenCanvas {
        Column(modifier = Modifier.fillMaxSize()) {
            LuxuryTopBar(
                title = "پروفایل کاربری",
                subtitle = "مشخصات حساب و تنظیمات",
                onBack = onBack,
                scoreValue = user.totalScore
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
                // VIP Identity Showcase Card
                LuxuryHighlightCard(contentPadding = PaddingValues(22.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(GoldGradientBrush)
                                .border(2.dp, GoldLight.copy(alpha = 0.8f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "تصویر پروفایل",
                                tint = ObsidianBlack,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = user.fullName.ifBlank { "عضو باشگاه رقابت" },
                            style = MaterialTheme.typography.headlineSmall,
                            color = WarmWhite
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LuxuryTierBadge(
                            panelType = user.panelType,
                            multiplier = user.multiplier
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = formatPhonePersian(user.phoneNumber),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = ObsidianBlack.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, CharcoalBorder)
                        ) {
                            Text(
                                text = "شناسه کاربری: ${user.id}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Store Primary Action
                LuxuryPrimaryButton(
                    text = "فروشگاه، ارتقای حساب و بازگشایی آنی",
                    icon = Icons.Rounded.Storefront,
                    onClick = onNavigateToStore,
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .testTag("btn_goto_store")
                )

                // Quick Navigation Rows
                ProfileActionRowCard(
                    title = "سطوح عضویت و ضرایب امتیاز",
                    subtitle = "بررسی و مقایسه پنل پایه، نقره‌ای و طلایی",
                    icon = Icons.Rounded.WorkspacePremium,
                    onClick = onNavigateToMembership
                )

                ProfileActionRowCard(
                    title = "مرکز پشتیبانی و پیام‌ها",
                    subtitle = "ثبت تیکت جدید، پیگیری وضعیت و گفت‌وگو با پشتیبانی",
                    icon = Icons.Rounded.SupportAgent,
                    onClick = onNavigateToSupport,
                    modifier = Modifier.testTag("btn_profile_support")
                )

                LuxurySectionHeader(
                    title = "اطلاعات حساب و تنظیمات",
                    modifier = Modifier.widthIn(max = 600.dp)
                )

                LuxuryCard(
                    borderColor = CharcoalBorder,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    activeCompetition?.let { comp ->
                        ProfileStatRow(
                            label = "دوره فعال رقابت",
                            value = "دوره ${comp.competitionNumber.toPersianDigits()} • ${comp.title}"
                        )
                    }
                    ProfileStatRow(
                        label = "مجموع امتیازهای دوره جاری",
                        value = "${formatNumberPersian(user.totalScore)} امتیاز",
                        isHighlight = true
                    )
                    ProfileStatRow(
                        label = "ضریب امتیاز فعلی",
                        value = "${user.multiplier.toPersianDigits()} برابر"
                    )
                    ProfileStatRow(
                        label = "وضعیت حساب",
                        value = "فعال و تأیید شده"
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    LuxuryHairlineDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onToggleNotifications(!user.notificationsEnabled) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "دریافت اعلان‌ها و اطلاعیه‌ها",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = WarmWhite
                                )
                                Text(
                                    text = "اطلاع از شروع دوره‌های جدید و پیام‌های پشتیبانی",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
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

                    Spacer(modifier = Modifier.height(12.dp))
                    LuxuryHairlineDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = GoldLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "قوانین کلی باشگاه رقابت",
                            style = MaterialTheme.typography.titleSmall,
                            color = GoldLight
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• کسب امتیاز: با مشاهده کامل هر تبلیغ در بخش ۱۰۰ مربع، امتیاز مربع با ضریب سطح عضویت شما ثبت می‌شود.\n" +
                                "• رتبه‌بندی و جوایز: جایگاه نهایی شما بر اساس مجموع امتیاز دوره تعیین شده و برندگان بدون کسر امتیاز هدیه دریافت می‌کنند.\n" +
                                "• ارتقا و بازگشایی: بسته‌های فروشگاه صرفاً از طریق درگاه امن مایکت پردازش می‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                // Logout Action
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
private fun ProfileActionRowCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LuxuryCard(
        modifier = modifier,
        onClick = onClick,
        borderColor = LuxuryBorder,
        contentPadding = PaddingValues(16.dp)
    ) {
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
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkGraySurface)
                        .border(1.dp, LuxuryBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = GoldLight,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = WarmWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = GoldLight,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ProfileStatRow(
    label: String,
    value: String,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = if (isHighlight) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold) else MaterialTheme.typography.labelLarge,
            color = if (isHighlight) GoldLight else WarmWhite
        )
    }
}
