package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import com.example.data.remote.ApiConfig
import com.example.data.remote.NetworkClientProvider
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.net.ssl.SSLHandshakeException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class ApiConfigTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("reqabat_preferences", Context.MODE_PRIVATE).edit().clear().apply()
        prefs = PreferencesManager(context)
    }

    @Test
    fun `default serverUrl uses Cloudflare test API while preserving production BASE_URL`() {
        assertEquals("https://api.kalametgame.ir", ApiConfig.BASE_URL)
        assertEquals("https://api-test.kalametgame.ir", ApiConfig.CLOUDFLARE_TEST_BASE_URL)
        assertEquals("https://api-test.kalametgame.ir", ApiConfig.ACTIVE_BASE_URL)
        assertEquals("https://api-test.kalametgame.ir", prefs.serverUrl)
    }

    @Test
    fun `legacy Render, cleartext, and prod URLs route cleanly while preserving localhost`() {
        val sp = context.getSharedPreferences("reqabat_preferences", Context.MODE_PRIVATE)

        sp.edit().putString("server_api_url", "https://reward-app-backend-1.onrender.com").commit()
        assertEquals(ApiConfig.ACTIVE_BASE_URL, prefs.serverUrl)

        sp.edit().putString("server_api_url", "https://reward-app-backend-a9ze.onrender.com/api").commit()
        assertEquals(ApiConfig.ACTIVE_BASE_URL, prefs.serverUrl)

        sp.edit().putString("server_api_url", "http://api-test.kalametgame.ir").commit()
        assertEquals(ApiConfig.ACTIVE_BASE_URL, prefs.serverUrl)

        sp.edit().putString("server_api_url", "https://api.kalametgame.ir").commit()
        assertEquals(ApiConfig.ACTIVE_BASE_URL, prefs.serverUrl)

        prefs.serverUrl = "https://reward-app-backend-1.onrender.com/"
        assertEquals(ApiConfig.ACTIVE_BASE_URL, prefs.serverUrl)

        prefs.serverUrl = "http://127.0.0.1:8080/"
        assertEquals("http://127.0.0.1:8080", prefs.serverUrl)
    }

    @Test
    fun `asset url resolution resolves relative paths against active base and explicit BASE_URL`() {
        assertEquals(
            "https://api-test.kalametgame.ir/uploads/gift.jpg",
            ApiConfig.resolveBackendAssetUrl("/uploads/gift.jpg")
        )
        assertEquals(
            "https://api.kalametgame.ir/uploads/gift.jpg",
            ApiConfig.resolveBackendAssetUrl("/uploads/gift.jpg", ApiConfig.BASE_URL)
        )
        assertEquals(
            "https://api-test.kalametgame.ir/uploads/gift.jpg",
            ApiConfig.resolveBackendAssetUrl("uploads/gift.jpg")
        )
        assertEquals(
            "https://api-test.kalametgame.ir/uploads/gift.jpg?v=2",
            ApiConfig.resolveBackendAssetUrl("https://reward-app-backend-1.onrender.com/uploads/gift.jpg?v=2")
        )
        assertEquals(
            "https://images.unsplash.com/photo-123?w=600",
            ApiConfig.resolveBackendAssetUrl("https://images.unsplash.com/photo-123?w=600")
        )
        assertEquals(
            "https://myproject.supabase.co/storage/v1/object/public/banners/a.png",
            ApiConfig.resolveBackendAssetUrl("https://myproject.supabase.co/storage/v1/object/public/banners/a.png")
        )
    }

    @Test
    fun `okHttp client rejects non-local cleartext http traffic`() {
        val client = NetworkClientProvider.createResilientClient()
        val req = Request.Builder().url("http://api-test.kalametgame.ir/api/health").build()
        try {
            client.newCall(req).execute()
            fail("Expected SSLHandshakeException for cleartext HTTP request")
        } catch (e: SSLHandshakeException) {
            assertTrue(e.message?.contains("HTTPS") == true)
        }
    }
}
