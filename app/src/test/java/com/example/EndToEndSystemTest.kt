package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import com.example.data.remote.Result
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.robolectric.shadows.ShadowLooper
import org.junit.Assert.*
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class EndToEndSystemTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var viewModel: MainViewModel
    private lateinit var mockWebServer: MockWebServer

    private var feedbackErrorCode = 200
    private var feedbackResponseBody = "{\"status\": \"success\", \"message\": \"Feedback recorded\", \"newTotalScore\": 102}"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        
        prefs = PreferencesManager(context)
        prefs.clearSession()
        
        mockWebServer = MockWebServer()
        mockWebServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.contains("/api/user/feedback") -> {
                        MockResponse().setResponseCode(feedbackErrorCode).setBody(feedbackResponseBody)
                    }
                    path.contains("/health") -> {
                        MockResponse().setResponseCode(200).setBody("{\"status\": \"ok\", \"uptime\": 1000}")
                    }
                    else -> {
                        MockResponse().setResponseCode(200).setBody("{\"status\": \"active\", \"competition\": {\"id\":\"c1\", \"title\":\"T1\", \"status\":\"ACTIVE\"}, \"userScore\": 100}")
                    }
                }
            }
        }
        mockWebServer.start()
        
        // Redirect API calls to mock server
        prefs.serverUrl = mockWebServer.url("/").toString()
        
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher())
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `E2E scenario - login navigation and feedback reward`() = runTest {
        // 1. Simulate Login
        prefs.saveUserSession("user_123", "تست", "کاربر", "09123456789", "mock_token", 100)
        feedbackErrorCode = 200
        feedbackResponseBody = "{\"status\": \"success\", \"message\": \"Feedback recorded\", \"newTotalScore\": 102}"
        
        // Initialize ViewModel
        viewModel = MainViewModel(ApplicationProvider.getApplicationContext())
        viewModel.isTickerEnabled = false
        
        waitUntil { viewModel.uiState.value.currentUser != null }
        
        val state = viewModel.uiState.value
        assertEquals("User ID should match", "user_123", prefs.userId)
        assertTrue("isLoggedIn should be true", prefs.isLoggedIn)
        assertNotNull("CurrentUser in UI state should not be null", state.currentUser)
        
        // 2. Verify Feedback Dialog Trigger
        viewModel.checkAndShowFeedbackDialog()
        assertTrue("Feedback dialog should be shown", viewModel.uiState.value.showFeedbackDialog)
        
        // 3. Submit Feedback
        viewModel.submitInternalFeedback(5, "عالی بود")
        waitUntil { !viewModel.uiState.value.showFeedbackDialog || viewModel.uiState.value.errorMessage != null }
        
        val errMsg = viewModel.uiState.value.errorMessage
        assertNull("submitFeedback should not produce error: $errMsg", errMsg)
        
        // 4. Verify Reward
        assertFalse("Dialog should be hidden after submission", viewModel.uiState.value.showFeedbackDialog)
        assertEquals("Score should increase by 2", 102, prefs.totalScore)
        assertTrue("Feedback should be marked as rewarded", prefs.isFeedbackRewarded)
        
        // 5. Verify Navigation
        viewModel.navigateTo(AppScreen.STORE)
        assertEquals(AppScreen.STORE, viewModel.uiState.value.currentScreen)
        
        viewModel.navigateTo(AppScreen.SQUARES)
        assertEquals(AppScreen.SQUARES, viewModel.uiState.value.currentScreen)
    }

    @Test
    fun `E2E scenario - prevent duplicate reward`() = runTest {
        prefs.saveUserSession("user_123", "تست", "کاربر", "09123456789", "mock_token", 100)
        feedbackErrorCode = 409
        feedbackResponseBody = "{\"error\": \"پاداش این بازخورد قبلاً دریافت شده است\"}"
        
        viewModel = MainViewModel(ApplicationProvider.getApplicationContext())
        viewModel.isTickerEnabled = false
        waitUntil { viewModel.uiState.value.currentUser != null }
        
        viewModel.submitInternalFeedback(5, "تکراری")
        waitUntil { viewModel.uiState.value.errorMessage != null }
        
        assertEquals("Score should remain 100", 100, prefs.totalScore)
        assertNotNull("Error message should be set", viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("قبلاً دریافت شده"))
    }

    @Test
    fun `E2E scenario - handle network error`() = runTest {
        prefs.saveUserSession("user_123", "تست", "کاربر", "09123456789", "mock_token", 100)
        feedbackErrorCode = 500
        feedbackResponseBody = "Internal Server Error"
        
        viewModel = MainViewModel(ApplicationProvider.getApplicationContext())
        viewModel.isTickerEnabled = false
        waitUntil { viewModel.uiState.value.currentUser != null }
        
        viewModel.submitInternalFeedback(3, "خطا")
        waitUntil { viewModel.uiState.value.errorMessage != null }
        
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals(100, prefs.totalScore)
    }

    private suspend fun waitUntil(timeoutMs: Long = 3000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition() && System.currentTimeMillis() - start < timeoutMs) {
            kotlinx.coroutines.delay(50)
            ShadowLooper.idleMainLooper()
        }
    }
}
