package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import com.example.viewmodel.AppScreen
import java.util.Locale

/**
 * Converts English digits (0-9) to Persian digits (۰-۹)
 */
fun String.toPersianDigits(): String {
    val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    val builder = StringBuilder()
    for (char in this) {
        if (char in '0'..'9') {
            builder.append(persianDigits[char - '0'])
        } else {
            builder.append(char)
        }
    }
    return builder.toString()
}

fun Int.toPersianDigits(): String = this.toString().toPersianDigits()
fun Long.toPersianDigits(): String = this.toString().toPersianDigits()

/**
 * Checks if device currently has active internet connectivity
 */
fun isNetworkAvailable(context: android.content.Context): Boolean {
    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/**
 * Formats milliseconds into HH:MM:SS in Persian digits
 */
fun formatRemainingTimePersian(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds).toPersianDigits()
}

@Composable
fun AppHeader(
    title: String = "رقابت کن، جایزه ببر",
    totalScore: Int,
    onProfileClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ObsidianBlack.copy(alpha = 0.95f)
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_header_profile")
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(DarkGraySurface)
                            .border(1.dp, LuxuryBorderStrong, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "پروفایل کاربری",
                            tint = GoldLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = WarmWhite,
                    textAlign = TextAlign.Center
                )

                LuxuryScorePill(
                    score = totalScore,
                    onClick = onProfileClick
                )
            }
            LuxuryHairlineDivider(useGoldAccent = true)
        }
    }
}

