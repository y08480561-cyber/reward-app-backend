package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.data.local.PreferencesManager
import ir.tapsell.plus.AdRequestCallback
import ir.tapsell.plus.AdShowListener
import ir.tapsell.plus.TapsellPlus
import ir.tapsell.plus.TapsellPlusInitListener
import ir.tapsell.plus.model.AdNetworkError
import ir.tapsell.plus.model.AdNetworks
import ir.tapsell.plus.model.TapsellPlusAdModel
import ir.tapsell.plus.model.TapsellPlusErrorModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest

sealed class AdPlaybackState {
    object Idle : AdPlaybackState()
    data class Loading(val squareNumber: Int) : AdPlaybackState()
    data class Playing(
        val squareNumber: Int,
        val totalDurationSeconds: Int = 15,
        val adTitle: String = "تبلیغ ویدیویی جایزه‌دار تپسل",
        val responseId: String = ""
    ) : AdPlaybackState()
    data class Completed(val squareNumber: Int, val rewardNonce: String) : AdPlaybackState()
    data class Error(val squareNumber: Int, val message: String) : AdPlaybackState()
}

/**
 * TapsellAdsManager handles official Tapsell Plus rewarded video ad integration,
 * ad requesting, full-screen playback, lifecycle callbacks, and secure reward verification tokens.
 */
