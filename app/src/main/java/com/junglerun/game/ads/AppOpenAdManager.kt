package com.junglerun.game.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

/**
 * App-open ads shown when the player returns to the game from the background
 * (never on the very first launch, never mid-run, and expiring after 4 hours as Google requires).
 */
class AppOpenAdManager(
    private val app: Application,
    private val ads: AdsManager,
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {
    private var ad: AppOpenAd? = null
    private var loadedAt = 0L
    private var loading = false
    private var currentActivity: Activity? = null
    private var backgrounded = false

    init {
        app.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun load() {
        if (loading || isFresh()) return
        loading = true
        AppOpenAd.load(app, AdIds.admobAppOpen, AdRequest.Builder().build(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(loaded: AppOpenAd) {
                ad = loaded
                loadedAt = System.currentTimeMillis()
                loading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loading = false
            }
        })
    }

    private fun isFresh(): Boolean = ad != null && System.currentTimeMillis() - loadedAt < 4 * 3600_000L

    override fun onStop(owner: LifecycleOwner) {
        backgrounded = true
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!backgrounded) return
        backgrounded = false
        val activity = currentActivity ?: return
        if (!ads.appOpenAllowed()) return
        val a = ad
        if (a == null || !isFresh()) {
            load()
            return
        }
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = ads.setFullScreen(true)
            override fun onAdDismissedFullScreenContent() {
                ads.setFullScreen(false)
                ad = null
                load()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ads.setFullScreen(false)
                ad = null
                load()
            }
        }
        a.show(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
