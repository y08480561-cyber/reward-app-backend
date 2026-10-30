package com.example.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.RewardCategoryItem
import com.example.model.RewardClaimRecord
import com.example.model.RewardItem
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState
import com.example.viewmodel.MainViewModel

@Composable
fun PrizesScreen(
    viewModel: MainViewModel,
    uiState: MainUiState,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Showcase, 1: History
    var selectedRewardForClaim by remember { mutableStateOf<RewardItem?>(null) }

    // On initial load or retry
    LaunchedEffect(Unit) {
        viewModel.loadRewardsData()
    }

    BackHandler(enabled = true) {
        if (selectedRewardForClaim != null) {
            selectedRewardForClaim = null
        } else if (selectedTab != 0) {
            selectedTab = 0
        } else {
            onBack?.invoke()
        }
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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(GoldAccent.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CardGiftcard,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = uiState.rewardAppearance.heroTitle.ifBlank { "ویترین جوایز و پاداش‌ها" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldLight
                            )
                            Text(
                                text = "تبدیل امتیاز مسابقه به شارژ و جایزه",
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

        // --- Tab Selection (Showcase vs History) ---
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = NavySecondary,
            contentColor = GoldAccent
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        "ویترین جوایز",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        "سوابق درخواست‌ها (${uiState.userClaimsHistory.size})",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            )
        }

        // --- Status Banners (Error or Success Messages) ---
        AnimatedVisibility(visible = uiState.rewardsErrorMessage != null || uiState.claimSuccessMessage != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (uiState.rewardsErrorMessage != null) {
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

                if (uiState.claimSuccessMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.claimSuccessMessage ?: "",
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
                        text = "در حال بارگذاری ویترین جوایز از سرور...",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                }
            }
        } else if (selectedTab == 0) {
            // === TAB 0: REWARDS SHOWCASE ===
            Column(modifier = Modifier.fillMaxSize()) {
                // Category Selector Filter Chips
                if (uiState.rewardCategories.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = uiState.selectedCategorySlug == null,
                                onClick = { viewModel.loadRewardsData(null) },
                                label = { Text("همه دسته‌ها") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldAccent,
                                    selectedLabelColor = NavyPrimary,
                                    containerColor = NavySecondary,
                                    labelColor = Color.White
                                )
                            )
                        }
                        items(uiState.rewardCategories) { cat ->
                            FilterChip(
                                selected = uiState.selectedCategorySlug == cat.slug || uiState.selectedCategorySlug == cat.id,
                                onClick = { viewModel.loadRewardsData(cat.slug) },
                                label = { Text(cat.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldAccent,
                                    selectedLabelColor = NavyPrimary,
                                    containerColor = NavySecondary,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                }

                if (uiState.rewardItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CardGiftcard,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "هیچ جایزه‌ای در این دسته‌بندی یافت نشد",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "لطفاً دسته‌بندی دیگری را انتخاب کنید یا دوباره بارگذاری نمایید.",
                                fontSize = 12.sp,
                                color = Color.LightGray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadRewardsData(uiState.selectedCategorySlug) },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = NavyPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تلاش مجدد", color = NavyPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp, top = 4.dp)
                    ) {
                        items(uiState.rewardItems) { reward ->
                            RewardCard(
                                reward = reward,
                                userScore = uiState.currentUser?.totalScore ?: 0,
                                onClaimClick = { selectedRewardForClaim = reward }
                            )
                        }
                    }
                }
            }
        } else {
            // === TAB 1: CLAIMS HISTORY ===
            if (uiState.userClaimsHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "شما هنوز هیچ جایزه‌ای ثبت نکرده‌اید",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "با تکمیل ۱۰۰ مربع روزانه امتیاز کسب کنید و از ویترین جوایز درخواست دهید.",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(uiState.userClaimsHistory) { claim ->
                        ClaimHistoryCard(claim = claim)
                    }
                }
            }
        }
    }

    // --- Claim Reward Dialog ---
    selectedRewardForClaim?.let { reward ->
        ClaimRewardDialog(
            reward = reward,
            user = uiState.currentUser,
            isSubmitting = uiState.claimingRewardId == reward.id,
            onDismiss = { selectedRewardForClaim = null },
            onSubmit = { name, phone, address ->
                viewModel.submitRewardClaim(
                    rewardId = reward.id,
                    recipientName = name,
                    phone = phone,
                    address = address,
                    onSuccess = { selectedRewardForClaim = null }
                )
            }
        )
    }
}

