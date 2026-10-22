package com.junglerun.game.game

import com.junglerun.game.art.CharacterArt
import com.junglerun.game.art.Mood
import com.junglerun.game.art.Pose
import com.junglerun.game.art.Theme
import com.junglerun.game.art.View
import com.junglerun.game.art.WorldArt
import com.junglerun.game.core.Animals
import com.junglerun.game.core.Biome
import com.junglerun.game.gfx.Align
import com.junglerun.game.gfx.Colors
import com.junglerun.game.gfx.GPath
import com.junglerun.game.gfx.Gfx
import com.junglerun.game.gfx.Gradient
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What the opponent is doing in an online/LAN race, drawn as a translucent "ghost". */
data class GhostState(
    val name: String,
    val animalId: String,
    val distance: Float,
    val y: Float,
    val sliding: Boolean,
    val alive: Boolean,
    val score: Long,
)

class ViewportStyle(
    val label: String? = null,
    val labelColor: Int = 0xFFFFFFFF.toInt(),
    val showPause: Boolean = true,
    val ghost: GhostState? = null,
)

enum class Button { JUMP, SLIDE, ABILITY, PAUSE }

/** Screen rectangle without Android types (the renderer also runs on desktop for previews). */
class Box(var l: Float = 0f, var t: Float = 0f, var r: Float = 0f, var b: Float = 0f) {
    val w get() = r - l
    val h get() = b - t
    val cx get() = (l + r) / 2f
    val cy get() = (t + b) / 2f
    fun set(l: Float, t: Float, r: Float, b: Float): Box { this.l = l; this.t = t; this.r = r; this.b = b; return this }
    fun contains(x: Float, y: Float) = x in l..r && y in t..b
}

/**
 * Draws one [GameWorld] into a viewport (split-screen just calls [draw] twice). Works in landscape
 * and portrait: the world scales to fit ~115 units of track width, and in portrait the touch
 * controls get their own band under the ground.
 */
class GameRenderer {
    private val chars = CharacterArt()
    private val world = WorldArt()
    private val v = View()
    private val p = GPath()
    private val tmp = Box()
    private val pits = FloatArray(32)

    // ------------------------------------------------------------------ layout

    private fun portrait(vp: Box) = vp.h > vp.w * 1.15f

    /** Positions the camera for [vp]; shared by drawing and input. */
    fun layout(vp: Box, out: View = v): View {
        out.left = vp.l; out.top = vp.t; out.right = vp.r; out.bottom = vp.b
        if (portrait(vp)) {
            out.s = min(vp.h / 100f, vp.w / 92f)
            out.groundY = vp.b - vp.h * 0.31f
            out.playerX = vp.l + vp.w * 0.27f
        } else {
            out.s = min(vp.h / 82f, vp.w / 110f)
            out.groundY = vp.t + vp.h * 0.8f
            out.playerX = vp.l + min(vp.w * 0.28f, 42f * out.s)
        }
        return out
    }

    private fun btnRadius(vp: Box) = min(vp.w, vp.h) * (if (portrait(vp)) 0.115f else 0.095f)

    fun button(b: Button, vp: Box, out: Box = tmp): Box {
        val r = btnRadius(vp)
        val view = layout(vp, View())
        val cy = if (portrait(vp)) (view.groundY + vp.b) / 2f + r * 0.15f else vp.b - r * 1.3f
        return when (b) {
            Button.SLIDE -> out.set(vp.l + r * 0.45f, cy - r, vp.l + r * 2.45f, cy + r)
            Button.JUMP -> out.set(vp.r - r * 2.45f, cy - r, vp.r - r * 0.45f, cy + r)
            Button.ABILITY -> {
                val ar = r * 0.8f
                val cx = if (portrait(vp)) vp.cx else vp.r - r * 3.8f
                out.set(cx - ar, cy - ar, cx + ar, cy + ar)
            }
            Button.PAUSE -> {
                val pr = r * 0.48f
                out.set(vp.r - pr * 2.4f, vp.t + pr * 0.5f, vp.r - pr * 0.4f, vp.t + pr * 2.5f)
            }
        }
    }

    // ------------------------------------------------------------------ frame

