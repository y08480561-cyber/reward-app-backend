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
    val developerPayload: String = ""
)

/**
 * Professional, secure Myket In-App Billing Manager.
 * Communicates with Myket Service via standard V3 AIDL IPC.
 * Keeps all validation secrets strictly on the backend.
 */
class MyketBillingManager(private val context: Context) {

    private val TAG = "MyketBillingManager"
    private val MYKET_PACKAGE = "ir.mservices.market"
    private val BILLING_SERVICE_ACTION = "ir.mservices.market.billing.InAppBillingService.BIND"
    private val BILLING_API_VERSION = 3

    private var billingService: IInAppBillingService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                billingService = IInAppBillingService.Stub.asInterface(service)
                isBound = true
                Log.d(TAG, "Connected to Myket InAppBillingService successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error binding to Myket service", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            billingService = null
            isBound = false
            Log.d(TAG, "Disconnected from Myket InAppBillingService.")
        }
    }

    /**
     * Connects to Myket billing service if Myket is installed.
     */
    fun startConnection() {
        if (!isMyketInstalled()) {
            Log.w(TAG, "Myket is not installed on this device.")
            return
        }
        try {
            val intent = Intent(BILLING_SERVICE_ACTION).apply {
                setPackage(MYKET_PACKAGE)
            }
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind to Myket Billing Service: ${e.message}")
        }
    }

    /**
     * Unbinds from Myket billing service to prevent memory leaks.
     */
    fun endConnection() {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding service: ${e.message}")
            }
            isBound = false
            billingService = null
        }
    }

    /**
     * Checks if Myket application is installed on the user device.
     */
    fun isMyketInstalled(): Boolean {
        return try {
            val pm = context.packageManager
            pm.getPackageInfo(MYKET_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                // Secondary check for alternative package if present
                context.packageManager.getPackageInfo("com.myket.android", 0)
                true
            } catch (e2: Exception) {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launches the official Myket purchase flow.
     * If product is marked as under preparation, gracefully informs user.
     */
    suspend fun launchPurchaseFlow(
        activity: Activity,
        sku: String,
        isReadyInMyket: Boolean,
        requestCode: Int = 1001,
        developerPayload: String = ""
    ): BillingResult<Unit> = withContext(Dispatchers.IO) {
        // 1. Preparation status guard
        if (!isReadyInMyket) {
            return@withContext BillingResult.UnderPreparation(
                "این بسته در حال حاضر در کنسول توسعه‌دهندگان مایکت در حال آماده‌سازی است و به زودی پس از تایید نهایی فعال می‌گردد."
            )
        }

        // 2. Check Myket installed
        if (!isMyketInstalled()) {
            return@withContext BillingResult.MyketNotInstalled(
                "برنامه مایکت روی دستگاه شما یافت نشد. لطفاً برای انجام خرید درون‌برنامه‌ای، ابتدا مایکت را نصب کنید."
            )
        }

        // 3. Ensure service connection
        val service = billingService
        if (service == null) {
            startConnection()
            return@withContext BillingResult.Error("در حال برقراری ارتباط با مایکت... لطفاً لحظاتی دیگر مجدداً تلاش فرمایید.")
        }

        try {
            // Check billing support
            val isSupported = service.isBillingSupported(BILLING_API_VERSION, context.packageName, "inapp")
            if (isSupported != 0) {
                return@withContext BillingResult.Error("پرداخت درون‌برنامه‌ای مایکت در حساب یا دستگاه شما پشتیبانی نمی‌شود (کد $isSupported).", isSupported)
            }

            // Request buy intent from Myket
            val buyIntentBundle = service.getBuyIntent(
                BILLING_API_VERSION,
                context.packageName,
                sku,
                "inapp",
                developerPayload
            )

            val responseCode = buyIntentBundle.getInt("RESPONSE_CODE", -1)
            if (responseCode == 0) {
                val pendingIntent = buyIntentBundle.getParcelable<android.app.PendingIntent>("BUY_INTENT")
                if (pendingIntent != null) {
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
                    return@withContext BillingResult.Success(Unit)
                } else {
                    return@withContext BillingResult.Error("اطلاعات اتصال به درگاه پرداخت مایکت دریافت نشد.")
                }
            } else if (responseCode == 7) {
                // ITEM_ALREADY_OWNED: user already has an unconsumed purchase token
                return@withContext BillingResult.Error("این محصول قبلاً خریداری شده اما هنوز مصرف نشده است. گزینه «بازیابی خریدهای قبلی» را بزنید.", responseCode)
            } else {
                return@withContext BillingResult.Error("خطا در ایجاد تراکنش خرید مایکت (کد $responseCode).", responseCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during launchPurchaseFlow", e)
            return@withContext BillingResult.Error("خطا در ارتباط با مایکت: ${e.message}")
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
        return try {
            val json = JSONObject(purchaseDataString)
            PurchaseData(
                orderId = json.optString("orderId", "MYKET_ORD_" + System.currentTimeMillis()),
                packageName = json.optString("packageName", context.packageName),
                productId = json.optString("productId"),
                purchaseTime = json.optLong("purchaseTime", System.currentTimeMillis()),
                purchaseToken = json.optString("purchaseToken", json.optString("token")),
                developerPayload = json.optString("developerPayload", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing purchase JSON", e)
            null
        }
    }

    /**
     * Queries unconsumed purchases to support purchase restoration.
     */
    suspend fun queryPurchases(): List<PurchaseData> = withContext(Dispatchers.IO) {
        val service = billingService ?: return@withContext emptyList()
        val results = mutableListOf<PurchaseData>()
        try {
            var continuationToken: String? = null
            do {
                val ownedItems = service.getPurchases(BILLING_API_VERSION, context.packageName, "inapp", continuationToken)
                val responseCode = ownedItems.getInt("RESPONSE_CODE", -1)
                if (responseCode == 0) {
                    val purchaseDataList = ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST")
                    if (purchaseDataList != null) {
                        for (raw in purchaseDataList) {
                            try {
                                val json = JSONObject(raw)
                                results.add(
                                    PurchaseData(
                                        orderId = json.optString("orderId"),
                                        packageName = json.optString("packageName"),
                                        productId = json.optString("productId"),
                                        purchaseTime = json.optLong("purchaseTime"),
                                        purchaseToken = json.optString("purchaseToken", json.optString("token")),
                                        developerPayload = json.optString("developerPayload")
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
        val service = billingService ?: return@withContext false
        try {
            val response = service.consumePurchase(BILLING_API_VERSION, context.packageName, purchaseToken)
            response == 0
        } catch (e: Exception) {
            Log.e(TAG, "Consume purchase exception", e)
            false
        }
    }
}
