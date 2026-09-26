package de.steppicrew.healthconnectview.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Entitlements from Play Billing: Pro is owned when Play lists a completed purchase of
 * [PRODUCT_ID].
 *
 * Nothing is stored by the app. Play keeps its own cache of the user's purchases and answers
 * from it offline, so a purchase survives reinstalls and moves to a new phone with the Google
 * account -- and a refund removes Pro again on the next [refresh], which a local flag would not.
 *
 * Billing talks to the Play Store app over IPC, not to the network, so it needs no permission
 * this app does not already have.
 */
class BillingEntitlements(context: Context) : Entitlements {

    private val state = MutableStateFlow(ProState())
    override val pro: StateFlow<ProState> = state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                scope.launch { apply(purchases.orEmpty()) }
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init {
        refresh()
    }

    override fun refresh() {
        if (client.isReady) {
            scope.launch { load() }
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { load() }
                } else {
                    Log.i(TAG, "Billing unavailable: ${result.responseCode}")
                }
            }

            // Reconnection is automatic; the next call reconnects on its own.
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    override fun buy(activity: Activity) {
        // Unknown when Play was unreachable at start; ask again so the next tap can work.
        val product = details ?: return refresh()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        // Only needed to pick between several offers; the plain purchase
                        // option of a legacy-compatible product may come without one.
                        .apply { product.oneTimePurchaseOfferDetails?.offerToken?.let(::setOfferToken) }
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    private suspend fun load() {
        val purchases = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        if (purchases.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            apply(purchases.purchasesList)
        }

        val product = client.queryProductDetails(
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build(),
                    ),
                )
                .build(),
        ).productDetailsList?.firstOrNull()
        details = product
        state.update { it.copy(price = product?.oneTimePurchaseOfferDetails?.formattedPrice) }
    }

    /**
     * Takes Play's word for which purchases exist, and acknowledges new ones. Play refunds a
     * purchase that is not acknowledged within three days, so this runs on every listing, not
     * only straight after buying -- a purchase completed while the app was killed is
     * acknowledged the next time it starts.
     */
    private suspend fun apply(purchases: List<Purchase>) {
        val pro = purchases.filter { PRODUCT_ID in it.products }
        val completed = pro.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        completed.filterNot { it.isAcknowledged }.forEach { purchase ->
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            )
        }
        state.update {
            it.copy(
                owned = completed.isNotEmpty(),
                pending = completed.isEmpty() &&
                    pro.any { purchase -> purchase.purchaseState == Purchase.PurchaseState.PENDING },
            )
        }
    }

    private companion object {
        /** The one-time product created by `scripts/play-product.sh`. */
        const val PRODUCT_ID = "pro"
        const val TAG = "Billing"
    }
}
