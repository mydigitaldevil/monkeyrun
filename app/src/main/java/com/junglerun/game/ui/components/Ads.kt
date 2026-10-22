package com.junglerun.game.ui.components

import android.content.Context
import android.graphics.Color as AColor
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.junglerun.game.ads.AdIds
import com.junglerun.game.ads.AdsManager

/**
 * Anchored adaptive AdMob banner, falling back to a Meta Audience Network banner if AdMob
 * has no fill. Renders nothing when ads are removed or consent isn't available.
 */
@Composable
fun BannerAd(ads: AdsManager, modifier: Modifier = Modifier, maxWidthDp: Int = 468) {
    val ready by ads.ready.collectAsState()
    if (!ready || !ads.bannersEnabled()) return
    var useMeta by remember { mutableStateOf(false) }
    if (!useMeta) {
        AndroidView(
            modifier = modifier.widthIn(max = maxWidthDp.dp).fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, maxWidthDp))
                    adUnitId = AdIds.admobBanner
                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            useMeta = true
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() },
        )
    } else {
        AndroidView(
            modifier = modifier.widthIn(max = maxWidthDp.dp).fillMaxWidth().height(50.dp),
            factory = { ctx ->
                com.facebook.ads.AdView(ctx, AdIds.metaBanner, com.facebook.ads.AdSize.BANNER_HEIGHT_50).apply { loadAd() }
            },
            onRelease = { it.destroy() },
        )
    }
}

/** Compact AdMob native ad card (icon, headline, body, media and call-to-action). */
@Composable
fun NativeAdCard(ads: AdsManager, modifier: Modifier = Modifier) {
    val ready by ads.ready.collectAsState()
    if (!ready || !ads.bannersEnabled()) return
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    DisposableEffect(Unit) {
        var disposed = false
        val loader = AdLoader.Builder(context, AdIds.admobNative)
            .forNativeAd { ad ->
                if (disposed) ad.destroy() else nativeAd = ad
            }
            .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).build())
            .withAdListener(object : AdListener() {})
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose {
            disposed = true
            nativeAd?.destroy()
        }
    }
    val ad = nativeAd ?: return
    AndroidView(
        modifier = modifier.widthIn(max = 420.dp).height(140.dp),
        factory = { ctx -> NativeCard(ctx).view },
        update = { (it.tag as NativeCard).bind(ad) },
    )
}

/** Builds the native ad layout in code (NativeAdView itself is final, so we compose it). */
private class NativeCard(context: Context) {
    val view = NativeAdView(context)
    private val dp = context.resources.displayMetrics.density
    private val media = MediaView(context)
    private val icon = ImageView(context)
    private val headline = TextView(context)
    private val body = TextView(context)
    private val cta = Button(context)

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 16 * dp
                setColor(0xEE12321F.toInt())
            }
        }
        root.addView(media, LinearLayout.LayoutParams((120 * dp).toInt(), (120 * dp).toInt()))
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * dp).toInt(), 0, 0, 0)
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val badge = TextView(context).apply {
            text = "Ad"
            setTextColor(AColor.BLACK)
            textSize = 10f
            setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            background = GradientDrawable().apply {
                cornerRadius = 4 * dp
                setColor(0xFFFFD93B.toInt())
            }
        }
        top.addView(badge)
        top.addView(icon, LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt()).apply { marginStart = (6 * dp).toInt() })
        headline.apply {
            setTextColor(AColor.WHITE)
            textSize = 15f
            maxLines = 1
            setPadding((6 * dp).toInt(), 0, 0, 0)
        }
        top.addView(headline)
        col.addView(top)
        body.apply {
            setTextColor(0xFFCCE8CC.toInt())
            textSize = 12f
            maxLines = 2
        }
        col.addView(body)
        cta.apply {
            textSize = 13f
            isAllCaps = true
        }
        col.addView(cta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        view.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.mediaView = media
        view.iconView = icon
        view.headlineView = headline
        view.bodyView = body
        view.callToActionView = cta
        view.tag = this
    }

    fun bind(ad: NativeAd) {
        headline.text = ad.headline
        body.text = ad.body ?: ""
        cta.text = ad.callToAction ?: "Open"
        val drawable = ad.icon?.drawable
        icon.setImageDrawable(drawable)
        icon.visibility = if (drawable != null) android.view.View.VISIBLE else android.view.View.GONE
        ad.mediaContent?.let { media.setMediaContent(it) }
        view.setNativeAd(ad)
    }
}
