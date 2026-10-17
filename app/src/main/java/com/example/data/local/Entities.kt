package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AdSquare
import com.example.model.SquareStatus

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val totalScore: Int,
    val token: String,
    val registeredAt: Long
)

@Entity(tableName = "squares")
data class SquareEntity(
    @PrimaryKey val squareNumber: Int, // 1..50
    val status: String,
    val completedAt: Long?,
    val cycleId: String
) {
    fun toDomain(): AdSquare {
        val squareStatus = try {
            SquareStatus.valueOf(status)
        } catch (e: Exception) {
            SquareStatus.READY
        }
        return AdSquare(
            squareNumber = squareNumber,
            status = squareStatus,
            completedAt = completedAt,
            cycleId = cycleId
        )
    }

    companion object {
        fun fromDomain(square: AdSquare): SquareEntity {
            return SquareEntity(
                squareNumber = square.squareNumber,
                status = square.status.name,
                completedAt = square.completedAt,
                cycleId = square.cycleId
            )
        }
    }
}

@Entity(tableName = "daily_cycles")
data class CycleEntity(
    @PrimaryKey val cycleId: String,
    val userId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val completedSquares: Int
)

@Entity(tableName = "ad_logs")
data class AdLogEntity(
    @PrimaryKey val sessionId: String,
    val userId: String,
    val squareNumber: Int,
    val cycleId: String,
    val timestamp: Long,
    val durationSeconds: Int,
    val status: String,
    val token: String
)
