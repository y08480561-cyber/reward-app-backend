package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.SquareStatus
import com.example.ui.components.AppBottomNavigation
import com.example.ui.components.ExperienceFeedbackDialog
import com.example.ui.components.LuxuryScreenCanvas
import com.example.ui.components.PurchaseSuccessModal
import com.example.ui.components.RewardCelebrationModal
import com.example.ui.components.isNetworkAvailable
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            MyApplicationTheme {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                    ) { _ -> }
                    LaunchedEffect(Unit) {
                        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val viewModel: MainViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                val authState by viewModel.authUiState.collectAsState()

                var showLaunchOverlay by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(550)
                    showLaunchOverlay = false
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBlack
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { _ ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            Crossfade(
                                targetState = uiState.currentScreen,
                                animationSpec = tween(durationMillis = 220),
                                label = "screen_crossfade",
                                modifier = Modifier.fillMaxSize()
                            ) { screen ->
                                when (screen) {
                                    AppScreen.AUTH -> {
                                        AuthScreen(
                                            state = authState,
                                            loginNotification = uiState.loginNotification,
                                            onFirstNameChange = { viewModel.onFirstNameChange(it) },
                                            onLastNameChange = { viewModel.onLastNameChange(it) },
                                            onPhoneChange = { viewModel.onPhoneNumberChange(it) },
                                            onPasswordChange = { viewModel.onPasswordChange(it) },
                                            onAuthModeChange = { viewModel.onAuthModeChange(it) },
                                            onDirectLogin = { viewModel.loginOrRegister() },
                                            onOtpChange = { viewModel.onOtpCodeChange(it) },
                                            onRequestOtp = { viewModel.requestOtp() },
                                            onVerifyOtp = { viewModel.verifyOtpAndSubmit() }
                                        )
                                    }
                                    AppScreen.HOME -> {
                                        uiState.currentUser?.let { user ->
                                            val currentUserRank = uiState.leaderboard
                                                .firstOrNull { it.isCurrentUser || it.userId == user.id }
                                                ?.rank
                                            val completedToday = uiState.squares.count { it.status == SquareStatus.COMPLETED }
                                            HomeScreen(
                                                user = user,
                                                cycle = uiState.dailyCycle,
                                                competition = uiState.activeCompetition,
                                                announcement = uiState.activeAnnouncement,
                                                userRank = currentUserRank,
                                                completedSquaresToday = completedToday,
                                                onNavigate = { target -> viewModel.navigateTo(target) }
                                            )
                                        }
                                    }
                                    AppScreen.SQUARES -> {
                                        SquaresScreen(
                                            squares = uiState.squares,
                                            cycle = uiState.dailyCycle,
                                            announcement = uiState.activeAnnouncement,
                                            errorMessage = uiState.errorMessage,
                                            onDismissError = { viewModel.clearMessages() },
                                            onSquareClick = { act, square -> viewModel.onSquareClicked(act, square) }
                                        )
                                    }
                                    AppScreen.LEADERBOARD -> {
                                        LeaderboardScreen(
                                            uiState = uiState,
                                            onRefresh = { viewModel.loadLeaderboard() },
                                            onBack = { viewModel.navigateTo(AppScreen.HOME) }
                                        )
                                    }
                                    AppScreen.PRIZES -> {
                                        PrizesScreen(
                                            viewModel = viewModel,
                                            uiState = uiState,
                                            onBack = { viewModel.navigateTo(AppScreen.HOME) }
                                        )
                                    }
                                    AppScreen.PROFILE -> {
                                        uiState.currentUser?.let { user ->
                                            ProfileScreen(
                                                user = user,
                                                activeCompetition = uiState.activeCompetition,
                                                onLogout = { viewModel.logout() },
                                                onNavigateToStore = { viewModel.navigateTo(AppScreen.STORE) },
                                                onNavigateToSupport = { viewModel.navigateTo(AppScreen.SUPPORT) },
                                                onNavigateToMembership = { viewModel.navigateTo(AppScreen.MEMBERSHIP) },
                                                onBack = { viewModel.navigateTo(AppScreen.HOME) },
                                                isTestAdsEnabled = uiState.isTestAdsEnabled,
                                                onToggleTestAds = { enabled -> viewModel.toggleTestAds(enabled) },
                                                onToggleNotifications = { enabled -> viewModel.toggleNotificationPreference(enabled) }
                                            )
                                        }
                                    }
                                    AppScreen.MEMBERSHIP -> {
                                        MembershipScreen(
                                            panels = uiState.panels,
                                            userMembership = uiState.userMembership,
                                            isLoading = uiState.isMembershipLoading,
                                            cycle = uiState.dailyCycle,
                                            errorMessage = uiState.errorMessage,
                                            infoMessage = uiState.infoMessage,
                                            onRefresh = { viewModel.loadMembershipData() },
                                            onPurchase = { productId -> viewModel.startMyketPurchaseFlow(this@MainActivity, productId) },
                                            onDismissMessage = { viewModel.clearMessages() },
                                            onBack = { viewModel.navigateTo(AppScreen.HOME) }
                                        )
                                    }
                                    AppScreen.STORE -> {
                                        StoreScreen(
                                            products = uiState.storeProducts,
                                            entitlements = uiState.userEntitlements,
                                            isLoading = uiState.isStoreLoading,
                                            errorMessage = uiState.storeErrorMessage,
                                            onPurchase = { productId -> viewModel.startMyketPurchaseFlow(this@MainActivity, productId) },
                                            onRefresh = { viewModel.loadStoreData() },
                                            onDismissError = { viewModel.clearStoreMessages() },
                                            onRestorePurchases = { viewModel.restoreMyketPurchases() },
                                            onBack = { viewModel.navigateTo(AppScreen.HOME) }
                                        )
                                    }
                                    AppScreen.SUPPORT -> {
                                        SupportScreen(
                                            tickets = uiState.supportTickets,
                                            activeTicket = uiState.activeSupportTicket,
                                            messages = uiState.activeTicketMessages,
                                            isLoading = uiState.isSupportLoading,
                                            isSending = uiState.isSendingTicketMessage,
                                            errorMessage = uiState.supportErrorMessage,
                                            successMessage = uiState.supportSuccessMessage,
                                            onRefresh = { viewModel.loadUserTickets() },
                                            onOpenTicket = { ticketId -> viewModel.openTicketDetail(ticketId) },
                                            onCloseTicketDetail = { viewModel.closeTicketDetail() },
                                            onCreateTicket = { subject, category, message ->
                                                viewModel.createSupportTicket(subject, category, message) {}
                                            },
                                            onSendMessage = { ticketId, text ->
                                                viewModel.sendTicketMessage(ticketId, text)
                                            },
                                            onClearMessages = { viewModel.clearSupportMessages() },
                                            onBackToHome = { viewModel.navigateTo(AppScreen.PROFILE) }
                                        )
                                    }
                                }
                            }

                            // Floating Bottom Navigation Dock
                            val density = LocalDensity.current
                            val isImeVisible = WindowInsets.ime.getBottom(density) > 0
                            if (uiState.currentScreen != AppScreen.AUTH && !isImeVisible) {
                                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                                    AppBottomNavigation(
                                        currentScreen = uiState.currentScreen,
                                        onScreenSelected = { screen ->
                                            if (screen == AppScreen.LEADERBOARD && !isNetworkAvailable(this@MainActivity)) {
                                                android.widget.Toast.makeText(
                                                    this@MainActivity,
                                                    "اتصال اینترنت برقرار نیست. لطفاً شبکه خود را بررسی کنید.",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            viewModel.navigateTo(screen)
                                        }
                                    )
                                }
                            }

                            // Experience Feedback Dialog
                            if (uiState.showFeedbackDialog) {
                                ExperienceFeedbackDialog(
                                    isSubmitting = uiState.isSubmittingFeedback,
                                    onDismiss = { viewModel.markFeedbackShown() },
                                    onSubmitInternal = { rating, comment ->
                                        viewModel.submitInternalFeedback(rating, comment)
                                    },
                                    onOpenMyket = {
                                        val appPackage = this@MainActivity.packageName
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("myket://comment?id=$appPackage"))
                                            intent.setPackage("ir.mservices.market")
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            startActivity(intent)
                                        } catch (e: Exception) {
                                            try {
                                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://myket.ir/app/$appPackage"))
                                                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                startActivity(webIntent)
                                            } catch (e2: Exception) {
                                                android.widget.Toast.makeText(
                                                    this@MainActivity,
                                                    "امکان باز کردن مایکت یا مرورگر وجود ندارد.",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        viewModel.markFeedbackShown()
                                    }
                                )
                            }

                            // Celebratory Reward Modal
                            uiState.awardedPointsModal?.let { points ->
                                RewardCelebrationModal(
                                    pointsAwarded = points,
                                    onDismiss = { viewModel.dismissRewardModal() }
                                )
                            }

                            // High-Trust Purchase Success Dialog
                            uiState.purchaseSuccessModal?.let { modalData ->
                                PurchaseSuccessModal(
                                    data = modalData,
                                    onDismiss = { viewModel.dismissPurchaseSuccessModal() }
                                )
                            }

                            // Brief Luxury Launch Fade Overlay
                            AnimatedVisibility(
                                visible = showLaunchOverlay,
                                enter = fadeIn(tween(150)),
                                exit = fadeOut(tween(350))
                            ) {
                                LuxuryLaunchOverlay()
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStart() {
        super.onStart()
        try {
            val viewModel: MainViewModel = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
            viewModel.myketBillingManager.startConnection()
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            val viewModel: MainViewModel = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
            viewModel.myketBillingManager.endConnection()
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun LuxuryLaunchOverlay() {
    LuxuryScreenCanvas {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = GoldAccent.copy(alpha = 0.4f))
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.5.dp, LuxuryBorderStrong, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_trophy),
                    contentDescription = "رقابت کن، جایزه ببر",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "رقابت کن، جایزه ببر",
                style = MaterialTheme.typography.headlineMedium,
                color = WarmWhite
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "باشگاه رقابت و جوایز ایرانسل",
                style = MaterialTheme.typography.bodySmall,
                color = GoldLight
            )
        }
    }
}