@Composable
fun RewardCard(
    reward: RewardItem,
    userScore: Int,
    onClaimClick: () -> Unit
) {
    val isEnoughScore = userScore >= reward.costPoints
    val hasStock = reward.stockQuantity > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavySecondary)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color(0xFF0F1B2B))
            ) {
                if (reward.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = reward.imageUrl,
                        contentDescription = reward.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Badges overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (reward.isFeatured) {
                        Surface(
                            color = GoldAccent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "ویژه",
                                color = NavyPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Stock Badge
                    Surface(
                        color = if (hasStock) Color(0xFF10B981) else ErrorRed,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (hasStock) "موجودی: ${reward.stockQuantity} عدد" else "پایان موجودی",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = reward.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (reward.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = reward.description,
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Points Required Badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${reward.costPoints} امتیاز",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )
                    }

                    // Claim Button
                    Button(
                        onClick = onClaimClick,
                        enabled = hasStock && reward.isActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEnoughScore) GoldAccent else Color(0xFF334155),
                            contentColor = if (isEnoughScore) NavyPrimary else Color.Gray,
                            disabledContainerColor = Color(0xFF1E293B),
                            disabledContentColor = Color.DarkGray
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (!hasStock) "ناموجود" else if (isEnoughScore) "دریافت جایزه" else "امتیاز ناكافی",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClaimHistoryCard(claim: RewardClaimRecord) {
    val statusColor = when (claim.status) {
        "COMPLETED", "APPROVED" -> Color(0xFF10B981)
        "SHIPPED", "PROCESSING" -> Color(0xFF3B82F6)
        "REJECTED" -> ErrorRed
        else -> GoldAccent
    }

    val statusTitle = when (claim.status) {
        "PENDING" -> "در حال بررسی پشتیبانی"
        "APPROVED" -> "تایید شده"
        "PROCESSING" -> "در حال پردازش و ارسال"
        "SHIPPED" -> "تحویل اداره پست گردید"
        "COMPLETED" -> "تحویل کامل داده شد"
        "REJECTED" -> "رد شده"
        else -> claim.status
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavySecondary)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = claim.rewardTitle.ifBlank { "جایزه مسابقه" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor)
                ) {
                    Text(
                        text = statusTitle,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "کد پیگیری: ${claim.trackingCode}",
                    fontSize = 12.sp,
                    color = GoldLight,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "مصرف: ${claim.costPoints} امتیاز",
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
            }

            if (claim.trackingNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "کد رهگیری مرسوله: ${claim.trackingNumber}",
                    fontSize = 12.sp,
                    color = Color(0xFF60A5FA),
                    fontWeight = FontWeight.Bold
                )
            }

            if (claim.adminNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "توضیحات مدیر: ${claim.adminNotes}",
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimRewardDialog(
    reward: RewardItem,
    user: com.example.model.User?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (name: String, phone: String, address: String) -> Unit
) {
    var recipientName by remember { mutableStateOf(user?.fullName ?: "") }
    var phone by remember { mutableStateOf(user?.phoneNumber ?: "") }
    var address by remember { mutableStateOf("") }

    val userScore = user?.totalScore ?: 0
    val isScoreEnough = userScore >= reward.costPoints

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onSubmit(recipientName, phone, address) },
                enabled = isScoreEnough && recipientName.isNotBlank() && phone.isNotBlank() && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NavyPrimary)
                } else {
                    Text("ثبت و کسر امتیاز", color = NavyPrimary, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = Color.LightGray)
            }
        },
        title = {
            Text(
                text = "درخواست دریافت جایزه",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GoldLight
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = reward.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "امتیاز موردنیاز: ${reward.costPoints} | امتیاز فعلی شما: $userScore",
                    fontSize = 12.sp,
                    color = if (isScoreEnough) Color(0xFF10B981) else ErrorRed,
                    fontWeight = FontWeight.Bold
                )

                if (!isScoreEnough) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "امتیاز شما برای دریافت این جایزه کافی نیست. برای کسب امتیاز، ۱۰۰ مربع روزانه را مشاهده کنید.",
                        fontSize = 11.sp,
                        color = ErrorRed
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    label = { Text("نام و نام خانوادگی تحویل‌گیرنده") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("شماره همراه هماهنگی") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("آدرس پستی یا شماره کارت / توضیحات تحویل") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        containerColor = NavySecondary,
        shape = RoundedCornerShape(16.dp)
    )
}
