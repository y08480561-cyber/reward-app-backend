package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AppBottomNavigation
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ensure system navigation bar is transparent to remove "blackness"
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            MyApplicationTheme {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                    ) { isGranted -> }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val viewModel: MainViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                val authState by viewModel.authUiState.collectAsState()
                val healthResult by viewModel.healthCheckResult.collectAsState()
                val isCheckingHealth by viewModel.isCheckingHealth.collectAsState()

                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent, // Transparent because Surface handles background
                    ) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Main Content
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        top = innerPadding.calculateTopPadding(),
                                        bottom = 0.dp
                                    )
                            ) {
                                when (uiState.currentScreen) {
                                    AppScreen.AUTH -> {
                                        AuthScreen(
                                            state = authState,
                                            onFirstNameChange = { viewModel.onFirstNameChange(it) },
                                            onLastNameChange = { viewModel.onLastNameChange(it) },
                                            onPhoneChange = { viewModel.onPhoneNumberChange(it) },
                                            onPasswordChange = { viewModel.onPasswordChange(it) },
                                            onAuthModeChange = { viewModel.onAuthModeChange(it) },
                                            onDirectLogin = { viewModel.loginOrRegister() },
                                            onOtpChange = { viewModel.onOtpCodeChange(it) },
                                            onRequestOtp = { viewModel.requestOtp() },
                                            onVerifyOtp = { viewModel.verifyOtpAndSubmit() },
                                            healthCheckResult = healthResult,
                                            isCheckingHealth = isCheckingHealth,
                                            onTestConnection = { viewModel.performServerHealthCheck() },
                                            onDismissHealthCheck = { viewModel.dismissHealthCheck() }
                                        )
                                    }
                                    AppScreen.HOME -> {
                                        uiState.currentUser?.let { user ->
                                            HomeScreen(
                                                user = user,
                                                cycle = uiState.dailyCycle,
                                                competition = uiState.activeCompetition,
                                                announcement = uiState.activeAnnouncement,
                                                userRank = 1,
                                                onNavigate = { screen -> viewModel.navigateTo(screen) }
                                            )
                                        }
                                    }
                                    AppScreen.SQUARES -> {
                                        SquaresScreen(
                                            squares = uiState.squares,
                                            cycle = uiState.dailyCycle,
                                            announcement = uiState.activeAnnouncement,
                                            onSquareClick = { act, square -> viewModel.onSquareClicked(act, square) }
                                        )
                                    }
                                    AppScreen.LEADERBOARD -> {
                                        LeaderboardScreen(
                                            onBack = { viewModel.navigateTo(AppScreen.SQUARES) }
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
                                            onPurchase = { productId -> viewModel.purchasePanel(productId) },
                                            onDismissMessage = { viewModel.clearMessages() }
                                        )
                                    }
                                    AppScreen.STORE -> {
                                        StoreScreen(
                                            products = uiState.storeProducts,
                                            entitlements = uiState.userEntitlements,
                                            isLoading = uiState.isStoreLoading,
                                            errorMessage = uiState.storeErrorMessage,
                                            onPurchase = { productId -> viewModel.purchaseStoreProduct(productId) },
                                            onRefresh = { viewModel.loadStoreData() },
                                            onDismissError = { viewModel.clearStoreMessages() }
                                        )
                                    }
                                    AppScreen.SUPPORT -> {
                                        SupportScreen(
                                            tickets = uiState.supportTickets,
                                            activeTicket = uiState.activeSupportTicket,
                                            messages = uiState.activeTicketMessages,
                                            isLoading = uiState.isSupportLoading,
                                            isSending = uiState.isSendingTicketMessage,
                                            giftBannerConfig = uiState.rewardAppearance,
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
                                            onSaveGiftBanner = { title, desc, url, active, terms ->
                                                viewModel.updateGiftBanner(title, desc, url, active, terms)
                                            },
                                            onClearMessages = { viewModel.clearSupportMessages() },
                                            onBackToHome = { viewModel.navigateTo(AppScreen.PROFILE) }
                                        )
                                    }
                                }
                            }

                            // Floating Navigation Bar
                            if (uiState.currentScreen != AppScreen.AUTH) {
                                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                                    AppBottomNavigation(
                                        currentScreen = uiState.currentScreen,
                                        onScreenSelected = { screen ->
                                            if (screen == AppScreen.LEADERBOARD) {
                                                if (!com.example.ui.screens.isNetworkAvailable(this@MainActivity)) {
                                                    android.widget.Toast.makeText(
                                                        this@MainActivity,
                                                        "اتصال اینترنت برقرار نیست. لطفاً اتصال شبکه خود را بررسی کنید.",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                            viewModel.navigateTo(screen)
                                        }
                                    )
                                }
                            }
                            // Experience Feedback Dialog
                            if (uiState.showFeedbackDialog) {
                                com.example.ui.components.ExperienceFeedbackDialog(
                                    isSubmitting = uiState.isSubmittingFeedback,
                                    onDismiss = { viewModel.markFeedbackShown() },
                                    onSubmitInternal = { rating, comment ->
                                        viewModel.submitInternalFeedback(rating, comment)
                                    },
                                    onOpenMyket = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("myket://comment?id=com.aistudio.reqabat.trophy"))
                                            intent.setPackage("ir.mservices.market")
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            startActivity(intent)
                                        } catch (e: Exception) {
                                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://myket.ir/app/com.aistudio.reqabat.trophy"))
                                            startActivity(webIntent)
                                        }
                                        viewModel.markFeedbackShown()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
