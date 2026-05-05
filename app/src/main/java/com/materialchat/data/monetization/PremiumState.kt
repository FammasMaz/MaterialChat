package com.materialchat.data.monetization

/**
 * Current monetization entitlement state used by UI and ad gates.
 */
data class PremiumState(
    val adsEnabled: Boolean = true,
    val isAdFree: Boolean = false,
    val hasLifetimeAdFree: Boolean = false,
    val rewardedPremiumUntilMillis: Long = 0L,
    val rewardedPremiumRemainingMillis: Long = 0L,
    val removeAdsPrice: String? = null,
    val billingReady: Boolean = false,
    val rewardedAdReady: Boolean = false,
    val isRewardedAdLoading: Boolean = false,
    val isPurchaseInProgress: Boolean = false
) {
    val shouldShowAds: Boolean
        get() = adsEnabled && !isAdFree

    val hasRewardedPremium: Boolean
        get() = rewardedPremiumRemainingMillis > 0L
}
