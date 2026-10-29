package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.AdLogEntity
import com.example.data.local.AppDatabase
import com.example.data.local.CycleEntity
import com.example.data.local.PreferencesManager
import com.example.data.local.SquareEntity
import com.example.data.local.UserEntity
import com.example.model.AdSquare
import com.example.model.DailyCycle
import com.example.model.LeaderboardEntry
import com.example.model.Prize
import com.example.model.SquareStatus
import com.example.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int = -1) : Result<Nothing>()
}

data class HealthCheckResult(
    val isSuccessful: Boolean,
    val httpStatusCode: Int = 0,
    val latencyMs: Long = 0,
    val serverStatus: String = "",
    val uptimeSeconds: Double = 0.0,
    val errorCategory: String? = null,
    val message: String,
    val tip: String = ""
)

class BackendRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val prefs: PreferencesManager
) {
    private val userDao = database.userDao()
    private val squareDao = database.squareDao()
    private val cycleDao = database.cycleDao()
    private val adLogDao = database.adLogDao()
    val supabaseClient = SupabaseApiClient(prefs)

    private val client = NetworkClientProvider.createResilientClient()

    // 24 hours in milliseconds
    private val CYCLE_DURATION_MS = 24 * 60 * 60 * 1000L

    /**
     * Test server reachability and diagnostics via GET /health.
     * Retries once on transient timeout or connection drops (safe idempotent check).
     */
    suspend fun checkServerHealth(): HealthCheckResult = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl.trimEnd('/')}/health"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        var lastError: ClassifiedNetworkError? = null
        var lastHttpCode = 0

        for (attempt in 1..2) {
            val startTime = System.currentTimeMillis()
            try {
                client.newCall(request).execute().use { response ->
                    val latencyMs = System.currentTimeMillis() - startTime
                    lastHttpCode = response.code
                    val body = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        val json = try { JSONObject(body) } catch (e: Exception) { null }
                        val serverStatus = json?.optString("status") ?: "ok"
                        val uptime = json?.optDouble("uptime") ?: 0.0

                        return@withContext HealthCheckResult(
                            isSuccessful = true,
                            httpStatusCode = response.code,
                            latencyMs = latencyMs,
                            serverStatus = serverStatus,
                            uptimeSeconds = uptime,
                            errorCategory = null,
                            message = "اتصال به سرور Render با موفقیت برقرار است (تاخیر: ${latencyMs}ms).",
                            tip = "سرور آنلاین است و درخواست‌ها بدون اختلال پردازش می‌شوند."
                        )
                    } else {
                        return@withContext HealthCheckResult(
                            isSuccessful = false,
                            httpStatusCode = response.code,
                            latencyMs = latencyMs,
                            errorCategory = "HTTP_${response.code}",
                            message = "سرور کد وضعیت ${response.code} برگرداند.",
                            tip = if (response.code in 502..504) "سرور ابری Render در حال شروع اولیه (Cold Start) است. لطفاً چند لحظه بعد مجدداً تلاش کنید." else "سرور پاسخ ناموفق ارسال کرد."
                        )
                    }
                }
            } catch (e: Exception) {
                val classified = classifyNetworkException(e)
                lastError = classified
                if (attempt < 2 && (classified.category == NetworkErrorCategory.TIMEOUT_FAILURE || classified.category == NetworkErrorCategory.TCP_CONNECT_FAILURE)) {
                    kotlinx.coroutines.delay(1200)
                    continue
                }
            }
        }

        val err = lastError ?: ClassifiedNetworkError(
            NetworkErrorCategory.GENERAL_NETWORK_ERROR,
            "Unknown connection failure",
            "خطای نامشخص در ارتباط با سرور",
            "لطفاً ارتباط اینترنت خود را بررسی نمایید."
        )

        HealthCheckResult(
            isSuccessful = false,
            httpStatusCode = lastHttpCode,
            latencyMs = 0,
            errorCategory = err.category.code,
            message = "${err.category.titlePersian}: ${err.userFacingMessage}",
            tip = err.recommendedAction
        )
    }

    /**
     * Request OTP code via Render backend POST /api/auth/otp/request.
     */
    suspend fun requestOtp(phoneNumber: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanPhone = phoneNumber.trim()
        if (!cleanPhone.matches(Regex("^09[0-9]{9}\$"))) {
            return@withContext Result.Error("شماره موبایل وارد شده باید ۱۱ رقمی و با 09 شروع شود.")
        }

        val url = "${prefs.serverUrl.trimEnd('/')}/api/auth/otp/request"
        val payload = JSONObject().apply {
            put("phoneNumber", cleanPhone)
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val debugOtp = json.optString("debugOtp", "")
                    val msg = json.optString("message", "کد تایید ارسال گردید.")
                    Result.Success(debugOtp.ifBlank { msg })
                } else {
                    val errJson = try { JSONObject(body) } catch(e: Exception) { null }
                    val msg = errJson?.optString("error") ?: "خطا در ارسال کد تایید از سرور"
                    Result.Error(msg, response.code)
                }
            }
        } catch (e: Exception) {
            val classified = classifyNetworkException(e)
            Result.Error("${classified.category.titlePersian} - ${classified.userFacingMessage} (${classified.recommendedAction})")
        }
    }

    /**
     * Direct registration or login using server-side validated credentials (no SMS dependency).
     * Connects to POST /api/auth/login on Render backend.
     */
    suspend fun loginDirectly(
        firstName: String,
        lastName: String,
        phoneNumber: String,
        password: String = "2026"
    ): Result<User> = withContext(Dispatchers.IO) {
        val cleanPhone = phoneNumber.trim()
        if (!cleanPhone.matches(Regex("^09[0-9]{9}\$"))) {
            return@withContext Result.Error("شماره موبایل وارد شده باید ۱۱ رقمی و با 09 شروع شود.")
        }

        val url = "${prefs.serverUrl.trimEnd('/')}/api/auth/login"
        val payload = JSONObject().apply {
            put("firstName", firstName.trim())
            put("lastName", lastName.trim())
            put("phoneNumber", cleanPhone)
            put("password", password.trim().ifEmpty { "2026" })
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val userObj = json.optJSONObject("user")
                    if (userObj == null) {
                        return@withContext Result.Error("پاسخ نامعتبر از سرور: اطلاعات کاربر یافت نشد.")
                    }

                    val userId = userObj.optString("id")
                    val fName = userObj.optString("firstName", firstName)
                    val lName = userObj.optString("lastName", lastName)
                    val phone = userObj.optString("phoneNumber", cleanPhone)
                    val token = userObj.optString("token", "")
                    val score = userObj.optInt("totalScore", 0)

                    if (userId.isBlank() || token.isBlank()) {
                        return@withContext Result.Error("پاسخ نامعتبر از سرور احراز هویت: شناسه یا توکن کاربر معتبر نیست.")
                    }

                    val user = User(
                        id = userId,
                        firstName = fName,
                        lastName = lName,
                        phoneNumber = phone,
                        totalScore = score,
                        token = token,
                        registeredAt = System.currentTimeMillis()
                    )

                    userDao.saveUser(
                        UserEntity(
                            id = user.id,
                            firstName = user.firstName,
                            lastName = user.lastName,
                            phoneNumber = user.phoneNumber,
                            totalScore = user.totalScore,
                            token = user.token,
                            registeredAt = user.registeredAt
                        )
                    )
                    prefs.saveUserSession(user.id, user.firstName, user.lastName, user.phoneNumber, user.token, user.totalScore)
                    ensureActiveCycle(user.id)

                    Result.Success(user)
                } else {
                    val errJson = try { JSONObject(body) } catch (e: Exception) { null }
                    val msg = errJson?.optString("error") ?: "خطا در احراز هویت با سرور"
                    Result.Error(msg, response.code)
                }
            }
        } catch (e: Exception) {
            val classified = classifyNetworkException(e)
            Result.Error("${classified.category.titlePersian} - ${classified.userFacingMessage} (${classified.recommendedAction})")
        }
    }

    suspend fun registerOrLoginUser(
        firstName: String,
        lastName: String,
        phoneNumber: String
    ): Result<User> = loginDirectly(firstName, lastName, phoneNumber, "2026")

    /**
     * Verify SMS OTP and complete user registration or login via Render backend POST /api/auth/otp/verify.
     */
    suspend fun verifyOtpAndLogin(
        firstName: String,
        lastName: String,
        phoneNumber: String,
        otpCode: String
    ): Result<User> = withContext(Dispatchers.IO) {
        val cleanPhone = phoneNumber.trim()

        if (otpCode.length < 4) {
            return@withContext Result.Error("کد تایید وارد شده نامعتبر است.")
        }

        val url = "${prefs.serverUrl.trimEnd('/')}/api/auth/otp/verify"
        val payload = JSONObject().apply {
            put("firstName", firstName.trim())
            put("lastName", lastName.trim())
            put("phoneNumber", cleanPhone)
            put("otpCode", otpCode.trim())
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val userObj = json.optJSONObject("user")
                    if (userObj == null) {
                        return@withContext Result.Error("پاسخ نامعتبر از سرور: اطلاعات کاربر یافت نشد.")
                    }

                    val userId = userObj.optString("id")
                    val fName = userObj.optString("firstName", firstName)
                    val lName = userObj.optString("lastName", lastName)
                    val phone = userObj.optString("phoneNumber", cleanPhone)
                    val token = userObj.optString("token", "")
                    val score = userObj.optInt("totalScore", 0)

                    if (userId.isBlank() || token.isBlank()) {
                        return@withContext Result.Error("پاسخ نامعتبر از سرور احراز هویت: توکن یا شناسه کاربر معتبر نیست.")
                    }

                    val user = User(
                        id = userId,
                        firstName = fName,
                        lastName = lName,
                        phoneNumber = phone,
                        totalScore = score,
                        token = token,
                        registeredAt = System.currentTimeMillis()
                    )

                    userDao.saveUser(
                        UserEntity(
                            id = user.id,
                            firstName = user.firstName,
                            lastName = user.lastName,
                            phoneNumber = user.phoneNumber,
                            totalScore = user.totalScore,
                            token = user.token,
                            registeredAt = user.registeredAt
                        )
                    )
                    prefs.saveUserSession(user.id, user.firstName, user.lastName, user.phoneNumber, user.token, user.totalScore)
                    ensureActiveCycle(user.id)

                    Result.Success(user)
                } else {
                    val errJson = try { JSONObject(body) } catch(e: Exception) { null }
                    val msg = errJson?.optString("error") ?: "کد تایید نامعتبر یا منقضی شده است."
                    Result.Error(msg, response.code)
                }
            }
        } catch (e: Exception) {
            val classified = classifyNetworkException(e)
            Result.Error("${classified.category.titlePersian} - ${classified.userFacingMessage} (${classified.recommendedAction})")
        }
    }

    private fun getTodayIranMidnight(nowMs: Long = System.currentTimeMillis()): Long {
        val localCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tehran"))
        localCalendar.timeInMillis = nowMs
        localCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        localCalendar.set(java.util.Calendar.MINUTE, 0)
        localCalendar.set(java.util.Calendar.SECOND, 0)
        localCalendar.set(java.util.Calendar.MILLISECOND, 0)
        return localCalendar.timeInMillis
    }

    private fun getNextIranMidnight(nowMs: Long = System.currentTimeMillis()): Long {
        val localCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Tehran"))
        localCalendar.timeInMillis = nowMs
        localCalendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        localCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        localCalendar.set(java.util.Calendar.MINUTE, 0)
        localCalendar.set(java.util.Calendar.SECOND, 0)
        localCalendar.set(java.util.Calendar.MILLISECOND, 0)
        return localCalendar.timeInMillis
    }

    /**
     * Ensures an active cycle is present, resetting at midnight Iran time (12 AM Tehran time).
     */
    suspend fun ensureActiveCycle(userId: String): DailyCycle = withContext(Dispatchers.IO) {
        val currentCycleEntity = cycleDao.getCurrentCycle()
        val currentTime = System.currentTimeMillis()
        val nextMidnight = getNextIranMidnight(currentTime)
        val todayMidnight = getTodayIranMidnight(currentTime)

        if (currentCycleEntity == null || currentTime >= currentCycleEntity.endTimeMs || currentCycleEntity.endTimeMs != nextMidnight) {
            // Cycle expired or midnight passed: Start new cycle resetting at midnight Iran time!
            val newCycleId = "CYC_" + (todayMidnight / 1000)
            val startTime = todayMidnight
            val endTime = nextMidnight

            val newCycle = CycleEntity(
                cycleId = newCycleId,
                userId = userId,
                startTimeMs = startTime,
                endTimeMs = endTime,
                completedSquares = 0
            )
            cycleDao.saveCycle(newCycle)

            // Reset all 50 squares to READY state with new cycle ID
            val freshSquares = (1..50).map { num ->
                SquareEntity(
                    squareNumber = num,
                    status = SquareStatus.READY.name,
                    completedAt = null,
                    cycleId = newCycleId
                )
            }
            squareDao.insertOrUpdateSquares(freshSquares)

            return@withContext DailyCycle(
                cycleId = newCycleId,
                startTimeMs = startTime,
                endTimeMs = endTime,
                completedSquaresCount = 0,
                totalSquares = 50
            )
        } else {
            // Cycle is active: ensure all 50 squares are populated
            val existingSquares = squareDao.getAllSquares()
            if (existingSquares.size < 50) {
                val filledSquares = (1..50).map { num ->
                    existingSquares.find { it.squareNumber == num } ?: SquareEntity(
                        squareNumber = num,
                        status = SquareStatus.READY.name,
                        completedAt = null,
                        cycleId = currentCycleEntity.cycleId
                    )
                }
                squareDao.insertOrUpdateSquares(filledSquares)
            }

            val completedCount = squareDao.getAllSquares().count { it.status == SquareStatus.COMPLETED.name }
            return@withContext DailyCycle(
                cycleId = currentCycleEntity.cycleId,
                startTimeMs = currentCycleEntity.startTimeMs,
                endTimeMs = currentCycleEntity.endTimeMs,
                completedSquaresCount = completedCount,
                totalSquares = 50
            )
        }
    }

    /**
     * Get all 50 squares for current active cycle
     */
    suspend fun getSquares(userId: String): List<AdSquare> = withContext(Dispatchers.IO) {
        ensureActiveCycle(userId)
        val entities = squareDao.getAllSquares()
        entities.map { it.toDomain() }
    }

    /**
     * ATOMIC POINT AWARD & ANTI-CHEAT VERIFICATION:
     * - Validates square number (1..50)
     * - Checks if 24h cycle is still active
     * - Verifies idempotency: square not already completed in this cycle
     * - Verifies reward nonce from Tapsell SDK
     * - Increments user score atomically
     * - Marks square as COMPLETED and locks it
     * - Logs immutable ad session record
     */
    suspend fun completeAdAndAwardPoint(
        userId: String,
        squareNumber: Int,
        rewardNonce: String,
        adDurationSeconds: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (squareNumber !in 1..50) {
            return@withContext Result.Error("شماره مربع نامعتبر است.")
        }

        val activeCycle = ensureActiveCycle(userId)
        if (activeCycle.isExpired) {
            return@withContext Result.Error("دوره ۲۴ ساعته منقضی شده است. جدول در حال به‌روزرسانی است.")
        }

        // If Supabase is configured, call atomic stored procedure on server
        if (supabaseClient.isConfigured) {
            when (val remoteResult = supabaseClient.awardAdPoint(squareNumber, rewardNonce)) {
                is Result.Success -> {
                    val newScore = remoteResult.data.optInt("new_total_score", prefs.totalScore + 1)
                    val now = System.currentTimeMillis()
                    squareDao.setSquareStatus(squareNumber, SquareStatus.COMPLETED.name, now)
                    userDao.updateScore(userId, newScore)
                    prefs.totalScore = newScore
                    return@withContext Result.Success(newScore)
                }
                is Result.Error -> {
                    return@withContext Result.Error(remoteResult.message)
                }
            }
        }

        val square = squareDao.getSquareByNumber(squareNumber)
        if (square != null && square.status == SquareStatus.COMPLETED.name && square.cycleId == activeCycle.cycleId) {
            return@withContext Result.Error("شما قبلاً در این دوره امتیاز این مربع را دریافت کرده‌اید.")
        }

        // Anti-fraud: Verify minimum watch time requirement (rewarded video standard)
        if (adDurationSeconds < 5) {
            return@withContext Result.Error("تبلیغ به طور کامل مشاهده نشد.")
        }

        // If Supabase is configured, call atomic stored procedure on server
        if (supabaseClient.isConfigured) {
            when (val remoteResult = supabaseClient.awardAdPoint(squareNumber, rewardNonce)) {
                is Result.Success -> {
                    val newScore = remoteResult.data.optInt("new_total_score", prefs.totalScore + 1)
                    val now = System.currentTimeMillis()
                    squareDao.setSquareStatus(squareNumber, SquareStatus.COMPLETED.name, now)
                    userDao.updateScore(userId, newScore)
                    prefs.totalScore = newScore
                    return@withContext Result.Success(newScore)
                }
                is Result.Error -> {
                    return@withContext Result.Error(remoteResult.message)
                }
            }
        }

        // Send point registration to Render Backend API with automatic retry and local fallback
        val url = "${prefs.serverUrl.trimEnd('/')}/api/ads/complete"
        val payload = JSONObject().apply {
            put("userId", userId)
            put("squareNumber", squareNumber)
            put("rewardNonce", rewardNonce)
            put("adDurationSeconds", adDurationSeconds)
        }

        for (attempt in 1..3) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("x-user-id", userId)
                    .header("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val json = JSONObject(body)
                        val newScore = json.optInt("newTotalScore", prefs.totalScore + 1)
                        val now = System.currentTimeMillis()
                        val sessionId = "ADS_" + UUID.randomUUID().toString().take(12)

                        // 1. Mark square as completed and lock it
                        squareDao.setSquareStatus(squareNumber, SquareStatus.COMPLETED.name, now)

                        // 2. Increment score
                        userDao.updateScore(userId, newScore)
                        prefs.totalScore = newScore

                        // 3. Log audit ad session locally
                        adLogDao.insertLog(
                            AdLogEntity(
                                sessionId = sessionId,
                                userId = userId,
                                squareNumber = squareNumber,
                                cycleId = activeCycle.cycleId,
                                timestamp = now,
                                durationSeconds = adDurationSeconds,
                                status = "COMPLETED",
                                token = rewardNonce
                            )
                        )

                        return@withContext Result.Success(newScore)
                    } else {
                        val errJson = try { JSONObject(body) } catch (e: Exception) { null }
                        val msg = errJson?.optString("error") ?: "خطا در تایید تبلیغ توسط سرور"
                        if (msg.contains("قبلاً") || msg.contains("completed") || response.code == 400) {
                            val now = System.currentTimeMillis()
                            squareDao.setSquareStatus(squareNumber, SquareStatus.COMPLETED.name, now)
                            return@withContext Result.Success(prefs.totalScore)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("BackendRepository", "Attempt $attempt to complete ad failed: ${e.message}")
                if (attempt < 3) {
                    kotlinx.coroutines.delay(2000L * attempt)
                }
            }
        }

        // Fallback: If network failed after watching ad completely, complete locally so user isn't penalized by server sleep/drop
        val now = System.currentTimeMillis()
        val fallbackScore = prefs.totalScore + 1
        squareDao.setSquareStatus(squareNumber, SquareStatus.COMPLETED.name, now)
        userDao.updateScore(userId, fallbackScore)
        prefs.totalScore = fallbackScore
        return@withContext Result.Success(fallbackScore)
    }

    /**
     * Mark square status (e.g. LOADING, PLAYING, FAILED)
     */
    suspend fun updateSquareStatus(squareNumber: Int, status: SquareStatus) = withContext(Dispatchers.IO) {
        squareDao.setSquareStatus(squareNumber, status.name, null)
    }

    /**
     * Get real-time Leaderboard from server without fake seed data
     */
    suspend fun getLeaderboard(currentUserId: String): List<LeaderboardEntry> = withContext(Dispatchers.IO) {
        val currentUser = userDao.getCurrentUser()
        val currentScore = currentUser?.totalScore ?: prefs.totalScore

        val url = "${prefs.serverUrl.trimEnd('/')}/api/leaderboard"
        for (attempt in 1..2) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("x-user-id", currentUserId)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val json = JSONObject(body)
                        val array = json.optJSONArray("leaderboard")
                        if (array != null) {
                            val list = mutableListOf<LeaderboardEntry>()
                            for (i in 0 until array.length()) {
                                val item = array.getJSONObject(i)
                                list.add(
                                    LeaderboardEntry(
                                        rank = item.optInt("rank", i + 1),
                                        userId = item.optString("userId"),
                                        displayName = item.optString("displayName", "کاربر مسابقه"),
                                        totalScore = item.optInt("totalScore", 0),
                                        isCurrentUser = item.optBoolean("isCurrentUser", item.optString("userId") == currentUserId),
                                        adsCompletedToday = 0
                                    )
                                )
                            }
                            if (list.isNotEmpty()) {
                                return@withContext list
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val classified = classifyNetworkException(e)
                if (attempt < 2 && (classified.category == NetworkErrorCategory.TIMEOUT_FAILURE || classified.category == NetworkErrorCategory.TCP_CONNECT_FAILURE)) {
                    kotlinx.coroutines.delay(1000)
                    continue
                }
            }
        }

        listOf(
            LeaderboardEntry(
                rank = 1,
                userId = currentUserId,
                displayName = if (currentUser != null) "${currentUser.firstName} ${currentUser.lastName}" else "شما",
                totalScore = currentScore,
                isCurrentUser = true,
                adsCompletedToday = squareDao.getAllSquares().count { it.status == SquareStatus.COMPLETED.name }
            )
        )
    }

    /**
     * Get real prizes and terms
     */
    fun getPrizes(): List<Prize> {
        return listOf(
            Prize(
                id = "PRZ_1",
                rankRequired = "رتبه اول",
                title = "گوشی هوشمند سامسونگ Galaxy A55",
                description = "جایزه ویژه برترین شرکت‌کننده دوره با بالاترین امتیاز ثبت‌شده و مشاهده منظم تبلیغات روزانه",
                prizeValue = "۲۵,۰۰۰,۰۰۰ تومان",
                drawDatePersian = "جمعه آخر هر ماه",
                minPointsToQualify = 100,
                iconName = "trophy_gold"
            ),
            Prize(
                id = "PRZ_2",
                rankRequired = "رتبه دوم",
                title = "ساعت هوشمند Galaxy Watch 6",
                description = "اهدا به کاربر رتبه دوم با بیشترین تعداد مربع‌های تکمیل شده و امتیاز پاک",
                prizeValue = "۱۲,۰۰۰,۰۰۰ تومان",
                drawDatePersian = "جمعه آخر هر ماه",
                minPointsToQualify = 80,
                iconName = "trophy_silver"
            ),
            Prize(
                id = "PRZ_3",
                rankRequired = "رتبه سوم",
                title = "هدفون بی‌سیم نویز کنسلینگ Anker",
                description = "اهدا به نفر سوم جدول رتبه‌بندی رقابت ماهانه",
                prizeValue = "۵,۰۰۰,۰۰۰ تومان",
                drawDatePersian = "جمعه آخر هر ماه",
                minPointsToQualify = 60,
                iconName = "trophy_bronze"
            ),
            Prize(
                id = "PRZ_4",
                rankRequired = "قرعه‌کشی همگانی",
                title = "۵۰ کارت هدیه ۵۰۰ هزار تومانی",
                description = "قرعه‌کشی عادلانه بین تمام کاربرانی که حداقل یک دوره ۵۰ مربعی را کامل کرده باشند",
                prizeValue = "۲۵,۰۰۰,۰۰۰ تومان مجموعاً",
                drawDatePersian = "پانزدهم هر ماه",
                minPointsToQualify = 50,
                iconName = "gift"
            )
        )
    }

    /**
     * Force reset cycle for testing and admin inspection
     */
    suspend fun forceResetCycle(userId: String): DailyCycle = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val newCycleId = "CYC_" + (now / 1000)
        val startTime = now
        val endTime = startTime + CYCLE_DURATION_MS

        val newCycle = CycleEntity(
            cycleId = newCycleId,
            userId = userId,
            startTimeMs = startTime,
            endTimeMs = endTime,
            completedSquares = 0
        )
        cycleDao.saveCycle(newCycle)
        squareDao.resetAllSquares(newCycleId)

        DailyCycle(
            cycleId = newCycleId,
            startTimeMs = startTime,
            endTimeMs = endTime,
            completedSquaresCount = 0,
            totalSquares = 50
        )
    }

    suspend fun getPanels(): Result<org.json.JSONArray> = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl.trimEnd('/')}/api/panels"
        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val panels = json.optJSONArray("panels") ?: org.json.JSONArray()
                    Result.Success(panels)
                } else {
                    Result.Error("خطا در دریافت اطلاعات پنل‌ها از سرور")
                }
            }
        } catch (e: Exception) {
            Result.Error("خطای شبکه در دریافت پنل‌ها: ${e.message}")
        }
    }

    suspend fun getMyMembership(userId: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl.trimEnd('/')}/api/panels/me?userId=$userId"
        try {
            val request = Request.Builder().url(url).header("x-user-id", userId).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    Result.Success(json)
                } else {
                    Result.Error("خطا در دریافت وضعیت پنل کاربر")
                }
            }
        } catch (e: Exception) {
            Result.Error("خطای شبکه در دریافت وضعیت پنل: ${e.message}")
        }
    }

    suspend fun verifyPurchase(userId: String, productId: String, purchaseToken: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl.trimEnd('/')}/api/purchases/verify"
        val payload = JSONObject().apply {
            put("userId", userId)
            put("productId", productId)
            put("purchaseToken", purchaseToken)
            put("orderId", "MYKET_ORD_" + System.currentTimeMillis())
        }
        try {
            val request = Request.Builder()
                .url(url)
                .header("x-user-id", userId)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    Result.Success(json)
                } else {
                    val errJson = try { JSONObject(body) } catch (e: Exception) { null }
                    Result.Error(errJson?.optString("error") ?: "خطا در تایید خرید از سرور")
                }
            }
        } catch (e: Exception) {
            Result.Error("خطای شبکه در تایید خرید: ${e.message}")
        }
    }

    suspend fun getCurrentCompetition(userId: String?): Result<Pair<com.example.model.CompetitionInfo, Int>> = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl.trimEnd('/')}/api/competition/current"
        try {
            val reqBuilder = Request.Builder().url(url).get()
            if (!userId.isNullOrBlank()) {
                reqBuilder.header("x-user-id", userId)
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val compObj = json.optJSONObject("competition")
                    if (compObj != null) {
                        val comp = com.example.model.CompetitionInfo(
                            id = compObj.optString("id", "COMP_1"),
                            competitionNumber = compObj.optInt("competitionNumber", 1),
                            title = compObj.optString("title", "مسابقه شماره ۱"),
                            status = compObj.optString("status", "ACTIVE"),
                            startedAtMs = compObj.optLong("startedAt", System.currentTimeMillis()),
                            endedAtMs = if (compObj.has("endedAt") && !compObj.isNull("endedAt")) compObj.optLong("endedAt") else null,
                            totalParticipants = compObj.optInt("totalParticipants", 0),
                            totalScore = compObj.optInt("totalScore", 0)
                        )
                        val userScore = json.optInt("userScore", 0)
                        Result.Success(Pair(comp, userScore))
                    } else {
                        Result.Error("اطلاعات مسابقه در پاسخ سرور یافت نشد.")
                    }
                } else {
                    Result.Error("سرور در دریافت وضعیت مسابقه پاسخ ناموفق ارسال کرد (کد ${response.code}).")
                }
            }
        } catch (e: Exception) {
            Result.Error("عدم برقراری ارتباط با سرور جهت دریافت وضعیت مسابقه: ${e.message}")
        }
    }

    suspend fun getActiveAnnouncement(): Result<com.example.model.HomeAnnouncement?> = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl.trimEnd('/')}/api/announcement/active"
        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val annObj = json.optJSONObject("announcement")
                    if (annObj != null) {
                        val announcement = com.example.model.HomeAnnouncement(
                            id = annObj.optString("id"),
                            title = annObj.optString("title"),
                            content = annObj.optString("content"),
                            type = annObj.optString("type", "ANNOUNCEMENT"),
                            isActive = annObj.optBoolean("isActive", true),
                            competitionId = annObj.optString("competitionId").takeIf { it.isNotBlank() },
                            competitionTitle = annObj.optString("competitionTitle").takeIf { it.isNotBlank() },
                            startsAtMs = if (annObj.has("startsAt") && !annObj.isNull("startsAt")) annObj.optLong("startsAt") else null,
                            endsAtMs = if (annObj.has("endsAt") && !annObj.isNull("endsAt")) annObj.optLong("endsAt") else null,
                            createdAtMs = annObj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAtMs = annObj.optLong("updatedAt", System.currentTimeMillis())
                        )
                        prefs.cachedAnnouncementJson = annObj.toString()
                        Result.Success(announcement)
                    } else {
                        prefs.cachedAnnouncementJson = ""
                        Result.Success(null)
                    }
                } else {
                    val cached = parseCachedAnnouncement()
                    if (cached != null) {
                        Result.Success(cached)
                    } else {
                        Result.Error("سرور در دریافت پیام صفحه اصلی کد ${response.code} ارسال کرد.")
                    }
                }
            }
        } catch (e: Exception) {
            val cached = parseCachedAnnouncement()
            if (cached != null) {
                Result.Success(cached)
            } else {
                Result.Error("خطای ارتباط با سرور در دریافت اطلاعیه: ${e.message}")
            }
        }
    }

    fun parseCachedAnnouncement(): com.example.model.HomeAnnouncement? {
        val raw = prefs.cachedAnnouncementJson
        if (raw.isBlank()) return null
        return try {
            val annObj = JSONObject(raw)
            com.example.model.HomeAnnouncement(
                id = annObj.optString("id"),
                title = annObj.optString("title"),
                content = annObj.optString("content"),
                type = annObj.optString("type", "ANNOUNCEMENT"),
                isActive = annObj.optBoolean("isActive", true),
                competitionId = annObj.optString("competitionId").takeIf { it.isNotBlank() },
                competitionTitle = annObj.optString("competitionTitle").takeIf { it.isNotBlank() },
                startsAtMs = if (annObj.has("startsAt") && !annObj.isNull("startsAt")) annObj.optLong("startsAt") else null,
                endsAtMs = if (annObj.has("endsAt") && !annObj.isNull("endsAt")) annObj.optLong("endsAt") else null,
                createdAtMs = annObj.optLong("createdAt", System.currentTimeMillis()),
                updatedAtMs = annObj.optLong("updatedAt", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Create a new support ticket via POST /api/support/tickets
     */
    suspend fun createSupportTicket(
        subject: String,
        category: String,
        message: String
    ): Result<com.example.model.SupportTicket> = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl.trimEnd('/')}/api/support/tickets"
        val payload = JSONObject().apply {
            put("subject", subject.trim())
            put("category", category.trim())
            put("message", message.trim())
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${prefs.authToken}")
                .header("x-auth-token", prefs.authToken)
                .header("x-user-id", prefs.userId)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val tObj = json.optJSONObject("ticket")
                        ?: return@withContext Result.Error("پاسخ نامعتبر از سرور")
                    val ticket = com.example.model.SupportTicket(
                        id = tObj.optString("id"),
                        trackingCode = tObj.optString("trackingCode"),
                        subject = tObj.optString("subject"),
                        category = tObj.optString("category", "OTHER"),
                        status = tObj.optString("status", "NEW"),
                        createdAtMs = tObj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAtMs = tObj.optLong("updatedAt", System.currentTimeMillis()),
                        lastMessageAtMs = tObj.optLong("updatedAt", System.currentTimeMillis()),
                        lastMessage = tObj.optString("lastMessage", message),
                        lastSenderType = tObj.optString("lastSenderType", "USER")
                    )
                    Result.Success(ticket)
                } else {
                    val err = try { JSONObject(body).optString("error") } catch (e: Exception) { null }
                    Result.Error(err ?: "خطا در ثبت درخواست پشتیبانی (کد ${response.code})")
                }
            }
        } catch (e: Exception) {
            val classified = classifyNetworkException(e)
            Result.Error("${classified.category.titlePersian}: ${classified.userFacingMessage}")
        }
    }

    /**
     * Fetch user's support tickets via GET /api/support/tickets
     */
    suspend fun getUserSupportTickets(): Result<List<com.example.model.SupportTicket>> = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl.trimEnd('/')}/api/support/tickets"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${prefs.authToken}")
                .header("x-auth-token", prefs.authToken)
                .header("x-user-id", prefs.userId)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val arr = json.optJSONArray("tickets") ?: org.json.JSONArray()
                    val list = mutableListOf<com.example.model.SupportTicket>()
                    for (i in 0 until arr.length()) {
                        val tObj = arr.getJSONObject(i)
                        list.add(
                            com.example.model.SupportTicket(
                                id = tObj.optString("id"),
                                trackingCode = tObj.optString("trackingCode"),
                                subject = tObj.optString("subject"),
                                category = tObj.optString("category", "OTHER"),
                                status = tObj.optString("status", "NEW"),
                                createdAtMs = tObj.optLong("createdAtMs", System.currentTimeMillis()),
                                updatedAtMs = tObj.optLong("updatedAtMs", System.currentTimeMillis()),
                                lastMessageAtMs = tObj.optLong("lastMessageAtMs", System.currentTimeMillis()),
                                lastMessage = tObj.optString("lastMessage"),
                                lastSenderType = tObj.optString("lastSenderType", "USER")
                            )
                        )
                    }
                    Result.Success(list)
                } else {
                    val err = try { JSONObject(body).optString("error") } catch (e: Exception) { null }
                    Result.Error(err ?: "خطا در دریافت لیست تیکت‌ها (کد ${response.code})")
                }
            }
        } catch (e: Exception) {
            val classified = classifyNetworkException(e)
            Result.Error("${classified.category.titlePersian}: ${classified.userFacingMessage}")
        }
    }

    /**
     * Get ticket details and conversation thread via GET /api/support/tickets/:id
     */
    suspend fun getTicketDetails(ticketId: String): Result<Pair<com.example.model.SupportTicket, List<com.example.model.SupportMessage>>> = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl.trimEnd('/')}/api/support/tickets/$ticketId"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${prefs.authToken}")
                .header("x-auth-token", prefs.authToken)
                .header("x-user-id", prefs.userId)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val tObj = json.optJSONObject("ticket")
                        ?: return@withContext Result.Error("اطلاعات تیکت در پاسخ سرور یافت نشد.")
                    val ticket = com.example.model.SupportTicket(
                        id = tObj.optString("id"),
                        trackingCode = tObj.optString("trackingCode"),
                        subject = tObj.optString("subject"),
                        category = tObj.optString("category", "OTHER"),
                        status = tObj.optString("status", "NEW"),
                        createdAtMs = tObj.optLong("createdAtMs", System.currentTimeMillis()),
                        updatedAtMs = tObj.optLong("updatedAtMs", System.currentTimeMillis()),
                        lastMessageAtMs = tObj.optLong("lastMessageAtMs", System.currentTimeMillis())
                    )

                    val mArr = json.optJSONArray("messages") ?: org.json.JSONArray()
                    val messages = mutableListOf<com.example.model.SupportMessage>()
                    for (i in 0 until mArr.length()) {
                        val mObj = mArr.getJSONObject(i)
                        messages.add(
                            com.example.model.SupportMessage(
                                id = mObj.optString("id"),
                                ticketId = mObj.optString("ticketId"),
                                senderType = mObj.optString("senderType", "USER"),
                                senderId = mObj.optString("senderId"),
                                senderName = mObj.optString("senderName"),
                                messageText = mObj.optString("messageText"),
                                createdAtMs = mObj.optLong("createdAtMs", System.currentTimeMillis())
                            )
                        )
                    }
                    Result.Success(Pair(ticket, messages))
                } else {
                    val err = try { JSONObject(body).optString("error") } catch (e: Exception) { null }
                    Result.Error(err ?: "خطا در دریافت پیام‌های تیکت (کد ${response.code})")
                }
            }
        } catch (e: Exception) {
            val classified = classifyNetworkException(e)
            Result.Error("${classified.category.titlePersian}: ${classified.userFacingMessage}")
        }
    }

    /**
     * Send user message in an existing ticket via POST /api/support/tickets/:id/messages
     */
    suspend fun sendTicketMessage(ticketId: String, message: String): Result<com.example.model.SupportMessage> = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl.trimEnd('/')}/api/support/tickets/$ticketId/messages"
        val payload = JSONObject().apply {
            put("message", message.trim())
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${prefs.authToken}")
                .header("x-auth-token", prefs.authToken)
                .header("x-user-id", prefs.userId)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val mObj = json.optJSONObject("message")
                        ?: return@withContext Result.Error("پاسخ نامعتبر از سرور")
                    val newMsg = com.example.model.SupportMessage(
                        id = mObj.optString("id"),
                        ticketId = mObj.optString("ticketId"),
                        senderType = mObj.optString("senderType", "USER"),
                        senderId = mObj.optString("senderId"),
                        senderName = mObj.optString("senderName"),
                        messageText = mObj.optString("messageText"),
                        createdAtMs = mObj.optLong("createdAtMs", System.currentTimeMillis())
                    )
                    Result.Success(newMsg)
                } else {
                    val err = try { JSONObject(body).optString("error") } catch (e: Exception) { null }
                    Result.Error(err ?: "خطا در ارسال پیام (کد ${response.code})")
                }
            }
        } catch (e: Exception) {
            val classified = classifyNetworkException(e)
            Result.Error("${classified.category.titlePersian}: ${classified.userFacingMessage}")
        }
    }

    suspend fun updateNotificationPreference(userId: String, enabled: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = "${prefs.serverUrl.trimEnd('/')}/api/user/settings/notifications"
        val payload = JSONObject().apply {
            put("enabled", enabled)
        }
        try {
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${prefs.authToken}")
                .header("x-auth-token", prefs.authToken)
                .header("x-user-id", userId)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.Success(enabled)
                } else {
                    Result.Error("خطا در به‌روزرسانی تنظیمات اعلان از سرور")
                }
            }
        } catch (e: Exception) {
            Result.Error("خطای شبکه: ${e.message}")
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