    fun draw(g: Gfx, w: GameWorld, vp: Box, style: ViewportStyle) {
        layout(vp)
        v.camX = w.distance
        v.time = w.animTime
        w.viewAhead = (vp.r - v.playerX) / v.s
        val th = Theme.of(w.biome)

        g.save()
        g.clipRect(vp.l, vp.t, vp.r, vp.b)
        world.background(g, th, w.biome, v, w.distance)

        var n = 0
        for (h in w.hazards) {
            if (h.kind != HazardKind.PIT || !h.active) continue
            val l = v.sx(h.x)
            val r = v.sx(h.x + h.w)
            if (r < vp.l || l > vp.r || n >= pits.size / 2) continue
            pits[n * 2] = l
            pits[n * 2 + 1] = r
            n++
        }
        world.ground(g, th, w.biome, v, pits, n)
        if (w.biome == Biome.RIVERBANK) {
            for (h in w.hazards) if (h.kind == HazardKind.PIT && h.variant == 0) world.crocEyes(g, v, v.sx(h.x + h.w / 2f))
        }

        drawHazards(g, w, th)
        drawPickups(g, w)
        style.ghost?.let { drawGhost(g, w, it) }
        drawChaser(g, w)
        drawRunner(g, w)
        drawParticles(g, w)
        drawHint(g, w, vp)
        drawHud(g, w, vp, style)
        g.restore()
    }

    // ------------------------------------------------------------------ hazards

    private fun drawHazards(g: Gfx, w: GameWorld, th: Theme) {
        val s = v.s
        for (h in w.hazards) {
            if (!h.active || h.smashed || h.kind == HazardKind.PIT) continue
            val l = v.sx(h.x)
            if (l > v.right + 20f * s || v.sx(h.x + h.w) < v.left - 20f * s) continue
            when (h.kind) {
                HazardKind.FALLING -> {
                    val cx = v.sx(h.x + h.w / 2f)
                    if (h.y > 0.5f) world.shadow(g, cx, v.groundY, (1.5f + (1f - min(1f, h.y / 70f)) * 2.5f) * s, 0.35f)
                    g.save(); g.translate(cx, v.sy(h.y + h.h / 2f)); g.scale(s, s)
                    val spin = h.y * 9f
                    when (w.biome) {
                        Biome.JUNGLE, Biome.RIVERBANK, Biome.BAMBOO_FOREST -> world.coconut(g, h.h / 2f, spin)
                        Biome.PINE_FOREST -> world.pinecone(g, h.h / 2f, spin)
                        else -> world.boulder(g, h.h / 2f, spin)
                    }
                    g.restore()
                    continue
                }
                HazardKind.MOVING -> {
                    val cx = v.sx(h.x + h.w / 2f)
                    world.shadow(g, cx, v.groundY, 3.5f * s, 0.25f)
                    if (w.biome == Biome.SAVANNA || w.biome == Biome.MEADOW) {
                        chars.drawBoar(g, cx, v.groundY, s, h.phase, w.animTime)
                    } else {
                        g.save(); g.translate(cx, v.sy(h.h / 2f)); g.scale(s, s)
                        world.boulder(g, h.h / 2f * 1.1f, -h.phase * 25f)
                        g.restore()
                    }
                    // dust trail
                    for (i in 0 until 3) {
                        val k = (w.animTime * 3f + i * 0.33f) % 1f
                        g.fillCircle(cx + (4f + k * 6f) * s, v.groundY - (0.6f + k) * s, (0.6f + k * 1.2f) * s, Colors.withAlpha(0xFFFFFFFF.toInt(), 0.5f * (1f - k)))
                    }
                    continue
                }
                HazardKind.FLYER -> {
                    val cx = v.sx(h.x + h.w / 2f)
                    val cy = v.sy(h.y + h.h / 2f)
                    if (w.biome == Biome.PINE_FOREST || w.biome == Biome.MEADOW) {
                        chars.drawChaser(g, com.junglerun.game.core.Chaser.BEES, cx - 1f * s, cy + 6.5f * s * 0.5f, s * 0.45f, 0f, w.animTime)
                    } else {
                        chars.drawBird(g, cx, cy, s * 0.9f, w.animTime + h.phase, parrot = w.biome == Biome.JUNGLE || w.biome == Biome.RIVERBANK)
                    }
                    continue
                }
                else -> Unit
            }
            g.save()
            g.translate(l, v.sy(h.y))
            g.scale(s, s)
            when (h.kind) {
                HazardKind.LOW -> when (w.biome) {
                    Biome.MEADOW -> if (h.variant == 2) world.fence(g, h.w, h.h) else if (h.variant == 1) world.rock(g, h.w, h.h) else world.bush(g, h.w, h.h, th, false)
                    Biome.SAVANNA -> if (h.variant == 0) world.bush(g, h.w, h.h, th, true) else world.rock(g, h.w, h.h, 0xFFC9A27E.toInt())
                    else -> when (h.variant) {
                        0 -> world.log(g, h.w, h.h, mossy = w.biome != Biome.PINE_FOREST)
                        1 -> world.rock(g, h.w, h.h)
                        else -> world.bush(g, h.w, h.h, th, false)
                    }
                }
                HazardKind.TALL -> when (w.biome) {
                    Biome.SAVANNA -> world.termiteMound(g, h.w, h.h)
                    Biome.BAMBOO_FOREST -> world.bambooWall(g, h.w, h.h)
                    else -> world.stump(g, h.w, h.h)
                }
                HazardKind.OVERHEAD -> {
                    val reach = (v.groundY - v.top) / s - h.y + 2f
                    when (w.biome) {
                        Biome.PINE_FOREST -> world.beehive(g, h.w, reach)
                        Biome.BAMBOO_FOREST -> world.bambooBar(g, h.w, reach)
                        else -> world.branch(g, h.w, reach, th)
                    }
                }
                else -> Unit
            }
            g.restore()
        }
    }

