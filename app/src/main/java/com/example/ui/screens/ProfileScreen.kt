package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.User
import com.example.ui.components.toPersianDigits
import com.example.ui.theme.*

@Composable
fun ProfileScreen(
    user: User,
    activeCompetition: com.example.model.CompetitionInfo? = null,
    onLogout: () -> Unit,
    onNavigateToStore: () -> Unit,
    onNavigateToSupport: () -> Unit = {},
    onToggleNotifications: (Boolean) -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LuxuryDarkGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Profile VIP Header Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = GoldAccent.copy(alpha = 0.3f)),
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
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(GoldGradientBrush)
                            .border(2.5.dp, GoldLight, CircleShape)
                            .shadow(10.dp, CircleShape, spotColor = GoldAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = LuxuryBlack,
                            modifier = Modifier.size(46.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = user.fullName,
                            color = WarmWhite,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        
                        if (user.panelType != "free") {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (user.panelType == "gold") GoldAccent.copy(alpha = 0.2f) else SilverAccent.copy(alpha = 0.2f),
                                modifier = Modifier.border(
                                    width = 1.dp,
                                    color = if (user.panelType == "gold") GoldAccent else SilverAccent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            ) {
                                Text(
                                    text = if (user.panelType == "gold") "👑 طلایی (۶X)" else "🥈 نقره‌ای (۳X)",
                                    color = if (user.panelType == "gold") GoldLight else SilverAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = user.phoneNumber.toPersianDigits(),
                        color = SilverAccent,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // User ID Pill - Dark Glass
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1AF5F2E9))
                            .border(1.dp, LuxuryBorder.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "شناسه کاربری: ${user.id}",
                            color = WarmWhite.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Store & Upgrade Banner Button - Metallic Gold Shimmer
            Button(
                onClick = onNavigateToStore,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(10.dp, RoundedCornerShape(18.dp), spotColor = GoldAccent)
                    .testTag("btn_goto_store"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(LuxuryBlack.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = LuxuryBlack,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "فروشگاه، ارتقای حساب و بازگشایی آنی",
                            color = LuxuryBlack,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = "مشاهده ←",
                        color = LuxuryBlack,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }

            // Support & Help Desk Card - Luxury Dark Style
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(18.dp))
                    .clickable { onNavigateToSupport() }
                    .testTag("btn_profile_support"),
                shape = RoundedCornerShape(18.dp),
                color = CardSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, LuxuryBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(GoldAccent.copy(alpha = 0.15f))
                                .border(1.dp, LuxuryBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "مرکز پشتیبانی و پیام‌ها",
                                color = WarmWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "ثبت تیکت، پیگیری و گفت‌وگوی مستقیم با مدیریت",
                                color = SilverAccent,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text(
                        text = "ورود ←",
                        color = GoldLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // Stats Card - Luxury VIP Design
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, LuxuryBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(GoldAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "آمار و اطلاعات حساب کاربری",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarmWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    activeCompetition?.let { comp ->
                        ProfileStatRow(
                            label = "مسابقه فعال",
                            value = "دوره ${comp.competitionNumber} (${comp.title})"
                        )
                    }
                    ProfileStatRow(
                        label = "مجموع امتیازهای دوره جاری",
                        value = "${user.totalScore.toPersianDigits()} امتیاز",
                        isHighlight = true
                    )
                    ProfileStatRow(
                        label = "وضعیت حساب",
                        value = "فعال و تایید شده"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = LuxuryBorder.copy(alpha = 0.3f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "دریافت اعلان‌ها و اطلاعیه‌ها",
                            fontSize = 12.sp,
                            color = WarmWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = user.notificationsEnabled,
                            onCheckedChange = onToggleNotifications,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = LuxuryBlack,
                                checkedTrackColor = GoldAccent
                            )
                        )
                    }
                }
            }

            // Logout Button - Luxury Muted Style
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_logout"),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("خروج از حساب کاربری", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = CardSurface,
            title = {
                Text(
                    text = "خروج از حساب کاربری",
                    fontWeight = FontWeight.Bold,
                    color = WarmWhite
                )
            },
            text = {
                Text(
                    text = "آیا اطمینان دارید که می‌خواهید از حساب خود خارج شوید؟",
                    color = SilverAccent,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("خروج قطعی", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("انصراف", color = SilverAccent)
                }
            }
        )
    }
}

@Composable
private fun ProfileStatRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = SilverAccent
        )
        Text(
            text = value,
            fontSize = if (isHighlight) 14.sp else 12.sp,
            fontWeight = if (isHighlight) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (isHighlight) GoldLight else WarmWhite
        )
    }
}
