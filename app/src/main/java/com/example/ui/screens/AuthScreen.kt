package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.remote.HealthCheckResult
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldLight
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.NavySecondary
import com.example.ui.theme.VazirmatnFontFamily
import com.example.viewmodel.AuthMode
import com.example.viewmodel.AuthUiState

@Composable
fun AuthScreen(
    state: AuthUiState,
    loginNotification: com.example.model.LoginNotificationConfig = com.example.model.LoginNotificationConfig(),
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAuthModeChange: (AuthMode) -> Unit,
    onDirectLogin: () -> Unit,
    onOtpChange: (String) -> Unit,
    onRequestOtp: () -> Unit,
    onVerifyOtp: () -> Unit,
    healthCheckResult: HealthCheckResult? = null,
    isCheckingHealth: Boolean = false,
    onTestConnection: () -> Unit = {},
    onDismissHealthCheck: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF1F5F9)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Header Hero Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(NavyPrimary, NavySecondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(GoldLight, GoldAccent, GoldDark)
                                )
                            )
                            .shadow(12.dp, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = NavyPrimary,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "رقابت کن، جایزه ببر",
                        fontFamily = VazirmatnFontFamily,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "سامانه مسابقه ۱۰۰ مربع جایزه‌دار و ثبت زنده در سرور",
                        fontFamily = VazirmatnFontFamily,
                        color = GoldAccent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // ==============================================================
                // Eye-catching Dynamic Notification Card (Frosted Glass Style)
                // ==============================================================
                if (loginNotification.isVisible) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(10.dp, RoundedCornerShape(22.dp))
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    listOf(GoldAccent, GoldAccent.copy(alpha = 0.3f), GoldAccent)
                                ),
                                shape = RoundedCornerShape(22.dp)
                            ),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF0F2542).copy(alpha = 0.92f), // Frosted Navy
                                            Color(0xFF16325B).copy(alpha = 0.96f)
                                        )
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Top Tag: Irancell Exclusive
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(30.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(GoldAccent, Color(0xFFFFA000))
                                                )
                                            )
                                            .padding(horizontal = 14.dp, vertical = 5.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = getIconByName(loginNotification.badgeIcon),
                                                contentDescription = null,
                                                tint = NavyPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = loginNotification.badgeText,
                                                fontFamily = VazirmatnFontFamily,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = NavyPrimary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Main Title
                                Text(
                                    text = loginNotification.mainTitle,
                                    fontFamily = VazirmatnFontFamily,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GoldAccent,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 26.sp
                                )

                                if (loginNotification.mainDescription.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = loginNotification.mainDescription,
                                        fontFamily = VazirmatnFontFamily,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color.White.copy(alpha = 0.9f),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 21.sp
                                    )
                                }

                                // Warning Banner
                                if (loginNotification.warningIsVisible) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF0A192F).copy(alpha = 0.8f))
                                            .border(1.2.dp, GoldAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(GoldAccent.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = getIconByName(loginNotification.warningIcon),
                                                    contentDescription = null,
                                                    tint = GoldAccent,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = loginNotification.warningTitle,
                                                    fontFamily = VazirmatnFontFamily,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = GoldAccent
                                                )
                                                if (loginNotification.warningDescription.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Text(
                                                        text = loginNotification.warningDescription,
                                                        fontFamily = VazirmatnFontFamily,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFFCBD5E1),
                                                        lineHeight = 18.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==============================================================
                // Personal Info Card
                // ==============================================================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .border(1.5.dp, NavySecondary.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(NavyPrimary)
                                    .shadow(2.dp, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "اطلاعات هویتی شرکت‌کننده",
                                fontFamily = VazirmatnFontFamily,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NavyPrimary
                            )
                        }

                        OutlinedTextField(
                            value = state.firstName,
                            onValueChange = onFirstNameChange,
                            label = { Text("نام کوچک", fontFamily = VazirmatnFontFamily) },
                            placeholder = { Text("مثلاً علی", fontFamily = VazirmatnFontFamily) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_first_name"),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NavyPrimary,
                                unfocusedBorderColor = NavySecondary.copy(alpha = 0.35f),
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = state.lastName,
                            onValueChange = onLastNameChange,
                            label = { Text("نام خانوادگی", fontFamily = VazirmatnFontFamily) },
                            placeholder = { Text("مثلاً رضایی", fontFamily = VazirmatnFontFamily) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_last_name"),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NavyPrimary,
                                unfocusedBorderColor = NavySecondary.copy(alpha = 0.35f),
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==============================================================
                // Irancell Phone Number & Direct Login Card
                // ==============================================================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .border(1.5.dp, NavySecondary.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(NavyPrimary)
                                    .shadow(2.dp, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "ورود مستقیم با سیم‌کارت ایرانسل",
                                fontFamily = VazirmatnFontFamily,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NavyPrimary
                            )
                        }

                        OutlinedTextField(
                            value = state.phoneNumber,
                            onValueChange = onPhoneChange,
                            label = {
                                Text(
                                    text = stringResource(R.string.irancell_phone_hint),
                                    fontFamily = VazirmatnFontFamily
                                )
                            },
                            placeholder = { Text("09351234567", fontFamily = VazirmatnFontFamily) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_phone_number"),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NavyPrimary,
                                unfocusedBorderColor = NavySecondary.copy(alpha = 0.35f),
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color.White
                            ),
                            supportingText = {
                                Text(
                                    text = "تنها شماره‌های معتبر شبکه ایرانسل مجاز به شرکت در رقابت می‌باشند.",
                                    fontFamily = VazirmatnFontFamily,
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = onDirectLogin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .shadow(6.dp, RoundedCornerShape(14.dp))
                                .testTag("btn_direct_login"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldAccent
                            ),
                            enabled = !state.isLoading
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(color = NavyPrimary, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ورود مستقیم و شروع مسابقه",
                                    fontFamily = VazirmatnFontFamily,
                                    fontWeight = FontWeight.Black,
                                    color = NavyPrimary,
                                    fontSize = 15.5.sp
                                )
                            }
                        }
                    }
                }

                // Info / Notice Feedback (if any)
                AnimatedVisibility(visible = state.infoMessage != null) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFE0F2FE))
                                .border(1.5.dp, Color(0xFF0284C7), RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = state.infoMessage ?: "",
                                fontFamily = VazirmatnFontFamily,
                                color = Color(0xFF0369A1),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun getIconByName(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (name.lowercase()) {
        "verified" -> Icons.Default.Verified
        "signal_cellular_alt" -> Icons.Default.SignalCellularAlt
        "check_circle" -> Icons.Default.CheckCircle
        "cell_tower" -> Icons.Default.CellTower
        "wifi_off" -> Icons.Default.WifiOff
        "stars" -> Icons.Default.Stars
        "emoji_events" -> Icons.Default.EmojiEvents
        "person" -> Icons.Default.Person
        "phone_android" -> Icons.Default.PhoneAndroid
        else -> Icons.Default.Verified
    }
}
