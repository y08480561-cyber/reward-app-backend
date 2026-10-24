package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.data.remote.HealthCheckResult
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.NavySecondary
import com.example.viewmodel.AuthMode
import com.example.viewmodel.AuthUiState

@Composable
fun AuthScreen(
    state: AuthUiState,
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
                    .height(200.dp)
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
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
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(GoldAccent)
                            .shadow(8.dp, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = NavyPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "رقابت کن، جایزه ببر",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "سامانه مسابقه ۵۰ مربع جایزه‌دار و ثبت زنده در سرور",
                        color = GoldAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Auth Mode Selector Tabs
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Direct Login Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (state.authMode == AuthMode.DIRECT) NavyPrimary else Color.Transparent
                                )
                                .clickable { onAuthModeChange(AuthMode.DIRECT) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ورود مستقیم (بدون پیامک)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.authMode == AuthMode.DIRECT) GoldAccent else Color.DarkGray
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // OTP Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (state.authMode == AuthMode.OTP) NavyPrimary else Color.Transparent
                                )
                                .clickable { onAuthModeChange(AuthMode.OTP) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ورود با پیامک (OTP)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.authMode == AuthMode.OTP) GoldAccent else Color.DarkGray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Personal Info Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(NavySecondary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "اطلاعات هویتی شرکت‌کننده",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                        }

                        OutlinedTextField(
                            value = state.firstName,
                            onValueChange = onFirstNameChange,
                            label = { Text("نام کوچک") },
                            placeholder = { Text("مثلاً علی") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_first_name"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NavyPrimary,
                                unfocusedBorderColor = Color.LightGray,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = state.lastName,
                            onValueChange = onLastNameChange,
                            label = { Text("نام خانوادگی") },
                            placeholder = { Text("مثلاً رضایی") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_last_name"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NavyPrimary,
                                unfocusedBorderColor = Color.LightGray,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Credentials / Auth Form
                if (state.authMode == AuthMode.DIRECT) {
                    // DIRECT AUTH CARD (No SMS)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(3.dp, RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(NavySecondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VpnKey,
                                        contentDescription = null,
                                        tint = GoldAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ورود مستقیم امن (اعتبارسنجی سرور)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                            }

                            OutlinedTextField(
                                value = state.phoneNumber,
                                onValueChange = onPhoneChange,
                                label = { Text("شماره همراه (09XXXXXXXXX)") },
                                placeholder = { Text("09123456789") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_phone_number"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NavyPrimary,
                                    unfocusedBorderColor = Color.LightGray,
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = state.password,
                                onValueChange = onPasswordChange,
                                label = { Text("رمز عبور یا پین امنیتی سرور") },
                                placeholder = { Text("2026") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_password"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NavyPrimary,
                                    unfocusedBorderColor = Color.LightGray,
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "راهنما: پین پیش‌فرض معتبر در سرور 2026 است. نیازی به پیامک نیست.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = onDirectLogin,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("btn_direct_login"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                                enabled = !state.isLoading
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(color = NavyPrimary, modifier = Modifier.size(22.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NavyPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ورود مستقیم و شروع مسابقه",
                                        fontWeight = FontWeight.Bold,
                                        color = NavyPrimary,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // OTP AUTH CARD
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(3.dp, RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(NavySecondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = GoldAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "احراز هویت با پیامک (OTP)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                            }

                            OutlinedTextField(
                                value = state.phoneNumber,
                                onValueChange = onPhoneChange,
                                label = { Text("شماره همراه (09XXXXXXXXX)") },
                                placeholder = { Text("09123456789") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_phone_number"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                enabled = !state.isOtpSent,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NavyPrimary,
                                    unfocusedBorderColor = Color.LightGray,
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )

                            if (!state.isOtpSent) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = onRequestOtp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("btn_request_otp"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NavySecondary),
                                    enabled = !state.isLoading
                                ) {
                                    if (state.isLoading) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                                    } else {
                                        Text("ارسال کد تأیید (OTP)", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = state.otpCode,
                                    onValueChange = onOtpChange,
                                    label = { Text("کد تأیید ۵ رقمی دریافتی") },
                                    placeholder = { Text("12345") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_otp_code"),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GoldAccent,
                                        unfocusedBorderColor = Color.LightGray,
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = onVerifyOtp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("btn_verify_otp"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                                    enabled = !state.isLoading
                                ) {
                                    if (state.isLoading) {
                                        CircularProgressIndicator(color = NavyPrimary, modifier = Modifier.size(22.dp))
                                    } else {
                                        Text("تأیید کد و ورود به مسابقه", fontWeight = FontWeight.Bold, color = NavyPrimary)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Error Message
                AnimatedVisibility(visible = state.errorMessage != null) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFEE2E2))
                                .border(1.5.dp, ErrorRed, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = state.errorMessage ?: "",
                                color = ErrorRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Info / Success Notice
                AnimatedVisibility(visible = state.infoMessage != null) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE0F2FE))
                                .border(1.5.dp, Color(0xFF0284C7), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = state.infoMessage ?: "",
                                color = Color(0xFF0369A1),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Network Diagnostics Button (Especially for Hamrah Aval / MCI verification)
                OutlinedButton(
                    onClick = onTestConnection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_network_diagnostics"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NavyPrimary
                    ),
                    enabled = !isCheckingHealth
                ) {
                    if (isCheckingHealth) {
                        CircularProgressIndicator(color = NavyPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("در حال بررسی اتصال به سرور Render…", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("بررسی زنده وضعیت سرور (عیب‌یابی همراه اول)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Trust Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = Color(0xFF0D9488),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "متصل به سرور Render | کاربران و امتیازها در دیتابیس سرور ثبت می‌شوند.",
                        fontSize = 11.sp,
                        color = Color(0xFF475569),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Server Health Check Diagnostics Modal
        if (healthCheckResult != null) {
            val res = healthCheckResult
            AlertDialog(
                onDismissRequest = onDismissHealthCheck,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (res.isSuccessful) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (res.isSuccessful) Color(0xFF16A34A) else ErrorRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (res.isSuccessful) "سرور در دسترس است" else "نتیجه عیب‌یابی اتصال سرور",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (res.isSuccessful) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = res.message,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.isSuccessful) Color(0xFF15803D) else ErrorRed
                                )
                                if (res.isSuccessful) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "تاخیر رفت و برگشت: ${res.latencyMs} میلی‌ثانیه | کد HTTP: ${res.httpStatusCode}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF166534)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "آدرس سرور:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "https://reward-app-backend-a9ze.onrender.com",
                            fontSize = 11.sp,
                            color = NavyPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (res.errorCategory != null) {
                            Text(
                                text = "دسته خطای شبکه: ${res.errorCategory}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed
                            )
                        }

                        if (res.tip.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFEF3C7))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "راهنمای همراه اول:\n${res.tip}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF92400E),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismissHealthCheck,
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("متوجه شدم", color = Color.White)
                    }
                }
            )
        }
    }
}
