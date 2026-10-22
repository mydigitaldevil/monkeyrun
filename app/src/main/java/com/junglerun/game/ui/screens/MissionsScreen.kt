package com.junglerun.game.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junglerun.game.AppContainer
import com.junglerun.game.core.Animals
import com.junglerun.game.data.Achievements
import com.junglerun.game.data.MissionLogic
import com.junglerun.game.ui.components.FoodIcon
import com.junglerun.game.ui.components.GameButton
import com.junglerun.game.ui.components.JungleBackground
import com.junglerun.game.ui.components.Panel
import com.junglerun.game.ui.components.ProgressRow
import com.junglerun.game.ui.components.TopBar
import com.junglerun.game.ui.components.AdaptiveRow
import com.junglerun.game.ui.components.landscapeScroll
import com.junglerun.game.ui.theme.JungleColors

@Composable
fun MissionsScreen(container: AppContainer, activity: Activity, onBack: () -> Unit) {
    val profile by container.repository.profile.collectAsState()
    val repo = container.repository

    JungleBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Missions", profile.coins, profile.gems, onBack)
            AdaptiveRow(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp), panels = listOf(@Composable { pm: Modifier ->
                // Missions + daily challenge
                Column(pm.landscapeScroll(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Panel(Modifier.fillMaxWidth()) {
                        Text("MISSION SET ${profile.missionSet + 1}", color = JungleColors.Banana, fontWeight = FontWeight.Black)
                        Text("Complete all 3 to raise your score multiplier to ${profile.scoreMultiplier + 1}x", color = Color(0xFFC8E6C9), fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        profile.missions.forEachIndexed { i, m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProgressRow(
                                    (if (m.done) "✓ " else "") + MissionLogic.description(m),
                                    m.progress, m.target, Modifier.weight(1f),
                                )
                                if (!m.done) {
                                    Spacer(Modifier.width(8.dp))
                                    GameButton("SKIP ◆5", height = 36.dp, brush = JungleColors.blueButton) {
                                        if (!repo.skipMission(i)) Toast.makeText(activity, "Need 5 gems", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        val allDone = profile.missions.isNotEmpty() && profile.missions.all { it.done }
                        GameButton(
                            if (allDone) "CLAIM ${profile.scoreMultiplier + 1}x MULTIPLIER + ◆3" else "Finish all missions to level up",
                            Modifier.fillMaxWidth(), enabled = allDone, brush = JungleColors.playButton, textColor = Color(0xFF3E2723),
                        ) {
                            if (repo.completeMissionSet()) Toast.makeText(activity, "Multiplier increased!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    profile.daily?.let { d ->
                        val a = Animals.get(d.animalId)
                        Panel(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("DAILY CHALLENGE", color = JungleColors.Banana, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                                FoodIcon(a.food)
                            }
                            ProgressRow("Run as ${a.displayName} and eat ${a.food.displayName.lowercase()}", d.progress, d.target)
                            if (a.id !in profile.owned) {
                                Text("Unlock ${a.displayName} to take part!", color = Color(0xFFFFCC80), fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            GameButton(
                                if (d.claimed) "CLAIMED ✓" else "CLAIM ● 500 + ◆ 5", Modifier.fillMaxWidth(),
                                enabled = d.done && !d.claimed, brush = JungleColors.playButton, textColor = Color(0xFF3E2723),
                            ) { repo.claimDailyChallenge() }
                        }
                    }
                    Panel(Modifier.fillMaxWidth()) {
                        Text("YOUR TOP SCORES", color = JungleColors.Banana, fontWeight = FontWeight.Black)
                        if (profile.highScores.isEmpty()) Text("Play a run to set a record!", color = Color.White)
                        profile.highScores.forEachIndexed { i, s ->
                            Row {
                                Text("#${i + 1}", color = Color(0xFFC8E6C9), modifier = Modifier.width(40.dp))
                                Text("$s", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }, @Composable { pm: Modifier ->
                // Achievements
                Column(pm.landscapeScroll(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ACHIEVEMENTS", color = JungleColors.Banana, fontWeight = FontWeight.Black)
                    Achievements.all.forEach { a ->
                        val value = a.stat(profile)
                        val claimed = a.id in profile.claimedAchievements
                        Panel(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProgressRow(a.title, value.coerceAtMost(a.target).toInt(), a.target.toInt(), Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                GameButton(
                                    if (claimed) "✓" else "◆${a.gems}", height = 36.dp,
                                    enabled = !claimed && value >= a.target, brush = JungleColors.blueButton,
                                ) { repo.claimAchievement(a) }
                            }
                        }
                    }
                }
            }))
        }
    }
}
