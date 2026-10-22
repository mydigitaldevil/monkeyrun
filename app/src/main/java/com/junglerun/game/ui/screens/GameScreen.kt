package com.junglerun.game.ui.screens

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.junglerun.game.AppContainer
import com.junglerun.game.ads.AdPlacement
import com.junglerun.game.core.Animals
import com.junglerun.game.data.Economy
import com.junglerun.game.data.MissionLogic
import com.junglerun.game.data.RunReward
import com.junglerun.game.game.CoachLevel
import com.junglerun.game.game.GameCallbacks
import com.junglerun.game.game.GameController
import com.junglerun.game.game.GameMode
import com.junglerun.game.game.GameView
import com.junglerun.game.game.GameWorld
import com.junglerun.game.game.PlayerSetup
import com.junglerun.game.game.RunResult
import com.junglerun.game.game.RunState
import com.junglerun.game.net.PeerResult
import com.junglerun.game.ui.components.AnimalPortrait
import com.junglerun.game.ui.components.GameButton
import com.junglerun.game.ui.components.NativeAdCard
import com.junglerun.game.ui.components.Panel
import com.junglerun.game.ui.components.LeagueBadge
import com.junglerun.game.ui.components.isPortrait
import com.junglerun.game.ui.theme.JungleColors
import kotlin.random.Random

