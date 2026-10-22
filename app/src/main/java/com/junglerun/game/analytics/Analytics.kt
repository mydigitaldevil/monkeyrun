package com.junglerun.game.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.junglerun.game.BuildConfig

/**
 * Thin analytics facade. Logs to Firebase Analytics when google-services.json is configured,
 * otherwise to Logcat, so the rest of the app never has to care.
 */
class Analytics(context: Context) {
    private val firebase: FirebaseAnalytics? = if (BuildConfig.FIREBASE_ENABLED) {
        try {
            FirebaseAnalytics.getInstance(context)
        } catch (e: Exception) {
            null
        }
    } else {
        null
    }

    fun log(event: String, vararg params: Pair<String, Any?>) {
        if (BuildConfig.DEBUG) Log.d("Analytics", "$event ${params.joinToString()}")
        val fa = firebase ?: return
        val bundle = Bundle()
        params.forEach { (k, v) ->
            when (v) {
                is Int -> bundle.putLong(k, v.toLong())
                is Long -> bundle.putLong(k, v)
                is Float -> bundle.putDouble(k, v.toDouble())
                is Double -> bundle.putDouble(k, v)
                is Boolean -> bundle.putLong(k, if (v) 1 else 0)
                null -> Unit
                else -> bundle.putString(k, v.toString())
            }
        }
        fa.logEvent(event, bundle)
    }

    fun recordError(t: Throwable) {
        Log.w("Analytics", "non-fatal", t)
        if (!BuildConfig.FIREBASE_ENABLED) return
        try {
            FirebaseCrashlytics.getInstance().recordException(t)
        } catch (_: Exception) {
        }
    }

    // Standard events used across the app
    fun runStart(mode: String, animal: String) = log("run_start", "mode" to mode, "animal" to animal)
    fun runEnd(mode: String, animal: String, score: Long, meters: Int, food: Int) =
        log("run_end", "mode" to mode, "animal" to animal, "score" to score, "meters" to meters, "food" to food)
    fun adImpression(format: String, network: String, placement: String) =
        log("ad_impression_custom", "format" to format, "network" to network, "placement" to placement)
    fun rewardEarned(placement: String) = log("ad_reward", "placement" to placement)
    fun purchase(productId: String) = log(FirebaseAnalytics.Event.PURCHASE, "item_id" to productId)
    fun unlock(animal: String, currency: String) = log("unlock_animal", "animal" to animal, "currency" to currency)
}
