package com.junglerun.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junglerun.game.AppContainer
import com.junglerun.game.core.Animals
import com.junglerun.game.net.Board
import com.junglerun.game.ui.components.AnimalPortrait
import com.junglerun.game.ui.components.JungleBackground
import com.junglerun.game.ui.components.LeagueBadge
import com.junglerun.game.ui.components.TopBar
import com.junglerun.game.ui.theme.JungleColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BoardTab(val title: String) { WEEK("This week's league"), ALL("All-time"), MINE("My runs") }

/** Weekly league + all-time leaderboard from the server, and the player's local run history. */
@Composable
fun LeaderboardScreen(container: AppContainer, onBack: () -> Unit) {
    val profile by container.repository.profile.collectAsState()
    var tab by remember { mutableStateOf(BoardTab.WEEK) }
    var board by remember { mutableStateOf<Board?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(tab) {
        if (tab == BoardTab.MINE) return@LaunchedEffect
        loading = true
        error = null
        container.syncScores()
        board = try {
            container.leaderboard.board(if (tab == BoardTab.ALL) "all" else "week", profile.playerId)
        } catch (e: Exception) {
            error = "Couldn't reach the leaderboard. Your runs are saved and will upload when you're online."
            null
        }
        loading = false
    }

    JungleBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Leaderboard", profile.coins, profile.gems, onBack)
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoardTab.entries.forEach { t ->
                    Text(
                        t.title,
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (t == tab) JungleColors.Banana else Color(0x33FFFFFF))
                            .clickable { tab = t }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = if (t == tab) Color(0xFF3E2723) else Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                LeagueBadge(profile.league, rank = profile.weeklyRank)
                Spacer(Modifier.width(10.dp))
                Text("Leagues reset every Monday • Bronze → Silver → Gold → Diamond → Legend", color = Color(0xFFC8E6C9), fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                when {
                    tab == BoardTab.MINE -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (profile.runHistory.isEmpty()) item { Text("No runs yet — go play!", color = Color.White) }
                        items(profile.runHistory, key = { it.id }) { r ->
                            val a = Animals.get(r.animalId)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(JungleColors.Card).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AnimalPortrait(a, Modifier.size(40.dp), running = false)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${r.score}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                    Text("${r.meters} m • ${r.food} ${a.food.displayName.lowercase()} • ${r.mode}" +
                                        (r.won?.let { if (it) " • WON" else " • lost" } ?: ""), color = Color(0xFFC8E6C9), fontSize = 12.sp)
                                }
                                Text(SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(r.time)), color = Color(0xFFA5D6A7), fontSize = 11.sp)
                            }
                        }
                    }
                    loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = JungleColors.Banana)
                    error != null -> Text(error!!, color = Color(0xFFFFCC80), modifier = Modifier.align(Alignment.Center))
                    else -> {
                        val b = board
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (b == null || b.entries.isEmpty()) item { Text("Be the first on the board this week!", color = Color.White) }
                            b?.let { bb ->
                                items(bb.entries, key = { "${it.rank}-${it.name}" }) { e ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                            .background(if (e.me) Color(0x55FFC107) else JungleColors.Card).padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            when (e.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#${e.rank}" },
                                            color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.width(48.dp), fontSize = 18.sp,
                                        )
                                        AnimalPortrait(Animals.get(e.animal), Modifier.size(40.dp), running = false)
                                        Spacer(Modifier.width(10.dp))
                                        Text(e.name + if (e.me) " (you)" else "", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        LeagueBadge(e.league)
                                        Spacer(Modifier.width(10.dp))
                                        Text("${e.score}", color = JungleColors.Banana, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                    }
                                }
                                if (bb.myRank != null && bb.entries.none { it.me }) {
                                    item {
                                        Text("You: #${bb.myRank} of ${bb.total} • ${bb.myScore}", color = JungleColors.Banana, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