@Composable
fun GameScreen(
    container: AppContainer,
    activity: Activity,
    mode: GameMode,
    p2Animal: String?,
    onExit: () -> Unit,
    onRestart: () -> Unit,
    onShop: () -> Unit,
    trialAnimal: String? = null,
    onTrial: (animalId: String) -> Unit = {},
    onAnimals: () -> Unit = {},
) {
    val repo = container.repository
    val networked = mode == GameMode.ONLINE || mode == GameMode.LAN
    val room = if (networked) container.room else null
    val main = remember { Handler(Looper.getMainLooper()) }

    var downResult by remember { mutableStateOf<RunResult?>(null) }
    var finalResults by remember { mutableStateOf<List<RunResult>?>(null) }
    var showResults by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var reward by remember { mutableStateOf<RunReward?>(null) }
    var doubled by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<com.junglerun.game.core.AnimalSpec?>(null) }

    val profileAtStart = remember { repo.current }
    val seed = remember { room?.state?.value?.seed ?: Random.nextLong() }

    val callbacks = remember {
        object : GameCallbacks {
            override fun onPlayerDown(index: Int, result: RunResult) {
                main.post { downResult = result }
            }

            override fun onAllFinished(results: List<RunResult>) {
                main.post { finalResults = results }
            }

            override fun onPauseRequested() {
                main.post { paused = true }
            }

            override fun onTutorialComplete() {
                main.post { repo.markTutorialDone() }
            }

            override fun onNetworkTick(world: GameWorld) {
                room?.sendState(world, alive = world.state == RunState.RUNNING || world.state == RunState.COUNTDOWN)
            }
        }
    }

    val controller = remember {
        val loadout = repo.loadoutFor(profileAtStart)
        val me = Animals.get(trialAnimal ?: profileAtStart.selected)
        val players = if (mode == GameMode.LOCAL_2P) {
            listOf(
                PlayerSetup("Player 1", me, loadout),
                PlayerSetup("Player 2", Animals.get(p2Animal ?: Animals.DEFAULT_ID), loadout),
            )
        } else {
            listOf(PlayerSetup(profileAtStart.settings.playerName, me, loadout))
        }
        container.analytics.runStart(mode.name.lowercase(), me.id)
        val coach = when {
            mode != GameMode.SOLO -> CoachLevel.NONE
            !profileAtStart.tutorialDone -> CoachLevel.TUTORIAL
            profileAtStart.totalRuns < 8 -> CoachLevel.HINTS
            else -> CoachLevel.NONE
        }
        GameController(mode, players, seed, callbacks, container.sound, coach)
    }

    // Networked races: stream the rival's ghost into the engine; no revives (fair race).
    LaunchedEffect(room) {
        room?.ghost?.collect { controller.ghost = it }
    }

    // Pause when the app goes to background (not in online races — the world keeps moving).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        container.ads.inGameplay = true
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE && !networked && finalResults == null) {
                controller.setPaused(true)
                paused = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            container.ads.inGameplay = false
            container.sound.menuMusic()
        }
    }

    BackHandler(enabled = finalResults == null) {
        controller.setPaused(true)
        paused = true
    }

    // A runner went down → revive prompt (solo) or straight to results (multiplayer).
    LaunchedEffect(downResult) {
        val r = downResult ?: return@LaunchedEffect
        if (mode != GameMode.SOLO) {
            if (networked) room?.sendDown(r)
            controller.finish(0)
            downResult = null
        }
    }

    // All runners finished → pay out, maybe show an interstitial, then show results.
    LaunchedEffect(finalResults) {
        val results = finalResults ?: return@LaunchedEffect
        container.ads.inGameplay = false
        val mine = results[0]
        container.analytics.runEnd(mode.name.lowercase(), mine.animalId, mine.score, mine.distanceMeters, mine.food)
        when (mode) {
            GameMode.SOLO -> {
                val r = repo.applyRun(mine, mode = if (trialAnimal != null) "trial" else "solo")
                reward = r
                if (r.newBest) container.sound.newBest()
                if (trialAnimal == null) suggestion = repo.animalToSuggest()
                container.syncScores()
            }
            GameMode.LOCAL_2P -> {
                reward = repo.applyRun(mine, mode = "local")
                repo.addCoins(results.drop(1).sumOf { it.coins })
            }
            else -> room?.markResults()
        }
        container.ads.maybeShowInterstitial(activity, repo.current.totalRuns) { showResults = true }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> GameView(ctx, controller).also { it.controllerCallbacks = callbacks } },
            onRelease = { it.stop() },
        )

        val down = downResult
        if (down != null && mode == GameMode.SOLO) {
            ReviveDialog(
                container, activity, down,
                onRevive = {
                    downResult = null
                    controller.revive(0)
                },
                onGiveUp = {
                    downResult = null
                    controller.finish(0)
                },
            )
        }

        if (paused && finalResults == null) {
            PauseOverlay(
                onResume = {
                    paused = false
                    controller.setPaused(false)
                },
                onRestart = if (networked) null else onRestart,
                onQuit = {
                    paused = false
                    controller.setPaused(false)
                    if (networked) controller.worlds.firstOrNull()?.let { room?.sendDown(it.result()) }
                    controller.worlds.indices.forEach { controller.finish(it) }
                },
            )
        }

        val results = finalResults
        if (results != null && showResults) {
            when (mode) {
                GameMode.SOLO -> SoloResults(
                    container, activity, results[0], reward, doubled,
                    onDouble = { doubled = true },
                    onRetry = onRestart, onHome = onExit, onShop = onShop,
                    suggestion = suggestion,
                    onTry = { id ->
                        repo.markTryPromptShown()
                        onTrial(id)
                    },
                    onDismissSuggestion = {
                        repo.markTryPromptShown()
                        suggestion = null
                    },
                    trial = trialAnimal != null && trialAnimal !in repo.current.owned,
                    onAnimals = onAnimals,
                )
                GameMode.LOCAL_2P -> LocalResults(controller, results, onRetry = onRestart, onHome = onExit)
                else -> RaceResults(container, results[0], onRematch = {
                    room?.backToLobby()
                    onExit()
                }, onLeave = {
                    room?.leave()
                    container.room = null
                    onExit()
                })
            }
        }
    }
}

