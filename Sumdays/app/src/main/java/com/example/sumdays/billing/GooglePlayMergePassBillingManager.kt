package com.example.sumdays.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.security.MessageDigest

class GooglePlayMergePassBillingManager(
    context: Context,
    private val onPurchased: (productId: String, purchaseToken: String) -> Unit,
    private val onPending: () -> Unit,
    private val onError: (String) -> Unit,
) : PurchasesUpdatedListener {
    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enableAutoServiceReconnection()
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    fun launchPurchase(activity: Activity, userId: Int) {
        connect { connected ->
            if (!connected) {
                onError("Google Play 결제를 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.")
                return@connect
            }
            queryPassDetails { details, offerToken ->
                if (details == null || offerToken == null) {
                    onError("현재 판매하지 않는 무제한 패스입니다.")
                    return@queryPassDetails
                }
                val params = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(details)
                                .setOfferToken(offerToken)
                                .build()
                        )
                    )
                    .setObfuscatedAccountId(obfuscatedAccountId(userId))
                    .build()
                val result = billingClient.launchBillingFlow(activity, params)
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    onError(result.debugMessage.ifBlank { "Google Play 결제를 시작하지 못했습니다." })
                }
            }
        }
    }

    fun refreshPurchases() {
        connect { connected ->
            if (!connected) return@connect
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) handlePurchases(purchases)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handlePurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> onError(result.debugMessage.ifBlank { "Google Play 결제를 확인하지 못했습니다." })
        }
    }

    fun close() {
        billingClient.endConnection()
    }

    private fun connect(callback: (Boolean) -> Unit) {
        if (billingClient.isReady) {
            callback(true)
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                callback(result.responseCode == BillingClient.BillingResponseCode.OK)
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun queryPassDetails(callback: (ProductDetails?, String?) -> Unit) {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        ) { result, queryResult ->
            val details = queryResult.productDetailsList.firstOrNull()
            val offerToken = details?.subscriptionOfferDetails
                ?.firstOrNull { it.basePlanId == PREPAID_BASE_PLAN_ID }
                ?.offerToken
            callback(
                if (result.responseCode == BillingClient.BillingResponseCode.OK) details else null,
                if (result.responseCode == BillingClient.BillingResponseCode.OK) offerToken else null,
            )
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        purchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    if (PRODUCT_ID in purchase.products) onPurchased(PRODUCT_ID, purchase.purchaseToken)
                }
                Purchase.PurchaseState.PENDING -> onPending()
            }
        }
    }

    private fun obfuscatedAccountId(userId: Int): String = MessageDigest
        .getInstance("SHA-256")
        .digest("sumdays:$userId".toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val PRODUCT_ID = "sumdays_merge_pass_1m"
        const val PREPAID_BASE_PLAN_ID = "merge-pass-prepaid-1m"
    }
}
