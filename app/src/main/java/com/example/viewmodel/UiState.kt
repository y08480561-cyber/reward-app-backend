package com.example.viewmodel

import com.example.model.AdSquare
import com.example.model.DailyCycle
import com.example.model.LeaderboardEntry
import com.example.model.Prize
import com.example.model.SquareStatus
import com.example.model.User

enum class AppScreen {
    AUTH,
    HOME,
    SQUARES,
    LEADERBOARD,
    PRIZES,
    PROFILE
}

data class AuthUiState(
    val firstName: String = "",
    val lastName: String = "",
    val phoneNumber: String = "",
    val otpCode: String = "",
    val isOtpSent: Boolean = false,
    val otpCountdown: Int = 120,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val simulatedSentOtp: String? = null
)

data class MainUiState(
    val currentScreen: AppScreen = AppScreen.AUTH,
    val currentUser: User? = null,
    val dailyCycle: DailyCycle? = null,
    val squares: List<AdSquare> = (1..50).map { AdSquare(squareNumber = it, status = SquareStatus.READY) },
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val prizes: List<Prize> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    // Ad Playback
    val activePlayingSquare: Int? = null,
    // Celebratory reward dialog
    val awardedPointsModal: Int? = null
)
