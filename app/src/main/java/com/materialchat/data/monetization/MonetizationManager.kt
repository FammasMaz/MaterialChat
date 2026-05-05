package com.materialchat.data.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
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
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.materialchat.BuildConfig
import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.di.ApplicationScope
import com.materialchat.di.MainDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val REWARDED_PREMIUM_DURATION_MILLIS = 24L * 60L * 60L * 1000L
private const val PREMIUM_TICK_INTERVAL_MILLIS = 60L * 1000L

/**
 * Handles AdMob rewarded ads and Google Play Billing entitlement for ad removal.
 */
@Singleton
class MonetizationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher
) : PurchasesUpdatedListener {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _billingSnapshot = MutableStateFlow(BillingSnapshot())
    private val _rewardedSnapshot = MutableStateFlow(RewardedSnapshot())
    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())

    private var billingClient: BillingClient? = null
    private var rewardedAd: RewardedAd? = null

    val premiumState: StateFlow<PremiumState> = combine(
        appPreferences.lifetimeAdFree,
        appPreferences.rewardedPremiumUntilMillis,
        _billingSnapshot,
        _rewardedSnapshot,
        _nowMillis
    ) { lifetimeAdFree, rewardedUntil, billing, rewarded, now ->
        val rewardedRemaining = (rewardedUntil - now).coerceAtLeast(0L)
        val adsEnabled = BuildConfig.ADS_ENABLED
        val isAdFree = !adsEnabled || lifetimeAdFree || rewardedRemaining > 0L

        PremiumState(
            adsEnabled = adsEnabled,
            isAdFree = isAdFree,
            hasLifetimeAdFree = !adsEnabled || lifetimeAdFree,
            rewardedPremiumUntilMillis = rewardedUntil,
            rewardedPremiumRemainingMillis = rewardedRemaining,
            removeAdsPrice = billing.removeAdsPrice,
            billingReady = billing.billingReady,
            rewardedAdReady = rewarded.rewardedAdReady,
            isRewardedAdLoading = rewarded.isRewardedAdLoading,
            isPurchaseInProgress = billing.isPurchaseInProgress
        )
    }.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = PremiumState(
            adsEnabled = BuildConfig.ADS_ENABLED,
            isAdFree = !BuildConfig.ADS_ENABLED,
            hasLifetimeAdFree = !BuildConfig.ADS_ENABLED
        )
    )

    init {
        applicationScope.launch {
            while (true) {
                _nowMillis.value = System.currentTimeMillis()
                delay(PREMIUM_TICK_INTERVAL_MILLIS)
            }
        }

        if (BuildConfig.ADS_ENABLED) {
            applicationScope.launch(mainDispatcher) {
                startBillingConnection()
                loadRewardedAd()
            }
        }
    }

    fun launchRemoveAdsPurchase(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED) {
            _events.tryEmit("This build is already ad-free")
            return
        }

        val client = billingClient
        val productDetails = _billingSnapshot.value.productDetails
        val offerToken = productDetails?.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.offerToken

        if (client == null || !client.isReady) {
            _events.tryEmit("Google Play Billing is not ready yet")
            startBillingConnection()
            return
        }

        if (productDetails == null || offerToken.isNullOrBlank()) {
            _events.tryEmit("Remove Ads product is not available yet")
            queryProductDetails()
            return
        }

        _billingSnapshot.update { it.copy(isPurchaseInProgress = true) }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = client.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _billingSnapshot.update { it.copy(isPurchaseInProgress = false) }
            if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                refreshPurchases()
            } else {
                _events.tryEmit(result.debugMessage.ifBlank { "Could not start purchase" })
            }
        }
    }

    fun showRewardedAd(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED) {
            _events.tryEmit("This build is already ad-free")
            return
        }

        val ad = rewardedAd
        if (ad == null) {
            _events.tryEmit("Rewarded ad is loading. Please try again shortly.")
            loadRewardedAd()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                _rewardedSnapshot.update { it.copy(rewardedAdReady = false) }
                loadRewardedAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                _rewardedSnapshot.update { it.copy(rewardedAdReady = false) }
                _events.tryEmit(adError.message.ifBlank { "Could not show rewarded ad" })
                loadRewardedAd()
            }
        }

        ad.show(activity) {
            applicationScope.launch {
                appPreferences.extendRewardedPremium(REWARDED_PREMIUM_DURATION_MILLIS)
                _nowMillis.value = System.currentTimeMillis()
                _events.tryEmit("Premium unlocked for 24 hours")
            }
        }
    }

    fun refreshPurchases() {
        if (!BuildConfig.ADS_ENABLED) return
        if (billingClient?.isReady == true) {
            queryExistingPurchases()
        } else {
            startBillingConnection()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        _billingSnapshot.update { it.copy(isPurchaseInProgress = false) }

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty().forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _events.tryEmit("Purchase canceled")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                refreshPurchases()
            }
            else -> {
                _events.tryEmit(billingResult.debugMessage.ifBlank { "Purchase failed" })
            }
        }
    }

    private fun startBillingConnection() {
        if (!BuildConfig.ADS_ENABLED) return

        val currentClient = billingClient ?: BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
            .also { billingClient = it }

        if (currentClient.isReady) {
            queryProductDetails()
            queryExistingPurchases()
            return
        }

        currentClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _billingSnapshot.update { it.copy(billingReady = true) }
                    queryProductDetails()
                    queryExistingPurchases()
                } else {
                    _billingSnapshot.update { it.copy(billingReady = false) }
                    _events.tryEmit(billingResult.debugMessage.ifBlank { "Billing unavailable" })
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingSnapshot.update { it.copy(billingReady = false) }
            }
        })
    }

    private fun queryProductDetails() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BuildConfig.REMOVE_ADS_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        client.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _events.tryEmit(billingResult.debugMessage.ifBlank { "Could not load product details" })
                return@queryProductDetailsAsync
            }

            val productDetails = productDetailsResult.productDetailsList.firstOrNull()
            val price = productDetails?.oneTimePurchaseOfferDetailsList
                ?.firstOrNull()
                ?.formattedPrice

            _billingSnapshot.update {
                it.copy(
                    productDetails = productDetails,
                    removeAdsPrice = price,
                    billingReady = true
                )
            }
        }
    }

    private fun queryExistingPurchases() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _events.tryEmit(billingResult.debugMessage.ifBlank { "Could not restore purchases" })
                return@queryPurchasesAsync
            }

            val removeAdsPurchase = purchases.firstOrNull {
                it.products.contains(BuildConfig.REMOVE_ADS_PRODUCT_ID) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
            }

            if (removeAdsPurchase == null) {
                applicationScope.launch {
                    appPreferences.setLifetimeAdFree(false)
                }
            } else {
                handlePurchase(removeAdsPurchase)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(BuildConfig.REMOVE_ADS_PRODUCT_ID)) return

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (purchase.isAcknowledged) {
                    applicationScope.launch {
                        appPreferences.setLifetimeAdFree(true)
                        _events.tryEmit("Ads removed permanently")
                    }
                } else {
                    acknowledgePurchase(purchase)
                }
            }
            Purchase.PurchaseState.PENDING -> {
                _events.tryEmit("Purchase pending. Premium starts after payment completes.")
            }
            else -> Unit
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        client.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                applicationScope.launch {
                    appPreferences.setLifetimeAdFree(true)
                    _events.tryEmit("Ads removed permanently")
                }
            } else {
                _events.tryEmit(billingResult.debugMessage.ifBlank { "Could not acknowledge purchase" })
            }
        }
    }

    private fun loadRewardedAd() {
        if (!BuildConfig.ADS_ENABLED || BuildConfig.ADMOB_REWARDED_AD_UNIT_ID.isBlank()) return
        if (_rewardedSnapshot.value.isRewardedAdLoading || rewardedAd != null) return

        applicationScope.launch(mainDispatcher) {
            _rewardedSnapshot.update { it.copy(isRewardedAdLoading = true) }
            RewardedAd.load(
                context,
                BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        _rewardedSnapshot.update {
                            it.copy(
                                rewardedAdReady = true,
                                isRewardedAdLoading = false
                            )
                        }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        rewardedAd = null
                        _rewardedSnapshot.update {
                            it.copy(
                                rewardedAdReady = false,
                                isRewardedAdLoading = false
                            )
                        }
                    }
                }
            )
        }
    }

    private data class BillingSnapshot(
        val productDetails: ProductDetails? = null,
        val removeAdsPrice: String? = null,
        val billingReady: Boolean = false,
        val isPurchaseInProgress: Boolean = false
    )

    private data class RewardedSnapshot(
        val rewardedAdReady: Boolean = false,
        val isRewardedAdLoading: Boolean = false
    )
}
