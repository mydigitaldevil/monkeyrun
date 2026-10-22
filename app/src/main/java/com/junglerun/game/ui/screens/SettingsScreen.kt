package com.junglerun.game.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.junglerun.game.AppContainer
import com.junglerun.game.BuildConfig
import com.junglerun.game.MainActivity
import com.junglerun.game.ui.components.GameButton
import com.junglerun.game.ui.components.JungleBackground
import com.junglerun.game.ui.components.Panel
import com.junglerun.game.ui.components.TopBar
import com.junglerun.game.ui.theme.JungleColors
import kotlinx.coroutines.launch

const val PRIVACY_POLICY_URL = "https://example.com/jungle-run/privacy"

@Composable
fun SettingsScreen(container: AppContainer, activity: Activity, onBack: () -> Unit) {
    val profile by container.repository.profile.collectAsState()
    val s = profile.settings
    var name by remember { mutableStateOf(s.playerName) }
    val scope = rememberCoroutineScope()

    fun toggle(update: (com.junglerun.game.data.Settings) -> com.junglerun.game.data.Settings) {
        container.repository.updateSettings(update)
        container.applySettings()
    }

    JungleBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar("Settings", profile.coins, profile.gems, onBack)
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    SettingRow("Sound effects", s.sound) { v -> toggle { it.copy(sound = v) } }
                    SettingRow("Music", s.music) { v -> toggle { it.copy(music = v) } }
                    SettingRow("Vibration", s.haptics) { v -> toggle { it.copy(haptics = v) } }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(14) },
                        label = { Text("Player name (shown in multiplayer)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    GameButton("SAVE NAME", Modifier.fillMaxWidth(), height = 44.dp) {
                        val clean = name.trim().ifEmpty { "Player" }
                        container.repository.updateSettings { it.copy(playerName = clean) }
                        Toast.makeText(activity, "Saved", Toast.LENGTH_SHORT).show()
                    }
                }
                Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Text("Account & privacy", color = JungleColors.Banana, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    GameButton("RESTORE PURCHASES", Modifier.fillMaxWidth(), height = 44.dp, brush = JungleColors.blueButton) {
                        scope.launch {
                            container.billing.restorePurchases()
                            Toast.makeText(activity, "Purchases restored", Toast.LENGTH_SHORT).show()
                        }
                    }
                    val consent = (activity as? MainActivity)?.consent
                    if (consent?.privacyOptionsRequired == true) {
                        Spacer(Modifier.height(8.dp))
                        GameButton("AD PRIVACY OPTIONS", Modifier.fillMaxWidth(), height = 44.dp, brush = JungleColors.blueButton) {
                            consent.showPrivacyOptions(activity)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    GameButton("PRIVACY POLICY", Modifier.fillMaxWidth(), height = 44.dp, brush = JungleColors.blueButton) {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Ads removed: ${if (profile.adsRemoved()) "Yes" else "No"}   •   VIP: ${if (profile.isVip()) "Active" else "No"}",
                        color = Color.White,
                    )
                    Text("Version ${BuildConfig.VERSION_NAME}", color = Color(0xFFA5D6A7))
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = JungleColors.Lime),
        )
    }
}
