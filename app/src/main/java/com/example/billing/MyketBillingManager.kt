package com.example.billing

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import ir.mservices.market.billing.IInAppBillingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
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
 * Myket Billing Implementation compliant with SDK v2.2 Standards.
 * Uses AIDL for reliable connection while providing high-level SDK capabilities.
 */
class MyketBillingManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val purchaseMutex = Mutex()

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
            billingService = IInAppBillingService.Stub.asInterface(service)
            isBound = (billingService != null)
            isBinding = false
            if (isBound) {
                Log.d(TAG, "ON_SERVICE_CONNECTED: Myket Billing Service connected.")
                checkBillingSupport()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            billingService = null
            isBound = false
            isBinding = false
            isBillingReady = false
            Log.d(TAG, "ON_SERVICE_DISCONNECTED: Service disconnected.")
        }
    }

    /**
     * SDK Capability: startSetup
     */
    fun startConnection() {
        if (billingService != null && isBillingReady) return
        if (!isMyketInstalled()) {
            isBillingReady = false
            return
        }
        if (isBinding) return
        isBinding = true
        Log.d(TAG, "BIND_SERVICE_STARTED: Connecting to Myket...")
        try {
            val intent = Intent(BILLING_SERVICE_ACTION).apply {
                setPackage(MYKET_PACKAGE)
            }
            val success = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "BIND_SERVICE_RESULT: $success")
            if (!success) {
                isBinding = false
                isBillingReady = false
            }
        } catch (e: Exception) {
            isBinding = false
            isBillingReady = false
        }
    }

    private fun checkBillingSupport() {
        val service = billingService ?: return
        try {
            val result = service.isBillingSupported(BILLING_API_VERSION, appContext.packageName, "inapp")
            if (result == 0) {
                isBillingReady = true
                Log.d(TAG, "BILLING_SUPPORTED: Ready for purchases.")
            } else {
                isBillingReady = false
                Log.e(TAG, "BILLING_NOT_SUPPORTED: Code $result")
            }
        } catch (e: Exception) {
            isBillingReady = false
        }
    }

    fun endConnection() {
        if (isBound) {
            try {
                appContext.unbindService(serviceConnection)
            } catch (e: Exception) {}
            isBound = false
            isBinding = false
            isBillingReady = false
            billingService = null
        }
    }

    fun isMyketInstalled(): Boolean {
        return try {
            val pm = appContext.packageManager
            pm.getPackageInfo(MYKET_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * SDK Capability: getSkuDetails / queryInventory
     */
    suspend fun queryInventory(): Map<String, SkuDetails>? = withContext(Dispatchers.IO) {
        val service = billingService ?: return@withContext null
        if (!isBillingReady) return@withContext null
        
        try {
            val queryBundle = Bundle().apply {
                putStringArrayList("ITEM_ID_LIST", ArrayList(ALL_SKUS))
            }
            val skuDetailsBundle = service.getSkuDetails(BILLING_API_VERSION, appContext.packageName, "inapp", queryBundle)
            val responseCode = skuDetailsBundle.getInt("RESPONSE_CODE")
            if (responseCode == 0) {
                val detailsList = skuDetailsBundle.getStringArrayList("DETAILS_LIST")
                val results = mutableMapOf<String, SkuDetails>()
                detailsList?.forEach { jsonStr ->
                    val json = JSONObject(jsonStr)
                    val sku = json.getString("productId")
                    results[sku] = SkuDetails(
                        sku = sku,
                        price = json.getString("price"),
                        title = json.getString("title"),
                        description = json.getString("description")
                    )
                }
                return@withContext results
            }
        } catch (e: Exception) {
            Log.e(TAG, "Query inventory failed", e)
        }
        null
    }

    /**
     * SDK Capability: launchPurchaseFlow
     */
    suspend fun launchPurchaseFlow(
        activity: Activity,
        sku: String,
        isReadyInMyket: Boolean = true,
        requestCode: Int = REQUEST_CODE_MYKET_PURCHASE,
        developerPayload: String = ""
    ): BillingResult<Unit> = withContext(Dispatchers.IO) {
        purchaseMutex.withLock {
            if (!isMyketInstalled()) {
                return@withLock BillingResult.MyketNotInstalled("لطفاً ابتدا مایکت را نصب کنید.")
            }

            startConnection()
            var waitCount = 0
            while (!isBillingReady && waitCount < 30) {
                kotlinx.coroutines.delay(100)
                waitCount++
            }

            val service = billingService ?: return@withLock BillingResult.Error("عدم اتصال به مایکت")

            try {
                val buyIntentBundle = service.getBuyIntent(BILLING_API_VERSION, appContext.packageName, sku, "inapp", developerPayload)
                val responseCode = buyIntentBundle.getInt("RESPONSE_CODE")
                Log.d(TAG, "GET_BUY_INTENT_RESULT: $responseCode")

                if (responseCode == 0) {
                    val pendingIntent = buyIntentBundle.getParcelable<android.app.PendingIntent>("BUY_INTENT")
                    if (pendingIntent != null) {
                        Log.d(TAG, "PAYMENT_INTENT_LAUNCHED: SKU $sku")
                        withContext(Dispatchers.Main) {
                            activity.startIntentSenderForResult(pendingIntent.intentSender, requestCode, Intent(), 0, 0, 0)
                        }
                        return@withLock BillingResult.Success(Unit)
                    }
                } else if (responseCode == 7) {
                    return@withLock BillingResult.Error("این محصول قبلاً خریداری شده است.", responseCode)
                }
                return@withLock BillingResult.Error("خطا در درگاه مایکت (کد $responseCode)", responseCode)
            } catch (e: Exception) {
                return@withLock BillingResult.Error("خطا در ارتباط: ${e.message}")
            }
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): PurchaseData? {
        if (requestCode != REQUEST_CODE_MYKET_PURCHASE || data == null) return null
        
        val purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA")
        val signature = data.getStringExtra("INAPP_DATA_SIGNATURE")
        
        if (resultCode == Activity.RESULT_OK && purchaseData != null) {
            try {
                val json = JSONObject(purchaseData)
                return PurchaseData(
                    orderId = json.optString("orderId"),
                    packageName = json.optString("packageName"),
                    productId = json.optString("productId"),
                    purchaseTime = json.optLong("purchaseTime"),
                    purchaseToken = json.optString("purchaseToken", json.optString("token")),
                    developerPayload = json.optString("developerPayload"),
                    rawPurchaseData = purchaseData,
                    signature = signature ?: ""
                )
            } catch (e: Exception) {}
        }
        return null
    }

    /**
     * SDK Capability: queryPurchases
     */
    suspend fun queryPurchases(): List<PurchaseData> = withContext(Dispatchers.IO) {
        val service = billingService ?: return@withContext emptyList()
        val results = mutableListOf<PurchaseData>()
        try {
            val ownedItems = service.getPurchases(BILLING_API_VERSION, appContext.packageName, "inapp", null)
            if (ownedItems.getInt("RESPONSE_CODE") == 0) {
                val dataList = ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST")
                val sigList = ownedItems.getStringArrayList("INAPP_DATA_SIGNATURE_LIST")
                dataList?.indices?.forEach { i ->
                    val raw = dataList[i]
                    val sig = sigList?.getOrNull(i) ?: ""
                    val json = JSONObject(raw)
                    results.add(PurchaseData(
                        orderId = json.optString("orderId"),
                        packageName = json.optString("packageName"),
                        productId = json.optString("productId"),
                        purchaseTime = json.optLong("purchaseTime"),
                        purchaseToken = json.optString("purchaseToken", json.optString("token")),
                        rawPurchaseData = raw,
                        signature = sig
                    ))
                }
            }
        } catch (e: Exception) {}
        results
    }

    /**
     * SDK Capability: consumeAsync
     */
    suspend fun consumePurchase(purchaseToken: String): Boolean = withContext(Dispatchers.IO) {
        val service = billingService ?: return@withContext false
        try {
            val response = service.consumePurchase(BILLING_API_VERSION, appContext.packageName, purchaseToken)
            response == 0
        } catch (e: Exception) {
            false
        }
    }
}

data class SkuDetails(
    val sku: String,
    val price: String,
    val title: String,
    val description: String
)