    // ------------------------------------------------------------------ pickups

    private fun drawPickups(g: Gfx, w: GameWorld) {
        val s = v.s
        for (pk in w.pickups) {
            val x = v.sx(pk.x)
            if (x < v.left - 5f * s || x > v.right + 5f * s) continue
            val bob = sin(w.animTime * 4f + pk.bobPhase) * 0.5f
            var scale = 1f
            var alpha = 1f
            if (pk.collected) {
                val k = pk.collectTime / 0.35f
                scale = 1f + k * 0.8f
                alpha = 1f - k
                if (alpha <= 0f) continue
            }
            val y = v.sy(pk.y + bob)
            g.save()
            g.translate(x, y)
            if (alpha < 1f) g.multiplyAlpha(alpha)
            when (pk.kind) {
                PickupKind.FOOD -> { g.scale(s * 1.75f * scale); world.food(g, w.animal.food, false, w.animTime) }
                PickupKind.GOLDEN_FOOD -> {
                    g.fillCircle(0f, 0f, 3.6f * s, 0, goldenGlow(s))
                    g.scale(s * 2.1f * scale); world.food(g, w.animal.food, true, w.animTime)
                }
                PickupKind.GEM -> { g.scale(s * 1.7f * scale); world.gem(g, w.animTime + pk.bobPhase) }
                PickupKind.MAGNET -> { g.scale(s * 2.4f * scale); world.powerUp(g, 0, w.animTime) }
                PickupKind.SHIELD -> { g.scale(s * 2.4f * scale); world.powerUp(g, 1, w.animTime) }
                PickupKind.DOUBLER -> { g.scale(s * 2.4f * scale); world.powerUp(g, 2, w.animTime) }
                PickupKind.MYSTERY -> { g.scale(s * 2.4f * scale); world.powerUp(g, 3, w.animTime) }
            }
            g.restore()
        }
    }

    private var glowS = -1f
    private var glow: Gradient? = null
    private fun goldenGlow(s: Float): Gradient {
        if (s != glowS || glow == null) {
            glowS = s
            glow = Gradient.radial(0f, 0f, 3.6f * s, intArrayOf(0x99FFF176.toInt(), 0x00FFF176), null)
        }
        return glow!!
    }

    // ------------------------------------------------------------------ characters

