package com.example.data.remote

import java.net.URI

/**
 * Centralized production backend configuration for «رقابت کن، جایزه ببر».
 * Single source of truth for the backend Base URL and backend-hosted asset URLs.
 */
object ApiConfig {
    const val BASE_URL = "https://api.kalametgame.ir"
    const val API_BASE_URL = "$BASE_URL/api"

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
     * Normalizes a server base URL to enforce production HTTPS on api.kalametgame.ir,
     * automatically migrating legacy Render URLs while preserving local test servers.
     */
    fun normalizeBaseUrl(rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty().trimEnd('/')
        if (trimmed.isBlank()) return BASE_URL
        if (isLegacyServerHost(trimmed)) return BASE_URL
        if (trimmed.contains("ais-dev", ignoreCase = true) || trimmed.contains("ais-pre", ignoreCase = true)) {
            return BASE_URL
        }

        val clean = if (trimmed.endsWith("/api")) {
            trimmed.substringBeforeLast("/api").trimEnd('/')
        } else {
            trimmed
        }

        if (isLocalTestUrl(clean)) return clean
        if (!clean.startsWith("https://")) return BASE_URL
        return clean
    }

    /**
     * Resolves relative backend paths (e.g., /uploads/banner.jpg) or legacy backend asset URLs
     * to https://api.kalametgame.ir while keeping external third-party URLs intact.
     */
    fun resolveBackendAssetUrl(rawUrl: String?, baseUrl: String = BASE_URL): String {
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
