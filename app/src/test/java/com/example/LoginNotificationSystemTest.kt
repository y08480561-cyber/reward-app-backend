package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.remote.BackendRepository
import com.example.data.remote.Result
import com.example.model.LoginNotificationConfig
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class LoginNotificationSystemTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var database: AppDatabase
    private lateinit var repository: BackendRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        prefs = PreferencesManager(context)
        prefs.clearSession()
        database = AppDatabase.getInstance(context)
        repository = BackendRepository(context, database, prefs)
    }

    @Test
    fun testOfflineCacheAndViewModelIntegration() {
        // Step 1: Simulate server returning test message "آزمایش پیام ۷۲۹۱"
        val serverJsonString = """
            {
                "id": "default",
                "is_visible": true,
                "badge_text": "سرویس ویژه",
                "badge_icon": "verified",
                "main_title": "آزمایش پیام ۷۲۹۱",
                "main_description": "توضیحات تست",
                "warning_is_visible": true,
                "warning_title": "هشدار اینترنت",
                "warning_description": "لطفاً اینترنت دیتا را فعال کنید",
                "warning_icon": "signal_cellular_alt",
                "updated_at": 1730544220000
            }
        """.trimIndent()

        // Cache the JSON payload
        prefs.cachedLoginNotificationJson = serverJsonString

        // Step 2: Validate parseCachedLoginNotification
        val cachedConfig = repository.parseCachedLoginNotification()
        assertNotNull("Cached config should not be null", cachedConfig)
        assertEquals("آزمایش پیام ۷۲۹۱", cachedConfig?.mainTitle)
        assertEquals("سرویس ویژه", cachedConfig?.badgeText)
        assertTrue(cachedConfig?.isVisible == true)

        // Step 3: Initialize ViewModel and ensure it picks up the cached message immediately
        val viewModel = MainViewModel(ApplicationProvider.getApplicationContext())
        val uiState = viewModel.uiState.value

        assertEquals("آزمایش پیام ۷۲۹۱", uiState.loginNotification.mainTitle)
        assertEquals("سرویس ویژه", uiState.loginNotification.badgeText)

        // Step 4: Verify that calling logout preserves the custom notification message
        viewModel.logout()
        val afterLogoutState = viewModel.uiState.value
        assertEquals("آزمایش پیام ۷۲۹۱", afterLogoutState.loginNotification.mainTitle)
    }
}
