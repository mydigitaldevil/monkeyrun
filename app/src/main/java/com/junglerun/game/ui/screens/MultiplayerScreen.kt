package com.junglerun.game.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junglerun.game.AppContainer
import com.junglerun.game.BuildConfig
import com.junglerun.game.core.Animals
import com.junglerun.game.net.LanDiscovery
import com.junglerun.game.net.LanGuest
import com.junglerun.game.net.LanHost
import com.junglerun.game.net.LanRoom
import com.junglerun.game.net.OnlineAction
import com.junglerun.game.net.OnlineTransport
import com.junglerun.game.net.RoomSession
import com.junglerun.game.ui.components.AnimalPortrait
import com.junglerun.game.ui.components.GameButton
import com.junglerun.game.ui.components.JungleBackground
import com.junglerun.game.ui.components.Panel
import com.junglerun.game.ui.components.TopBar
import com.junglerun.game.ui.theme.JungleColors

@Composable
fun MultiplayerScreen(
    container: AppContainer,
    activity: Activity,
    onBack: () -> Unit,
    onLocalMatch: (p2Animal: String) -> Unit,
    onRoomOpened: () -> Unit,
) {
    val profile by container.repository.profile.collectAsState()
    val name = profile.settings.playerName
    val discovery = remember { LanDiscovery(activity) }
    val lanRooms by discovery.rooms.collectAsState()
    var p2 by remember { mutableStateOf(Animals.DEFAULT_ID) }
    var lanFilter by remember { mutableStateOf("") }
    var onlineCode by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        discovery.start()
        onDispose { discovery.stop() }
    }

    fun open(session: RoomSession) {
        container.room?.leave()
        container.room = session
        onRoomOpened()
    }

    fun hostLan() {
        discovery.stop()
        val s = RoomSession(name, profile.selected, online = false)
        val host = LanHost(activity.applicationContext, name, s)
        s.attach(host)
        host.open()
        open(s)
    }

    fun joinLan(room: LanRoom) {
        discovery.stop()
        val s = RoomSession(name, profile.selected, online = false)
        val guest = LanGuest(room, s)
        s.attach(guest)
        guest.open()
        open(s)
    }

    fun online(action: OnlineAction, code: String? = null) {
        val s = RoomSession(name, profile.selected, online = true)
        val t = OnlineTransport(BuildConfig.ONLINE_SERVER_URL, action, code, name, s)
        s.attach(t)
        t.open()
        open(s)
    }

    JungleBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Multiplayer", profile.coins, profile.gems, onBack)
            Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 1) Same device split-screen
                Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Text("SAME DEVICE", color = JungleColors.Banana, fontWeight = FontWeight.Black)
                    Text("2 players, one phone. Top half vs bottom half — tap to jump, swipe down to slide.", color = Color(0xFFC8E6C9), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Player 2 picks an animal:", color = Color.White, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(Animals.all.filter { it.id in profile.owned }, key = { it.id }) { a ->
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (p2 == a.id) Color(0x55FFC107) else Color(0x22FFFFFF))
                                    .border(if (p2 == a.id) 2.dp else 0.dp, JungleColors.Banana, RoundedCornerShape(12.dp))
                                    .clickable { p2 = a.id }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AnimalPortrait(a, Modifier.size(56.dp), running = p2 == a.id)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    GameButton("▶ START 2 PLAYER", Modifier.fillMaxWidth(), brush = JungleColors.playButton, textColor = Color(0xFF3E2723)) {
                        onLocalMatch(p2)
                    }
                }

                // 2) Local Wi-Fi room
                Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Text("WI-FI ROOM", color = JungleColors.Banana, fontWeight = FontWeight.Black)
                    Text("Play with a friend on the same Wi-Fi or hotspot. No internet needed.", color = Color(0xFFC8E6C9), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    GameButton("+ CREATE ROOM", Modifier.fillMaxWidth(), brush = JungleColors.greenButton) { hostLan() }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lanFilter,
                        onValueChange = { lanFilter = it.filter(Char::isDigit).take(4) },
                        label = { Text("Room code (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    val visible = lanRooms.filter { lanFilter.isEmpty() || it.code.startsWith(lanFilter) }
                    if (visible.isEmpty()) {
                        Text("Searching for rooms nearby…", color = Color(0xFFA5D6A7), fontSize = 13.sp)
                    }
                    visible.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp)).background(Color(0x22FFFFFF)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Room ${r.code}", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Host: ${r.hostName}", color = Color(0xFFC8E6C9), fontSize = 12.sp)
                            }
                            GameButton("JOIN", height = 38.dp, brush = JungleColors.playButton, textColor = Color(0xFF3E2723)) { joinLan(r) }
                        }
                    }
                }

                // 3) Online
                Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Text("ONLINE", color = JungleColors.Banana, fontWeight = FontWeight.Black)
                    Text("Race anyone over the internet, or a friend with a private room code.", color = Color(0xFFC8E6C9), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    GameButton("⚡ QUICK MATCH", Modifier.fillMaxWidth(), brush = JungleColors.playButton, textColor = Color(0xFF3E2723)) {
                        online(OnlineAction.QUICK_MATCH)
                    }
                    Spacer(Modifier.height(8.dp))
                    GameButton("+ PRIVATE ROOM", Modifier.fillMaxWidth(), brush = JungleColors.blueButton) { online(OnlineAction.CREATE) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = onlineCode,
                        onValueChange = { onlineCode = it.uppercase().filter(Char::isLetterOrDigit).take(5) },
                        label = { Text("Friend's room code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    GameButton("JOIN ROOM", Modifier.fillMaxWidth(), enabled = onlineCode.length >= 4) {
                        online(OnlineAction.JOIN, onlineCode)
                    }
                }
            }
        }
    }
}
