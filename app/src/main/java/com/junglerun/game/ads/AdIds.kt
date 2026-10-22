package com.junglerun.game.ads

import com.junglerun.game.BuildConfig

/**
 * Ad unit ids are injected at build time (see app/build.gradle.kts). The defaults are Google's
 * and Meta's official TEST ids, which are always safe to click during development.
 */
object AdIds {
    val admobBanner = BuildConfig.ADMOB_BANNER_ID
    val admobInterstitial = BuildConfig.ADMOB_INTERSTITIAL_ID
    val admobRewarded = BuildConfig.ADMOB_REWARDED_ID
    val admobRewardedInterstitial = BuildConfig.ADMOB_REWARDED_INTERSTITIAL_ID
    val admobAppOpen = BuildConfig.ADMOB_APP_OPEN_ID
    val admobNative = BuildConfig.ADMOB_NATIVE_ID
    val metaBanner = BuildConfig.META_BANNER_ID
    val metaInterstitial = BuildConfig.META_INTERSTITIAL_ID
    val metaRewarded = BuildConfig.META_REWARDED_ID
}

/** Where an ad is shown — used for analytics and frequency rules. */
enum class AdPlacement(val key: String) {
    REVIVE("revive"),
    DOUBLE_COINS("double_coins"),
    FREE_COINS("free_coins"),
    FREE_GEMS("free_gems"),
    DAILY_DOUBLE("daily_double"),
    MISSION_SKIP("mission_skip"),
    GAME_OVER("game_over"),
    APP_OPEN("app_open"),
}