private data class NavTabSpec(
    val screen: AppScreen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun AppBottomNavigation(
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit
) {
    val startItems = remember {
        listOf(
            NavTabSpec(AppScreen.HOME, "خانه", Icons.Filled.Home, Icons.Outlined.Home),
            NavTabSpec(AppScreen.PRIZES, "جوایز", Icons.Filled.CardGiftcard, Icons.Outlined.CardGiftcard)
        )
    }
    val endItems = remember {
        listOf(
            NavTabSpec(AppScreen.LEADERBOARD, "رتبه‌بندی", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
            NavTabSpec(AppScreen.PROFILE, "پروفایل", Icons.Filled.Person, Icons.Outlined.Person)
        )
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Protrusion cradle behind central 100 Squares button
            Surface(
                modifier = Modifier
                    .offset(y = (-14).dp)
                    .size(72.dp),
                shape = CircleShape,
                color = DockSurfaceColor,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, LuxuryBorderStrong)
            ) {}

            // Floating Obsidian Dock
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(62.dp)
                    .shadow(18.dp, RoundedCornerShape(28.dp), spotColor = Color.Black),
                color = DockSurfaceColor,
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, CharcoalBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        startItems.forEach { tab ->
                            val isSelected = currentScreen == tab.screen
                            BottomNavItem(
                                selected = isSelected,
                                icon = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                label = tab.label,
                                onClick = { onScreenSelected(tab.screen) },
                                modifier = Modifier.testTag("btn_nav_${tab.screen.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(72.dp))

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        endItems.forEach { tab ->
                            val isSelected = currentScreen == tab.screen ||
                                (tab.screen == AppScreen.PROFILE &&
                                    (currentScreen == AppScreen.STORE || currentScreen == AppScreen.MEMBERSHIP || currentScreen == AppScreen.SUPPORT))
                            BottomNavItem(
                                selected = isSelected,
                                icon = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                label = tab.label,
                                onClick = { onScreenSelected(tab.screen) },
                                modifier = Modifier.testTag("btn_nav_${tab.screen.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            CentralLogoButton(
                selected = currentScreen == AppScreen.SQUARES,
                label = "۱۰۰ مربع",
                icon = Icons.Filled.GridView,
                onClick = { onScreenSelected(AppScreen.SQUARES) },
                modifier = Modifier.testTag("btn_nav_squares")
            )
        }
    }
}

@Composable
fun BottomNavItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "nav_item_scale"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(bounded = true, color = GoldAccent),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) GoldLight else TextSecondary,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp
            ),
            color = if (selected) GoldLight else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CentralLogoButton(
    selected: Boolean,
    label: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "center_nav_scale"
    )

    Box(
        modifier = modifier
            .offset(y = (-16).dp)
            .size(64.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(12.dp, CircleShape, spotColor = GoldAccent.copy(alpha = 0.4f))
            .clip(CircleShape)
            .background(GoldGradientBrush, CircleShape)
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(listOf(GoldLight, GoldAccent, GoldDark)),
                shape = CircleShape
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label ?: "۱۰۰ مربع",
                tint = ObsidianBlack,
                modifier = Modifier.size(23.dp)
            )
            if (label != null) {
                Text(
                    text = label,
                    color = ObsidianBlack,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subValue: String? = null,
    icon: ImageVector,
    accentColor: Color = GoldAccent,
    modifier: Modifier = Modifier
) {
    LuxuryCard(
        modifier = modifier,
        borderColor = LuxuryBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(19.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = WarmWhite
        )
        if (subValue != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subValue,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

/**
 * Celebratory Modal when points are awarded from completing a rewarded video
 */
@Composable
fun RewardCelebrationModal(
    pointsAwarded: Int = 1,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            color = CharcoalSurface,
            border = BorderStroke(1.2.dp, LuxuryBorderStrong),
            shadowElevation = 20.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LuxuryElevatedCardGradient)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(GoldGradientBrush)
                        .border(1.5.dp, GoldLight.copy(alpha = 0.7f), CircleShape)
                        .shadow(12.dp, CircleShape, spotColor = GoldAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = ObsidianBlack,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "امتیاز جدید ثبت شد",
                    style = MaterialTheme.typography.headlineSmall,
                    color = GoldLight,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "+${formatNumberPersian(pointsAwarded)} امتیاز",
                    style = MaterialTheme.typography.displaySmall,
                    color = WarmWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "مشاهده ویدیو با موفقیت تأیید شد و امتیاز این مربع به مجموع امتیازهای شما افزوده گردید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                LuxuryPrimaryButton(
                    text = "ادامه رقابت",
                    onClick = onDismiss,
                    modifier = Modifier.testTag("btn_dismiss_celebration")
                )
            }
        }
    }
}

/**
 * Dialog to ask for user feedback/review with reward points
 */
@Composable
fun ExperienceFeedbackDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmitInternal: (rating: Int, comment: String) -> Unit,
    onOpenMyket: () -> Unit
) {
    var rating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }
    var isInternalView by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CharcoalSurface,
            border = BorderStroke(1.dp, LuxuryBorderStrong),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LuxuryElevatedCardGradient)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(GoldAccent.copy(alpha = 0.14f))
                        .border(1.dp, LuxuryBorderStrong, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isInternalView) Icons.Rounded.SupportAgent else Icons.Rounded.Star,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isInternalView) "ثبت دیدگاه مستقیم" else "دیدگاه شما ارزشمند است",
                    style = MaterialTheme.typography.headlineSmall,
                    color = WarmWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isInternalView)
                        "با ثبت نظر سازنده خود، ما را در ارتقای کیفیت برنامه یاری کنید و ۲ امتیاز هدیه دریافت نمایید."
                    else
                        "خوشحالیم که همراه باشگاه رقابت هستید. با ثبت امتیاز در مایکت یا ارسال دیدگاه مستقیم، از تیم توسعه حمایت کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (!isInternalView) {
                    LuxuryPrimaryButton(
                        text = "ثبت امتیاز در مایکت",
                        icon = Icons.AutoMirrored.Rounded.Launch,
                        onClick = onOpenMyket,
                        modifier = Modifier.testTag("btn_myket_review")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { isInternalView = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_feedback_internal"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, LuxuryBorderStrong)
                    ) {
                        Text(
                            text = "ارسال دیدگاه و دریافت ۲ امتیاز",
                            style = MaterialTheme.typography.labelLarge,
                            color = GoldLight
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_dismiss_feedback")
                    ) {
                        Text(
                            text = "فعلاً نه",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(5) { index ->
                            val starIndex = index + 1
                            val isSelected = starIndex <= rating
                            IconButton(
                                onClick = { rating = starIndex },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = "امتیاز ${starIndex.toPersianDigits()}",
                                    tint = if (isSelected) GoldAccent else TextMuted,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp),
                        placeholder = {
                            Text(
                                text = "دیدگاه یا پیشنهاد خود را بنویسید (اختیاری)…",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = WarmWhite),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = WarmWhite,
                            unfocusedTextColor = WarmWhite,
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedContainerColor = ObsidianBlack,
                            unfocusedContainerColor = ObsidianBlack
                        )
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    if (isSubmitting) {
                        CircularProgressIndicator(color = GoldAccent, modifier = Modifier.size(28.dp))
                    } else {
                        LuxuryPrimaryButton(
                            text = "ثبت دیدگاه و دریافت ۲ امتیاز",
                            onClick = { onSubmitInternal(rating, comment) }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(
                            onClick = { isInternalView = false },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = "بازگشت",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
