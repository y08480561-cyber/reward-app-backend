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

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean
        get() = prefs.supabaseUrl.isNotBlank() && prefs.supabaseAnonKey.isNotBlank()

    /**
     * Call Supabase stored procedure: rpc_register_or_login
     */
    suspend fun registerOrLogin(
        firstName: String,
        lastName: String,
        phoneNumber: String
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.Error("تنظیمات اتصال به Supabase ست نشده است.")
        }

        val url = "${prefs.supabaseUrl.trimEnd('/')}/rest/v1/rpc/rpc_register_or_login"
        val payload = JSONObject().apply {
            put("p_first_name", firstName)
            put("p_last_name", lastName)
            put("p_phone", phoneNumber)
        }

        executePostRpc(url, payload)
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
