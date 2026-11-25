package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.remote.ApiConfig
import com.example.data.remote.BackendRepository
import com.example.data.remote.NetworkClientProvider
import com.example.data.remote.Result
import com.example.model.PromoAdBanner
import com.example.model.PromoBannerUrlValidator
import com.example.model.User
import com.example.ui.components.PromoAdBannerSection
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
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

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var db: AppDatabase
    private lateinit var httpServer: HttpServer
    private lateinit var repo: BackendRepository
    private val recordedRequests = CopyOnWriteArrayList<Pair<String, String>>()
    private val activeRequestHeaders = CopyOnWriteArrayList<String>()

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
            val cc = exchange.requestHeaders.getFirst("Cache-Control").orEmpty()
            activeRequestHeaders.add(cc)
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
        assertTrue(PromoBannerUrlValidator.isSafeHttpUrl("https://kalametgame.ir/"))
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
    fun `fetchActiveAdBanners handles live production payload serverNow no-cache headers and ISO dates`() = runBlocking {
        // Exact JSON structure returned by https://api.kalametgame.ir/api/ad-banners/active
        // plus an active banner with ISO timestamps to verify flexible timestamp parsing.
        activeBannersResponseBody = """
            {
              "success": true,
              "banners": [
                {
                  "id": "ad_97f0e7e0-c26f-43f5-84f7-9cf0c9d6aa19",
                  "title": "سایت ساز",
                  "imageUrl": "/uploads/ad-banners/ad_banner_1795602314982_1f4523511aab.png",
                  "targetUrl": "https://kalametgame.ir/",
                  "linkOpenMode": "external",
                  "isActive": true,
                  "startAt": null,
                  "endAt": 1795861860000,
                  "displayOrder": 1,
                  "updatedAt": 1795602694437
                },
                {
                  "id": "ad_iso_2",
                  "title": "ISO Banner",
                  "imageUrl": "/uploads/ad-banners/iso.png",
                  "targetUrl": "https://kalametgame.ir/iso",
                  "linkOpenMode": "internal",
                  "isActive": true,
                  "startAt": "2026-11-20T00:00:00.000Z",
                  "endAt": "2026-12-01T00:00:00.000Z",
                  "displayOrder": 2,
                  "updatedAt": 1795602694500
                }
              ],
              "version": "2:1795602694500",
              "serverNow": 1795603000000
            }
        """.trimIndent()

        val res = repo.fetchActiveAdBanners()
        assertTrue(res is Result.Success)
        val banners = (res as Result.Success).data
        assertEquals(2, banners.size)
        assertEquals("ad_97f0e7e0-c26f-43f5-84f7-9cf0c9d6aa19", banners[0].id)
        assertEquals("سایت ساز", banners[0].title)
        assertTrue(
            "Expected resolved banner image path",
            banners[0].imageUrl.endsWith("/uploads/ad-banners/ad_banner_1795602314982_1f4523511aab.png")
        )
        assertEquals("https://kalametgame.ir/", banners[0].targetUrl)
        assertEquals("external", banners[0].linkOpenMode)
        assertEquals(1795602694437L, banners[0].updatedAtMs)
        assertEquals("ad_iso_2", banners[1].id)

        // Verify network request bypassed HTTP cache
        assertTrue(
            "Request must bypass cache via no-cache",
            activeRequestHeaders.any { it.contains("no-cache") }
        )

        // Verify serverNow clock offset was persisted and offline parsing succeeds even with skewed local clock
        assertTrue(prefs.cachedAdBannersClockOffsetMs != 0L)
        val offlineParsed = repo.parseCachedAdBanners()
        assertEquals(2, offlineParsed.size)
        assertEquals("ad_97f0e7e0-c26f-43f5-84f7-9cf0c9d6aa19", offlineParsed[0].id)

        // Verify base production domain URL resolution matches https://api.kalametgame.ir
        val resolvedProdUrl = ApiConfig.resolveBackendAssetUrl(
            "/uploads/ad-banners/ad_banner_1795602314982_1f4523511aab.png",
            ApiConfig.BASE_URL
        )
        assertEquals(
            "https://api.kalametgame.ir/uploads/ad-banners/ad_banner_1795602314982_1f4523511aab.png",
            resolvedProdUrl
        )

        // Analytics calls hit expected routes
        repo.recordAdBannerImpression("ad_97f0e7e0-c26f-43f5-84f7-9cf0c9d6aa19", "user_42")
        repo.recordAdBannerClick("ad_97f0e7e0-c26f-43f5-84f7-9cf0c9d6aa19", "user_42", "nonce_abc")
        assertTrue(recordedRequests.any { it.first == "/api/ad-banners/ad_97f0e7e0-c26f-43f5-84f7-9cf0c9d6aa19/impression" })
        assertTrue(recordedRequests.any { it.first == "/api/ad-banners/ad_97f0e7e0-c26f-43f5-84f7-9cf0c9d6aa19/click" && it.second.contains("nonce_abc") })

        // Server returning empty list clears banner display cleanly
        activeBannersResponseBody = """{"success":true,"banners":[],"serverNow":1795603100000}"""
        val emptyRes = repo.fetchActiveAdBanners() as Result.Success
        assertTrue(emptyRes.data.isEmpty())
        assertTrue(repo.parseCachedAdBanners().isEmpty())
    }

    @Test
    fun `viewModel debounces double clicks and opens validated url with configured mode`() {
        val vm = MainViewModel(ApplicationProvider.getApplicationContext())
        vm.isTickerEnabled = false

        val banner = PromoAdBanner(
            id = "ad_97f0e7e0-c26f-43f5-84f7-9cf0c9d6aa19",
            title = "سایت ساز",
            imageUrl = "https://api.kalametgame.ir/uploads/ad-banners/ad_banner_1795602314982_1f4523511aab.png",
            targetUrl = "https://kalametgame.ir/",
            linkOpenMode = "external",
            isActive = true,
            endAtMs = System.currentTimeMillis() + 86_400_000L,
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
        assertEquals("https://kalametgame.ir/", openedUrl)
        assertEquals("external", openedMode)

        // Unsafe URL banner should never trigger callback
        val badBanner = banner.copy(id = "ad_unsafe", targetUrl = "javascript:alert(1)")
        vm.onPromoBannerClick(badBanner) { _, _ ->
            openCount++
        }
        assertEquals(1, openCount)
    }

    @Test
    fun `homeScreen renders registered banner on home and click opens target url`() {
        val sampleBanner = PromoAdBanner(
            id = "ad_97f0e7e0-c26f-43f5-84f7-9cf0c9d6aa19",
            title = "سایت ساز",
            imageUrl = "https://api.kalametgame.ir/uploads/ad-banners/ad_banner_1795602314982_1f4523511aab.png",
            targetUrl = "https://kalametgame.ir/",
            linkOpenMode = "external",
            isActive = true,
            endAtMs = System.currentTimeMillis() + 86_400_000L,
            displayOrder = 1,
            updatedAtMs = 1795602694437L
        )
        val visibleIds = mutableListOf<String>()
        var clickedBanner: PromoAdBanner? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                HomeScreen(
                    user = User(
                        id = "u_1",
                        firstName = "علی",
                        lastName = "محمدی",
                        phoneNumber = "09350001122",
                        totalScore = 120
                    ),
                    cycle = null,
                    promoAdBanners = listOf(sampleBanner),
                    promoAdBannersClockOffsetMs = 0L,
                    onPromoBannerVisible = { visibleIds.add(it) },
                    onPromoBannerClick = { clickedBanner = it },
                    onNavigate = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("promo_ad_banners_section").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("promo_ad_banner_card_ad_97f0e7e0-c26f-43f5-84f7-9cf0c9d6aa19")
            .assertExists()
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()
        assertTrue(visibleIds.contains("ad_97f0e7e0-c26f-43f5-84f7-9cf0c9d6aa19"))
        assertEquals("https://kalametgame.ir/", clickedBanner?.targetUrl)
    }

    @Test
    fun `homeScreen hides banner section completely when active list is empty`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                HomeScreen(
                    user = User(
                        id = "u_1",
                        firstName = "علی",
                        lastName = "محمدی",
                        phoneNumber = "09350001122",
                        totalScore = 50
                    ),
                    cycle = null,
                    promoAdBanners = emptyList(),
                    onNavigate = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("promo_ad_banners_section").assertDoesNotExist()
    }

    @Test
    fun `promoAdBannerSection shows horizontal pager for multiple active banners`() {
        val now = System.currentTimeMillis()
        val banners = listOf(
            PromoAdBanner(
                id = "b_first",
                title = "بنر اول",
                imageUrl = "https://api.kalametgame.ir/uploads/ad-banners/1.png",
                targetUrl = "https://kalametgame.ir/1",
                isActive = true,
                endAtMs = now + 60_000L,
                displayOrder = 1
            ),
            PromoAdBanner(
                id = "b_second",
                title = "بنر دوم",
                imageUrl = "https://api.kalametgame.ir/uploads/ad-banners/2.png",
                targetUrl = "https://kalametgame.ir/2",
                isActive = true,
                endAtMs = now + 60_000L,
                displayOrder = 2
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                PromoAdBannerSection(
                    banners = banners,
                    onBannerVisible = {},
                    onBannerClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("promo_ad_banners_section").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("promo_ad_banners_pager").assertExists().assertIsDisplayed()
    }

    @Test
    fun `resilient network client initializes cleanly for image loader and api calls`() {
        val client = NetworkClientProvider.createResilientClient()
        assertNotNull(client)
        assertTrue(client.connectTimeoutMillis >= 25_000)
        assertTrue(client.readTimeoutMillis >= 35_000)
    }
}
