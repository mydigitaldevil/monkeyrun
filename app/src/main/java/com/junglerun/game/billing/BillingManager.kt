package com.junglerun.game.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Something the game should grant after Google Play confirms a purchase. */
data class Entitlement(val productId: String, val purchaseToken: String, val quantity: Int, val expiresAtMillis: Long = 0)

/**
 * Google Play Billing v7 wrapper: product catalogue, purchase flow, consumption of
 * consumables, acknowledgement of non-consumables/subscriptions and restore on start.
 *
 * NOTE: for production, also verify [Purchase.getPurchaseToken] on your backend with the
 * Google Play Developer API before granting high-value items.
 */
class BillingManager(
    context: Context,
    private val scope: CoroutineScope,
) : PurchasesUpdatedListener {
    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val _details = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val details: StateFlow<Map<String, ProductDetails>> = _details.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _entitlements = MutableSharedFlow<Entitlement>(extraBufferCapacity = 16)
    /** Emits each verified purchase once it should be granted. */
    val entitlements: SharedFlow<Entitlement> = _entitlements.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var retry = 0

    fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    retry = 0
                    _connected.value = true
                    scope.launch {
                        queryProducts()
                        restorePurchases()
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _connected.value = false
                if (retry++ < 3) connect()
            }
        })
    }

    private suspend fun queryProducts() {
        val map = HashMap<String, ProductDetails>()
        for ((type, kinds) in listOf(
            BillingClient.ProductType.INAPP to setOf(ProductKind.CONSUMABLE, ProductKind.NON_CONSUMABLE),
            BillingClient.ProductType.SUBS to setOf(ProductKind.SUBSCRIPTION),
        )) {
            val ids = Products.all.filter { it.kind in kinds }.map { it.id }
            if (ids.isEmpty()) continue
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(ids.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(type).build()
                })
                .build()
            val result = client.queryProductDetails(params)
            result.productDetailsList?.forEach { map[it.productId] = it }
        }
        _details.value = map
    }

    /** Localised price from Play, falling back to the catalogue's display price. */
    fun priceOf(product: StoreProduct): String {
        val d = _details.value[product.id] ?: return product.fallbackPrice
        return d.oneTimePurchaseOfferDetails?.formattedPrice
            ?: d.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            ?: product.fallbackPrice
    }

    fun launchPurchase(activity: Activity, productId: String) {
        val d = _details.value[productId]
        if (d == null) {
            _messages.tryEmit(if (_connected.value) "This item isn't available yet. Try again later." else "Google Play is not connected.")
            connect()
            return
        }
        val pdp = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d)
        d.subscriptionOfferDetails?.firstOrNull()?.offerToken?.let { pdp.setOfferToken(it) }
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(pdp.build())).build()
        client.launchBillingFlow(activity, flow)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { p -> scope.launch { handle(p) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { restorePurchases() }
            else -> _messages.tryEmit("Purchase failed (${result.responseCode}). Please try again.")
        }
    }

    private suspend fun handle(p: Purchase) {
        if (p.purchaseState == Purchase.PurchaseState.PENDING) {
            _messages.tryEmit("Purchase pending — it will be delivered once payment completes.")
            return
        }
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        for (id in p.products) {
            val product = Products.get(id) ?: continue
            val expires = if (product.kind == ProductKind.SUBSCRIPTION) {
                // Play renews subscriptions automatically; we re-check on every launch.
                System.currentTimeMillis() + 3L * 24 * 3600_000
            } else 0L
            _entitlements.emit(Entitlement(id, p.purchaseToken, p.quantity, expires))
            when (product.kind) {
                ProductKind.CONSUMABLE -> client.consumePurchase(
                    ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build(),
                )
                ProductKind.NON_CONSUMABLE, ProductKind.SUBSCRIPTION -> if (!p.isAcknowledged) {
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build(),
                    )
                }
            }
        }
    }

    /** Re-delivers owned non-consumables/subscriptions and any unconsumed consumables. */
    suspend fun restorePurchases() {
        if (!client.isReady) return
        for (type in listOf(BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS)) {
            val res = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build())
            res.purchasesList.forEach { handle(it) }
        }
    }

    fun endConnection() {
        client.endConnection()
    }

    companion object {
        private const val TAG = "JungleBilling"
    }
}
