package com.example.billing

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import ir.mservices.market.billing.IInAppBillingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Result of a Myket In-App Billing action
 */
sealed class BillingResult<out T> {
    data class Success<out T>(val data: T) : BillingResult<T>()
    data class UnderPreparation(val message: String) : BillingResult<Nothing>()
    data class MyketNotInstalled(val message: String) : BillingResult<Nothing>()
    data class Error(val message: String, val responseCode: Int = -1) : BillingResult<Nothing>()
}

data class PurchaseData(
    val orderId: String,
    val packageName: String,
    val productId: String,
    val purchaseTime: Long,
    val purchaseToken: String,
    val developerPayload: String = "",
    val rawPurchaseData: String = "",
    val signature: String = ""
)

/**
 * Professional, secure Myket In-App Billing Manager.
 * Communicates with Myket Service via standard V3 AIDL IPC.
 * Keeps all validation secrets strictly on the backend.
 */
class MyketBillingManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val purchaseMutex = kotlinx.coroutines.sync.Mutex()

    companion object {
        private const val TAG = "MyketBillingManager"
        const val MYKET_PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCIUMmkIxGVpyKupQajOZqGfhYSkoGA0I1rhDiTLkKCiObtCnqXHJYdTJTRYKlSmA/KIIa4rC08CoZILDRjKKVt+Z3ZOt2pONjTr6jt+FtObbg7bNpTSWbcUE6Np5rz/nV/wHfQvn3wv/AJ9UFW95qAaKpwf0CZLLxG8a33BaB4dwIDAQAB"
        const val REQUEST_CODE_MYKET_PURCHASE = 1001
    }

    private val MYKET_PACKAGE = "ir.mservices.market"
    private val BILLING_SERVICE_ACTION = "ir.mservices.market.InAppBillingService.BIND"
    private val BILLING_API_VERSION = 3

    @Volatile
    private var billingService: IInAppBillingService? = null
    @Volatile
    private var isBound = false
    @Volatile
    private var isBinding = false
    @Volatile
    var isBillingReady = false
        private set

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                billingService = IInAppBillingService.Stub.asInterface(service)
                isBound = (billingService != null)
                isBinding = false
                Log.d(TAG, "ON_SERVICE_CONNECTED: Connected to Myket InAppBillingService successfully.")
            } catch (e: Exception) {
                isBinding = false
                isBound = false
                isBillingReady = false
                Log.e(TAG, "ON_SERVICE_CONNECTED_ERROR: Error binding to Myket service", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            billingService = null
            isBound = false
            isBinding = false
            isBillingReady = false
            Log.d(TAG, "ON_SERVICE_DISCONNECTED: Disconnected from Myket InAppBillingService.")
        }

        override fun onNullBinding(name: ComponentName?) {
            billingService = null
            isBound = false
            isBinding = false
            isBillingReady = false
            Log.e(TAG, "ON_NULL_BINDING: Myket service returned null binding.")
        }

        override fun onBindingDied(name: ComponentName?) {
            billingService = null
            isBound = false
            isBinding = false
            isBillingReady = false
            Log.e(TAG, "ON_BINDING_DIED: Myket service binding died.")
        }
    }

    /**
     * Connects to Myket billing service if Myket is installed.
     */
    fun startConnection() {
        if (billingService != null && isBillingReady) return
        if (!isMyketInstalled()) {
            isBillingReady = false
            Log.w(TAG, "BIND_SERVICE_SKIPPED: Myket is not installed on this device.")
            return
        }
        if (isBinding) return
        isBinding = true
        Log.d(TAG, "BIND_SERVICE_STARTED: Initiating bindService for action $BILLING_SERVICE_ACTION...")
        try {
            val intent = Intent(BILLING_SERVICE_ACTION).apply {
                setPackage(MYKET_PACKAGE)
            }
            val success = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "BIND_SERVICE_RESULT: bindService returned $success")
            if (!success) {
                isBinding = false
                isBillingReady = false
                Log.e(TAG, "BIND_SERVICE_FAILED: bindService returned false for Myket Billing Service")
            }
        } catch (e: Exception) {
            isBinding = false
            isBillingReady = false
            Log.e(TAG, "BIND_SERVICE_EXCEPTION: Failed to bind to Myket Billing Service: ${e.message}")
        }
    }

    /**
     * Unbinds from Myket billing service to prevent memory leaks.
     */
    fun endConnection() {
        if (isBound) {
            try {
                appContext.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding service: ${e.message}")
            }
            isBound = false
            isBinding = false
            isBillingReady = false
            billingService = null
        }
    }

    /**
     * Checks if Myket application is installed on the user device.
     */
    fun isMyketInstalled(): Boolean {
        return try {
            val pm = appContext.packageManager
            val packages = listOf(MYKET_PACKAGE, "com.myket.android")
            for (pkg in packages) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    return true
                } catch (e: Exception) {
                    // check next
                }
            }
            // Fallback intent check
            val intent = Intent(BILLING_SERVICE_ACTION).apply { setPackage(MYKET_PACKAGE) }
            val list = pm.queryIntentServices(intent, 0)
            !list.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launches the official Myket purchase flow with thread-safe Mutex lock.
     */
    suspend fun launchPurchaseFlow(
        activity: Activity,
        sku: String,
        isReadyInMyket: Boolean = true,
        requestCode: Int = 1001,
        developerPayload: String = ""
    ): BillingResult<Unit> = withContext(Dispatchers.IO) {
        if (purchaseMutex.isLocked) {
            Log.w(TAG, "PURCHASE_LOCKED: Another purchase flow is already in progress.")
            return@withContext BillingResult.Error("درخواست خرید دیگری در حال پردازش است. لطفاً شکیبا باشید.")
        }

        purchaseMutex.withLock {
            // 1. Check Myket installed
            if (!isMyketInstalled()) {
                isBillingReady = false
                return@withLock BillingResult.MyketNotInstalled(
                    "برنامه مایکت روی دستگاه شما یافت نشد. لطفاً برای انجام خرید درون‌برنامه‌ای، ابتدا مایکت را نصب کنید."
                )
            }

            // 2. Ensure service connection & wait if binding in progress
            startConnection()
            var service = billingService
            if (service == null) {
                var waitCount = 0
                while (billingService == null && waitCount < 30) {
                    kotlinx.coroutines.delay(100)
                    waitCount++
                }
                service = billingService
            }

            if (service == null) {
                isBillingReady = false
                Log.e(TAG, "BIND_SERVICE_TIMEOUT: Service connection failed to establish.")
                return@withLock BillingResult.Error("امکان برقراری ارتباط با سرویس پرداخت مایکت فراهم نشد. لطفاً مطمئن شوید برنامه مایکت روی دستگاه شما نصب و فعال است.")
            }

            try {
                // 3. Verify billing support
                val isSupported = service.isBillingSupported(BILLING_API_VERSION, appContext.packageName, "inapp")
                if (isSupported != 0) {
                    isBillingReady = false
                    Log.e(TAG, "BILLING_NOT_SUPPORTED: Response code $isSupported")
                    val msg = when (isSupported) {
                        3 -> "لطفاً ابتدا در اپلیکیشن مایکت وارد حساب کاربری خود شوید."
                        2 -> "اتصال به شبکه مایکت برقرار نیست. لطفاً اتصال اینترنت خود را بررسی کنید."
                        else -> "پرداخت درون‌برنامه‌ای مایکت در حساب یا دستگاه شما پشتیبانی نمی‌شود (کد $isSupported)."
                    }
                    return@withLock BillingResult.Error(msg, isSupported)
                }

                isBillingReady = true
                Log.d(TAG, "BILLING_SUPPORTED: Myket V3 Billing API is supported and ready.")

                // 4. Request buy intent from Myket
                val buyIntentBundle = service.getBuyIntent(
                    BILLING_API_VERSION,
                    appContext.packageName,
                    sku,
                    "inapp",
                    developerPayload
                )

                val responseCode = buyIntentBundle.getInt("RESPONSE_CODE", -1)
                Log.d(TAG, "GET_BUY_INTENT_RESULT: Response code $responseCode for SKU $sku")

                if (responseCode == 0) {
                    val pendingIntent = buyIntentBundle.getParcelable<android.app.PendingIntent>("BUY_INTENT")
                    if (pendingIntent != null) {
                        Log.d(TAG, "PAYMENT_INTENT_LAUNCHED: Launching Myket payment intent sender for SKU $sku")
                        withContext(Dispatchers.Main) {
                            activity.startIntentSenderForResult(
                                pendingIntent.intentSender,
                                requestCode,
                                Intent(),
                                0,
                                0,
                                0
                            )
                        }
                        return@withLock BillingResult.Success(Unit)
                    } else {
                        Log.e(TAG, "GET_BUY_INTENT_ERROR: BUY_INTENT bundle parcelable is null")
                        return@withLock BillingResult.Error("اطلاعات اتصال به درگاه پرداخت مایکت دریافت نشد.")
                    }
                } else if (responseCode == 3) {
                    return@withLock BillingResult.Error("لطفاً ابتدا در اپلیکیشن مایکت وارد حساب کاربری خود شوید.", responseCode)
                } else if (responseCode == 4) {
                    return@withLock BillingResult.Error("این محصول در مایکت یافت نشد یا در حالت آماده‌سازی است.", responseCode)
                } else if (responseCode == 7) {
                    return@withLock BillingResult.Error("این محصول قبلاً خریداری شده اما هنوز مصرف نشده است. گزینه «بازیابی خریدهای قبلی» را بزنید.", responseCode)
                } else {
                    return@withLock BillingResult.Error("خطا در ایجاد تراکنش خرید مایکت (کد $responseCode).", responseCode)
                }
            } catch (e: Exception) {
                Log.e(TAG, "PURCHASE_FLOW_EXCEPTION: Exception during purchase flow", e)
                return@withLock BillingResult.Error("خطا در ارتباط با مایکت: ${e.message}")
            }
        }
    }

    /**
     * Parses purchase result from Activity.onActivityResult intent data
     */
    fun parsePurchaseResult(resultCode: Int, data: Intent?): PurchaseData? {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return null
        }
        val purchaseDataString = data.getStringExtra("INAPP_PURCHASE_DATA") ?: return null
        val signature = data.getStringExtra("INAPP_DATA_SIGNATURE") ?: ""

        // RSA Signature Security Verification against Myket Public Key
        if (signature.isNotBlank() && !verifyPurchaseSignature(purchaseDataString, signature)) {
            Log.e(TAG, "Myket purchase RSA signature verification failed!")
            return null
        }

        return try {
            val json = JSONObject(purchaseDataString)
            PurchaseData(
                orderId = json.optString("orderId", "MYKET_ORD_" + System.currentTimeMillis()),
                packageName = json.optString("packageName", appContext.packageName),
                productId = json.optString("productId"),
                purchaseTime = json.optLong("purchaseTime", System.currentTimeMillis()),
                purchaseToken = json.optString("purchaseToken", json.optString("token")),
                developerPayload = json.optString("developerPayload", ""),
                rawPurchaseData = purchaseDataString,
                signature = signature
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing purchase JSON", e)
            null
        }
    }

    /**
     * Verifies RSA SHA1withRSA signature against Myket Base64 Public Key
     */
    fun verifyPurchaseSignature(signedData: String, signature: String): Boolean {
        if (signedData.isBlank() || signature.isBlank()) return false
        return try {
            val keyBytes = android.util.Base64.decode(MYKET_PUBLIC_KEY, android.util.Base64.DEFAULT)
            val keySpec = java.security.spec.X509EncodedKeySpec(keyBytes)
            val keyFactory = java.security.KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)
            val sig = java.security.Signature.getInstance("SHA1withRSA")
            sig.initVerify(publicKey)
            sig.update(signedData.toByteArray())
            sig.verify(android.util.Base64.decode(signature, android.util.Base64.DEFAULT))
        } catch (e: Exception) {
            Log.e(TAG, "RSA Signature verification exception: ${e.message}")
            false
        }
    }

    /**
     * Queries unconsumed purchases to support purchase restoration.
     */
    suspend fun queryPurchases(): List<PurchaseData> = withContext(Dispatchers.IO) {
        startConnection()
        var service = billingService
        if (service == null) {
            var waitCount = 0
            while (billingService == null && waitCount < 20) {
                kotlinx.coroutines.delay(100)
                waitCount++
            }
            service = billingService
        }
        val currentService = service ?: return@withContext emptyList()
        val results = mutableListOf<PurchaseData>()
        try {
            var continuationToken: String? = null
            do {
                val ownedItems = currentService.getPurchases(BILLING_API_VERSION, appContext.packageName, "inapp", continuationToken)
                val responseCode = ownedItems.getInt("RESPONSE_CODE", -1)
                if (responseCode == 0) {
                    val purchaseDataList = ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST")
                    val signatureList = ownedItems.getStringArrayList("INAPP_DATA_SIGNATURE_LIST")
                    if (purchaseDataList != null) {
                        for (i in purchaseDataList.indices) {
                            val raw = purchaseDataList[i]
                            val sig = signatureList?.getOrNull(i) ?: ""
                            if (sig.isNotBlank() && !verifyPurchaseSignature(raw, sig)) {
                                Log.w(TAG, "Query purchase signature mismatch, skipping item: $raw")
                                continue
                            }
                            try {
                                val json = JSONObject(raw)
                                results.add(
                                    PurchaseData(
                                        orderId = json.optString("orderId"),
                                        packageName = json.optString("packageName"),
                                        productId = json.optString("productId"),
                                        purchaseTime = json.optLong("purchaseTime"),
                                        purchaseToken = json.optString("purchaseToken", json.optString("token")),
                                        developerPayload = json.optString("developerPayload"),
                                        rawPurchaseData = raw,
                                        signature = sig
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Parse single purchase error", e)
                            }
                        }
                    }
                    continuationToken = ownedItems.getString("INAPP_CONTINUATION_TOKEN")
                } else {
                    break
                }
            } while (!continuationToken.isNullOrEmpty())
        } catch (e: Exception) {
            Log.e(TAG, "Error querying purchases", e)
        }
        results
    }

    /**
     * Consumes purchase so non-consumable lock is released in Myket.
     */
    suspend fun consumePurchase(purchaseToken: String): Boolean = withContext(Dispatchers.IO) {
        startConnection()
        var service = billingService
        if (service == null) {
            var waitCount = 0
            while (billingService == null && waitCount < 20) {
                kotlinx.coroutines.delay(100)
                waitCount++
            }
            service = billingService
        }
        val currentService = service ?: return@withContext false
        try {
            val response = currentService.consumePurchase(BILLING_API_VERSION, appContext.packageName, purchaseToken)
            response == 0
        } catch (e: Exception) {
            Log.e(TAG, "Consume purchase exception", e)
            false
        }
    }
}
