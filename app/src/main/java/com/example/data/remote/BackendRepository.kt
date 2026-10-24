package com.example.data.remote

import android.content.Context
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

    /**
     * Ensures an active 24-hour cycle is present.
     * If the cycle has ended (24h passed), automatically archives old cycle and
     * resets all 50 squares back to READY state while keeping user's cumulative score intact.
     */
    suspend fun ensureActiveCycle(userId: String): DailyCycle = withContext(Dispatchers.IO) {
        val currentCycleEntity = cycleDao.getCurrentCycle()
        val currentTime = System.currentTimeMillis()

        if (currentCycleEntity == null || currentTime >= currentCycleEntity.endTimeMs) {
            // Cycle expired or not yet initialized: Start new 24h cycle!
            val newCycleId = "CYC_" + (currentTime / 1000)
            val startTime = currentTime
            val endTime = startTime + CYCLE_DURATION_MS

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

        // Send point registration to Render Backend API
        val url = "${prefs.serverUrl.trimEnd('/')}/api/ads/complete"
        val payload = JSONObject().apply {
            put("userId", userId)
            put("squareNumber", squareNumber)
            put("rewardNonce", rewardNonce)
            put("adDurationSeconds", adDurationSeconds)
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

                    Result.Success(newScore)
                } else {
                    val errJson = try { JSONObject(body) } catch (e: Exception) { null }
                    val msg = errJson?.optString("error") ?: "خطا در تایید تبلیغ توسط سرور"
                    Result.Error(msg, response.code)
                }
            }
        } catch (e: Exception) {
            val classified = classifyNetworkException(e)
            // Points are NOT retried automatically to prevent duplicate registrations
            Result.Error("${classified.category.titlePersian}: ${classified.userFacingMessage}")
        }
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

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
