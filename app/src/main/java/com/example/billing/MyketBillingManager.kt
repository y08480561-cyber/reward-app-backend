package com.example.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import ir.myket.billingclient.IabHelper
import ir.myket.billingclient.util.IabResult
import ir.myket.billingclient.util.Inventory
import ir.myket.billingclient.util.Purchase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

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
 * Official Myket Billing SDK v2.2 Implementation.
 * Uses ir.myket.billingclient.util.IabHelper for secure and reliable purchases.
 */
class MyketBillingManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val connectionMutex = Mutex()
    private val purchaseMutex = Mutex()
    private var iabHelper: IabHelper? = null
    
    @Volatile
    var isBillingReady = false
        private set

    @Volatile
    private var isConnecting = false

    companion object {
        private const val TAG = "MyketBillingManager"
        const val MYKET_PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCIUMmkIxGVpyKupQajOZqGfhYSkoGA0I1rhDiTLkKCiObtCnqXHJYdTJTRYKlSmA/KIIa4rC08CoZILDRjKKVt+Z3ZOt2pONjTr6jt+FtObbg7bNpTSWbcUE6Np5rz/nV/wHfQvn3wv/AJ9UFW95qAaKpwf0CZLLxG8a33BaB4dwIDAQAB"
        const val REQUEST_CODE_MYKET_PURCHASE = 1001
        
        const val SKU_SQUARES_50 = "reqabat_unlock_50"
        const val SKU_SQUARES_100 = "reqabat_unlock_100"
        const val SKU_UPGRADE_SILVER = "reqabat_upgrade_silver"
        const val SKU_UPGRADE_GOLD = "reqabat_upgrade_gold"
        
        val ALL_SKUS = listOf(SKU_SQUARES_50, SKU_SQUARES_100, SKU_UPGRADE_SILVER, SKU_UPGRADE_GOLD)
    }

    /**
     * Initializes the Myket Billing SDK safely.
     */
    fun startConnection() {
        if (isBillingReady || isConnecting) return
        
        // Use a coroutine on Main thread to handle the async setup start
        // This is safe to call from any thread
        Handler(Looper.getMainLooper()).post {
            if (isBillingReady || isConnecting) return@post
            isConnecting = true
            
            Log.d(TAG, "BIND_SERVICE_STARTED: Initializing Myket SDK v2.2...")
            
            try {
                if (iabHelper == null) {
                    iabHelper = IabHelper(appContext, MYKET_PUBLIC_KEY)
                    iabHelper?.enableDebugLogging(true)
                }
                
                iabHelper?.startSetup { result ->
                    isConnecting = false
                    Log.d(TAG, "BIND_SERVICE_RESULT: Setup result: ${result.message} (Success: ${result.isSuccess})")
                    if (result.isSuccess) {
                        isBillingReady = true
                        Log.d(TAG, "ON_SERVICE_CONNECTED: Myket Billing Service connected and ready.")
                    } else {
                        isBillingReady = false
                        Log.e(TAG, "BIND_SERVICE_FAILED: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                isConnecting = false
                isBillingReady = false
                Log.e(TAG, "Error starting connection", e)
            }
        }
    }

    /**
     * Safely closes the IabHelper connection.
     */
    fun endConnection() {
        Log.d(TAG, "ON_SERVICE_DISCONNECTED: Disposing Myket IabHelper.")
        try {
            iabHelper?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "Error during dispose: ${e.message}")
        }
        iabHelper = null
        isBillingReady = false
        isConnecting = false
    }

    /**
     * Checks if Myket app is installed.
     */
    fun isMyketInstalled(): Boolean {
        return try {
            val pm = appContext.packageManager
            pm.getPackageInfo("ir.mservices.market", 0)
            true
        } catch (e: Exception) {
            try {
                appContext.packageManager.getPackageInfo("com.myket.android", 0)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /**
     * Ensures connection is established before performing billing operations.
     */
    private suspend fun ensureConnected(): Boolean {
        if (isBillingReady) return true
        startConnection()
        
        var waitCount = 0
        while ((isConnecting || !isBillingReady) && waitCount < 50) {
            kotlinx.coroutines.delay(100)
            waitCount++
            if (isBillingReady) return true
        }
        return isBillingReady
    }

    /**
     * Queries prices and titles of the products from Myket.
     */
    suspend fun queryInventory(): Inventory? = withContext(Dispatchers.IO) {
        if (!ensureConnected()) return@withContext null
        val helper = iabHelper ?: return@withContext null
        
        suspendCancellableCoroutine { continuation ->
            try {
                helper.queryInventoryAsync(true, ALL_SKUS) { result, inv ->
                    if (result.isSuccess) {
                        Log.d(TAG, "BILLING_SUPPORTED: Inventory queried successfully.")
                        if (continuation.isActive) continuation.resume(inv)
                    } else {
                        Log.e(TAG, "Query inventory failed: ${result.message}")
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during queryInventoryAsync", e)
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    /**
     * Launches the purchase flow for a specific SKU.
     */
    suspend fun launchPurchaseFlow(
        activity: Activity,
        sku: String,
        isReadyInMyket: Boolean = true,
        requestCode: Int = REQUEST_CODE_MYKET_PURCHASE,
        developerPayload: String = ""
    ): BillingResult<PurchaseData?> = withContext(Dispatchers.IO) {
        purchaseMutex.withLock {
            if (!isReadyInMyket) {
                return@withLock BillingResult.UnderPreparation("این بسته در حال آماده‌سازی در مایکت است.")
            }
            
            if (!isMyketInstalled()) {
                return@withLock BillingResult.MyketNotInstalled("لطفاً ابتدا برنامه مایکت را نصب کنید.")
            }

            if (!ensureConnected()) {
                return@withLock BillingResult.Error("امکان برقراری ارتباط با مایکت فراهم نشد. لطفاً وارد حساب مایکت خود شوید.")
            }

            val helper = iabHelper ?: run {
                return@withLock BillingResult.Error("در حال برقراری ارتباط با مایکت... لطفاً لحظاتی دیگر تلاش کنید.")
            }

            suspendCancellableCoroutine<BillingResult<PurchaseData?>> { continuation ->
                Handler(Looper.getMainLooper()).post {
                    Log.d(TAG, "PAYMENT_INTENT_LAUNCHED: Starting purchase flow for $sku")
                    try {
                        helper.launchPurchaseFlow(activity, sku, { result, purchase ->
                            Log.d(TAG, "GET_BUY_INTENT_RESULT: Finished with message: ${result.message}")
                            if (result.isSuccess && purchase != null) {
                                val pData = PurchaseData(
                                    orderId = purchase.orderId ?: "",
                                    packageName = purchase.packageName,
                                    productId = purchase.sku,
                                    purchaseTime = purchase.purchaseTime,
                                    purchaseToken = purchase.token,
                                    developerPayload = purchase.developerPayload ?: "",
                                    rawPurchaseData = purchase.originalJson,
                                    signature = purchase.signature
                                )
                                if (continuation.isActive) continuation.resume(BillingResult.Success(pData))
                            } else if (result.response == 1) { // User canceled
                                if (continuation.isActive) continuation.resume(BillingResult.Error("عملیات توسط کاربر لغو شد.", result.response))
                            } else {
                                if (continuation.isActive) continuation.resume(BillingResult.Error(result.message, result.response))
                            }
                        }, developerPayload)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error launching purchase flow", e)
                        if (continuation.isActive) continuation.resume(BillingResult.Error("خطا در اجرای فرایند خرید: ${e.message}"))
                    }
                }
            }
        }
    }

    /**
     * Extracts purchase data from the intent (legacy support).
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): PurchaseData? {
        var pData: PurchaseData? = null
        if (requestCode == REQUEST_CODE_MYKET_PURCHASE && data != null) {
            val responseCode = data.getIntExtra("RESPONSE_CODE", 0)
            val purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA")
            val dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE")
            
            if (resultCode == Activity.RESULT_OK && responseCode == 0 && purchaseData != null) {
                try {
                    val json = org.json.JSONObject(purchaseData)
                    pData = PurchaseData(
                        orderId = json.optString("orderId"),
                        packageName = json.optString("packageName"),
                        productId = json.optString("productId"),
                        purchaseTime = json.optLong("purchaseTime"),
                        purchaseToken = json.optString("purchaseToken", json.optString("token")),
                        developerPayload = json.optString("developerPayload"),
                        rawPurchaseData = purchaseData,
                        signature = dataSignature ?: ""
                    )
                    Log.d(TAG, "Purchase result parsed successfully: ${pData.productId}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing purchase data", e)
                }
            }
        }
        return pData
    }

    /**
     * Queries unconsumed purchases for restoration.
     */
    suspend fun queryPurchases(): List<PurchaseData> = withContext(Dispatchers.IO) {
        if (!ensureConnected()) return@withContext emptyList()
        val helper = iabHelper ?: return@withContext emptyList()

        suspendCancellableCoroutine { continuation ->
            try {
                helper.queryInventoryAsync(true, ALL_SKUS) { result, inv ->
                    if (result.isSuccess && inv != null) {
                        val list = inv.allPurchases.map { p ->
                            PurchaseData(
                                orderId = p.orderId ?: "",
                                packageName = p.packageName,
                                productId = p.sku,
                                purchaseTime = p.purchaseTime,
                                purchaseToken = p.token,
                                developerPayload = p.developerPayload ?: "",
                                rawPurchaseData = p.originalJson,
                                signature = p.signature
                            )
                        }
                        if (continuation.isActive) continuation.resume(list)
                    } else {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during queryPurchases", e)
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }
    }

    /**
     * Consumes a consumable product.
     */
    suspend fun consumePurchase(purchaseToken: String): Boolean = withContext(Dispatchers.IO) {
        if (!ensureConnected()) return@withContext false
        val helper = iabHelper ?: return@withContext false

        try {
            val p = Purchase("inapp", "{\"purchaseToken\":\"$purchaseToken\",\"token\":\"$purchaseToken\"}", "")
            suspendCancellableCoroutine { continuation ->
                helper.consumeAsync(p) { purchase, result ->
                    Log.d(TAG, "Consume result: ${result.message} for token: ${purchase.token}")
                    if (continuation.isActive) continuation.resume(result.isSuccess)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during consumePurchase", e)
            false
        }
    }
}
