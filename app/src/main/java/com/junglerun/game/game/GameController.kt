package com.junglerun.game.game

import com.junglerun.game.audio.Voice
import com.junglerun.game.core.AnimalSpec
import com.junglerun.game.core.Chaser
import com.junglerun.game.core.Shape
import java.util.concurrent.ConcurrentLinkedQueue

enum class GameMode { SOLO, LOCAL_2P, ONLINE, LAN }

data class PlayerSetup(val name: String, val animal: AnimalSpec, val loadout: Loadout)

/** Sound/haptic hooks, implemented by the app layer so the engine stays UI-agnostic. */
interface FeedbackSink {
    fun play(event: FeedbackEvent) {}
    fun voice(v: Voice, volume: Float = 0.9f) {}
    fun music(state: MusicState) {}
}

enum class FeedbackEvent { JUMP, SLIDE, FOOD, GOLDEN, GEM, POWERUP, STUMBLE, SMASH, ABILITY, DOWN, LAND, COUNT, GO }

/** What the adaptive music should reflect right now. */
class MusicState(var running: Boolean = false, var danger: Float = 0f, var power: Boolean = false, var speed01: Float = 0f)

fun voiceOf(shape: Shape): Voice = when (shape) {
    Shape.MONKEY -> Voice.MONKEY
    Shape.RABBIT -> Voice.RABBIT
    Shape.PANDA -> Voice.PANDA
    Shape.LION -> Voice.LION
    Shape.BEAR -> Voice.BEAR
    Shape.ELEPHANT -> Voice.ELEPHANT
    Shape.CHEETAH -> Voice.CHEETAH
    Shape.GIRAFFE -> Voice.GIRAFFE
}

fun voiceOf(chaser: Chaser): Voice = when (chaser) {
    Chaser.LION -> Voice.LION
    Chaser.TIGER -> Voice.TIGER
    Chaser.FOX -> Voice.FOX
    Chaser.RHINO -> Voice.RHINO
    Chaser.BEES -> Voice.BEES
    Chaser.CROCODILE -> Voice.CROC
    Chaser.HYENA -> Voice.HYENA
}

interface GameCallbacks {
    /** A runner went down. In solo/online the UI may offer a revive; call [GameController.revive] or [GameController.finish]. */
    fun onPlayerDown(index: Int, result: RunResult) {}

    /** Every runner is finished — show results. */
    fun onAllFinished(results: List<RunResult>) {}

    /** The first-time tutorial was completed. */
    fun onTutorialComplete() {}

    fun onPauseRequested() {}

    /** ~12 times a second for networked modes so the session can broadcast our state. */
    fun onNetworkTick(world: GameWorld) {}
}

/**
 * Owns the worlds for one match and is driven by [GameView]'s game thread.
 * UI threads talk to it only through [post]ed commands, so there is no shared mutable state.
 */