    private fun pose(w: GameWorld): Pose = when {
        w.state == RunState.COUNTDOWN && w.distance < 1f -> Pose.IDLE
        w.sliding -> Pose.SLIDE
        !w.onGround && w.vy > 0f -> Pose.JUMP
        !w.onGround -> Pose.FALL
        else -> Pose.RUN
    }

    private fun drawRunner(g: Gfx, w: GameWorld) {
        val s = v.s
        val x = v.playerX
        val y = v.sy(w.y)
        if (w.y > -0.5f) world.shadow(g, x, v.groundY, 3.4f * s * max(0.35f, 1f - w.y / 30f), 0.28f)

        if (w.abilityActive || w.animal.id == "golden_monkey") {
            g.fillCircle(x, y - 6f * s, 11f * s, 0, auraGrad(s, if (w.abilityActive) 0x88FFD54F.toInt() else 0x55FFE082)) 
        }
        val flashing = w.stumbleFlash > 0f && ((w.animTime * 12f).toInt() % 2 == 0)
        g.save()
        if (w.landTime > 0f) {
            val k = w.landTime / 0.16f * 0.12f
            g.translate(x, y); g.scale(1f + k, 1f - k); g.translate(-x, -y)
        }
        chars.drawAnimal(
            g, w.animal.shape, x, y, s, w.distance * 0.3f, pose(w), w.mood, w.animTime,
            golden = w.animal.id == "golden_monkey", alpha = if (flashing) 0.45f else 1f,
        )
        g.restore()

        if (w.invincible) {
            val c = if (w.shieldTime > 0f || w.reviveShield > 0f) 0xFF42A5F5.toInt() else 0xFFFFD54F.toInt()
            val r = (8.5f + sin(w.animTime * 6f) * 0.3f) * s
            g.fillCircle(x + 0.5f * s, y - 6f * s, r, Colors.withAlpha(c, 0.16f))
            g.strokeCircle(x + 0.5f * s, y - 6f * s, r, 0.45f * s, Colors.withAlpha(c, 0.75f))
            g.fillOval(x - 4f * s, y - 12f * s, x - 1f * s, y - 10f * s, 0x66FFFFFF)
        }
        if (w.magnetTime > 0f) {
            for (i in 0 until 2) {
                val k = ((w.animTime * 1.5f + i * 0.5f) % 1f)
                g.strokeCircle(x, y - 6f * s, (5f + k * 12f) * s, 0.3f * s, Colors.withAlpha(0xFFE53950.toInt(), 0.5f * (1f - k)))
            }
        }
    }

    private val auraCache = HashMap<Int, Gradient>()
    private var auraS = -1f
    private fun auraGrad(s: Float, color: Int): Gradient {
        if (s != auraS) { auraCache.clear(); auraS = s }
        return auraCache.getOrPut(color) { Gradient.radial(0f, 0f, 11f * s, intArrayOf(color, color and 0x00FFFFFF), null) }
    }

    private fun drawChaser(g: Gfx, w: GameWorld) {
        val s = v.s
        val caught = w.state == RunState.DOWN && w.cause == DeathCause.CAUGHT
        val gap = if (caught) 4f else 10f + (1f - w.chase) * 40f
        val x = v.playerX - gap * s
        if (x < v.left - 15f * s) return
        val bob = abs(sin(w.animTime * 9f)) * 0.7f
        world.shadow(g, x, v.groundY, 4f * s, 0.25f)
        chars.drawChaser(g, w.animal.chaser, x, v.groundY - bob * s, s * 1.2f, w.animTime * 11f, w.animTime)
    }

