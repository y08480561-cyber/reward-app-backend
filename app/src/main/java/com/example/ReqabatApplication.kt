package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.util.Log
import com.example.data.local.PreferencesManager
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ONESIGNAL_APP_ID should be set in AI Studio Secrets panel.
// It will be available via BuildConfig.ONESIGNAL_APP_ID if using the Secrets Gradle Plugin.
val ONESIGNAL_APP_ID: String = BuildConfig.ONESIGNAL_APP_ID

class ReqabatApplication : Application() {

    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()

        // Safely check if ONESIGNAL_APP_ID is a real, configured App ID before initializing
        if (isValidOneSignalAppId(ONESIGNAL_APP_ID)) {
            try {
                OneSignal.Debug.logLevel = com.onesignal.debug.LogLevel.VERBOSE
                OneSignal.initWithContext(this, ONESIGNAL_APP_ID)
                CoroutineScope(Dispatchers.IO).launch {
                    OneSignal.Notifications.requestPermission(true)
                }
                Log.d("ReqabatApplication", "OneSignal initialized successfully.")
            } catch (e: Exception) {
                Log.e("ReqabatApplication", "Failed to initialize OneSignal", e)
            }
        } else {
            Log.i(
                "ReqabatApplication",
                "[OneSignal] Initialization skipped: Placeholder or unconfigured App ID ('$ONESIGNAL_APP_ID')."
            )
        }

        registerActivityLifecycleTracker()
        registerInProcessNetworkMonitor()

        val prefs = PreferencesManager(this)
        if (prefs.isLoggedIn && prefs.notificationsEnabled) {
            CompetitionReminderManager.scheduleInactivityReminder(this)
        }
    }

    private fun registerActivityLifecycleTracker() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                if (startedActivityCount == 1) {
                    AppForegroundState.isInForeground = true
                    val prefs = PreferencesManager(this@ReqabatApplication)
                    prefs.lastAppForegroundAtMs = System.currentTimeMillis()
                    prefs.wasOfflineSinceLastVisit = false
                    CompetitionReminderManager.cancelReconnectWork(this@ReqabatApplication)
                    if (prefs.isLoggedIn && prefs.notificationsEnabled) {
                        CompetitionReminderManager.registerPersistentNetworkCallback(this@ReqabatApplication)
                    }
                }
            }

            override fun onActivityStopped(activity: Activity) {
                if (startedActivityCount > 0) {
                    startedActivityCount--
                }
                if (startedActivityCount == 0) {
                    AppForegroundState.isInForeground = false
                    val prefs = PreferencesManager(this@ReqabatApplication)
                    prefs.lastAppBackgroundAtMs = System.currentTimeMillis()
                    if (prefs.isLoggedIn && prefs.notificationsEnabled) {
                        if (!CompetitionReminderManager.isNetworkConnected(this@ReqabatApplication)) {
                            prefs.wasOfflineSinceLastVisit = true
                            CompetitionReminderManager.scheduleReconnectWork(this@ReqabatApplication)
                        }
                        CompetitionReminderManager.scheduleInactivityReminder(this@ReqabatApplication)
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun registerInProcessNetworkMonitor() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    val prefs = PreferencesManager(this@ReqabatApplication)
                    if (!CompetitionReminderManager.isNetworkConnected(this@ReqabatApplication)) {
                        prefs.wasOfflineSinceLastVisit = true
                        if (!AppForegroundState.isInForeground && prefs.isLoggedIn && prefs.notificationsEnabled) {
                            CompetitionReminderManager.scheduleReconnectWork(this@ReqabatApplication)
                        }
                    }
                }

                override fun onUnavailable() {
                    val prefs = PreferencesManager(this@ReqabatApplication)
                    prefs.wasOfflineSinceLastVisit = true
                    if (!AppForegroundState.isInForeground && prefs.isLoggedIn && prefs.notificationsEnabled) {
                        CompetitionReminderManager.scheduleReconnectWork(this@ReqabatApplication)
                    }
                }

                override fun onAvailable(network: Network) {
                    val prefs = PreferencesManager(this@ReqabatApplication)
                    if (!AppForegroundState.isInForeground &&
                        prefs.isLoggedIn &&
                        prefs.notificationsEnabled &&
                        prefs.wasOfflineSinceLastVisit
                    ) {
                        CompetitionReminderManager.maybeNotifyOnReconnect(
                            context = this@ReqabatApplication,
                            source = ReconnectSource.IN_PROCESS_CALLBACK
                        )
                    }
                }
            })
        } catch (e: Exception) {
            Log.w("ReqabatApplication", "Unable to register default network callback", e)
        }
    }

    private fun isValidOneSignalAppId(appId: String?): Boolean {
        if (appId.isNullOrBlank()) return false
        val trimmed = appId.trim()
        if (trimmed.startsWith("YOUR_", ignoreCase = true)) return false
        if (trimmed.contains("YOUR_ONESIGNAL", ignoreCase = true)) return false
        if (trimmed.equals("placeholder", ignoreCase = true)) return false
        return trimmed.length >= 20 && !trimmed.contains(" ")
    }
}
