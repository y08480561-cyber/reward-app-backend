package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.remote.BackendRepository
import com.example.data.remote.Result
import com.example.model.PromoAdBanner
import com.example.model.PromoBannerUrlValidator
import com.example.viewmodel.MainViewModel
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class PromoAdBannerTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var db: AppDatabase
    private lateinit var httpServer: HttpServer
    private lateinit var repo: BackendRepository
    private val recordedRequests = CopyOnWriteArrayList<Pair<String, String>>()
    @Volatile
    private var activeBannersResponseBody: String = """{"success":true,"banners":[]}"""

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("reqabat_preferences", Context.MODE_PRIVATE).edit().clear().apply()
        prefs = PreferencesManager(context)
        db = AppDatabase.getInstance(context)

        httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/api/ad-banners/active") { exchange ->
            val bytes = activeBannersResponseBody.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        httpServer.createContext("/api/ad-banners/") { exchange ->
            val reqBody = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).readText()
            recordedRequests.add(exchange.requestURI.path to reqBody)
            val resp = """{"success":true,"recorded":true}""".toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, resp.size.toLong())
            exchange.responseBody.use { it.write(resp) }
        }
        httpServer.start()

        prefs.serverUrl = "http://127.0.0.1:${httpServer.address.port}"
        repo = BackendRepository(context, db, prefs)
    }

    @After
    fun teardown() {
        httpServer.stop(0)
    }

    @Test
    fun `url validator accepts only safe http and https urls`() {
        assertTrue(PromoBannerUrlValidator.isSafeHttpUrl("https://kalametgame.ir/promo"))
        assertTrue(PromoBannerUrlValidator.isSafeHttpUrl("http://example.com/offer?id=1"))

        assertFalse(PromoBannerUrlValidator.isSafeHttpUrl("javascript:alert(1)"))
        assertFalse(PromoBannerUrlValidator.isSafeHttpUrl("data:text/html,<script>alert(1)</script>"))
        assertFalse(PromoBannerUrlValidator.isSafeHttpUrl("file:///etc/passwd"))
        assertFalse(PromoBannerUrlValidator.isSafeHttpUrl("intent://scan/#Intent;end"))
        assertFalse(PromoBannerUrlValidator.isSafeHttpUrl("https://user:pass@evil.com/x"))
        assertFalse(PromoBannerUrlValidator.isSafeHttpUrl("   "))
        assertNull(PromoBannerUrlValidator.normalizeSafeHttpUrl("not a valid url"))
    }

    @Test
    fun `filterAndSortActive excludes inactive expired future and unsafe banners`() {
        val now = 1_700_000_000_000L
        val items = listOf(
            PromoAdBanner(
                id = "b_second",
                title = "Second",
                imageUrl = "https://api.kalametgame.ir/uploads/ad-banners/2.png",
                targetUrl = "https://example.com/2",
                linkOpenMode = "external",
                isActive = true,
                displayOrder = 2
            ),
            PromoAdBanner(
                id = "b_first",
                title = "First",
                imageUrl = "https://api.kalametgame.ir/uploads/ad-banners/1.png",
                targetUrl = "https://example.com/1",
                linkOpenMode = "internal",
                isActive = true,
                startAtMs = now - 10_000L,
                endAtMs = now + 60_000L,
                displayOrder = 1
            ),
            PromoAdBanner(
                id = "b_expired",
                title = "Expired",
                imageUrl = "https://api.kalametgame.ir/uploads/ad-banners/exp.png",
                targetUrl = "https://example.com/exp",
                isActive = true,
                endAtMs = now - 1_000L,
                displayOrder = 0
            ),
            PromoAdBanner(
                id = "b_future",
                title = "Future",
                imageUrl = "https://api.kalametgame.ir/uploads/ad-banners/fut.png",
                targetUrl = "https://example.com/fut",
                isActive = true,
                startAtMs = now + 50_000L,
                displayOrder = 0
            ),
            PromoAdBanner(
                id = "b_inactive",
                title = "Inactive",
                imageUrl = "https://api.kalametgame.ir/uploads/ad-banners/off.png",
                targetUrl = "https://example.com/off",
                isActive = false,
                displayOrder = 0
            ),
            PromoAdBanner(
                id = "b_bad_url",
                title = "Unsafe",
                imageUrl = "https://api.kalametgame.ir/uploads/ad-banners/bad.png",
                targetUrl = "javascript:alert(1)",
                isActive = true,
                displayOrder = 0
            )
        )

        val result = PromoBannerUrlValidator.filterAndSortActive(items, nowMs = now)
        assertEquals(2, result.size)
        assertEquals("b_first", result[0].id)
        assertEquals("b_second", result[1].id)
    }

    @Test
    fun `fetchActiveAdBanners resolves urls caches response tracks analytics and handles empty list`() = runBlocking {
        val futureEnd = System.currentTimeMillis() + 3600_000L
        activeBannersResponseBody = """
            {
              "success": true,
              "banners": [
                {
                  "id": "ad_2",
                  "title": "Banner 2",
                  "imageUrl": "/uploads/ad-banners/b2.png",
                  "targetUrl": "https://example.com/offer2",
                  "linkOpenMode": "external",
                  "isActive": true,
                  "displayOrder": 2,
                  "endAt": $futureEnd
                },
                {
                  "id": "ad_1",
                  "title": "Banner 1",
                  "imageUrl": "/uploads/ad-banners/b1.jpg",
                  "targetUrl": "https://example.com/offer1",
                  "linkOpenMode": "internal",
                  "isActive": true,
                  "displayOrder": 1
                },
                {
                  "id": "ad_bad",
                  "title": "Bad",
                  "imageUrl": "/uploads/ad-banners/bad.png",
                  "targetUrl": "javascript:void(0)",
                  "isActive": true,
                  "displayOrder": 0
                }
              ]
            }
        """.trimIndent()

        val res = repo.fetchActiveAdBanners()
        assertTrue(res is Result.Success)
        val banners = (res as Result.Success).data
        assertEquals(2, banners.size)
        assertEquals("ad_1", banners[0].id)
        assertEquals("internal", banners[0].linkOpenMode)
        assertTrue(banners[0].imageUrl.endsWith("/uploads/ad-banners/b1.jpg"))
        assertEquals("ad_2", banners[1].id)

        // Cached copy works offline
        val cached = repo.parseCachedAdBanners()
        assertEquals(2, cached.size)
        assertEquals("ad_1", cached[0].id)

        // Analytics calls hit expected routes
        repo.recordAdBannerImpression("ad_1", "user_42")
        repo.recordAdBannerClick("ad_1", "user_42", "nonce_abc")
        assertTrue(recordedRequests.any { it.first == "/api/ad-banners/ad_1/impression" && it.second.contains("user_42") })
        assertTrue(recordedRequests.any { it.first == "/api/ad-banners/ad_1/click" && it.second.contains("nonce_abc") })

        // Server returning empty list clears banner display cleanly
        activeBannersResponseBody = """{"success":true,"banners":[]}"""
        val emptyRes = repo.fetchActiveAdBanners() as Result.Success
        assertTrue(emptyRes.data.isEmpty())
        assertTrue(repo.parseCachedAdBanners().isEmpty())
    }

    @Test
    fun `viewModel debounces double clicks and opens validated url with configured mode`() {
        val vm = MainViewModel(ApplicationProvider.getApplicationContext())
        vm.isTickerEnabled = false

        val banner = PromoAdBanner(
            id = "ad_tap_test",
            title = "Tap Test",
            imageUrl = "https://api.kalametgame.ir/uploads/ad-banners/tap.png",
            targetUrl = "https://kalametgame.ir/campaign",
            linkOpenMode = "internal",
            isActive = true,
            displayOrder = 1
        )

        var openCount = 0
        var openedUrl = ""
        var openedMode = ""

        vm.onPromoBannerClick(banner) { safeUrl, mode ->
            openCount++
            openedUrl = safeUrl
            openedMode = mode
        }
        // Immediate rapid second tap within 800ms should be ignored
        vm.onPromoBannerClick(banner) { _, _ ->
            openCount++
        }

        assertEquals(1, openCount)
        assertEquals("https://kalametgame.ir/campaign", openedUrl)
        assertEquals("internal", openedMode)

        // Unsafe URL banner should never trigger callback
        val badBanner = banner.copy(id = "ad_unsafe", targetUrl = "javascript:alert(1)")
        vm.onPromoBannerClick(badBanner) { _, _ ->
            openCount++
        }
        assertEquals(1, openCount)
    }
}
