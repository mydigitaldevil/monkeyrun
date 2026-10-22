package com.junglerun.game.game

import android.graphics.RectF
import com.junglerun.game.core.AnimalSpec
import java.util.concurrent.ConcurrentLinkedQueue

enum class GameMode { SOLO, LOCAL_2P, ONLINE, LAN }

data class PlayerSetup(val name: String, val animal: AnimalSpec, val loadout: Loadout)

/** Sound/haptic hooks, implemented by the app layer so the engine stays UI-agnostic. */
interface FeedbackSink {
    fun play(event: FeedbackEvent) {}
}

enum class FeedbackEvent { JUMP, SLIDE, FOOD, GOLDEN, GEM, POWERUP, STUMBLE, SMASH, ABILITY, DOWN }

interface GameCallbacks {
    /** A runner went down. In solo/online the UI may offer a revive; call [GameController.revive] or [GameController.finish]. */
    fun onPlayerDown(index: Int, result: RunResult) {}

    /** Every runner is finished — show results. */
    fun onAllFinished(results: List<RunResult>) {}

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
) {
    val worlds: List<GameWorld> = players.map { p ->
        GameWorld(p.animal, seed, p.loadout, countdown = 3f)
    }

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
                override fun onAbility() = feedback.play(FeedbackEvent.ABILITY)
                override fun onDown(cause: DeathCause) = feedback.play(FeedbackEvent.DOWN)
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

    /** Viewport layout: one full-screen view, or top/bottom halves for same-device 2 player. */
    fun layout(width: Int, height: Int, out: List<RectF>) {
        if (worlds.size == 1) {
            out[0].set(0f, 0f, width.toFloat(), height.toFloat())
        } else {
            val half = height / 2f
            out[0].set(0f, 0f, width.toFloat(), half - 2f)
            out[1].set(0f, half + 2f, width.toFloat(), height.toFloat())
        }
    }
}
