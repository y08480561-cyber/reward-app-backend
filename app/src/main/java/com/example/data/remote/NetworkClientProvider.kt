package com.example.data.remote

import android.util.Log
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLException

/**
 * Diagnostic categorization for network and connectivity events.
 * Provides clear, actionable insights for cellular operator issues (e.g. Hamrah Aval / MCI)
 * without ever leaking sensitive tokens or phone numbers.
 */
enum class NetworkErrorCategory(val code: String, val titlePersian: String) {
    DNS_RESOLUTION_FAILURE("DNS_ERROR", "خطای تحلیل آدرس سرور (DNS)"),
    TIMEOUT_FAILURE("TIMEOUT_ERROR", "پایان مهلت زمانی ارتباط (Timeout)"),
    TLS_HANDSHAKE_FAILURE("TLS_ERROR", "خطای دست‌دهی امن TLS/گواهی امنیتی"),
    TCP_CONNECT_FAILURE("CONNECT_ERROR", "عدم امکان برقراری ارتباط TCP با سرور"),
    HTTP_CLIENT_ERROR("HTTP_4XX", "خطای سمت کلاینت یا احراز هویت"),
    HTTP_SERVER_ERROR("HTTP_5XX", "خطای سمت سرور یا گیت‌وی Render"),
    GENERAL_NETWORK_ERROR("NET_UNKNOWN", "خطای عمومی شبکه")
}

data class ClassifiedNetworkError(
    val category: NetworkErrorCategory,
    val technicalMessage: String,
    val userFacingMessage: String,
    val recommendedAction: String
)

/**
 * Custom DNS implementation that prioritizes IPv4 over IPv6.
 *
 * Motivation for Hamrah Aval (MCI):
 * MCI cellular APNs frequently issue dual-stack IPv4/IPv6 addresses, but international
 * IPv6 routes to Render/Cloudflare CDNs can experience severe packet dropping or blackholing.
 * Prioritizing IPv4 addresses prevents OkHttp from getting stuck waiting for dead IPv6 SYN packets,
 * while still maintaining IPv6 as a fallback.
 */
class ResilientDns : Dns {
    companion object {
        private const val TAG = "ResilientDns"
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val resolved = try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            Log.w(TAG, "DNS resolution failed for hostname: $hostname (${e.javaClass.simpleName})")
            throw e
        }

        // Sort so that IPv4 addresses (Inet4Address) appear before IPv6 addresses (Inet6Address)
        return resolved.sortedWith(compareBy { it !is Inet4Address })
    }
}

/**
 * Sanitizing interceptor: Logs network diagnostics (URL path, response code, latency)
 * while strictly omitting request/response bodies, query parameters with secrets, and authorization tokens.
 */
class SafeLoggingInterceptor : Interceptor {
    companion object {
        private const val TAG = "NetworkDiagnostics"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val host = request.url.host
        val method = request.method
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "-> $method https://$host$path")

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val classified = classifyNetworkException(e)
            Log.w(TAG, "<- FAILED $method https://$host$path in ${duration}ms [${classified.category.code}] ${e.javaClass.simpleName}")
            throw e
        }

        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "<- ${response.code} $method https://$host$path (${duration}ms)")
        return response
    }
}

/**
 * Analyzes exceptions to identify whether the root cause is DNS, Timeout, TLS Handshake, or Connection drop.
 * Contains tailored Persian advice for Hamrah Aval (MCI) users.
 */
fun classifyNetworkException(e: Throwable): ClassifiedNetworkError {
    return when (e) {
        is UnknownHostException -> ClassifiedNetworkError(
            category = NetworkErrorCategory.DNS_RESOLUTION_FAILURE,
            technicalMessage = "DNS lookup failed for remote host: ${e.message?.take(60)}",
            userFacingMessage = "خطا در پیدا کردن آدرس سرور (DNS). ارتباط شبکه اینترنت شما برقرار نیست یا دامنه مسدود است.",
            recommendedAction = "در همراه اول: اتصال داده را خاموش و روشن کنید یا از یک DNS معتبر (مانند 1.1.1.1 یا 8.8.8.8) یا VPN پایدار استفاده نمایید."
        )
        is SocketTimeoutException -> ClassifiedNetworkError(
            category = NetworkErrorCategory.TIMEOUT_FAILURE,
            technicalMessage = "Socket timeout during connection/read: ${e.message?.take(60)}",
            userFacingMessage = "مهلت زمانی اتصال به پایان رسید (Timeout). سرور در زمان مقرر پاسخ نداد.",
            recommendedAction = "سرورهای ابری Render در صورت عدم استفاده موقتاً به حالت خواب می‌روند و بیدار شدن آن‌ها تا ۴۰ ثانیه طول می‌کشد. لطفاً ۲۰ ثانیه دیگر دوباره امتحان کنید."
        )
        is SSLHandshakeException,
        is SSLException -> ClassifiedNetworkError(
            category = NetworkErrorCategory.TLS_HANDSHAKE_FAILURE,
            technicalMessage = "TLS/SSL Handshake failure: ${e.message?.take(60)}",
            userFacingMessage = "خطا در برقراری ارتباط امن با سرور (TLS Handshake).",
            recommendedAction = "اپراتور همراه اول ممکن است پروتکل امن به دامنه onrender.com را مختل کرده باشد. فعال‌سازی یک فیلترشکن/VPN با پروتکل استاندارد این مشکل را برطرف می‌کند."
        )
        is ConnectException -> ClassifiedNetworkError(
            category = NetworkErrorCategory.TCP_CONNECT_FAILURE,
            technicalMessage = "TCP Connection refused or unreachable: ${e.message?.take(60)}",
            userFacingMessage = "سرور در دسترس نیست یا اتصال توسط شبکه قطع شد.",
            recommendedAction = "پایداری اینترنت سیم‌کارت خود را بررسی کنید یا حالت پرواز را برای چند ثانیه فعال و غیرفعال کنید."
        )
        else -> ClassifiedNetworkError(
            category = NetworkErrorCategory.GENERAL_NETWORK_ERROR,
            technicalMessage = "${e.javaClass.simpleName}: ${e.message?.take(60)}",
            userFacingMessage = "خطای غیرمنتظره در ارتباط با سرور رخ داد.",
            recommendedAction = "اتصال اینترنت را بررسی و مجدداً تلاش کنید."
        )
    }
}

/**
 * Provides a resilient OkHttpClient configured specifically for high-latency cellular networks,
 * Render cloud cold starts, and operator TLS compatibility.
 */
object NetworkClientProvider {

    fun createResilientClient(): OkHttpClient {
        return OkHttpClient.Builder()
            // Generous timeouts to withstand Render free-tier cold starts (spins up in ~30-45s)
            .connectTimeout(35, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            // Enable retry on connection drop/reset
            .retryOnConnectionFailure(true)
            // Support both modern TLS and compatible TLS specifications
            .connectionSpecs(
                listOf(
                    ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS
                )
            )
            // Support HTTP/2 with transparent HTTP/1.1 fallback
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            // Prioritize IPv4 over IPv6 to avoid Hamrah Aval IPv6 packet drops
            .dns(ResilientDns())
            // Safe logging without leaking sensitive data
            .addInterceptor(SafeLoggingInterceptor())
            .build()
    }
}
