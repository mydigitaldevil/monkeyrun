package com.junglerun.game.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junglerun.game.AppContainer
import com.junglerun.game.ads.AdPlacement
import com.junglerun.game.billing.ProductKind
import com.junglerun.game.billing.Products
import com.junglerun.game.billing.StoreProduct
import com.junglerun.game.data.Economy
import com.junglerun.game.data.Upgrade
import com.junglerun.game.ui.components.GameButton
import com.junglerun.game.ui.components.JungleBackground
import com.junglerun.game.ui.components.Panel
import com.junglerun.game.ui.components.TopBar
import com.junglerun.game.ui.components.formatNumber
import com.junglerun.game.ui.theme.JungleColors
import kotlinx.coroutines.launch

private enum class ShopTab(val title: String) { CURRENCY("Coins & Gems"), SPECIAL("Specials"), UPGRADES("Upgrades"), FREE("Free") }

/** Session-limited free rewards (rewarded video). */
private var freeRewardsThisSession = 0
private const val FREE_REWARD_LIMIT = 6

@Composable
fun ShopScreen(container: AppContainer, activity: Activity, onBack: () -> Unit) {
    val profile by container.repository.profile.collectAsState()
    val details by container.billing.details.collectAsState()
    var tab by remember { mutableStateOf(ShopTab.CURRENCY) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        container.billing.messages.collect { Toast.makeText(activity, it, Toast.LENGTH_LONG).show() }
    }

    JungleBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Shop", profile.coins, profile.gems, onBack)
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShopTab.entries.forEach { t ->
                    Text(
                        t.title,
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (t == tab) JungleColors.Banana else Color(0x33FFFFFF))
                            .clickable { tab = t }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = if (t == tab) Color(0xFF3E2723) else Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when (tab) {
                ShopTab.CURRENCY, ShopTab.SPECIAL -> {
                    val list = Products.all.filter {
                        if (tab == ShopTab.CURRENCY) it.kind == ProductKind.CONSUMABLE else it.kind != ProductKind.CONSUMABLE
                    }
                    LazyVerticalGrid(
                        GridCells.Adaptive(200.dp),
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(list, key = { it.id }) { p ->
                            val owned = when (p.id) {
                                Products.REMOVE_ADS -> profile.removeAds
                                Products.VIP_MONTHLY -> profile.isVip()
                                else -> p.id in profile.ownedProducts
                            }
                            ProductCard(p, if (details.isEmpty()) p.fallbackPrice else container.billing.priceOf(p), owned) {
                                container.billing.launchPurchase(activity, p.id)
                            }
                        }
                        item {
                            Panel {
                                Text("Already bought something?", color = Color.White, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                GameButton("RESTORE PURCHASES", Modifier.fillMaxWidth(), height = 44.dp) {
                                    scope.launch {
                                        container.billing.restorePurchases()
                                        Toast.makeText(activity, "Purchases restored", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }
                ShopTab.UPGRADES -> LazyVerticalGrid(
                    GridCells.Adaptive(240.dp),
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(Upgrade.entries.toList()) { u ->
                        val lvl = profile.level(u)
                        Panel {
                            Text(u.displayName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text(u.description, color = Color(0xFFC8E6C9), fontSize = 13.sp)
                            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(Economy.MAX_UPGRADE) { i ->
                                    Spacer(
                                        Modifier
                                            .width(26.dp)
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(if (i < lvl) JungleColors.Lime else Color(0x44FFFFFF)),
                                    )
                                }
                            }
                            if (lvl >= Economy.MAX_UPGRADE) {
                                GameButton("MAXED", Modifier.fillMaxWidth(), enabled = false, height = 44.dp) {}
                            } else {
                                val cost = Economy.upgradeCost(lvl)
                                GameButton(
                                    "UPGRADE ● ${formatNumber(cost.toLong())}", Modifier.fillMaxWidth(), height = 44.dp,
                                    brush = if (profile.coins >= cost) JungleColors.playButton else JungleColors.redButton,
                                    textColor = if (profile.coins >= cost) Color(0xFF3E2723) else Color.White,
                                ) {
                                    if (!container.repository.buyUpgrade(u)) {
                                        Toast.makeText(activity, "Not enough coins", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }
                ShopTab.FREE -> FreeTab(container, activity)
            }
        }
    }
}

@Composable
private fun ProductCard(p: StoreProduct, price: String, owned: Boolean, onBuy: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    p.gems > 0 && p.coins == 0 -> "◆"
                    p.coins > 0 && p.gems == 0 -> "●"
                    p.id == Products.REMOVE_ADS -> "🚫"
                    p.id == Products.VIP_MONTHLY -> "👑"
                    else -> "🎁"
                },
                color = if (p.gems > 0 && p.coins == 0) JungleColors.Gem else JungleColors.Coin, fontSize = 26.sp,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, color = Color.White, fontWeight = FontWeight.Black)
                Text(p.description, color = Color(0xFFC8E6C9), fontSize = 12.sp)
            }
        }
        p.badge?.let {
            Text(it, color = Color(0xFF3E2723), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(JungleColors.Banana).padding(horizontal = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (owned) {
            GameButton("OWNED ✓", Modifier.fillMaxWidth(), enabled = false, height = 44.dp) {}
        } else {
            GameButton(price, Modifier.fillMaxWidth(), brush = JungleColors.playButton, textColor = Color(0xFF3E2723), height = 44.dp, onClick = onBuy)
        }
    }
}

@Composable
private fun FreeTab(container: AppContainer, activity: Activity) {
    val ready by container.ads.rewardedAvailable.collectAsState()
    var used by remember { mutableIntStateOf(freeRewardsThisSession) }
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            Triple("Free Coins", "Watch a short video for 150 coins", AdPlacement.FREE_COINS),
            Triple("Free Gems", "Watch a short video for 2 gems", AdPlacement.FREE_GEMS),
        ).forEach { (title, desc, placement) ->
            Panel(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(desc, color = Color(0xFFC8E6C9))
                Text("${FREE_REWARD_LIMIT - used} left this session", color = JungleColors.Banana, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                GameButton(
                    if (ready) "▶ WATCH" else "Loading ad…", Modifier.fillMaxWidth(),
                    brush = JungleColors.blueButton, enabled = ready && used < FREE_REWARD_LIMIT,
                ) {
                    container.ads.showRewarded(activity, placement) { ok ->
                        if (ok) {
                            freeRewardsThisSession++
                            used = freeRewardsThisSession
                            if (placement == AdPlacement.FREE_COINS) container.repository.addCoins(150) else container.repository.addGems(2)
                            Toast.makeText(activity, "Reward added!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}