    private fun drawGhost(g: Gfx, w: GameWorld, ghost: GhostState) {
        val s = v.s
        val spec = Animals.get(ghost.animalId)
        val gx = v.sx(ghost.distance)
        if (gx < v.left + 6f * s || gx > v.right - 6f * s) {
            val ahead = gx > v.right
            val diff = ((ghost.distance - w.distance) / Physics.UNITS_PER_METER).toInt()
            val ex = if (ahead) v.right - 4f * s else v.left + 4f * s
            pill(g, "${ghost.name} ${if (diff > 0) "+" else ""}${diff}m ${if (ahead) "▶" else "◀"}", ex, v.top + 30f * s * 0.6f, 3.2f * s,
                if (ahead) Align.RIGHT else Align.LEFT, 0xCC7E57C2.toInt())
            return
        }
        val pose = when {
            !ghost.alive -> Pose.IDLE
            ghost.sliding -> Pose.SLIDE
            ghost.y > 0.5f -> Pose.JUMP
            else -> Pose.RUN
        }
        chars.drawAnimal(g, spec.shape, gx, v.sy(ghost.y), s, ghost.distance * 0.3f, pose, Mood.NORMAL, w.animTime, alpha = 0.5f)
        pill(g, ghost.name, gx, v.sy(ghost.y + 16f), 2.6f * s, Align.CENTER, 0xCC7E57C2.toInt())
    }

    private fun drawParticles(g: Gfx, w: GameWorld) {
        val s = v.s
        for (pt in w.particles) {
            val a = (pt.life / pt.maxLife).coerceIn(0f, 1f)
            val x = v.sx(pt.x)
            val y = v.sy(pt.y)
            if (pt.size > 1.1f) world.sparkleBurst(g, x, y, pt.size * s * 1.3f, Colors.withAlpha(pt.color, a))
            else g.fillCircle(x, y, pt.size * s, Colors.withAlpha(pt.color, a))
        }
        for (t in w.texts) {
            val a = t.life.coerceIn(0f, 1f)
            g.save(); g.multiplyAlpha(a)
            val pop = 1f + max(0f, t.life - 0.8f) * 1.5f
            g.text(t.text, v.sx(t.x), v.sy(t.y), 3.8f * s * pop, t.color, Align.CENTER, outline = 0xDD2A1A12.toInt(), outlineWidth = 0.8f * s)
            g.restore()
        }
    }

    // ------------------------------------------------------------------ coach

    private fun drawHint(g: Gfx, w: GameWorld, vp: Box) {
        if (w.hint == Hint.NONE || w.state != RunState.RUNNING) return
        val s = v.s
        val u = min(vp.w, vp.h) / 100f
        val t = w.animTime
        val bounce = abs(sin(t * 7f)) * 1.2f * s
        val target = w.hintHazard
        val color = when (w.hint) {
            Hint.JUMP -> 0xFF43A047.toInt()
            Hint.SLIDE -> 0xFF1E88E5.toInt()
            else -> 0xFFAB47BC.toInt()
        }
        if (target != null) {
            val hx = v.sx(target.x + target.w / 2f)
            val top = if (target.kind == HazardKind.OVERHEAD) v.sy(target.y) + 7f * s else v.sy(max(target.y + target.h, 8f)) - 6f * s
            val y = top - bounce
            val r = 3.4f * s
            g.fillCircle(hx, y, r + 0.4f * s, 0xFFFFFFFF.toInt())
            g.fillCircle(hx, y, r, color)
            arrow(g, hx, y, r * 0.6f, up = w.hint == Hint.JUMP)
            g.text(if (w.hint == Hint.JUMP) "JUMP!" else "SLIDE!", hx, y - r - 1.2f * s, 3.2f * s, 0xFFFFFFFF.toInt(), Align.CENTER, outline = color, outlineWidth = 0.9f * s)
        }
        // Ring on the matching button
        val btn = when (w.hint) {
            Hint.JUMP -> Button.JUMP
            Hint.SLIDE -> Button.SLIDE
            Hint.ABILITY -> Button.ABILITY
            Hint.NONE -> return
        }
        button(btn, vp, tmp)
        val k = (t * 1.6f) % 1f
        g.strokeCircle(tmp.cx, tmp.cy, tmp.w / 2f * (1f + k * 0.45f), 1.2f * u, Colors.withAlpha(0xFFFFFFFF.toInt(), 1f - k))
        g.strokeCircle(tmp.cx, tmp.cy, tmp.w / 2f + 0.6f * u, 0.8f * u, color)
        if (w.tutorialActive || w.hint == Hint.ABILITY) {
            val msg = when (w.hint) {
                Hint.JUMP -> "Tap JUMP — or tap anywhere / swipe up"
                Hint.SLIDE -> "Tap SLIDE — or swipe down"
                else -> "Ability ready! Tap to use ${w.animal.ability.displayName}"
            }
            pill(g, msg, vp.cx, v.groundY - (if (portrait(vp)) 40f else 46f) * s, 3.4f * u, Align.CENTER, Colors.withAlpha(color, 0.92f))
            hand(g, tmp.cx + tmp.w * 0.2f, tmp.cy + tmp.h * 0.15f - abs(sin(t * 5f)) * 2f * u, 7f * u)
        }
    }

