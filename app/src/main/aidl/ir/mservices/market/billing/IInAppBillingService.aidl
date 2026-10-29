package ir.mservices.market.billing;

import android.os.Bundle;

/**
 * Standard In-App Billing Service AIDL definition for Myket.
 * Follows Google Play / Myket V3 In-App Billing Specification.
 */
interface IInAppBillingService {
    /**
     * Checks support for the requested API version, package, and billing type.
     * @param apiVersion billing API version (3)
     * @param packageName application package name
     * @param type "inapp" for one-time purchases or "subs" for subscriptions
     * @return 0 on success, error code otherwise
     */
    int isBillingSupported(int apiVersion, String packageName, String type);

    /**
     * Provides details of a list of SKUs.
     */
    Bundle getSkuDetails(int apiVersion, String packageName, String type, in Bundle skusBundle);

    /**
     * Returns a pending intent (BUY_INTENT) to launch the purchase flow.
     */
    Bundle getBuyIntent(int apiVersion, String packageName, String sku, String type, String developerPayload);

    /**
     * Returns the current purchases owned by the user.
     */
    Bundle getPurchases(int apiVersion, String packageName, String type, String continuationToken);

    /**
     * Consumes a purchase so it can be bought again.
     */
    int consumePurchase(int apiVersion, String packageName, String purchaseToken);
}