class GameController(
    val mode: GameMode,
    val players: List<PlayerSetup>,
    val seed: Long,
    private val callbacks: GameCallbacks,
    private val feedback: FeedbackSink,
    coach: CoachLevel = CoachLevel.NONE,
) {
    val worlds: List<GameWorld> = players.map { p ->
        GameWorld(p.animal, seed, p.loadout, countdown = 3f, coach = if (players.size == 1) coach else CoachLevel.NONE)
    }
    private val musicState = MusicState()
    private val voiceTimer = FloatArray(players.size) { 3.5f + it * 2f }
    private var chaserVoiceCooldown = 0f
    private var lastCount = 4

    @Volatile var ghost: GhostState? = null
    @Volatile var paused = false
        private set

    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    private val notifiedDown = BooleanArray(worlds.size)
    private var allFinishedSent = false
    private var netAccumulator = 0f

    init {
        worlds.forEach { w ->
            w.listener = object : WorldListener {
                override fun onJump() = feedback.play(FeedbackEvent.JUMP)
                override fun onSlide() = feedback.play(FeedbackEvent.SLIDE)
                override fun onCollect(kind: PickupKind) = feedback.play(
                    when (kind) {
                        PickupKind.FOOD -> FeedbackEvent.FOOD
                        PickupKind.GOLDEN_FOOD -> FeedbackEvent.GOLDEN
                        PickupKind.GEM -> FeedbackEvent.GEM
                        else -> FeedbackEvent.POWERUP
                    },
                )
                override fun onStumble() = feedback.play(FeedbackEvent.STUMBLE)
                override fun onSmash() = feedback.play(FeedbackEvent.SMASH)
                override fun onAbility() {
                    feedback.play(FeedbackEvent.ABILITY)
                    feedback.voice(voiceOf(w.animal.shape))
                }
                override fun onDown(cause: DeathCause) {
                    feedback.play(FeedbackEvent.DOWN)
                    if (cause == DeathCause.CAUGHT) feedback.voice(voiceOf(w.animal.chaser), 1f)
                }
                override fun onLand() = feedback.play(FeedbackEvent.LAND)
                override fun onChaserClose() {
                    if (chaserVoiceCooldown <= 0f) {
                        chaserVoiceCooldown = 6f
                        feedback.voice(voiceOf(w.animal.chaser), 0.8f)
                    }
                }
                override fun onTutorialComplete() = callbacks.onTutorialComplete()
            }
        }
    }

    fun post(cmd: () -> Unit) {
        commands.add(cmd)
    }

    fun jump(i: Int) = post { worlds.getOrNull(i)?.jump() }
    fun slide(i: Int) = post { worlds.getOrNull(i)?.slide() }
    fun ability(i: Int) = post { worlds.getOrNull(i)?.useAbility() }
    fun setPaused(p: Boolean) = post { paused = p }

    fun revive(i: Int) = post {
        worlds.getOrNull(i)?.revive()
        notifiedDown[i] = false
    }

    fun finish(i: Int) = post { worlds.getOrNull(i)?.finish() }

    fun update(dt: Float) {
        while (true) {
            val cmd = commands.poll() ?: break
            cmd()
        }
        if (paused) return

        audioTick(dt)
        worlds.forEachIndexed { i, w ->
            w.update(dt)
            if (w.state == RunState.DOWN && !notifiedDown[i]) {
                notifiedDown[i] = true
                if (mode == GameMode.LOCAL_2P) {
                    w.finish()
                } else {
                    callbacks.onPlayerDown(i, w.result())
                }
            }
        }

        if (mode == GameMode.ONLINE || mode == GameMode.LAN) {
            netAccumulator += dt
            if (netAccumulator >= 0.08f) {
                netAccumulator = 0f
                callbacks.onNetworkTick(worlds[0])
            }
        }

        if (!allFinishedSent && worlds.all { it.state == RunState.FINISHED }) {
            allFinishedSent = true
            callbacks.onAllFinished(worlds.map { it.result() })
        }
    }

    /** Animal calls every few seconds, countdown beeps and the adaptive music state. */
    private fun audioTick(dt: Float) {
        chaserVoiceCooldown -= dt
        val w0 = worlds[0]
        if (w0.state == RunState.COUNTDOWN && w0.distance < 1f) {
            val n = kotlin.math.ceil(w0.countdown).toInt()
            if (n != lastCount && n >= 0) {
                lastCount = n
                feedback.play(if (n == 0) FeedbackEvent.GO else FeedbackEvent.COUNT)
            }
        }
        worlds.forEachIndexed { i, w ->
            if (w.state != RunState.RUNNING) return@forEachIndexed
            voiceTimer[i] -= dt
            if (voiceTimer[i] <= 0f) {
                voiceTimer[i] = 7f + ((w.distance * 13).toInt() % 7)
                feedback.voice(voiceOf(w.animal.shape), 0.75f)
            }
        }
        musicState.running = worlds.any { it.state == RunState.RUNNING || it.state == RunState.COUNTDOWN }
        musicState.danger = worlds.maxOf { if (it.state == RunState.RUNNING) it.chase else 0f }
        musicState.power = worlds.any { it.abilityActive || it.magnetTime > 0f || it.doublerTime > 0f || it.shieldTime > 0f }
        musicState.speed01 = ((w0.speed - Physics.BASE_SPEED) / (Physics.MAX_SPEED - Physics.BASE_SPEED)).coerceIn(0f, 1f)
        feedback.music(musicState)
    }

    /** Viewport layout: one full-screen view, or top/bottom halves for same-device 2 player. */
    fun layout(width: Int, height: Int, out: List<Box>) {
        if (worlds.size == 1) {
            out[0].set(0f, 0f, width.toFloat(), height.toFloat())
        } else {
            val half = height / 2f
            out[0].set(0f, 0f, width.toFloat(), half - 2f)
            out[1].set(0f, half + 2f, width.toFloat(), height.toFloat())
        }
    }
}