    private fun arrow(g: Gfx, x: Float, y: Float, r: Float, up: Boolean) {
        val d = if (up) -1f else 1f
        p.reset().moveTo(x, y + d * r).lineTo(x + r, y).lineTo(x + r * 0.4f, y).lineTo(x + r * 0.4f, y - d * r)
            .lineTo(x - r * 0.4f, y - d * r).lineTo(x - r * 0.4f, y).lineTo(x - r, y).close()
        g.fillPath(p, 0xFFFFFFFF.toInt())
    }

    private fun hand(g: Gfx, x: Float, y: Float, size: Float) {
        val skin = 0xFFFFD7B5.toInt()
        val ink = 0xFF2A1A12.toInt()
        g.save(); g.translate(x, y); g.scale(size / 10f, size / 10f); g.rotate(-15f)
        g.fillRoundRect(-2.6f, -1f, 2.6f, 6f, 2f, ink)
        g.fillRoundRect(-2.3f, -0.7f, 2.3f, 5.7f, 1.8f, skin)
        g.fillRoundRect(-1.1f, -7.3f, 1.1f, 1f, 1.1f, ink)
        g.fillRoundRect(-0.85f, -7f, 0.85f, 0.8f, 0.85f, skin)
        g.restore()
    }

    // ------------------------------------------------------------------ HUD

