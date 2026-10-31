package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class FeedbackSystemTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        
        prefs = PreferencesManager(context)
        prefs.clearSession()
    }

    @Test
    fun `test feedback dialog triggers after login`() {
        // Given: User is logged in
        prefs.saveUserSession("u1", "First", "Last", "09123456789", "token", 10)
        viewModel = MainViewModel(ApplicationProvider.getApplicationContext())
        
        // When: App starts (ViewModel init)
        viewModel.checkAndShowFeedbackDialog()
        
        // Then: Dialog state should be true
        assertTrue(viewModel.uiState.value.showFeedbackDialog)
    }

    @Test
    fun `test feedback dialog does not trigger if already shown`() {
        // Given: Feedback already shown
        prefs.saveUserSession("u1", "First", "Last", "09123456789", "token", 10)
        prefs.isFeedbackShown = true
        viewModel = MainViewModel(ApplicationProvider.getApplicationContext())
        
        // When: check is called
        viewModel.checkAndShowFeedbackDialog()
        
        // Then: Dialog state should be false
        assertFalse(viewModel.uiState.value.showFeedbackDialog)
    }

    @Test
    fun `test navigation items visibility`() {
        viewModel = MainViewModel(ApplicationProvider.getApplicationContext())
        // Mock UI state
        val uiState = viewModel.uiState.value
        
        // Verify Store exists in navigation items logic (implicitly checked by verifying navigation to STORE)
        viewModel.navigateTo(AppScreen.STORE)
        assertEquals(AppScreen.STORE, viewModel.uiState.value.currentScreen)
        
        // Verify Squares exists
        viewModel.navigateTo(AppScreen.SQUARES)
        assertEquals(AppScreen.SQUARES, viewModel.uiState.value.currentScreen)
    }

    @Test
    fun `test myket button marks feedback as shown but no points awarded locally`() {
        // Given
        prefs.saveUserSession("u1", "First", "Last", "09123456789", "token", 10)
        viewModel = MainViewModel(ApplicationProvider.getApplicationContext())
        viewModel.checkAndShowFeedbackDialog()
        assertTrue(viewModel.uiState.value.showFeedbackDialog)
        
        // When: Open Myket
        viewModel.markFeedbackShown()
        
        // Then: Dialog dismissed, but score same (10)
        assertFalse(viewModel.uiState.value.showFeedbackDialog)
        assertEquals(10, viewModel.uiState.value.currentUser?.totalScore)
        assertTrue(prefs.isFeedbackShown)
    }
}
