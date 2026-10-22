package com.junglerun.game.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.junglerun.game.AppContainer
import com.junglerun.game.ads.AdPlacement
import com.junglerun.game.core.Animals
import com.junglerun.game.data.DailyRewards
import com.junglerun.game.game.GameMode
import com.junglerun.game.ui.Routes
import com.junglerun.game.ui.components.AnimalPortrait
import com.junglerun.game.ui.components.BannerAd
import com.junglerun.game.ui.components.CurrencyBar
import com.junglerun.game.ui.components.FoodIcon
import com.junglerun.game.ui.components.GameButton
import com.junglerun.game.ui.components.JungleBackground
import com.junglerun.game.ui.components.Panel
import com.junglerun.game.ui.theme.JungleColors

@Composable
fun HomeScreen(container: AppContainer, activity: Activity, onNavigate: (String) -> Unit) {
    val profile by container.repository.profile.collectAsState()
    val animal = Animals.get(profile.selected)
    var showDaily by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        container.repository.refreshDailyState()
        container.ads.inGameplay = false
        container.sound.resumeMusic()
        if (container.repository.canClaimDailyReward()) showDaily = true
    }

    JungleBackground {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
            // Left: title + hero animal
            Column(
                Modifier.weight(1.1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "JUNGLE RUN",
                    style = MaterialTheme.typography.displayLarge,
                    color = JungleColors.Banana,
                )
                Text("Run • Jump • Escape the ${animal.chaser.displayName}!", color = Color(0xFFC8E6C9))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AnimalPortrait(animal, Modifier.size(220.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(animal.displayName, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    FoodIcon(animal.food)
                }
                Text(
                    "Best ${profile.bestScore}  •  ${profile.scoreMultiplier}x multiplier",
                    color = Color(0xFFFFF59D),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                BannerAd(container.ads)
            }

            Spacer(Modifier.width(16.dp))

            // Right: currencies + menu
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CurrencyBar(profile.coins, profile.gems) { onNavigate(Routes.SHOP) }
                GameButton(
                    "▶  PLAY", Modifier.fillMaxWidth(), brush = JungleColors.playButton,
                    textColor = Color(0xFF3E2723), height = 64.dp, pulse = true,
                ) { onNavigate(Routes.game(GameMode.SOLO)) }
                GameButton("👥  MULTIPLAYER", Modifier.fillMaxWidth(), brush = JungleColors.blueButton) {
                    onNavigate(Routes.MULTIPLAYER)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameButton("🐾 ANIMALS", Modifier.weight(1f)) { onNavigate(Routes.ANIMALS) }
                    GameButton("🛒 SHOP", Modifier.weight(1f), brush = JungleColors.purpleButton) { onNavigate(Routes.SHOP) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val missionsDone = profile.missions.count { it.done }
                    GameButton("🎯 MISSIONS $missionsDone/3", Modifier.weight(1f)) { onNavigate(Routes.MISSIONS) }
                    GameButton("⚙", Modifier.width(64.dp), brush = JungleColors.greenButton) { onNavigate(Routes.SETTINGS) }
                }
                profile.daily?.let { d ->
                    val target = Animals.get(d.animalId)
                    Panel(Modifier.fillMaxWidth().clickable { onNavigate(Routes.MISSIONS) }) {
                        Text("TODAY'S CHALLENGE", color = JungleColors.Banana, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Run as ${target.displayName} and eat ${d.target} ${target.food.displayName.lowercase()}",
                                color = Color.White, modifier = Modifier.weight(1f),
                            )
                            FoodIcon(target.food)
                        }
                        Text(
                            if (d.claimed) "Claimed ✓" else "${d.progress}/${d.target} • Reward 500 coins + 5 gems",
                            color = Color(0xFFA5D6A7), fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }

    if (showDaily) {
        DailyRewardDialog(container, activity, profile.rewardStreak, profile.lastRewardDay) { showDaily = false }
    }
}

@Composable
private fun DailyRewardDialog(container: AppContainer, activity: Activity, streak: Int, lastDay: Long, onClose: () -> Unit) {
    val today = container.repository.today()
    val nextStreak = if (lastDay == today - 1) streak + 1 else 1
    val dayIndex = (nextStreak - 1) % DailyRewards.cycle.size
    val rewardedReady by container.ads.rewardedAvailable.collectAsState()

    fun claim(doubled: Boolean) {
        val r = container.repository.claimDailyReward(doubled)
        if (r != null) {
            Toast.makeText(activity, "+${r.coins} coins  +${r.gems} gems", Toast.LENGTH_SHORT).show()
            container.analytics.log("daily_reward", "day" to dayIndex + 1, "doubled" to doubled)
        }
        onClose()
    }

    Dialog(onDismissRequest = onClose) {
        Panel {
            Text("DAILY REWARD", style = MaterialTheme.typography.headlineMedium, color = JungleColors.Banana,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text("Come back every day for bigger rewards!", color = Color.White, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DailyRewards.cycle.forEachIndexed { i, r ->
                    val current = i == dayIndex
                    val past = i < dayIndex
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (current) Brush.verticalGradient(listOf(Color(0xFFFFC107), Color(0xFFFF9800)))
                                else Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF))),
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Day ${i + 1}", fontSize = 11.sp, color = if (current) Color(0xFF3E2723) else Color.White)
                        if (r.coins > 0) Text("●${r.coins}", color = if (current) Color(0xFF3E2723) else JungleColors.Coin, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        if (r.gems > 0) Text("◆${r.gems}", color = if (current) Color(0xFF01579B) else JungleColors.Gem, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        if (past) Text("✓", color = JungleColors.Lime)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton("CLAIM", Modifier.weight(1f)) { claim(false) }
                GameButton(
                    "▶ CLAIM x2", Modifier.weight(1f), brush = JungleColors.playButton,
                    textColor = Color(0xFF3E2723), enabled = rewardedReady,
                ) {
                    container.ads.showRewarded(activity, AdPlacement.DAILY_DOUBLE) { ok -> claim(ok) }
                }
            }
        }
    }
}
