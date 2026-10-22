package com.junglerun.game.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junglerun.game.AppContainer
import com.junglerun.game.core.AnimalSpec
import com.junglerun.game.core.Animals
import com.junglerun.game.core.UnlockType
import com.junglerun.game.ui.components.AnimalPortrait
import com.junglerun.game.ui.components.FoodIcon
import com.junglerun.game.ui.components.GameButton
import com.junglerun.game.ui.components.JungleBackground
import com.junglerun.game.ui.components.Panel
import com.junglerun.game.ui.components.TopBar
import com.junglerun.game.ui.components.formatNumber
import com.junglerun.game.ui.components.isPortrait
import com.junglerun.game.ui.theme.JungleColors

@Composable
fun AnimalsScreen(container: AppContainer, activity: Activity, onBack: () -> Unit, onShop: () -> Unit) {
    val profile by container.repository.profile.collectAsState()
    var focused by remember { mutableStateOf(Animals.get(profile.selected)) }

    val portrait = isPortrait()
    JungleBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Animals", profile.coins, profile.gems, onBack, onShop)
            val detail: @Composable (Modifier) -> Unit = { m ->
                Panel(m) {
                    val owned = focused.id in profile.owned
                    Box(
                        (if (portrait) Modifier.height(190.dp) else Modifier.weight(1f)).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimalPortrait(focused, Modifier.size(if (portrait) 180.dp else 150.dp), locked = !owned,
                            mood = if (owned) com.junglerun.game.art.Mood.HAPPY else com.junglerun.game.art.Mood.NORMAL)
                    }
                    Text(focused.displayName, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(focused.tagline, color = Color(0xFFC8E6C9), fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FoodIcon(focused.food)
                        Text(" Eats ${focused.food.displayName}  •  ${focused.biome.displayName}", color = Color.White, fontSize = 13.sp)
                    }
                    Text("Chased by: ${focused.chaser.displayName}  •  Challenge: ${focused.biome.challengeName}", color = Color(0xFFFFCC80), fontSize = 13.sp)
                    Text("Ability: ${focused.ability.displayName} — ${focused.ability.description}", color = Color(0xFFE1BEE7), fontSize = 13.sp)
                    val perks = buildList {
                        if (focused.doubleJump) add("Double jump")
                        if (focused.jumpMultiplier > 1f) add("High jump")
                        if (focused.speedMultiplier > 1f) add("Fast")
                        if (focused.coinMultiplier > 1f) add("${"%.2f".format(focused.coinMultiplier)}x coins")
                    }
                    if (perks.isNotEmpty()) Text("Perks: ${perks.joinToString()}", color = JungleColors.Banana, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    ActionButton(container, activity, focused, owned, profile.selected == focused.id, profile.coins, profile.gems)
                }
            }
            val carousel: @Composable (Modifier) -> Unit = { m ->
                LazyRow(
                    if (portrait) m.height(170.dp) else m,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(Animals.all, key = { it.id }) { a ->
                        val owned = a.id in profile.owned
                        val selected = a.id == focused.id
                        Column(
                            Modifier
                                .width(124.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) Color(0x55FFC107) else JungleColors.Card)
                                .border(if (selected) 3.dp else 1.dp, if (selected) JungleColors.Banana else Color(0x3366BB6A), RoundedCornerShape(18.dp))
                                .clickable { focused = a }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AnimalPortrait(a, Modifier.size(86.dp), locked = !owned, running = selected)
                            Text(a.displayName.substringBefore(" ").removePrefix("Golden").ifEmpty { "Golden" }, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    a.id == profile.selected -> "SELECTED"
                                    owned -> "Owned"
                                    else -> priceLabel(a)
                                },
                                color = if (owned) JungleColors.Lime else JungleColors.Banana, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            if (portrait) {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                    carousel(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    detail(Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    detail(Modifier.weight(0.9f).fillMaxHeight())
                    Spacer(Modifier.width(12.dp))
                    carousel(Modifier.weight(1.4f).fillMaxHeight())
                }
            }
        }
    }
}

private fun priceLabel(a: AnimalSpec): String = when (a.unlockType) {
    UnlockType.FREE -> "Free"
    UnlockType.COINS -> "● ${formatNumber(a.price.toLong())}"
    UnlockType.GEMS -> "◆ ${a.price}"
    UnlockType.PURCHASE -> "Premium"
}

@Composable
private fun ActionButton(
    container: AppContainer, activity: Activity, a: AnimalSpec,
    owned: Boolean, selected: Boolean, coins: Long, gems: Int,
) {
    when {
        selected -> GameButton("SELECTED ✓", Modifier.fillMaxWidth(), enabled = false) {}
        owned -> GameButton("SELECT", Modifier.fillMaxWidth()) { container.repository.select(a.id) }
        a.unlockType == UnlockType.PURCHASE -> {
            val details by container.billing.details.collectAsState()
            val product = com.junglerun.game.billing.Products.get(a.productId ?: "")
            val price = if (product != null && details.isNotEmpty()) container.billing.priceOf(product) else product?.fallbackPrice ?: ""
            GameButton("BUY $price", Modifier.fillMaxWidth(), brush = JungleColors.purpleButton) {
                container.billing.launchPurchase(activity, a.productId ?: return@GameButton)
            }
        }
        else -> {
            val affordable = if (a.unlockType == UnlockType.COINS) coins >= a.price else gems >= a.price
            GameButton(
                "UNLOCK ${priceLabel(a)}", Modifier.fillMaxWidth(),
                brush = if (affordable) JungleColors.playButton else JungleColors.redButton,
                textColor = if (affordable) Color(0xFF3E2723) else Color.White,
            ) {
                if (container.repository.buyAnimal(a)) {
                    container.analytics.unlock(a.id, a.unlockType.name.lowercase())
                    Toast.makeText(activity, "${a.displayName} unlocked!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, "Not enough ${if (a.unlockType == UnlockType.COINS) "coins" else "gems"} — visit the shop!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
