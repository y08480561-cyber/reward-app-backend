package com.example.model

/**
 * User account model
 */
data class User(
    val id: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val totalScore: Int = 0,
    val token: String = "",
    val registeredAt: Long = System.currentTimeMillis(),
    val panelType: String = "free",
    val multiplier: Int = 1
) {
    val fullName: String
        get() = "$firstName $lastName".trim()

    val maskedName: String
        get() {
            val familyMasked = if (lastName.length > 2) {
                "${lastName.take(2)}***"
            } else {
                lastName
            }
            return "$firstName $familyMasked"
        }

    val maskedPhone: String
        get() = if (phoneNumber.length >= 11) {
            "${phoneNumber.substring(0, 4)}***${phoneNumber.substring(7)}"
        } else phoneNumber
}

/**
 * Status of each of the 50 ad squares
 */
enum class SquareStatus {
    READY,      // آماده مشاهده تبلیغ
    LOADING,    // در حال بارگذاری
    PLAYING,    // در حال پخش
    COMPLETED,  // تکمیل شده و قفل شده (۱ امتیاز)
    FAILED      // ناموفق / تلاش مجدد
}

/**
 * Represents one of the 50 reward ad squares
 */
data class AdSquare(
    val squareNumber: Int, // 1 to 50
    val status: SquareStatus = SquareStatus.READY,
    val completedAt: Long? = null,
    val cycleId: String = "",
    val pointsAwarded: Int = if (status == SquareStatus.COMPLETED) 1 else 0
)

/**
 * 24-hour cycle state managed by the server
 */
data class DailyCycle(
    val cycleId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val completedSquaresCount: Int = 0,
    val totalSquares: Int = 50
) {
    val remainingMs: Long
        get() = (endTimeMs - System.currentTimeMillis()).coerceAtLeast(0L)

    val isExpired: Boolean
        get() = System.currentTimeMillis() >= endTimeMs
}

/**
 * Leaderboard entry
 */
data class LeaderboardEntry(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val totalScore: Int,
    val isCurrentUser: Boolean = false,
    val adsCompletedToday: Int = 0
)

/**
 * Real competition prize
 */
data class Prize(
    val id: String,
    val rankRequired: String,
    val title: String,
    val description: String,
    val prizeValue: String,
    val drawDatePersian: String,
    val minPointsToQualify: Int,
    val iconName: String = "trophy"
)

/**
 * Audit log of each ad session for anti-cheat verification
 */
data class AdSession(
    val sessionId: String,
    val userId: String,
    val squareNumber: Int,
    val cycleId: String,
    val timestamp: Long,
    val durationSeconds: Int,
    val status: String,
    val rewardNonce: String
)

/**
 * Information regarding the active or past competition round
 */
data class CompetitionInfo(
    val id: String,
    val competitionNumber: Int,
    val title: String,
    val status: String,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val totalParticipants: Int = 0,
    val totalScore: Int = 0
)