// ---------------------------------------------------------------------- overlays

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ReviveDialog(
    container: AppContainer, activity: Activity, result: RunResult,
    onRevive: () -> Unit, onGiveUp: () -> Unit,
) {
    val profile by container.repository.profile.collectAsState()
    val adReady by container.ads.rewardedAvailable.collectAsState()
    val gemCost = Economy.reviveGemCost(result.revives)
    val timer = remember { Animatable(1f) }
    var adShowing by remember { mutableStateOf(false) }

    LaunchedEffect(adShowing) {
        if (adShowing) return@LaunchedEffect
        timer.animateTo(0f, tween((timer.value * 7000).toInt(), easing = LinearEasing))
        onGiveUp()
    }

    Scrim {
        Panel(Modifier.widthIn(max = 420.dp)) {
            Text("SECOND CHANCE?", style = MaterialTheme.typography.headlineMedium, color = JungleColors.Banana,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text("Score ${result.score}  •  ${result.distanceMeters} m", color = Color.White,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { timer.value }, modifier = Modifier.size(56.dp), color = JungleColors.Banana)
            }
            if (adReady) {
                GameButton("▶ WATCH AD TO CONTINUE", Modifier.fillMaxWidth(), brush = JungleColors.playButton, textColor = Color(0xFF3E2723), pulse = true) {
                    adShowing = true
                    container.ads.showRewarded(activity, AdPlacement.REVIVE) { ok ->
                        adShowing = false
                        if (ok) onRevive()
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            GameButton(
                "◆ $gemCost  CONTINUE", Modifier.fillMaxWidth(), brush = JungleColors.blueButton,
                enabled = profile.gems >= gemCost,
            ) {
                if (container.repository.spendGems(gemCost)) onRevive()
            }
            Spacer(Modifier.height(8.dp))
            GameButton("NO THANKS", Modifier.fillMaxWidth(), brush = JungleColors.redButton, height = 44.dp) { onGiveUp() }
        }
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onRestart: (() -> Unit)?, onQuit: () -> Unit) {
    Scrim {
        Panel(Modifier.width(320.dp)) {
            Text("PAUSED", style = MaterialTheme.typography.headlineMedium, color = JungleColors.Banana,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            GameButton("▶ RESUME", Modifier.fillMaxWidth(), brush = JungleColors.playButton, textColor = Color(0xFF3E2723), onClick = onResume)
            Spacer(Modifier.height(8.dp))
            if (onRestart != null) {
                GameButton("↻ RESTART", Modifier.fillMaxWidth(), onClick = onRestart)
                Spacer(Modifier.height(8.dp))
            }
            GameButton("✕ END RUN", Modifier.fillMaxWidth(), brush = JungleColors.redButton, onClick = onQuit)
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, color: Color = Color.White) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Color(0xFFC8E6C9), modifier = Modifier.weight(1f))
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SoloResults(
    container: AppContainer, activity: Activity, r: RunResult, reward: RunReward?, doubled: Boolean,
    onDouble: () -> Unit, onRetry: () -> Unit, onHome: () -> Unit, onShop: () -> Unit,
    suggestion: com.junglerun.game.core.AnimalSpec?, onTry: (String) -> Unit, onDismissSuggestion: () -> Unit,
    trial: Boolean, onAnimals: () -> Unit,
) {
    val adReady by container.ads.rewardedAvailable.collectAsState()
    val profile by container.repository.profile.collectAsState()
    val animal = Animals.get(r.animalId)
    val portrait = isPortrait()
    Scrim {
        val summary: @Composable (Modifier) -> Unit = { m ->
            Panel(m) {
                Text(
                    if (reward?.newBest == true) "🎉 NEW BEST!" else if (r.cause == null) "RUN OVER" else "CAUGHT!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (reward?.newBest == true) JungleColors.Banana else Color.White,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                )
                Text("${r.score}", fontSize = 44.sp, fontWeight = FontWeight.Black, color = Color.White,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    LeagueBadge(profile.league, rank = profile.weeklyRank)
                }
                Spacer(Modifier.height(6.dp))
                StatLine("Distance", "${r.distanceMeters} m")
                StatLine("${animal.food.displayName} eaten", "${r.food}")
                StatLine("Coins", "+${r.coins * (if (doubled) 2 else 1)}", JungleColors.Coin)
                if (r.gems > 0) StatLine("Gems", "+${r.gems}", JungleColors.Gem)
                reward?.missionsCompleted?.forEach {
                    StatLine("✓ ${MissionLogic.description(it)}", "+${MissionLogic.REWARD_PER_MISSION}", JungleColors.Lime)
                }
                if (reward?.dailyCompleted == true) StatLine("✓ Daily challenge complete!", "Claim in Missions", JungleColors.Banana)
                Spacer(Modifier.height(10.dp))
                if (!doubled && r.coins > 0 && adReady) {
                    GameButton("▶ DOUBLE COINS", Modifier.fillMaxWidth(), brush = JungleColors.playButton, textColor = Color(0xFF3E2723), pulse = true) {
                        container.ads.showRewarded(activity, AdPlacement.DOUBLE_COINS) { ok ->
                            if (ok) {
                                container.repository.addCoins(r.coins)
                                onDouble()
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameButton("↻ RETRY", Modifier.weight(1f), onClick = onRetry)
                    GameButton("⌂ HOME", Modifier.weight(1f), brush = JungleColors.blueButton, onClick = onHome)
                }
                Spacer(Modifier.height(8.dp))
                GameButton("🛒 SHOP", Modifier.fillMaxWidth(), brush = JungleColors.purpleButton, height = 44.dp, onClick = onShop)
            }
        }
        val side: @Composable (Modifier) -> Unit = { m ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    trial -> Panel(Modifier.fillMaxWidth()) {
                        Text("Enjoyed ${animal.displayName}?", color = JungleColors.Banana, fontWeight = FontWeight.Black)
                        AnimalPortrait(animal, Modifier.size(110.dp).align(Alignment.CenterHorizontally), mood = com.junglerun.game.art.Mood.HAPPY)
                        GameButton("🔓 UNLOCK NOW", Modifier.fillMaxWidth(), brush = JungleColors.playButton, textColor = Color(0xFF3E2723), onClick = onAnimals)
                    }
                    suggestion != null -> Panel(Modifier.fillMaxWidth()) {
                        Text("${animal.displayName.substringBefore(" ")} looks tired! 😴", color = JungleColors.Banana, fontWeight = FontWeight.Black)
                        Text("Try ${suggestion.displayName} — eats ${suggestion.food.displayName.lowercase()}, chased by the ${suggestion.chaser.displayName.lowercase()}!",
                            color = Color.White, fontSize = 13.sp)
                        AnimalPortrait(suggestion, Modifier.size(110.dp).align(Alignment.CenterHorizontally), mood = com.junglerun.game.art.Mood.HAPPY)
                        val free = suggestion.id !in profile.owned
                        GameButton(if (free) "▶ FREE TRIAL RUN" else "▶ PLAY AS ${suggestion.displayName.substringBefore(" ").uppercase()}",
                            Modifier.fillMaxWidth(), brush = JungleColors.playButton, textColor = Color(0xFF3E2723), pulse = true) {
                            if (!free) container.repository.select(suggestion.id)
                            onTry(suggestion.id)
                        }
                        Spacer(Modifier.height(4.dp))
                        GameButton("Maybe later", Modifier.fillMaxWidth(), height = 38.dp, brush = JungleColors.greenButton, onClick = onDismissSuggestion)
                    }
                    else -> AnimalPortrait(animal, Modifier.size(140.dp), mood = if (reward?.newBest == true) com.junglerun.game.art.Mood.HAPPY else com.junglerun.game.art.Mood.HURT)
                }
                NativeAdCard(container.ads)
            }
        }
        if (portrait) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                summary(Modifier.fillMaxWidth())
                side(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                summary(Modifier.width(360.dp).verticalScroll(rememberScrollState()))
                side(Modifier.width(300.dp))
            }
        }
    }
}

@Composable
private fun LocalResults(controller: GameController, results: List<RunResult>, onRetry: () -> Unit, onHome: () -> Unit) {
    val winner = when {
        results[0].score > results[1].score -> 0
        results[1].score > results[0].score -> 1
        else -> -1
    }
    Scrim {
        Panel(Modifier.widthIn(max = 560.dp)) {
            Text(
                if (winner < 0) "IT'S A DRAW!" else "PLAYER ${winner + 1} WINS!",
                style = MaterialTheme.typography.headlineMedium, color = JungleColors.Banana,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                results.forEachIndexed { i, r ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimalPortrait(Animals.get(r.animalId), Modifier.size(90.dp), running = i == winner)
                        Text(controller.players[i].name, color = if (i == 0) Color(0xFF80D8FF) else Color(0xFFFF8A80), fontWeight = FontWeight.Black)
                        StatLine("Score", "${r.score}")
                        StatLine("Distance", "${r.distanceMeters} m")
                        StatLine("Treats", "${r.food}")
                        StatLine("Coins", "+${r.coins}", JungleColors.Coin)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton("↻ REMATCH", Modifier.weight(1f), brush = JungleColors.playButton, textColor = Color(0xFF3E2723), onClick = onRetry)
                GameButton("⌂ HOME", Modifier.weight(1f), brush = JungleColors.blueButton, onClick = onHome)
            }
        }
    }
}

@Composable
private fun RaceResults(container: AppContainer, mine: RunResult, onRematch: () -> Unit, onLeave: () -> Unit) {
    val room = container.room
    val state = room?.state?.collectAsState()?.value
    val ghost = room?.ghost?.collectAsState()?.value
    val peer: PeerResult? = state?.peerResult
    var rewarded by remember { mutableStateOf<RunReward?>(null) }

    LaunchedEffect(peer) {
        if (peer != null && rewarded == null) {
            rewarded = container.repository.applyRun(mine, wonRace = mine.score > peer.score, mode = if (state?.online == true) "online" else "lan", race = true)
            container.syncScores()
        }
    }

    Scrim {
        Panel(Modifier.widthIn(max = 520.dp)) {
            val title = when {
                peer == null -> "WAITING FOR ${state?.peerName?.uppercase() ?: "RIVAL"}…"
                mine.score > peer.score -> "YOU WIN! 🏆"
                mine.score < peer.score -> "YOU LOSE"
                else -> "DRAW!"
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = JungleColors.Banana,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            state?.error?.let { Text(it, color = Color(0xFFFFCC80), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("YOU", color = Color(0xFF80D8FF), fontWeight = FontWeight.Black)
                    StatLine("Score", "${mine.score}")
                    StatLine("Distance", "${mine.distanceMeters} m")
                    StatLine("Treats", "${mine.food}")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state?.peerName ?: "Rival", color = Color(0xFFFF8A80), fontWeight = FontWeight.Black)
                    if (peer != null) {
                        StatLine("Score", "${peer.score}")
                        StatLine("Distance", "${peer.meters} m")
                        StatLine("Treats", "${peer.food}")
                    } else {
                        StatLine("Live score", "${ghost?.score ?: 0}")
                        CircularProgressIndicator(Modifier.padding(8.dp).size(28.dp), color = JungleColors.Banana)
                    }
                }
            }
            rewarded?.let { StatLine("Coins earned", "+${it.coins}", JungleColors.Coin) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton("↻ REMATCH", Modifier.weight(1f), brush = JungleColors.playButton, textColor = Color(0xFF3E2723),
                    enabled = peer != null && state?.error == null, onClick = onRematch)
                GameButton("LEAVE", Modifier.weight(1f), brush = JungleColors.redButton, onClick = onLeave)
            }
        }
    }
}
