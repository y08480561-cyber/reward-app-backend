package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppBottomNavigation
import com.example.ui.components.AppHeader
import com.example.ui.components.RewardCelebrationModal
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.PrizesScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SquaresScreen
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                // Ensure complete Right-to-Left (RTL) Persian layout direction
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainAppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val authUiState by viewModel.authUiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Display info or error messages in snackbar or banner
    LaunchedEffect(uiState.infoMessage, uiState.errorMessage) {
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    // Back button behavior for sub-screens
    BackHandler(enabled = uiState.currentScreen != AppScreen.SQUARES && uiState.currentScreen != AppScreen.AUTH) {
        viewModel.navigateTo(AppScreen.SQUARES)
    }

    val showBars = uiState.currentScreen != AppScreen.AUTH && uiState.currentUser != null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (showBars) {
                AppHeader(
                    title = "رقابت کن، جایزه ببر",
                    totalScore = uiState.currentUser?.totalScore ?: 0,
                    onProfileClick = { viewModel.navigateTo(AppScreen.PROFILE) }
                )
            }
        },
        bottomBar = {
            if (showBars) {
                AppBottomNavigation(
                    currentScreen = uiState.currentScreen,
                    onScreenSelected = { screen -> viewModel.navigateTo(screen) }
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentScreen) {
                AppScreen.AUTH -> {
                    AuthScreen(
                        state = authUiState,
                        onFirstNameChange = viewModel::onFirstNameChange,
                        onLastNameChange = viewModel::onLastNameChange,
                        onPhoneChange = viewModel::onPhoneNumberChange,
                        onSubmit = viewModel::loginOrRegister
                    )
                }
                AppScreen.HOME -> {
                    uiState.currentUser?.let { user ->
                        val rank = uiState.leaderboard.find { it.userId == user.id }?.rank ?: 1
                        HomeScreen(
                            user = user,
                            cycle = uiState.dailyCycle,
                            userRank = rank,
                            onNavigate = viewModel::navigateTo
                        )
                    }
                }
                AppScreen.SQUARES -> {
                    SquaresScreen(
                        squares = uiState.squares,
                        cycle = uiState.dailyCycle,
                        onSquareClick = { activity, square ->
                            viewModel.onSquareClicked(activity, square)
                        }
                    )
                }
                AppScreen.LEADERBOARD -> {
                    LeaderboardScreen(entries = uiState.leaderboard)
                }
                AppScreen.PRIZES -> {
                    PrizesScreen(prizes = uiState.prizes)
                }
                AppScreen.PROFILE -> {
                    uiState.currentUser?.let { user ->
                        ProfileScreen(
                            user = user,
                            onLogout = viewModel::logout
                        )
                    }
                }
            }

            // Reward Awarded Celebration Modal (+1 point)
            uiState.awardedPointsModal?.let { points ->
                RewardCelebrationModal(
                    pointsAwarded = points,
                    onDismiss = viewModel::dismissRewardModal
                )
            }
        }
    }
}
