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
                                onScreenSelected = { screen -> viewModel.navigateTo(screen) }
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
                                        userRank = 1,
                                        onNavigate = { screen -> viewModel.navigateTo(screen) }
                                    )
                                }
                            }
                            AppScreen.SQUARES -> {
                                SquaresScreen(
                                    squares = uiState.squares,
                                    cycle = uiState.dailyCycle,
                                    onSquareClick = { act, square -> viewModel.onSquareClicked(act, square) }
                                )
                            }
                            AppScreen.LEADERBOARD -> {
                                LeaderboardScreen(entries = uiState.leaderboard)
                            }
                            AppScreen.PRIZES -> {
                                PrizesScreen(prizes = viewModel.backendRepository.getPrizes())
                            }
                            AppScreen.PROFILE -> {
                                uiState.currentUser?.let { user ->
                                    ProfileScreen(user = user, onLogout = { viewModel.logout() })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
