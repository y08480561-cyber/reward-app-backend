package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import com.example.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class AuthValidationTest {

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferencesManager(context).clearSession()
        viewModel = MainViewModel(ApplicationProvider.getApplicationContext())
        viewModel.isTickerEnabled = false
    }

    @Test
    fun `english letters are blocked in first and last name`() {
        viewModel.onFirstNameChange("Ali")
        assertEquals("", viewModel.authUiState.value.firstName)
        assertNotNull(viewModel.authUiState.value.errorMessage)

        viewModel.onLastNameChange("Rezaei123")
        assertEquals("", viewModel.authUiState.value.lastName)
        assertNotNull(viewModel.authUiState.value.errorMessage)
    }

    @Test
    fun `persian letters are accepted in first and last name`() {
        viewModel.onFirstNameChange("علی")
        viewModel.onLastNameChange("رضایی")

        assertEquals("علی", viewModel.authUiState.value.firstName)
        assertEquals("رضایی", viewModel.authUiState.value.lastName)
    }

    @Test
    fun `phone number requires leading zero and exact 11 digits`() {
        viewModel.onFirstNameChange("علی")
        viewModel.onLastNameChange("رضایی")

        // Missing leading 0
        viewModel.onPhoneNumberChange("93512345678")
        viewModel.loginOrRegister()
        assertTrue(viewModel.authUiState.value.errorMessage?.contains("0") == true)

        // Shorter than 11 digits
        viewModel.onPhoneNumberChange("093512345")
        viewModel.loginOrRegister()
        assertTrue(viewModel.authUiState.value.errorMessage?.contains("۱۱") == true)

        // Longer than 11 digits is capped at 11, Persian digits normalized
        viewModel.onPhoneNumberChange("۰۹۳۵۱۲۳۴۵۶۷۸۹")
        assertEquals("09351234567", viewModel.authUiState.value.phoneNumber)
    }
}
