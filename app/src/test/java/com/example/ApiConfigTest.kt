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
    fun `default serverUrl is strictly production custom domain`() {
        assertEquals("https://api.kalametgame.ir", ApiConfig.BASE_URL)
        assertEquals("https://api.kalametgame.ir", prefs.serverUrl)
    }

    @Test
    fun `legacy Render and cleartext URLs migrate automatically to api_kalametgame_ir`() {
        val sp = context.getSharedPreferences("reqabat_preferences", Context.MODE_PRIVATE)

        sp.edit().putString("server_api_url", "https://reward-app-backend-1.onrender.com").commit()
        assertEquals("https://api.kalametgame.ir", prefs.serverUrl)

        sp.edit().putString("server_api_url", "https://reward-app-backend-a9ze.onrender.com/api").commit()
        assertEquals("https://api.kalametgame.ir", prefs.serverUrl)

        sp.edit().putString("server_api_url", "http://api.kalametgame.ir").commit()
        assertEquals("https://api.kalametgame.ir", prefs.serverUrl)

        prefs.serverUrl = "https://reward-app-backend-1.onrender.com/"
        assertEquals("https://api.kalametgame.ir", prefs.serverUrl)
    }

    @Test
    fun `asset url resolution migrates legacy backend hosts while preserving third-party CDNs`() {
        assertEquals(
            "https://api.kalametgame.ir/uploads/gift.jpg",
            ApiConfig.resolveBackendAssetUrl("/uploads/gift.jpg")
        )
        assertEquals(
            "https://api.kalametgame.ir/uploads/gift.jpg",
            ApiConfig.resolveBackendAssetUrl("uploads/gift.jpg")
        )
        assertEquals(
            "https://api.kalametgame.ir/uploads/gift.jpg?v=2",
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
        val req = Request.Builder().url("http://api.kalametgame.ir/health").build()
        try {
            client.newCall(req).execute()
            fail("Expected SSLHandshakeException for cleartext HTTP request")
        } catch (e: SSLHandshakeException) {
            assertTrue(e.message?.contains("HTTPS") == true)
        }
    }
}
