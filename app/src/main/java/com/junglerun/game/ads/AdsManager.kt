package com.junglerun.game.ads

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.ads.Ad
import com.facebook.ads.AudienceNetworkAds
import com.facebook.ads.InterstitialAdListener
import com.facebook.ads.RewardedVideoAd
import com.facebook.ads.RewardedVideoAdListener
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.junglerun.game.analytics.Analytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * All ad formats in one place, with a waterfall across two networks:
 *
 *  - Google AdMob is primary (and also mediates Meta via the AdMob Meta adapter).
 *  - Meta Audience Network is called directly as a fallback when AdMob has no fill.
 *
 * Formats: banner, interstitial, rewarded, rewarded interstitial, app open and native.
 * Rewarded ads are always opt-in, so they stay available even after "Remove Ads" is bought.
 */
class AdsManager(
    private val app: Application,
    private val analytics: Analytics,
    private val adsRemoved: () -> Boolean,
) {
    private val main = Handler(Looper.getMainLooper())
    private val started = AtomicBoolean(false)

    private val _ready = MutableStateFlow(false)
    /** True once consent is resolved and the SDKs are initialised. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _rewardedAvailable = MutableStateFlow(false)
    val rewardedAvailable: StateFlow<Boolean> = _rewardedAvailable.asStateFlow()

    // AdMob
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var rewardedInterstitial: RewardedInterstitialAd? = null

    // Meta Audience Network
    private var metaInterstitial: com.facebook.ads.InterstitialAd? = null
    private var metaInterstitialLoaded = false
    private var metaRewarded: RewardedVideoAd? = null
    private var metaRewardedLoaded = false

    private val retries = HashMap<String, Int>()

    /** Suppresses app-open ads while another full-screen ad or a run is on screen. */
    @Volatile var fullScreenShowing = false
        private set
    @Volatile var inGameplay = false

    // Interstitial pacing
    private var lastInterstitialAt = 0L
    private var gameOversSinceInterstitial = 0

    val appOpen = AppOpenAdManager(app, this)

    // ------------------------------------------------------------------ init

    fun start(activity: Activity, consent: ConsentManager) {
        consent.gather(activity) { canRequest ->
            if (canRequest) main.post { initSdks() }
        }
    }

    private fun initSdks() {
        if (!started.compareAndSet(false, true)) return
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                .build(),
        )
        if (!AudienceNetworkAds.isInitialized(app)) AudienceNetworkAds.initialize(app)
        // Google recommends initialising on a background thread.
        Thread {
            MobileAds.initialize(app) {
                main.post {
                    _ready.value = true
                    loadAll()
                }
            }
        }.start()
    }

    private fun loadAll() {
        loadRewarded()
        loadMetaRewarded()
        loadRewardedInterstitial()
        if (!adsRemoved()) {
            loadInterstitial()
            loadMetaInterstitial()
            appOpen.load()
        }
    }

    private fun request(): AdRequest = AdRequest.Builder().build()

    private fun retryLater(key: String, block: () -> Unit) {
        val n = (retries[key] ?: 0) + 1
        retries[key] = n
        val delay = min(60_000L, 2_000L * (1L shl min(n, 5)))
        main.postDelayed(block, delay)
    }

    private fun updateRewardedAvailability() {
        _rewardedAvailable.value = rewarded != null || metaRewardedLoaded || rewardedInterstitial != null
    }

    // ------------------------------------------------------------------ AdMob loaders

    private fun loadInterstitial() {
        if (interstitial != null) return
        InterstitialAd.load(app, AdIds.admobInterstitial, request(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitial = ad
                retries.remove("int")
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                interstitial = null
                Log.d(TAG, "AdMob interstitial failed: ${error.message}")
                retryLater("int") { loadInterstitial() }
            }
        })
    }

    private fun loadRewarded() {
        if (rewarded != null) return
        RewardedAd.load(app, AdIds.admobRewarded, request(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewarded = ad
                retries.remove("rw")
                updateRewardedAvailability()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                rewarded = null
                updateRewardedAvailability()
                Log.d(TAG, "AdMob rewarded failed: ${error.message}")
                retryLater("rw") { loadRewarded() }
            }
        })
    }

    private fun loadRewardedInterstitial() {
        if (rewardedInterstitial != null) return
        RewardedInterstitialAd.load(app, AdIds.admobRewardedInterstitial, request(), object : RewardedInterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedInterstitialAd) {
                rewardedInterstitial = ad
                retries.remove("rwi")
                updateRewardedAvailability()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                rewardedInterstitial = null
                updateRewardedAvailability()
                retryLater("rwi") { loadRewardedInterstitial() }
            }
        })
    }

    // ------------------------------------------------------------------ Meta loaders

    private fun loadMetaInterstitial() {
        if (metaInterstitialLoaded) return
        metaInterstitial?.destroy()
        val ad = com.facebook.ads.InterstitialAd(app, AdIds.metaInterstitial)
        metaInterstitial = ad
        val listener = object : InterstitialAdListener {
            override fun onInterstitialDisplayed(a: Ad) {
                fullScreenShowing = true
                analytics.adImpression("interstitial", "meta", AdPlacement.GAME_OVER.key)
            }

            override fun onInterstitialDismissed(a: Ad) {
                fullScreenShowing = false
                metaInterstitialLoaded = false
                pendingInterstitialDone?.invoke()
                pendingInterstitialDone = null
                loadMetaInterstitial()
            }

            override fun onError(a: Ad, error: com.facebook.ads.AdError) {
                metaInterstitialLoaded = false
                Log.d(TAG, "Meta interstitial failed: ${error.errorMessage}")
                retryLater("mint") { loadMetaInterstitial() }
            }

            override fun onAdLoaded(a: Ad) {
                metaInterstitialLoaded = true
                retries.remove("mint")
            }

            override fun onAdClicked(a: Ad) {}
            override fun onLoggingImpression(a: Ad) {}
        }
        ad.loadAd(ad.buildLoadAdConfig().withAdListener(listener).build())
    }

    private var metaRewardEarned = false
    private var metaRewardCallback: ((Boolean) -> Unit)? = null

    private fun loadMetaRewarded() {
        if (metaRewardedLoaded) return
        metaRewarded?.destroy()
        val ad = RewardedVideoAd(app, AdIds.metaRewarded)
        metaRewarded = ad
        val listener = object : RewardedVideoAdListener {
            override fun onError(a: Ad, error: com.facebook.ads.AdError) {
                metaRewardedLoaded = false
                updateRewardedAvailability()
                Log.d(TAG, "Meta rewarded failed: ${error.errorMessage}")
                retryLater("mrw") { loadMetaRewarded() }
            }

            override fun onAdLoaded(a: Ad) {
                metaRewardedLoaded = true
                retries.remove("mrw")
                updateRewardedAvailability()
            }

            override fun onAdClicked(a: Ad) {}
            override fun onLoggingImpression(a: Ad) {
                fullScreenShowing = true
            }

            override fun onRewardedVideoCompleted() {
                metaRewardEarned = true
            }

            override fun onRewardedVideoClosed() {
                fullScreenShowing = false
                metaRewardedLoaded = false
                updateRewardedAvailability()
                metaRewardCallback?.invoke(metaRewardEarned)
                metaRewardCallback = null
                loadMetaRewarded()
            }
        }
        ad.loadAd(ad.buildLoadAdConfig().withAdListener(listener).build())
    }

    // ------------------------------------------------------------------ interstitial

    private var pendingInterstitialDone: (() -> Unit)? = null

    /**
     * Called at game over. Shows an interstitial at most every 3rd game over and 90 seconds,
     * never in the first runs of a fresh install and never for players who removed ads.
     */
    fun maybeShowInterstitial(activity: Activity, totalRuns: Int, onDone: () -> Unit) {
        gameOversSinceInterstitial++
        val now = System.currentTimeMillis()
        val due = gameOversSinceInterstitial >= 3 && now - lastInterstitialAt > 90_000 && totalRuns >= 3
        if (adsRemoved() || !due) {
            onDone()
            return
        }
        val admob = interstitial
        when {
            admob != null -> {
                admob.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() {
                        fullScreenShowing = true
                        analytics.adImpression("interstitial", "admob", AdPlacement.GAME_OVER.key)
                    }

                    override fun onAdDismissedFullScreenContent() {
                        fullScreenShowing = false
                        interstitial = null
                        loadInterstitial()
                        onDone()
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        fullScreenShowing = false
                        interstitial = null
                        loadInterstitial()
                        onDone()
                    }
                }
                markInterstitial(now)
                admob.show(activity)
            }
            metaInterstitialLoaded && metaInterstitial?.isAdInvalidated == false -> {
                markInterstitial(now)
                pendingInterstitialDone = onDone
                metaInterstitial?.show()
            }
            else -> onDone()
        }
    }

    private fun markInterstitial(now: Long) {
        lastInterstitialAt = now
        gameOversSinceInterstitial = 0
    }

    // ------------------------------------------------------------------ rewarded

    /**
     * Shows a rewarded ad (AdMob → Meta → AdMob rewarded interstitial). [onResult] is called on
     * the main thread with true only if the user earned the reward.
     */
    fun showRewarded(activity: Activity, placement: AdPlacement, onResult: (Boolean) -> Unit) {
        val admob = rewarded
        if (admob != null) {
            var earned = false
            admob.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    fullScreenShowing = true
                    analytics.adImpression("rewarded", "admob", placement.key)
                }

                override fun onAdDismissedFullScreenContent() {
                    fullScreenShowing = false
                    rewarded = null
                    updateRewardedAvailability()
                    loadRewarded()
                    if (earned) analytics.rewardEarned(placement.key)
                    onResult(earned)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    fullScreenShowing = false
                    rewarded = null
                    updateRewardedAvailability()
                    loadRewarded()
                    onResult(false)
                }
            }
            admob.show(activity) { earned = true }
            return
        }

        val meta = metaRewarded
        if (metaRewardedLoaded && meta != null && !meta.isAdInvalidated) {
            metaRewardEarned = false
            metaRewardCallback = { ok ->
                if (ok) analytics.rewardEarned(placement.key)
                onResult(ok)
            }
            analytics.adImpression("rewarded", "meta", placement.key)
            meta.show()
            return
        }

        val ri = rewardedInterstitial
        if (ri != null) {
            var earned = false
            ri.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    fullScreenShowing = true
                    analytics.adImpression("rewarded_interstitial", "admob", placement.key)
                }

                override fun onAdDismissedFullScreenContent() {
                    fullScreenShowing = false
                    rewardedInterstitial = null
                    updateRewardedAvailability()
                    loadRewardedInterstitial()
                    onResult(earned)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    fullScreenShowing = false
                    rewardedInterstitial = null
                    updateRewardedAvailability()
                    loadRewardedInterstitial()
                    onResult(false)
                }
            }
            ri.show(activity) { earned = true }
            return
        }

        // Nothing ready: try loading again for next time.
        loadRewarded()
        loadMetaRewarded()
        onResult(false)
    }

    /** Banner ads are shown only when consent allows and ads aren't removed. */
    fun bannersEnabled(): Boolean = _ready.value && !adsRemoved()

    fun onAdsRemoved() {
        interstitial = null
        metaInterstitial?.destroy()
        metaInterstitial = null
        metaInterstitialLoaded = false
    }

    internal fun setFullScreen(showing: Boolean) {
        fullScreenShowing = showing
    }

    internal fun appOpenAllowed(): Boolean = _ready.value && !adsRemoved() && !fullScreenShowing && !inGameplay

    companion object {
        const val TAG = "JungleAds"
    }
}
