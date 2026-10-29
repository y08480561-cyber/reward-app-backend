package com.example

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
import androidx.compose.ui.Modifier
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

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (uiState.currentScreen != AppScreen.AUTH) {
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
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
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
                                PrizesScreen(prizes = viewModel.backendRepository.getPrizes())
                            }
                            AppScreen.PROFILE -> {
                                uiState.currentUser?.let { user ->
                                    ProfileScreen(
                                        user = user,
                                        activeCompetition = uiState.activeCompetition,
                                        onLogout = { viewModel.logout() },
                                        onNavigateToMembership = { viewModel.navigateTo(AppScreen.MEMBERSHIP) },
                                        onNavigateToSupport = { viewModel.navigateTo(AppScreen.SUPPORT) }
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
                }
            }
        }
    }
}
