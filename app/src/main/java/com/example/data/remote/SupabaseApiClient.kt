package com.example.data.remote

import android.util.Log
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * SupabaseApiClient provides direct, secure communication with Supabase PostgreSQL
 * via public PostgREST / RPC endpoints using the public Anon Key.
 *
 * All sensitive operations (score addition, nonces, daily constraints) are handled
 * by SECURITY DEFINER stored procedures inside PostgreSQL, ensuring zero chance of client manipulation.
 */
class SupabaseApiClient(
    private val prefs: PreferencesManager
) {
    companion object {
        private const val TAG = "SupabaseApiClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = NetworkClientProvider.createResilientClient()

    val isConfigured: Boolean
        get() = prefs.supabaseUrl.isNotBlank() && prefs.supabaseAnonKey.isNotBlank()

    private fun formatPhone(phone: String): String {
        val clean = phone.trim()
        return if (clean.startsWith("09")) {
            "+98${clean.substring(1)}"
        } else if (clean.startsWith("9")) {
            "+98$clean"
        } else {
            clean
        }
    }

    /**
     * Request OTP code via Supabase GoTrue Auth API (/auth/v1/otp).
     */
    suspend fun requestAuthOtp(phoneNumber: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.Error("تنظیمات اتصال به Supabase ست نشده است.")
        }

        val formattedPhone = formatPhone(phoneNumber)
        val url = "${prefs.supabaseUrl.trimEnd('/')}/auth/v1/otp"
        val payload = JSONObject().apply {
            put("phone", formattedPhone)
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("apikey", prefs.supabaseAnonKey)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Result.Success("کد تایید با موفقیت از طریق Supabase Auth به شماره شما ارسال شد.")
                } else {
                    Log.e(TAG, "requestAuthOtp error HTTP ${response.code}: $body")
                    val errJson = try { JSONObject(body) } catch(e: Exception) { null }
                    val msg = errJson?.optString("error_description") ?: errJson?.optString("msg") ?: "خطا در ارسال کد تایید از طریق سرور"
                    Result.Error(msg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception in requestAuthOtp", e)
            Result.Error("خطای شبکه در ارتباط با سرور: ${e.localizedMessage}")
        }
    }

    /**
     * Verify OTP code via Supabase GoTrue Auth API (/auth/v1/verify) and return JWT access token.
     */
    suspend fun verifyAuthOtp(phoneNumber: String, token: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.Error("تنظیمات اتصال به Supabase ست نشده است.")
        }

        val formattedPhone = formatPhone(phoneNumber)
        val url = "${prefs.supabaseUrl.trimEnd('/')}/auth/v1/verify"
        val payload = JSONObject().apply {
            put("type", "sms")
            put("phone", formattedPhone)
            put("token", token)
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("apikey", prefs.supabaseAnonKey)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val accessToken = json.optString("access_token")
                    val userObj = json.optJSONObject("user")
                    val userId = userObj?.optString("id")

                    if (accessToken.isBlank() || userId.isNullOrBlank()) {
                        Result.Error("پاسخ نامعتبر از سرور احراز هویت: توکن یا شناسه کاربر یافت نشد.")
                    } else {
                        Result.Success(json)
                    }
                } else {
                    Log.e(TAG, "verifyAuthOtp error HTTP ${response.code}: $body")
                    val errJson = try { JSONObject(body) } catch(e: Exception) { null }
                    val msg = errJson?.optString("error_description") ?: errJson?.optString("msg") ?: "کد تایید نامعتبر یا منقضی شده است."
                    Result.Error(msg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception in verifyAuthOtp", e)
            Result.Error("خطای شبکه در تایید کد: ${e.localizedMessage}")
        }
    }

    /**
     * Call Supabase stored procedure: rpc_award_ad_point
     * Validates Nonce (replay attack prevention) and 1 point/square/day limit in DB transaction.
     */
    suspend fun awardAdPoint(
        squareNumber: Int,
        nonce: String
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.Error("تنظیمات اتصال به Supabase ست نشده است.")
        }

        val url = "${prefs.supabaseUrl.trimEnd('/')}/rest/v1/rpc/rpc_award_ad_point"
        val payload = JSONObject().apply {
            put("p_square_number", squareNumber)
            put("p_nonce", nonce)
        }

        executePostRpc(url, payload)
    }

    /**
     * Call Supabase stored procedure: rpc_get_leaderboard
     */
    suspend fun getLeaderboard(limit: Int = 50): Result<JSONArray> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.Error("تنظیمات اتصال به Supabase ست نشده است.")
        }

        val url = "${prefs.supabaseUrl.trimEnd('/')}/rest/v1/rpc/rpc_get_leaderboard"
        val payload = JSONObject().apply {
            put("p_limit", limit)
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("apikey", prefs.supabaseAnonKey)
                .header("Authorization", "Bearer ${prefs.supabaseAnonKey}")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Result.Success(JSONArray(body))
                } else {
                    Log.e(TAG, "getLeaderboard error HTTP ${response.code}: $body")
                    Result.Error("خطا در دریافت جدول رتبه‌بندی از سرور")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception in getLeaderboard", e)
            Result.Error("خطای شبکه در اتصال به سرور: ${e.localizedMessage}")
        }
    }

    private fun executePostRpc(url: String, payload: JSONObject): Result<JSONObject> {
        return try {
            val authHeader = if (prefs.authToken.startsWith("ey")) "Bearer ${prefs.authToken}" else "Bearer ${prefs.supabaseAnonKey}"
            val request = Request.Builder()
                .url(url)
                .header("apikey", prefs.supabaseAnonKey)
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    if (json.optBoolean("success", true)) {
                        Result.Success(json)
                    } else {
                        Result.Error(json.optString("message", "خطای بازگشتی از سرور"))
                    }
                } else {
                    Log.e(TAG, "RPC error HTTP ${response.code}: $body")
                    Result.Error("خطا در پردازش سرور (کد ${response.code})")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception in executePostRpc", e)
            Result.Error("خطای شبکه در ارتباط با Supabase: ${e.localizedMessage}")
        }
    }
}
