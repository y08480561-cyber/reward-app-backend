package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("reqabat_preferences", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_FIRST_NAME = "first_name"
        private const val KEY_LAST_NAME = "last_name"
        private const val KEY_PHONE = "phone_number"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_TOTAL_SCORE = "total_score"
        private const val KEY_TAPSELL_KEY = "tapsell_app_key"
        private const val KEY_TAPSELL_ZONE = "tapsell_rewarded_zone"
        private const val KEY_SERVER_URL = "server_api_url"
        private const val KEY_ADMIN_PIN = "admin_security_pin"
        private const val KEY_USE_TEST_ADS = "use_test_ads_mode"
        private const val KEY_SUPABASE_URL = "supabase_url"
        private const val KEY_SUPABASE_ANON_KEY = "supabase_anon_key"

        // Real Tapsell Plus credentials provided by user
        const val DEFAULT_TAPSELL_KEY = "sgedhqdbofphgpajkrqtahjkedbkokdlqiesotcmfqcklbamjaaiddqsqnihdtmobfidcb"
        const val DEFAULT_REWARDED_ZONE = "6ad231b89140ba2215e772db"
        const val DEFAULT_SERVER_URL = "https://api.reqabat.app"
        const val DEFAULT_ADMIN_PIN = "1234"
        const val DEFAULT_SUPABASE_URL = ""
        const val DEFAULT_SUPABASE_ANON_KEY = ""
    }

    var supabaseUrl: String
        get() = prefs.getString(KEY_SUPABASE_URL, DEFAULT_SUPABASE_URL) ?: DEFAULT_SUPABASE_URL
        set(value) = prefs.edit().putString(KEY_SUPABASE_URL, value).apply()

    var supabaseAnonKey: String
        get() = prefs.getString(KEY_SUPABASE_ANON_KEY, DEFAULT_SUPABASE_ANON_KEY) ?: DEFAULT_SUPABASE_ANON_KEY
        set(value) = prefs.edit().putString(KEY_SUPABASE_ANON_KEY, value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var userId: String
        get() = prefs.getString(KEY_USER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var firstName: String
        get() = prefs.getString(KEY_FIRST_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FIRST_NAME, value).apply()

    var lastName: String
        get() = prefs.getString(KEY_LAST_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_NAME, value).apply()

    var phoneNumber: String
        get() = prefs.getString(KEY_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PHONE, value).apply()

    var authToken: String
        get() = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()

    var totalScore: Int
        get() = prefs.getInt(KEY_TOTAL_SCORE, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_SCORE, value).apply()

    var tapsellAppKey: String
        get() = prefs.getString(KEY_TAPSELL_KEY, DEFAULT_TAPSELL_KEY) ?: DEFAULT_TAPSELL_KEY
        set(value) = prefs.edit().putString(KEY_TAPSELL_KEY, value).apply()

    var tapsellRewardedZone: String
        get() = prefs.getString(KEY_TAPSELL_ZONE, DEFAULT_REWARDED_ZONE) ?: DEFAULT_REWARDED_ZONE
        set(value) = prefs.edit().putString(KEY_TAPSELL_ZONE, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var adminPin: String
        get() = prefs.getString(KEY_ADMIN_PIN, DEFAULT_ADMIN_PIN) ?: DEFAULT_ADMIN_PIN
        set(value) = prefs.edit().putString(KEY_ADMIN_PIN, value).apply()

    var useTestAds: Boolean
        get() = prefs.getBoolean(KEY_USE_TEST_ADS, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_TEST_ADS, value).apply()

    fun saveUserSession(
        id: String,
        first: String,
        last: String,
        phone: String,
        token: String,
        score: Int
    ) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_ID, id)
            .putString(KEY_FIRST_NAME, first)
            .putString(KEY_LAST_NAME, last)
            .putString(KEY_PHONE, phone)
            .putString(KEY_AUTH_TOKEN, token)
            .putInt(KEY_TOTAL_SCORE, score)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .putString(KEY_USER_ID, "")
            .putString(KEY_FIRST_NAME, "")
            .putString(KEY_LAST_NAME, "")
            .putString(KEY_PHONE, "")
            .putString(KEY_AUTH_TOKEN, "")
            .putInt(KEY_TOTAL_SCORE, 0)
            .apply()
    }
}
