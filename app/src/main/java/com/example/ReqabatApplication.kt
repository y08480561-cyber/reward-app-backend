package com.example

import android.app.Application
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

        // Verbose Logging to help us debug during development
        OneSignal.Debug.logLevel = com.onesignal.debug.LogLevel.VERBOSE

        // OneSignal Initialization
        OneSignal.initWithContext(this, ONESIGNAL_APP_ID)

        // Request permission for notifications on Android 13+
        CoroutineScope(Dispatchers.IO).launch {
            OneSignal.Notifications.requestPermission(true)
        }
    }
}
