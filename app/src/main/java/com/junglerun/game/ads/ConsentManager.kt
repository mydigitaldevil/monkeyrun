package com.junglerun.game.ads

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GDPR / US-state privacy consent via Google's User Messaging Platform (required for AdMob
 * in the EEA/UK and covers Meta via AdMob's IAB TCF signals).
 */
class ConsentManager(activity: Activity) {
    private val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)
    private val requested = AtomicBoolean(false)

    val canRequestAds: Boolean get() = info.canRequestAds()

    val privacyOptionsRequired: Boolean
        get() = info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Shows the consent form if needed, then calls [onReady] exactly once. */
    fun gather(activity: Activity, onReady: (canRequestAds: Boolean) -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        val done = AtomicBoolean(false)
        fun finish() {
            if (done.compareAndSet(false, true)) onReady(info.canRequestAds())
        }
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ -> finish() }
            },
            { _ -> finish() },
        )
        // Consent from a previous session lets us start loading ads immediately.
        if (info.canRequestAds() && requested.compareAndSet(false, true)) finish()
    }

    fun showPrivacyOptions(activity: Activity, onDone: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ -> onDone() }
    }
}
