package com.example

import android.app.Application
import android.util.Log
import androidx.work.*
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ONESIGNAL_APP_ID should be set in AI Studio Secrets panel.
// It will be available via BuildConfig.ONESIGNAL_APP_ID if using the Secrets Gradle Plugin.
const val ONESIGNAL_APP_ID = BuildConfig.ONESIGNAL_APP_ID

class ReqabatApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Safely check if ONESIGNAL_APP_ID is a real, configured App ID before initializing
        if (isValidOneSignalAppId(ONESIGNAL_APP_ID)) {
            try {
                // Verbose Logging to help us debug during development
                OneSignal.Debug.logLevel = com.onesignal.debug.LogLevel.VERBOSE

                // OneSignal Initialization
                OneSignal.initWithContext(this, ONESIGNAL_APP_ID)

                // Request permission for notifications on Android 13+
                CoroutineScope(Dispatchers.IO).launch {
                    OneSignal.Notifications.requestPermission(true)
                }
                Log.d("ReqabatApplication", "OneSignal initialized successfully.")
            } catch (e: Exception) {
                Log.e("ReqabatApplication", "Failed to initialize OneSignal", e)
            }
        } else {
            Log.i("ReqabatApplication", "[OneSignal] Initialization skipped: Placeholder or unconfigured App ID ('$ONESIGNAL_APP_ID').")
        }

        // Schedule Local Reminder
        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(24, java.util.concurrent.TimeUnit.HOURS)
            .addTag("reminder_work")
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "reminder_work",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun isValidOneSignalAppId(appId: String?): Boolean {
        if (appId.isNullOrBlank()) return false
        val trimmed = appId.trim()
        if (trimmed.startsWith("YOUR_", ignoreCase = true)) return false
        if (trimmed.contains("YOUR_ONESIGNAL", ignoreCase = true)) return false
        if (trimmed.equals("placeholder", ignoreCase = true)) return false
        // Valid OneSignal App ID is usually a UUID format (e.g. 36 characters)
        return trimmed.length >= 20 && !trimmed.contains(" ")
    }
}
