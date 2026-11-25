package com.example

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class ReminderNotificationTest {

    private lateinit var app: Application
    private lateinit var prefs: PreferencesManager
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        prefs = PreferencesManager(app)
        prefs.clearSession()
        prefs.lastReminderMessageIndex = -1
        AppForegroundState.isInForeground = false
        notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        if (network != null) {
            val caps = ShadowNetworkCapabilities.newInstance()
            Shadows.shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            Shadows.shadowOf(cm).setNetworkCapabilities(network, caps)
        }
    }

    @After
    fun tearDown() {
        AppForegroundState.isInForeground = false
        prefs.clearSession()
    }

    @Test
    fun `varied messages never repeat consecutively`() {
        prefs.saveUserSession("u1", "علی", "رضایی", "09351234567", "tok", 100)
        var previousPair: Pair<String, String>? = null
        repeat(15) {
            val picked = CompetitionReminderMessages.pickNextMessage(prefs)
            assertTrue(picked.first.isNotBlank())
            assertTrue(picked.second.isNotBlank())
            if (previousPair != null) {
                assertNotEquals(previousPair!!.first, picked.first)
            }
            previousPair = picked
        }
    }

    @Test
    fun `does not notify when user is not logged in`() {
        AppForegroundState.isInForeground = false
        prefs.wasOfflineSinceLastVisit = true
        prefs.lastAppBackgroundAtMs = 1_000L

        val triggered = CompetitionReminderManager.maybeNotifyOnReconnect(
            context = app,
            source = ReconnectSource.IN_PROCESS_CALLBACK,
            nowMs = 100_000L
        )

        assertFalse(triggered)
        assertEquals(0, Shadows.shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun `does not notify when app is in foreground`() {
        prefs.saveUserSession("u1", "علی", "رضایی", "09351234567", "tok", 100)
        AppForegroundState.isInForeground = true
        prefs.wasOfflineSinceLastVisit = true
        prefs.lastAppBackgroundAtMs = 1_000L

        val triggered = CompetitionReminderManager.maybeNotifyOnReconnect(
            context = app,
            source = ReconnectSource.IN_PROCESS_CALLBACK,
            nowMs = 100_000L
        )

        assertFalse(triggered)
        assertEquals(0, Shadows.shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun `does not notify when notifications preference is disabled`() {
        prefs.saveUserSession("u1", "علی", "رضایی", "09351234567", "tok", 100)
        prefs.notificationsEnabled = false
        AppForegroundState.isInForeground = false
        prefs.wasOfflineSinceLastVisit = true
        prefs.lastAppBackgroundAtMs = 1_000L

        val triggered = CompetitionReminderManager.maybeNotifyOnReconnect(
            context = app,
            source = ReconnectSource.IN_PROCESS_CALLBACK,
            nowMs = 100_000L
        )

        assertFalse(triggered)
        assertEquals(0, Shadows.shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun `notifies with varied messages on reconnect when logged in and outside app`() {
        prefs.saveUserSession("u1", "علی", "رضایی", "09351234567", "tok", 100)
        prefs.notificationsEnabled = true
        AppForegroundState.isInForeground = false
        prefs.lastAppBackgroundAtMs = 1_000L
        prefs.wasOfflineSinceLastVisit = true

        val firstTriggered = CompetitionReminderManager.maybeNotifyOnReconnect(
            context = app,
            source = ReconnectSource.IN_PROCESS_CALLBACK,
            nowMs = 50_000L
        )
        assertTrue(firstTriggered)
        assertFalse(prefs.wasOfflineSinceLastVisit)

        val firstNotif = Shadows.shadowOf(notificationManager).allNotifications.first()
        val firstTitle = firstNotif.extras.getString(Notification.EXTRA_TITLE)

        // Within 30s cooldown, duplicate trigger from WorkManager/PendingIntent is ignored
        prefs.wasOfflineSinceLastVisit = true
        val duplicateBlocked = CompetitionReminderManager.maybeNotifyOnReconnect(
            context = app,
            source = ReconnectSource.WORK_MANAGER,
            nowMs = 60_000L
        )
        assertFalse(duplicateBlocked)

        // After cooldown, next internet-on event posts a different title
        val secondTriggered = CompetitionReminderManager.maybeNotifyOnReconnect(
            context = app,
            source = ReconnectSource.IN_PROCESS_CALLBACK,
            nowMs = 120_000L
        )
        assertTrue(secondTriggered)
        val secondNotif = Shadows.shadowOf(notificationManager).allNotifications.first()
        val secondTitle = secondNotif.extras.getString(Notification.EXTRA_TITLE)
        assertNotEquals(firstTitle, secondTitle)
    }
}
