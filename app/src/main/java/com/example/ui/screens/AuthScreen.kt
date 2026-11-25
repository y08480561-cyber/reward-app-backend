package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.FeedbackTone
import com.example.ui.components.LuxuryCard
import com.example.ui.components.LuxuryFeedbackBanner
import com.example.ui.components.LuxuryHairlineDivider
import com.example.ui.components.LuxuryPrimaryButton
import com.example.ui.components.LuxuryScreenCanvas
import com.example.ui.components.LuxuryTextField
import com.example.ui.components.toPersianDigits
import com.example.ui.theme.*
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
    onVerifyOtp: () -> Unit
) {
    val scrollState = rememberScrollState()
    var isNoticeExpanded by remember { mutableStateOf(false) }

    LuxuryScreenCanvas {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Minimal Brand Header (no box, open space)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .shadow(14.dp, RoundedCornerShape(22.dp), spotColor = GoldAccent.copy(alpha = 0.35f))
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.2.dp, LuxuryBorderStrong, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_trophy),
                        contentDescription = stringResource(R.string.app_full_title),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.app_full_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = WarmWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "ورود و عضویت در باشگاه رقابت",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Quiet, Single-Layer Collapsible Irancell Strip (no nested cards)
            if (loginNotification.isVisible) {
                val hasMoreDetails = loginNotification.mainDescription.isNotBlank() ||
                    (loginNotification.warningIsVisible && loginNotification.warningDescription.isNotBlank())

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .let { mod ->
                            if (hasMoreDetails) mod.clickable { isNoticeExpanded = !isNoticeExpanded } else mod
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = CharcoalSurface.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, CharcoalBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = getIconByName(loginNotification.badgeIcon),
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = loginNotification.badgeText.ifBlank { loginNotification.mainTitle },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = GoldLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (loginNotification.warningIsVisible && loginNotification.warningTitle.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = loginNotification.warningTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = if (isNoticeExpanded) 3 else 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (hasMoreDetails) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = if (isNoticeExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (isNoticeExpanded) "بستن توضیحات" else "نمایش توضیحات",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        AnimatedVisibility(visible = isNoticeExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                            ) {
                                LuxuryHairlineDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                                if (loginNotification.mainDescription.isNotBlank()) {
                                    Text(
                                        text = loginNotification.mainDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WarmWhite.copy(alpha = 0.85f)
                                    )
                                }
                                if (loginNotification.warningIsVisible && loginNotification.warningDescription.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = loginNotification.warningDescription,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoldLight.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Streamlined Single Authentication Card
            LuxuryCard(
                modifier = Modifier.widthIn(max = 520.dp),
                borderColor = CharcoalBorder,
                contentPadding = PaddingValues(18.dp)
            ) {
                // First & Last Name side-by-side for clean density
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LuxuryTextField(
                        value = state.firstName,
                        onValueChange = onFirstNameChange,
                        label = "نام",
                        placeholder = "علی",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_first_name")
                    )

                    LuxuryTextField(
                        value = state.lastName,
                        onValueChange = onLastNameChange,
                        label = "نام خانوادگی",
                        placeholder = "رضایی",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_last_name")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val phoneHelper = if (state.phoneNumber.isNotEmpty() && state.phoneNumber.length < 11) {
                    "${state.phoneNumber.length.toPersianDigits()} از ۱۱ رقم"
                } else null

                LuxuryTextField(
                    value = state.phoneNumber,
                    onValueChange = onPhoneChange,
                    label = "شماره موبایل ایرانسل",
                    placeholder = "09351234567",
                    supportingText = phoneHelper,
                    leadingIcon = Icons.Rounded.PhoneAndroid,
                    isLtrContent = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.testTag("input_phone_number")
                )

                if (!state.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    LuxuryFeedbackBanner(
                        message = state.errorMessage,
                        tone = FeedbackTone.ERROR
                    )
                }

                if (!state.infoMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    LuxuryFeedbackBanner(
                        message = state.infoMessage,
                        tone = FeedbackTone.INFO
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                LuxuryPrimaryButton(
                    text = "ورود",
                    icon = Icons.AutoMirrored.Rounded.Login,
                    isLoading = state.isLoading,
                    onClick = onDirectLogin,
                    modifier = Modifier.testTag("btn_direct_login")
                )
            }
        }
    }
}

@Composable
fun getIconByName(name: String): ImageVector {
    return when (name.lowercase()) {
        "verified" -> Icons.Rounded.Verified
        "signal_cellular_alt" -> Icons.Rounded.SignalCellularAlt
        "check_circle" -> Icons.Rounded.CheckCircle
        "cell_tower" -> Icons.Rounded.CellTower
        "wifi_off" -> Icons.Rounded.WifiOff
        "stars" -> Icons.Rounded.Stars
        "emoji_events" -> Icons.Rounded.EmojiEvents
        "person" -> Icons.Rounded.Person
        "phone_android" -> Icons.Rounded.PhoneAndroid
        else -> Icons.Rounded.Verified
    }
}
