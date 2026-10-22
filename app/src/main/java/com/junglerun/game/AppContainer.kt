package com.junglerun.game

import android.app.Application
import com.junglerun.game.ads.AdsManager
import com.junglerun.game.analytics.Analytics
import com.junglerun.game.audio.SoundManager
import com.junglerun.game.billing.BillingManager
import com.junglerun.game.billing.Entitlement
import com.junglerun.game.billing.ProductKind
import com.junglerun.game.billing.Products
import com.junglerun.game.data.PlayerRepository
import com.junglerun.game.net.LeaderboardClient
import com.junglerun.game.net.RoomSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container — small enough that a DI framework would be overkill. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val analytics = Analytics(app)
    val repository = PlayerRepository(app)
    val sound = SoundManager(app)
    val ads = AdsManager(app, analytics) { repository.current.adsRemoved() }
    val billing = BillingManager(app, scope)
    val leaderboard = LeaderboardClient(BuildConfig.ONLINE_SERVER_URL)

    /** The active multiplayer room, kept here so it survives navigation between lobby and race. */
    var room: RoomSession? = null

    init {
        scope.launch(Dispatchers.IO) { sound.prepare() }
        scope.launch { billing.entitlements.collect { grant(it) } }
        billing.connect()
        applySettings()
        syncScores()
    }

    /** Uploads queued runs to the online leaderboard; silently retries next time if offline. */
    fun syncScores() {
        scope.launch {
            val p = repository.current
            if (p.pendingScores.isEmpty()) return@launch
            val done = HashSet<String>()
            var league: String? = null
            var rank: Int? = null
            for (r in p.pendingScores) {
                try {
                    val res = leaderboard.submit(p.playerId, p.settings.playerName, r)
                    done += r.id
                    league = res.league ?: league
                    rank = res.rank ?: rank
                } catch (e: Exception) {
                    break
                }
            }
            if (done.isNotEmpty()) repository.onScoresUploaded(done, league, rank)
        }
    }

    fun applySettings() {
        val s = repository.current.settings
        sound.soundEnabled = s.sound
        sound.hapticsEnabled = s.haptics
        sound.musicEnabled = s.music
    }

    /** Idempotently grants a purchase (consumables are tracked by purchase token). */
    private fun grant(e: Entitlement) {
        val product = Products.get(e.productId) ?: return
        repository.update { p ->
            if (product.kind == ProductKind.CONSUMABLE && e.purchaseToken in p.grantedTokens) return@update p
            var next = p
            if (product.kind == ProductKind.CONSUMABLE) {
                next = next.copy(
                    coins = next.coins + product.coins.toLong() * e.quantity,
                    gems = next.gems + product.gems * e.quantity,
                    grantedTokens = (next.grantedTokens + e.purchaseToken).toList().takeLast(200).toSet(),
                )
            } else {
                val firstTime = e.purchaseToken !in p.grantedTokens
                when (product.id) {
                    Products.REMOVE_ADS -> next = next.copy(removeAds = true)
                    Products.STARTER_PACK -> {
                        next = next.copy(removeAds = true, owned = next.owned + "rabbit")
                        if (firstTime) next = next.copy(coins = next.coins + product.coins, gems = next.gems + product.gems)
                    }
                    Products.GOLDEN_MONKEY -> next = next.copy(owned = next.owned + "golden_monkey")
                    Products.VIP_MONTHLY -> next = next.copy(vipUntil = maxOf(next.vipUntil, e.expiresAtMillis))
                }
                next = next.copy(ownedProducts = next.ownedProducts + product.id)
                if (firstTime) next = next.copy(grantedTokens = next.grantedTokens + e.purchaseToken)
            }
            next
        }
        if (repository.current.adsRemoved()) ads.onAdsRemoved()
        analytics.purchase(e.productId)
    }
}
