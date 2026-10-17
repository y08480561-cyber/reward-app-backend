package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    fun getCurrentUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: UserEntity)

    @Query("UPDATE users SET totalScore = :newScore WHERE id = :userId")
    suspend fun updateScore(userId: String, newScore: Int)

    @Query("DELETE FROM users")
    suspend fun clearUser()
}

@Dao
interface SquareDao {
    @Query("SELECT * FROM squares ORDER BY squareNumber ASC")
    fun getAllSquaresFlow(): Flow<List<SquareEntity>>

    @Query("SELECT * FROM squares ORDER BY squareNumber ASC")
    suspend fun getAllSquares(): List<SquareEntity>

    @Query("SELECT * FROM squares WHERE squareNumber = :number LIMIT 1")
    suspend fun getSquareByNumber(number: Int): SquareEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSquares(squares: List<SquareEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSquare(square: SquareEntity)

    @Query("UPDATE squares SET status = :status, completedAt = :completedAt WHERE squareNumber = :number")
    suspend fun setSquareStatus(number: Int, status: String, completedAt: Long? = null)

    @Query("UPDATE squares SET status = 'READY', completedAt = NULL, cycleId = :newCycleId")
    suspend fun resetAllSquares(newCycleId: String)

    @Query("DELETE FROM squares")
    suspend fun clearAll()
}

@Dao
interface CycleDao {
    @Query("SELECT * FROM daily_cycles ORDER BY startTimeMs DESC LIMIT 1")
    fun getCurrentCycleFlow(): Flow<CycleEntity?>

    @Query("SELECT * FROM daily_cycles ORDER BY startTimeMs DESC LIMIT 1")
    suspend fun getCurrentCycle(): CycleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCycle(cycle: CycleEntity)

    @Query("DELETE FROM daily_cycles")
    suspend fun clearCycles()
}

@Dao
interface AdLogDao {
    @Query("SELECT * FROM ad_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogsFlow(): Flow<List<AdLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AdLogEntity)

    @Query("SELECT COUNT(*) FROM ad_logs WHERE squareNumber = :squareNumber AND cycleId = :cycleId AND status = 'COMPLETED'")
    suspend fun countCompletedForSquareInCycle(squareNumber: Int, cycleId: String): Int
}