    private fun pill(g: Gfx, text: String, x: Float, y: Float, size: Float, align: Align, bg: Int) {
        val tw = g.textWidth(text, size)
        val padX = size * 0.6f
        val l = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - tw / 2f - padX
            Align.RIGHT -> x - tw - padX * 2
        }
        g.fillRoundRect(l, y - size * 1.05f, l + tw + padX * 2, y + size * 0.45f, size * 0.75f, bg)
        g.text(text, l + padX, y, size, 0xFFFFFFFF.toInt())
    }

    private fun drawHud(g: Gfx, w: GameWorld, vp: Box, style: ViewportStyle) {
        val u = min(vp.w, vp.h) / 100f
        val pad = 3f * u
        val panel = 0x99102A1C.toInt()

        // Danger vignette when the chaser is close
        if (w.chase > 0.45f && w.state == RunState.RUNNING) {
            val k = ((w.chase - 0.45f) / 0.55f).coerceIn(0f, 1f)
            val a = (0.18f + 0.12f * sin(w.animTime * 10f)) * k
            g.fillRect(vp.l, vp.t, vp.l + vp.w * 0.22f, vp.b, 0, dangerGrad(vp, Colors.withAlpha(0xFFE53935.toInt(), a)))
        }

        // Score panel
        val scoreSize = 7.5f * u
        val sw = max(g.textWidth(w.score.toString(), scoreSize), 18f * u)
        g.fillRoundRect(vp.l + pad, vp.t + pad, vp.l + pad + sw + 5f * u, vp.t + pad + 15f * u, 4f * u, panel)
        g.text(w.score.toString(), vp.l + pad + 2.5f * u, vp.t + pad + 8.2f * u, scoreSize, 0xFFFFFFFF.toInt())
        g.text("${w.meters} m", vp.l + pad + 2.5f * u, vp.t + pad + 12.8f * u, 3.4f * u, 0xFFB9F6CA.toInt(), bold = true)

        // Food & coins pill
        val fx = vp.l + pad + sw + 7f * u
        val pillW = 34f * u + (if (w.gems > 0) 12f * u else 0f)
        g.fillRoundRect(fx, vp.t + pad, fx + pillW, vp.t + pad + 8f * u, 4f * u, panel)
        g.save(); g.translate(fx + 4f * u, vp.t + pad + 4f * u); g.scale(2.4f * u); world.food(g, w.animal.food, false, w.animTime); g.restore()
        g.text("${w.food}", fx + 7.5f * u, vp.t + pad + 5.6f * u, 4f * u, 0xFFFFF59D.toInt())
        g.fillCircle(fx + 20f * u, vp.t + pad + 4f * u, 2.3f * u, 0xFFFFC107.toInt())
        g.fillCircle(fx + 20f * u, vp.t + pad + 4f * u, 1.4f * u, 0xFFFFE082.toInt())
        g.text("${w.coins}", fx + 23.5f * u, vp.t + pad + 5.6f * u, 4f * u, 0xFFFFE082.toInt())
        if (w.gems > 0) {
            g.save(); g.translate(fx + 37f * u, vp.t + pad + 4f * u); g.scale(2.2f * u); world.gem(g, w.animTime); g.restore()
            g.text("${w.gems}", fx + 40f * u, vp.t + pad + 5.6f * u, 4f * u, 0xFF81D4FA.toInt())
        }

        // Chaser proximity meter
        val mx = fx
        val my = vp.t + pad + 10f * u
        val mw = 30f * u
        g.fillRoundRect(mx, my, mx + mw, my + 4.2f * u, 2.1f * u, panel)
        val danger = w.chase.coerceIn(0f, 1f)
        val barColor = Colors.mix(0xFF66BB6A.toInt(), 0xFFE53935.toInt(), danger)
        g.fillRoundRect(mx + 0.6f * u, my + 0.6f * u, mx + 0.6f * u + (mw - 1.2f * u) * max(0.08f, danger), my + 3.6f * u, 1.5f * u, barColor)
        g.text(w.animal.chaser.displayName, mx + 1.6f * u, my + 3.2f * u, 2.6f * u, 0xFFFFFFFF.toInt())

        // Active power-up timers as rings
        var tx = vp.l + pad + 4f * u
        val ty = vp.t + pad + 21f * u
        fun ring(kind: Int, t: Float, max: Float) {
            if (t <= 0f) return
            g.fillCircle(tx, ty, 3.6f * u, panel)
            g.strokeArc(tx - 3.2f * u, ty - 3.2f * u, tx + 3.2f * u, ty + 3.2f * u, -90f, 360f * (t / max).coerceIn(0f, 1f), 0.8f * u, 0xFFFFFFFF.toInt())
            g.save(); g.translate(tx, ty); g.scale(2.3f * u); world.powerUp(g, kind, 0f); g.restore()
            tx += 9f * u
        }
        ring(0, w.magnetTime, 18f)
        ring(1, w.shieldTime, 13.5f)
        ring(2, w.doublerTime, 20f)

        // Split-screen label & opponent
        style.label?.let { pill(g, it, vp.cx, vp.t + pad + 5f * u, 3.6f * u, Align.CENTER, Colors.withAlpha(style.labelColor, 0.85f)) }
        style.ghost?.let { gh ->
            val status = if (gh.alive) "${gh.score}" else "${gh.score} ✖"
            pill(g, "VS ${gh.name}: $status", vp.cx, vp.t + pad + 5f * u, 3.4f * u, Align.CENTER, 0xCC7E57C2.toInt())
        }

        // Buttons
        if (style.showPause) {
            button(Button.PAUSE, vp, tmp)
            g.fillCircle(tmp.cx, tmp.cy, tmp.w / 2f, panel)
            val bw = tmp.w * 0.1f
            g.fillRoundRect(tmp.cx - bw * 2.2f, tmp.cy - bw * 2.6f, tmp.cx - bw * 0.7f, tmp.cy + bw * 2.6f, bw * 0.5f, 0xFFFFFFFF.toInt())
            g.fillRoundRect(tmp.cx + bw * 0.7f, tmp.cy - bw * 2.6f, tmp.cx + bw * 2.2f, tmp.cy + bw * 2.6f, bw * 0.5f, 0xFFFFFFFF.toInt())
        }
        controlButton(g, Button.SLIDE, vp, w, false)
        controlButton(g, Button.JUMP, vp, w, true)
        abilityButton(g, w, vp, u)

        // Countdown / banners
        when (w.state) {
            RunState.COUNTDOWN -> {
                val n = ceil(w.countdown).toInt()
                val frac = w.countdown - (n - 1)
                val pop = 1f + (frac - 0.7f).coerceAtLeast(0f) * 1.5f
                g.text(if (n > 0) "$n" else "GO!", vp.cx, vp.cy + 6f * u, 22f * u * pop, 0xFFFFEB3B.toInt(), Align.CENTER,
                    outline = 0xFF2A1A12.toInt(), outlineWidth = 2.2f * u)
                if (w.distance < 1f) {
                    pill(g, "Run from the ${w.animal.chaser.displayName}! Eat ${w.animal.food.displayName.lowercase()}s!", vp.cx, vp.cy + 16f * u, 3.6f * u, Align.CENTER, 0xCC102A1C.toInt())
                }
            }
            RunState.DOWN, RunState.FINISHED -> {
                val msg = when {
                    w.state == RunState.FINISHED -> "FINISHED"
                    w.cause == DeathCause.FELL -> "SPLASH!"
                    else -> "CAUGHT!"
                }
                g.text(msg, vp.cx, vp.cy, 13f * u, 0xFFFF7043.toInt(), Align.CENTER, outline = 0xFF2A1A12.toInt(), outlineWidth = 1.6f * u)
            }
            RunState.RUNNING -> Unit
        }
    }

    private var dangerKey = ""
    private var danger: Gradient? = null
    private fun dangerGrad(vp: Box, c: Int): Gradient {
        val key = "${vp.l}-${vp.w}-$c"
        if (key != dangerKey) {
            dangerKey = key
            danger = Gradient.linear(vp.l, 0f, vp.l + vp.w * 0.22f, 0f, intArrayOf(c, c and 0x00FFFFFF), null)
        }
        return danger!!
    }

    private fun controlButton(g: Gfx, b: Button, vp: Box, w: GameWorld, up: Boolean) {
        button(b, vp, tmp)
        val r = tmp.w / 2f
        val active = if (up) !w.onGround else w.sliding
        val base = if (up) 0xFF43A047.toInt() else 0xFF1E88E5.toInt()
        g.fillCircle(tmp.cx, tmp.cy + r * 0.08f, r, 0x55000000)
        g.fillCircle(tmp.cx, tmp.cy, r, Colors.withAlpha(base, if (active) 0.95f else 0.6f))
        g.strokeCircle(tmp.cx, tmp.cy, r * 0.92f, r * 0.06f, 0x88FFFFFF.toInt())
        arrow(g, tmp.cx, tmp.cy - r * 0.12f, r * 0.42f, up)
        g.text(if (up) "JUMP" else "SLIDE", tmp.cx, tmp.cy + r * 0.62f, r * 0.26f, 0xFFFFFFFF.toInt(), Align.CENTER)
    }

    private fun abilityButton(g: Gfx, w: GameWorld, vp: Box, u: Float) {
        button(Button.ABILITY, vp, tmp)
        val r = tmp.w / 2f
        val ready = w.abilityReady
        val pulse = if (ready) 1f + sin(w.animTime * 8f) * 0.06f else 1f
        g.fillCircle(tmp.cx, tmp.cy, r * pulse, if (ready) 0xEEAB47BC.toInt() else 0x88102A1C.toInt())
        g.strokeArc(tmp.l + r * 0.1f, tmp.t + r * 0.1f, tmp.r - r * 0.1f, tmp.b - r * 0.1f, -90f, 360f * w.abilityCharge, r * 0.14f, 0xFFFFD54F.toInt())
        g.save(); g.translate(tmp.cx, tmp.cy - r * 0.1f); g.scale(r * 0.42f); world.food(g, w.animal.food, ready, w.animTime); g.restore()
        g.text(if (ready) "TAP!" else "${(w.abilityCharge * 100).toInt()}%", tmp.cx, tmp.cy + r * 0.68f, r * 0.27f, 0xFFFFFFFF.toInt(), Align.CENTER)
    }
}
