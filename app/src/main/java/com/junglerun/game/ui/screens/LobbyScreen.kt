package com.junglerun.game.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junglerun.game.AppContainer
import com.junglerun.game.core.Animals
import com.junglerun.game.net.RoomPhase
import com.junglerun.game.ui.components.AnimalPortrait
import com.junglerun.game.ui.components.GameButton
import com.junglerun.game.ui.components.JungleBackground
import com.junglerun.game.ui.components.Panel
import com.junglerun.game.ui.theme.JungleColors

@Composable
fun LobbyScreen(container: AppContainer, onLeave: () -> Unit, onStart: (online: Boolean) -> Unit) {
    val room = container.room
    if (room == null) {
        LaunchedEffect(Unit) { onLeave() }
        return
    }
    val state by room.state.collectAsState()
    val profile by container.repository.profile.collectAsState()
    val myAnimal = remember(profile.selected) { Animals.get(profile.selected) }

    fun leave() {
        room.leave()
        container.room = null
        onLeave()
    }

    BackHandler { leave() }

    LaunchedEffect(room) {
        room.starts.collect { onStart(state.online) }
    }
    LaunchedEffect(state.phase) {
        if (state.phase == RoomPhase.CLOSED) onLeave()
    }

    JungleBackground {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Panel(Modifier.widthIn(max = 640.dp)) {
                Text(
                    if (state.online) "ONLINE ROOM" else "WI-FI ROOM",
                    color = JungleColors.Banana, style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                )
                state.code?.let {
                    Text("Room code: $it", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(10.dp))
                when (state.phase) {
                    RoomPhase.CONNECTING, RoomPhase.WAITING_FOR_PEER -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(28.dp), color = JungleColors.Banana)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                if (state.phase == RoomPhase.CONNECTING) "Connecting…"
                                else "Waiting for a friend to join — share the code!",
                                color = Color.White,
                            )
                        }
                    }
                    RoomPhase.ERROR -> {
                        Text(state.error ?: "Something went wrong", color = Color(0xFFFF8A80),
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlayerCard(profile.settings.playerName + " (you)", myAnimal.id, state.localReady, Modifier.weight(1f))
                            Text("VS", color = JungleColors.Banana, fontSize = 32.sp, fontWeight = FontWeight.Black)
                            PlayerCard(state.peerName ?: "Rival", state.peerAnimal ?: Animals.DEFAULT_ID, state.peerReady, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Same course for both • highest score wins • no revives",
                            color = Color(0xFFC8E6C9), fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GameButton(
                                if (state.localReady) "NOT READY" else "I'M READY", Modifier.weight(1f),
                                brush = if (state.localReady) JungleColors.redButton else JungleColors.greenButton,
                            ) { room.setReady(!state.localReady, myAnimal.id) }
                            if (state.isHost) {
                                GameButton(
                                    "▶ START RACE", Modifier.weight(1f), brush = JungleColors.playButton, textColor = Color(0xFF3E2723),
                                    enabled = state.localReady && state.peerReady,
                                ) { room.startMatch() }
                            } else {
                                Text("Host starts the race", color = Color.White, modifier = Modifier.weight(1f).padding(top = 14.dp), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                GameButton("LEAVE ROOM", Modifier.fillMaxWidth(), brush = JungleColors.redButton, height = 44.dp) { leave() }
            }
        }
    }
}

@Composable
private fun PlayerCard(name: String, animalId: String, ready: Boolean, modifier: Modifier = Modifier) {
    val a = Animals.get(animalId)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimalPortrait(a, Modifier.size(100.dp), running = ready)
        Text(name, color = Color.White, fontWeight = FontWeight.Bold)
        Text(a.displayName, color = Color(0xFFC8E6C9), fontSize = 12.sp)
        Text(if (ready) "READY ✓" else "Not ready", color = if (ready) JungleColors.Lime else Color(0xFFFFCC80), fontWeight = FontWeight.Black)
    }
}