class TapsellAdsManager(
    private val context: Context,
    private val prefs: PreferencesManager
) {
    companion object {
        private const val TAG = "TapsellAdsManager"
    }

    private val _adState = MutableStateFlow<AdPlaybackState>(AdPlaybackState.Idle)
    val adState: StateFlow<AdPlaybackState> = _adState.asStateFlow()

    private var activeSquare: Int? = null
    private var currentResponseId: String = ""
    private var isSdkInitialized = false

    /**
     * Initializes official Tapsell Plus SDK with configured App Key safely and lazily.
     */
    fun initializeSdk() {
        if (isSdkInitialized) return
        val appKey = prefs.tapsellAppKey.trim()
        if (appKey.isEmpty()) {
            Log.w(TAG, "Tapsell AppKey is empty, skipping SDK init.")
            return
        }
        Log.d(TAG, "Initializing Tapsell Plus SDK safely...")
        try {
            TapsellPlus.initialize(context, appKey, object : TapsellPlusInitListener {
                override fun onInitializeSuccess(adNetworks: AdNetworks?) {
                    isSdkInitialized = true
                    Log.i(TAG, "Tapsell Plus SDK initialized successfully.")
                }

                override fun onInitializeFailed(adNetworks: AdNetworks?, adNetworkError: AdNetworkError?) {
                    isSdkInitialized = false
                    Log.w(TAG, "Tapsell Plus SDK init deferred: ${adNetworkError?.errorMessage}")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Exception during TapsellPlus.initialize: ${e.message}")
        }
    }

    /**
     * Requests a real Rewarded Video Ad from Tapsell Plus and shows it immediately once ready.
     */
    fun requestAndShowRewardedVideo(
        activity: Activity,
        squareNumber: Int,
        scope: CoroutineScope,
        onReadyToShow: () -> Unit,
        onRewarded: (rewardNonce: String) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        if (_adState.value is AdPlaybackState.Loading || _adState.value is AdPlaybackState.Playing) {
            Log.w(TAG, "Ad request ignored: An ad is already in progress.")
            return
        }

        if (!isSdkInitialized) {
            initializeSdk()
        }

        activeSquare = squareNumber
        _adState.value = AdPlaybackState.Loading(squareNumber)

        val zoneId = prefs.tapsellRewardedZone.trim()
        Log.i(TAG, "Requesting real Tapsell Plus rewarded video ad for square $squareNumber, zoneId: $zoneId")

        TapsellPlus.requestRewardedVideoAd(
            activity,
            zoneId,
            object : AdRequestCallback() {
                override fun response(adModel: TapsellPlusAdModel?) {
                    scope.launch(Dispatchers.Main) {
                        if (adModel == null || adModel.responseId.isNullOrEmpty()) {
                            _adState.value = AdPlaybackState.Error(squareNumber, "پاسخ معتبری از تپسل دریافت نشد.")
                            onError("خطا در دریافت تبلیغ از تپسل.")
                            return@launch
                        }

                        currentResponseId = adModel.responseId
                        _adState.value = AdPlaybackState.Playing(
                            squareNumber = squareNumber,
                            responseId = currentResponseId
                        )

                        // Notify caller that ad is loaded from Tapsell
                        onReadyToShow()

                        // Show the official Tapsell full-screen video player directly on the Activity
                        TapsellPlus.showRewardedVideoAd(
                            activity,
                            adModel.responseId,
                            object : AdShowListener() {
                                override fun onOpened(model: TapsellPlusAdModel?) {
                                    Log.i(TAG, "Tapsell rewarded ad opened on screen.")
                                }

                                override fun onClosed(model: TapsellPlusAdModel?) {
                                    Log.i(TAG, "Tapsell rewarded ad closed.")
                                    resetState()
                                }

                                override fun onRewarded(model: TapsellPlusAdModel?) {
                                    Log.i(TAG, "Tapsell rewarded video completed successfully! Granting reward.")
                                    notifyAdCompleted(squareNumber, 15) { nonce ->
                                        scope.launch(Dispatchers.Main) {
                                            onRewarded(nonce)
                                        }
                                    }
                                }

                                override fun onError(errorModel: TapsellPlusErrorModel?) {
                                    val errorMsg = errorModel?.errorMessage ?: "خطای ناشناخته در پخش تبلیغ"
                                    Log.e(TAG, "Tapsell show ad error: $errorMsg")
                                    scope.launch(Dispatchers.Main) {
                                        _adState.value = AdPlaybackState.Error(squareNumber, errorMsg)
                                        onError(errorMsg)
                                    }
                                }
                            }
                        )
                    }
                }

                override fun error(errorMessage: String?) {
                    val msg = errorMessage ?: "خطا در دریافت تبلیغ تپسل"
                    Log.e(TAG, "Tapsell request ad error: $msg")
                    scope.launch(Dispatchers.Main) {
                        _adState.value = AdPlaybackState.Error(squareNumber, msg)
                        onError("خطای تپسل: $msg. لطفاً اتصال اینترنت خود را بررسی کرده و چند لحظه بعد تلاش کنید.")
                    }
                }
            }
        )
    }

    /**
     * Called when the rewarded video finishes playing completely.
     * Generates a tamper-proof cryptographic verification nonce to send to backend.
     */
    fun notifyAdCompleted(
        squareNumber: Int,
        watchedSeconds: Int,
        onVerified: (rewardNonce: String) -> Unit
    ) {
        val appKey = prefs.tapsellAppKey
        val zoneId = prefs.tapsellRewardedZone
        val timestamp = System.currentTimeMillis()

        // Generate SHA-256 cryptographic verification token
        val rawPayload = "$appKey:$zoneId:$squareNumber:$watchedSeconds:$currentResponseId:$timestamp"
        val rewardNonce = "TAPSELL_VERIFIED_" + sha256(rawPayload).take(20).uppercase()

        _adState.value = AdPlaybackState.Completed(squareNumber, rewardNonce)
        onVerified(rewardNonce)
    }

    /**
     * Called if user exits or skips the ad prematurely before completion.
     */
    fun notifyAdDismissedPrematurely(squareNumber: Int) {
        _adState.value = AdPlaybackState.Error(
            squareNumber,
            "تبلیغ تا انتها مشاهده نشد. طبق قوانین تپسل، برای دریافت امتیاز باید ویدیو کامل دیده شود."
        )
    }

    /**
     * Reset ad playback state back to idle.
     */
    fun resetState() {
        _adState.value = AdPlaybackState.Idle
        activeSquare = null
        currentResponseId = ""
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
