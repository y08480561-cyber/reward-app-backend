package com.example.data.remote

import com.example.BuildConfig
import java.net.URI

/**
 * Centralized backend configuration for «رقابت کن، جایزه ببر».
 * Supports staged Cloudflare Workers test environment (api-test.kalametgame.ir)
 * while preserving the production custom domain (api.kalametgame.ir) and local test servers.
 */
object ApiConfig {
    const val BASE_URL = "https://api.kalametgame.ir"
    const val API_BASE_URL = "$BASE_URL/api"

    val CLOUDFLARE_TEST_BASE_URL: String = BuildConfig.API_TEST_URL
    val PRODUCTION_BASE_URL: String = BuildConfig.API_PRODUCTION_URL
    val LEGACY_RENDER_FALLBACK_URL: String = BuildConfig.LEGACY_RENDER_FALLBACK_URL
    val USE_TEST_BACKEND: Boolean = BuildConfig.USE_CLOUDFLARE_TEST_API

    val ACTIVE_BASE_URL: String
        get() = if (USE_TEST_BACKEND) CLOUDFLARE_TEST_BASE_URL else BASE_URL

    val ACTIVE_API_BASE_URL: String
        get() = "$ACTIVE_BASE_URL/api"

    fun activeHost(): String = try {
        URI(ACTIVE_BASE_URL).host ?: "api-test.kalametgame.ir"
    } catch (_: Exception) {
        "api-test.kalametgame.ir"
    }

    private val LEGACY_HOST_MARKERS = listOf(
        "onrender.com",
        "reward-app-backend-1.onrender.com",
        "reward-app-backend-a9ze.onrender.com"
    )

    fun isLegacyServerHost(urlOrHost: String): Boolean {
        val lower = urlOrHost.lowercase()
        return LEGACY_HOST_MARKERS.any { lower.contains(it) }
    }

    fun isLocalTestUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.startsWith("http://localhost") ||
            lower.startsWith("https://localhost") ||
            lower.startsWith("http://127.0.0.1") ||
            lower.startsWith("https://127.0.0.1")
    }

    /**
     * Normalizes a server base URL, automatically migrating legacy Render/cleartext URLs
     * to the active HTTPS backend while preserving explicit HTTPS domains and local test servers.
     */
    fun normalizeBaseUrl(rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty().trimEnd('/')
        if (trimmed.isBlank()) return ACTIVE_BASE_URL
        if (isLegacyServerHost(trimmed)) return ACTIVE_BASE_URL
        if (trimmed.contains("ais-dev", ignoreCase = true) || trimmed.contains("ais-pre", ignoreCase = true)) {
            return ACTIVE_BASE_URL
        }

        val clean = if (trimmed.endsWith("/api")) {
            trimmed.substringBeforeLast("/api").trimEnd('/')
        } else {
            trimmed
        }

        if (isLocalTestUrl(clean)) return clean
        if (!clean.startsWith("https://")) return ACTIVE_BASE_URL
        return clean
    }

    /**
     * Normalizes the persisted client API endpoint: when Cloudflare test mode is active,
     * stored Render or legacy production URLs are routed to CLOUDFLARE_TEST_BASE_URL
     * while local MockWebServer URLs remain untouched.
     */
    fun normalizeConfiguredServerUrl(rawUrl: String?): String {
        val normalized = normalizeBaseUrl(rawUrl)
        if (isLocalTestUrl(normalized)) return normalized
        if (USE_TEST_BACKEND && normalized.equals(BASE_URL, ignoreCase = true)) {
            return ACTIVE_BASE_URL
        }
        return normalized
    }

    /**
     * Resolves relative backend paths (e.g., /uploads/banner.jpg) or legacy backend asset URLs
     * to the active backend base URL while keeping external third-party URLs intact.
     */
    fun resolveBackendAssetUrl(rawUrl: String?, baseUrl: String = ACTIVE_BASE_URL): String {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isBlank()) return ""

        val effectiveBase = normalizeBaseUrl(baseUrl)
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return try {
                val uri = URI(trimmed)
                val host = uri.host.orEmpty()
                if (isLegacyServerHost(host)) {
                    val pathPart = uri.rawPath.orEmpty()
                    val queryPart = uri.rawQuery?.let { "?$it" }.orEmpty()
                    "$effectiveBase$pathPart$queryPart"
                } else {
                    trimmed
                }
            } catch (_: Exception) {
                trimmed
            }
        }

        return if (trimmed.startsWith("/")) {
            "$effectiveBase$trimmed"
        } else {
            "$effectiveBase/$trimmed"
        }
    }
}
