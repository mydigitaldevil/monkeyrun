package com.junglerun.game.art

import com.junglerun.game.core.Chaser
import com.junglerun.game.core.Shape
import com.junglerun.game.gfx.Colors
import com.junglerun.game.gfx.GPath
import com.junglerun.game.gfx.Gfx
import com.junglerun.game.gfx.Gradient
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class Pose { RUN, JUMP, FALL, SLIDE, IDLE }
enum class Mood { NORMAL, HAPPY, SCARED, ANGRY, HURT }

enum class Ears { MONKEY, LONG, ROUND_TOP, POINTY, FLAP, OSSICONE, NONE }
enum class Tail { CURLY, PUFF, TUFT, SHORT, LONG, BUSHY, NONE }

/** Everything that makes one species look the way it does. Gradients are cached in part-local space. */
class Look(
    val body: Int,
    val belly: Int,
    val ears: Ears,
    val tail: Tail,
    val earInner: Int = 0xFFFFB3C1.toInt(),
    val limbs: Int = body,
    val biped: Boolean = false,
    val faceMask: Int = 0,
    val mane: Int = 0,
    val trunk: Boolean = false,
    val longNeck: Boolean = false,
    val eyePatches: Boolean = false,
    val spots: Int = 0,
    val stripes: Int = 0,
    val horn: Boolean = false,
    val tuft: Boolean = false,
    val nose: Int = 0xFF3A2418.toInt(),
    val blush: Int = 0x66FF7F9A,
    val headScale: Float = 1f,
    val sparkle: Boolean = false,
) {
    val outline: Int = Colors.mix(Colors.shade(body, 0.32f), 0xFF2A1A12.toInt(), 0.45f)
    val bodyDark: Int = Colors.shade(body, 0.78f)
    val limbDark: Int = Colors.shade(limbs, 0.75f)
    val headR = 3.5f * headScale

    // Cached gradients (local space, never change)
    val headGrad = Gradient.radial(-headR * 0.35f, -headR * 0.45f, headR * 1.45f,
        intArrayOf(Colors.lighten(body, 0.35f), body, Colors.shade(body, 0.8f)), floatArrayOf(0f, 0.55f, 1f))
    val bodyGrad = Gradient.radial(-1.2f, -1.4f, 4.6f,
        intArrayOf(Colors.lighten(body, 0.3f), body, Colors.shade(body, 0.78f)), floatArrayOf(0f, 0.5f, 1f))
    val bellyGrad = Gradient.radial(0f, -0.6f, 2.6f, intArrayOf(Colors.lighten(belly, 0.25f), belly), null)
    val maneGrad = if (mane != 0) Gradient.radial(-0.6f, -0.8f, headR * 1.9f,
        intArrayOf(Colors.lighten(mane, 0.25f), mane, Colors.shade(mane, 0.75f)), floatArrayOf(0f, 0.55f, 1f)) else null
    val faceGrad = if (faceMask != 0) Gradient.radial(1.0f, -0.6f, 3.2f, intArrayOf(Colors.lighten(faceMask, 0.3f), faceMask), null) else null
}

/**
 * Cute "chibi" characters drawn from vector shapes: big heads, big glossy eyes, blush and soft
 * shading. Local space: (0,0) = centre between the feet, -y is up, ≈12 units tall.
 */
class CharacterArt {
    private val p = GPath()
    private val q = GPath()
    private val o = 0.34f // outline width

    private val looks = HashMap<Shape, Look>()
    private val golden = Look(0xFFE8A93A.toInt(), 0xFFFFE6A6.toInt(), Ears.MONKEY, Tail.CURLY, biped = true,
        faceMask = 0xFFFFE3B3.toInt(), tuft = true, sparkle = true)
    private val chaserLooks = HashMap<Chaser, Look>()

    fun lookFor(shape: Shape, golden: Boolean = false): Look = if (golden) this.golden else looks.getOrPut(shape) {
        when (shape) {
            Shape.MONKEY -> Look(0xFF9A6240.toInt(), 0xFFF3CFA2.toInt(), Ears.MONKEY, Tail.CURLY, biped = true,
                faceMask = 0xFFFFD9B0.toInt(), tuft = true)
            Shape.RABBIT -> Look(0xFFF3F0FA.toInt(), 0xFFFFFFFF.toInt(), Ears.LONG, Tail.PUFF, biped = true,
                nose = 0xFFFF7FA0.toInt(), headScale = 1.02f)
            Shape.PANDA -> Look(0xFFFAFAFA.toInt(), 0xFFFFFFFF.toInt(), Ears.ROUND_TOP, Tail.SHORT,
                limbs = 0xFF2B2B33.toInt(), eyePatches = true, nose = 0xFF22222A.toInt())
            Shape.LION -> Look(0xFFF2B54A.toInt(), 0xFFFFE2A8.toInt(), Ears.ROUND_TOP, Tail.TUFT,
                mane = 0xFFC8662B.toInt(), faceMask = 0xFFFFE6B8.toInt(), nose = 0xFF8A3B2A.toInt())
            Shape.BEAR -> Look(0xFF8A5A3C.toInt(), 0xFFE2B98F.toInt(), Ears.ROUND_TOP, Tail.SHORT,
                faceMask = 0xFFE9C39A.toInt())
            Shape.ELEPHANT -> Look(0xFF9EB4C8.toInt(), 0xFFD7E3EE.toInt(), Ears.FLAP, Tail.SHORT, trunk = true,
                earInner = 0xFFF7B6C8.toInt(), nose = 0)
            Shape.CHEETAH -> Look(0xFFF6C453.toInt(), 0xFFFFF0C9.toInt(), Ears.ROUND_TOP, Tail.LONG,
                spots = 0xFF5A3A1E.toInt(), faceMask = 0xFFFFF0CF.toInt())
            Shape.GIRAFFE -> Look(0xFFF7C66A.toInt(), 0xFFFFEBC2.toInt(), Ears.OSSICONE, Tail.TUFT, longNeck = true,
                spots = 0xFFB86B2E.toInt(), faceMask = 0xFFFFE7C0.toInt(), headScale = 0.9f)
        }
    }

    private fun chaserLook(c: Chaser): Look = chaserLooks.getOrPut(c) {
        when (c) {
            Chaser.LION -> Look(0xFFE8A53C.toInt(), 0xFFFFDFA0.toInt(), Ears.ROUND_TOP, Tail.TUFT,
                mane = 0xFFA94E22.toInt(), faceMask = 0xFFFFE2B0.toInt(), nose = 0xFF6E2C1E.toInt(), blush = 0)
            Chaser.TIGER -> Look(0xFFF28A2E.toInt(), 0xFFFFF1DC.toInt(), Ears.ROUND_TOP, Tail.LONG,
                stripes = 0xFF2A1A12.toInt(), faceMask = 0xFFFFF4E4.toInt(), blush = 0)
            Chaser.FOX -> Look(0xFFF07A2A.toInt(), 0xFFFFF4E8.toInt(), Ears.POINTY, Tail.BUSHY,
                faceMask = 0xFFFFF4E8.toInt(), limbs = 0xFF5B3524.toInt(), blush = 0)
            Chaser.RHINO -> Look(0xFF8E9AA6.toInt(), 0xFFC4CDD6.toInt(), Ears.POINTY, Tail.SHORT, horn = true,
                headScale = 1.05f, blush = 0, nose = 0)
            Chaser.BEES -> Look(0xFFFFD23F.toInt(), 0xFFFFE98A.toInt(), Ears.NONE, Tail.NONE, blush = 0)
            Chaser.CROCODILE -> Look(0xFF5E9B4A.toInt(), 0xFFD7E8A8.toInt(), Ears.NONE, Tail.NONE, blush = 0)
            Chaser.HYENA -> Look(0xFFC9A774.toInt(), 0xFFEBD8B4.toInt(), Ears.POINTY, Tail.BUSHY,
                spots = 0xFF5B4128.toInt(), faceMask = 0xFFE9D3AA.toInt(), blush = 0)
        }
    }

    // ------------------------------------------------------------------ public entry points

    fun drawAnimal(
        g: Gfx, shape: Shape, x: Float, y: Float, scale: Float, phase: Float, pose: Pose,
        mood: Mood = Mood.NORMAL, time: Float = 0f, golden: Boolean = false, alpha: Float = 1f,
    ) {
        val look = lookFor(shape, golden)
        g.save()
        g.translate(x, y)
        g.scale(scale, scale)
        if (alpha < 1f) g.multiplyAlpha(alpha)
        poseTransform(g, pose, phase)
        if (look.biped) biped(g, look, phase, pose, mood, time) else quadruped(g, look, phase, pose, mood, time)
        if (look.sparkle) sparkles(g, time)
        g.restore()
    }

    fun drawChaser(g: Gfx, chaser: Chaser, x: Float, y: Float, scale: Float, phase: Float, time: Float, alpha: Float = 1f) {
        val look = chaserLook(chaser)
        g.save()
        g.translate(x, y)
        g.scale(scale, scale)
        if (alpha < 1f) g.multiplyAlpha(alpha)
        when (chaser) {
            Chaser.BEES -> { g.scale(1.35f, 1.35f); bees(g, look, time) }
            Chaser.CROCODILE -> { g.scale(1.45f, 1.45f); croc(g, look, phase, time) }
            else -> {
                g.rotate(-4f) // leaning forward, hungry
                quadruped(g, look, phase, Pose.RUN, Mood.ANGRY, time)
            }
        }
        g.restore()
    }

    private fun poseTransform(g: Gfx, pose: Pose, phase: Float) {
        when (pose) {
            Pose.RUN -> {
                val bob = abs(sin(phase)) * 0.45f
                val squash = (cos(phase * 2f) * 0.03f)
                g.translate(0f, -bob)
                g.scale(1f + squash, 1f - squash)
                g.rotate(5f)
            }
            Pose.JUMP -> {
                g.scale(0.94f, 1.07f)
                g.rotate(-6f)
            }
            Pose.FALL -> g.rotate(8f)
            Pose.SLIDE -> {
                g.translate(0f, 0.2f)
                g.scale(1.18f, 0.6f)
                g.rotate(12f)
            }
            Pose.IDLE -> {
                val breathe = sin(phase * 0.5f) * 0.02f
                g.scale(1f - breathe, 1f + breathe)
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun Gfx.oCircle(cx: Float, cy: Float, r: Float, color: Int, outline: Int, grad: Gradient? = null) {
        fillCircle(cx, cy, r + o, outline)
        fillCircle(cx, cy, r, color, grad)
    }

    private fun Gfx.oOval(cx: Float, cy: Float, rx: Float, ry: Float, color: Int, outline: Int, grad: Gradient? = null) {
        fillOval(cx - rx - o, cy - ry - o, cx + rx + o, cy + ry + o, outline)
        fillOval(cx - rx, cy - ry, cx + rx, cy + ry, color, grad)
    }

    /** A limb: thick rounded stroke with an outline. */
    private fun Gfx.limb(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int, outline: Int) {
        strokeLine(x1, y1, x2, y2, w + o * 2, outline)
        strokeLine(x1, y1, x2, y2, w, color)
    }

    private fun Gfx.curveStroke(x1: Float, y1: Float, cx: Float, cy: Float, x2: Float, y2: Float, w: Float, color: Int, outline: Int) {
        p.reset().moveTo(x1, y1).quadTo(cx, cy, x2, y2)
        strokePath(p, w + o * 2, outline)
        strokePath(p, w, color)
    }

    // ------------------------------------------------------------------ bipeds (monkey, rabbit)

    private fun biped(g: Gfx, l: Look, ph: Float, pose: Pose, mood: Mood, t: Float) {
        val s = sin(ph)
        val c = cos(ph)
        val tuck = pose == Pose.JUMP || pose == Pose.FALL
        // Tail
        when (l.tail) {
            Tail.CURLY -> {
                val sway = sin(ph * 0.5f + 1f) * 0.6f
                p.reset().moveTo(-2.0f, -3.6f).cubicTo(-5.2f, -3.2f, -6.6f + sway, -6.4f, -5.0f + sway, -8.0f)
                    .cubicTo(-3.8f + sway, -9.2f, -2.8f + sway, -7.8f, -3.9f + sway, -7.0f)
                g.strokePath(p, 0.95f + o * 2, l.outline)
                g.strokePath(p, 0.95f, l.body)
            }
            Tail.PUFF -> g.oCircle(-2.9f, -3.6f, 1.15f, 0xFFFFFFFF.toInt(), l.outline)
            else -> Unit
        }
        // Back leg & arm
        val footY = if (tuck) -1.6f else 0f
        val lift = 0.9f
        val bx = if (tuck) -1.2f else -s * 1.6f
        val by = if (tuck) footY else -max(0f, -c) * lift
        g.limb(-0.5f, -2.6f, bx, by - 0.4f, 1.35f, l.bodyDark, l.outline)
        g.oOval(bx + 0.35f, by - 0.25f, 0.95f, 0.55f, l.bodyDark, l.outline)
        val armSwing = if (tuck) -1f else s
        g.limb(-0.4f, -5.4f, if (tuck) -2.8f else -1.6f - armSwing * 0.9f, if (tuck) -6.4f else -3.8f, 1.05f, l.bodyDark, l.outline)

        // Body
        g.save(); g.translate(-0.1f, -4.3f)
        g.oOval(0f, 0f, 2.55f, 2.45f, l.body, l.outline, l.bodyGrad)
        g.fillOval(-0.6f, -1.2f, 2.2f, 2.1f, l.belly, l.bellyGrad)
        g.restore()

        // Front leg
        val fx = if (tuck) 1.0f else s * 1.6f
        val fy = if (tuck) footY - 0.4f else -max(0f, c) * lift
        g.limb(0.5f, -2.6f, fx, fy - 0.4f, 1.35f, l.body, l.outline)
        g.oOval(fx + 0.4f, fy - 0.25f, 1.0f, 0.58f, l.body, l.outline)

        // Head
        g.save(); g.translate(1.2f, -8.7f)
        head(g, l, mood, t, ph)
        g.restore()

        // Front arm: swings while running, reaches forward ("superhero") while jumping
        val hx = if (tuck) 3.9f else 1.9f + armSwing * 0.9f
        val hy = if (tuck) -5.6f else -3.9f
        g.limb(0.5f, -5.4f, hx, hy, 1.05f, l.body, l.outline)
        g.oCircle(hx, hy, 0.6f, l.belly, l.outline)
    }

    // ------------------------------------------------------------------ quadrupeds

    private fun quadruped(g: Gfx, l: Look, ph: Float, pose: Pose, mood: Mood, t: Float) {
        val tuck = pose == Pose.JUMP || pose == Pose.FALL
        val legLen = 2.5f
        val hipY = -2.6f
        fun leg(x: Float, phase: Float, color: Int) {
            val s = sin(phase)
            val c = cos(phase)
            val fx = if (tuck) x + 0.6f else x + s * 1.3f
            val fy = if (tuck) hipY + legLen * 0.55f else -max(0f, c) * 0.8f
            g.limb(x, hipY, fx, fy - 0.35f, 1.3f, color, l.outline)
            g.oOval(fx + 0.25f, fy - 0.25f, 0.8f, 0.5f, color, l.outline)
        }
        // Tail
        val sway = sin(ph * 0.7f) * 0.5f
        when (l.tail) {
            Tail.TUFT -> {
                g.curveStroke(-4.0f, -5.4f, -6.2f, -5.6f + sway, -6.4f, -8.0f + sway, 0.55f, l.body, l.outline)
                g.oCircle(-6.4f, -8.2f + sway, 0.85f, if (l.mane != 0) l.mane else l.bodyDark, l.outline)
            }
            Tail.LONG -> {
                g.curveStroke(-4.0f, -5.2f, -7.0f, -4.6f + sway, -7.4f, -7.6f + sway, 0.65f, l.body, l.outline)
                if (l.stripes != 0) g.fillCircle(-7.4f, -7.6f + sway, 0.4f, l.stripes)
            }
            Tail.BUSHY -> {
                p.reset().moveTo(-3.8f, -5.2f).cubicTo(-6.4f, -6.0f + sway, -8.2f, -4.2f + sway, -7.6f, -6.8f + sway)
                g.strokePath(p, 1.9f + o * 2, l.outline)
                g.strokePath(p, 1.9f, l.body)
                g.oCircle(-7.6f, -6.9f + sway, 0.95f, l.belly, l.outline)
            }
            Tail.SHORT -> g.oCircle(-4.0f, -5.0f, 0.75f, l.bodyDark, l.outline)
            else -> Unit
        }
        // Far legs
        leg(-2.6f, ph + 0.7f, l.limbDark)
        leg(2.0f, ph + 3.6f, l.limbDark)
        // Body
        g.save(); g.translate(-0.6f, -4.6f)
        g.oOval(0f, 0f, 3.9f, 2.55f, l.body, l.outline, l.bodyGrad)
        g.fillOval(-2.0f, 0.2f, 2.6f, 2.35f, l.belly, l.bellyGrad)
        if (l.spots != 0) {
            val sx = floatArrayOf(-2.6f, -1.2f, 0.2f, -2.0f, 1.4f, -0.4f)
            val sy = floatArrayOf(-1.0f, -1.7f, -1.1f, 0.3f, -0.4f, 0.1f)
            for (i in sx.indices) g.fillOval(sx[i] - 0.45f, sy[i] - 0.38f, sx[i] + 0.45f, sy[i] + 0.38f, l.spots)
        }
        if (l.stripes != 0) {
            for (i in 0 until 4) {
                val x = -2.8f + i * 1.3f
                p.reset().moveTo(x, -2.4f).quadTo(x + 0.6f, -1.2f, x + 0.1f, -0.2f)
                g.strokePath(p, 0.42f, l.stripes)
            }
        }
        g.restore()
        // Near legs
        leg(-2.0f, ph, l.limbs)
        leg(2.6f, ph + 2.9f, l.limbs)

        // Neck (giraffe)
        val headX: Float
        val headY: Float
        if (l.longNeck) {
            g.limb(1.6f, -5.6f, 3.6f, -11.8f, 2.0f, l.body, l.outline)
            g.fillCircle(2.4f, -8.0f, 0.45f, l.spots)
            g.fillCircle(3.1f, -10.2f, 0.4f, l.spots)
            headX = 3.9f
            headY = -13.0f
        } else {
            headX = 3.0f
            headY = -7.7f
        }
        g.save(); g.translate(headX, headY)
        if (l.mane != 0) mane(g, l, ph)
        head(g, l, mood, t, ph)
        g.restore()
    }

    private fun mane(g: Gfx, l: Look, ph: Float) {
        val n = 12
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val r = l.headR * 1.45f
        for (i in 0 until n) {
            val a = i * (2f * Math.PI.toFloat() / n)
            val rr = r * (if (i % 2 == 0) 1f else 0.86f) + sin(ph + i) * 0.08f
            xs[i] = -0.5f + cos(a) * rr
            ys[i] = sin(a) * rr
        }
        q.reset().blob(xs, ys)
        g.save()
        g.scale(1f + o / r, 1f + o / r)
        g.fillPath(q, l.outline)
        g.restore()
        g.fillPath(q, l.mane, l.maneGrad)
    }

    // ------------------------------------------------------------------ head & face

    private fun head(g: Gfx, l: Look, mood: Mood, t: Float, ph: Float) {
        val r = l.headR
        // Ears behind the head
        when (l.ears) {
            Ears.MONKEY -> {
                g.oCircle(-r * 0.92f, 0.2f, 1.45f, l.body, l.outline)
                g.fillCircle(-r * 0.92f, 0.2f, 0.85f, l.faceMask)
            }
            Ears.LONG -> {
                val flop = sin(ph * 0.5f) * 3f
                for ((dx, rot) in listOf(-0.8f to -24f + flop, 0.6f to -8f + flop)) {
                    g.save(); g.translate(dx, -r + 0.4f); g.rotate(rot)
                    g.oOval(0f, -2.5f, 0.95f, 2.7f, l.body, l.outline)
                    g.fillOval(-0.45f, -4.6f, 0.45f, -0.6f, l.earInner)
                    g.restore()
                }
            }
            Ears.ROUND_TOP -> {
                val earC = if (l.eyePatches) l.limbs else l.body
                g.oCircle(-r * 0.62f, -r * 0.78f, 1.15f, earC, l.outline)
                g.oCircle(r * 0.45f, -r * 0.86f, 1.05f, earC, l.outline)
                if (!l.eyePatches) {
                    g.fillCircle(-r * 0.62f, -r * 0.78f, 0.6f, Colors.lighten(l.belly, 0.2f))
                    g.fillCircle(r * 0.45f, -r * 0.86f, 0.55f, Colors.lighten(l.belly, 0.2f))
                }
            }
            Ears.POINTY -> {
                for ((dx, h) in listOf(-r * 0.55f to 2.6f, r * 0.35f to 2.4f)) {
                    p.reset().moveTo(dx - 1.1f, -r * 0.6f).lineTo(dx, -r * 0.6f - h).lineTo(dx + 1.1f, -r * 0.6f).close()
                    g.save(); g.scale(1.08f, 1.08f); g.fillPath(p, l.outline); g.restore()
                    g.fillPath(p, l.body)
                    p.reset().moveTo(dx - 0.55f, -r * 0.65f).lineTo(dx, -r * 0.6f - h * 0.65f).lineTo(dx + 0.55f, -r * 0.65f).close()
                    g.fillPath(p, if (l.horn) l.bodyDark else 0xFF3A2418.toInt())
                }
            }
            Ears.FLAP -> {
                val flap = sin(ph * 0.5f) * 0.25f
                g.oOval(-r * 0.62f, 0.4f, 2.3f + flap, 2.9f, l.body, l.outline)
                g.fillOval(-r * 0.62f - 1.5f, 0.4f - 2.1f, -r * 0.62f + 1.5f, 0.4f + 2.1f, l.earInner)
            }
            Ears.OSSICONE -> {
                g.limb(-0.6f, -r * 0.8f, -0.9f, -r - 1.4f, 0.5f, l.body, l.outline)
                g.limb(0.6f, -r * 0.8f, 0.7f, -r - 1.5f, 0.5f, l.body, l.outline)
                g.oCircle(-0.9f, -r - 1.5f, 0.45f, l.spots, l.outline)
                g.oCircle(0.7f, -r - 1.6f, 0.45f, l.spots, l.outline)
            }
            Ears.NONE -> Unit
        }

        // Head shape
        g.oCircle(0f, 0f, r, l.body, l.outline, l.headGrad)
        if (l.tuft) {
            p.reset().moveTo(-0.6f, -r + 0.2f).quadTo(-0.2f, -r - 1.4f, 0.9f, -r - 0.9f).quadTo(0.1f, -r - 0.6f, 0.4f, -r + 0.3f).close()
            g.fillPath(p, l.body)
            g.strokePath(p, o * 0.8f, l.outline)
        }
        if (l.stripes != 0) {
            g.strokeLine(-0.6f, -r + 0.1f, -0.3f, -r + 1.3f, 0.4f, l.stripes)
            g.strokeLine(0.4f, -r + 0.1f, 0.3f, -r + 1.2f, 0.4f, l.stripes)
            g.strokeLine(-r + 0.2f, -0.4f, -r + 1.1f, -0.2f, 0.4f, l.stripes)
        }
        if (l.spots != 0 && l.longNeck) {
            g.fillCircle(-1.4f, -1.2f, 0.4f, l.spots)
            g.fillCircle(-2.0f, 0.4f, 0.35f, l.spots)
        }

        // Face mask / muzzle
        if (l.faceMask != 0) {
            if (l.ears == Ears.MONKEY) {
                q.reset().blob(
                    floatArrayOf(-0.6f, 0.9f, 2.4f, 3.0f, 2.2f, 0.9f, -0.4f),
                    floatArrayOf(-1.6f, -0.9f, -1.7f, -0.4f, 1.7f, 2.5f, 1.5f),
                )
                g.fillPath(q, l.faceMask, l.faceGrad)
            } else {
                g.fillOval(0.2f, -0.1f, 3.5f, 2.6f, l.faceMask, l.faceGrad)
            }
        } else if (l.ears == Ears.LONG) {
            g.fillOval(1.0f, 0.2f, 3.4f, 2.3f, l.belly)
        }

        // Eyes
        val angry = mood == Mood.ANGRY
        val blink = ((t + ph * 0.01f) % 3.3f) < 0.12f
        val nearX = 1.7f
        val farX = -0.25f
        val eyeY = -0.55f
        if (l.eyePatches) {
            g.save(); g.translate(nearX, eyeY + 0.1f); g.rotate(-20f); g.fillOval(-1.15f, -1.4f, 1.15f, 1.4f, l.limbs); g.restore()
            g.save(); g.translate(farX, eyeY + 0.1f); g.rotate(20f); g.fillOval(-0.95f, -1.3f, 0.95f, 1.3f, l.limbs); g.restore()
        }
        eye(g, nearX, eyeY, 0.92f, mood, blink, angry)
        eye(g, farX, eyeY - 0.05f, 0.78f, mood, blink, angry)
        if (angry) {
            g.strokeLine(nearX - 0.9f, eyeY - 1.6f, nearX + 0.9f, eyeY - 0.9f, 0.45f, l.outline)
            g.strokeLine(farX - 0.8f, eyeY - 0.95f, farX + 0.7f, eyeY - 1.55f, 0.45f, l.outline)
        }
        if (l.spots != 0 && !l.longNeck && !angry) {
            // Cheetah tear marks
            g.strokeLine(nearX - 0.55f, eyeY + 1.25f, nearX - 0.8f, eyeY + 2.0f, 0.24f, 0xFF3A2418.toInt())
        }

        // Trunk / horn / nose & mouth
        if (l.trunk) {
            val sw = sin(ph) * 0.5f
            p.reset().moveTo(2.4f, 0.6f).cubicTo(4.4f, 1.0f, 4.8f + sw, 2.6f, 4.0f + sw, 4.0f)
            g.strokePath(p, 1.5f + o * 2, l.outline)
            g.strokePath(p, 1.5f, l.body)
            g.fillOval(3.4f + sw, 3.6f, 4.6f + sw, 4.4f, l.bodyDark)
            g.limb(2.0f, 1.6f, 2.9f, 2.4f, 0.4f, 0xFFFFFDF5.toInt(), l.outline)
        }
        if (l.horn) {
            p.reset().moveTo(2.5f, -0.2f).quadTo(4.6f, -1.2f, 4.4f, -3.4f).quadTo(3.6f, -1.4f, 2.0f, -1.2f).close()
            g.fillPath(p, 0xFFF4EBDD.toInt())
            g.strokePath(p, o, l.outline)
        }
        if (l.nose != 0) {
            val nx = if (l.ears == Ears.MONKEY) 2.55f else 2.9f
            g.fillOval(nx - 0.55f, 0.35f, nx + 0.45f, 1.0f, l.nose)
            g.fillCircle(nx - 0.2f, 0.5f, 0.15f, 0x88FFFFFF.toInt())
        }
        mouth(g, l, mood)

        // Blush
        if (l.blush != 0 && mood != Mood.ANGRY) {
            g.fillOval(nearX + 0.2f, 0.75f, nearX + 1.5f, 1.35f, l.blush)
            g.fillOval(farX - 1.0f, 0.75f, farX + 0.1f, 1.3f, l.blush)
        }
    }

    private fun eye(g: Gfx, x: Float, y: Float, s: Float, mood: Mood, blink: Boolean, angry: Boolean) {
        val dark = 0xFF2A1A12.toInt()
        when {
            mood == Mood.HURT -> {
                g.strokeLine(x - 0.6f * s, y - 0.6f * s, x + 0.6f * s, y + 0.6f * s, 0.35f, dark)
                g.strokeLine(x - 0.6f * s, y + 0.6f * s, x + 0.6f * s, y - 0.6f * s, 0.35f, dark)
            }
            mood == Mood.HAPPY || blink -> {
                g.strokeArc(x - 0.7f * s, y - 0.5f * s, x + 0.7f * s, y + 0.7f * s, 200f, 140f, 0.38f, dark)
            }
            else -> {
                val ry = if (angry) 0.85f * s else 1.2f * s
                g.fillOval(x - 0.95f * s, y - ry, x + 0.95f * s, y + ry, 0xFFFFFFFF.toInt())
                val pr = if (mood == Mood.SCARED) 0.45f * s else 0.72f * s
                val pry = if (mood == Mood.SCARED) 0.55f * s else 0.95f * s
                g.fillOval(x + 0.2f * s - pr, y + 0.1f * s - pry, x + 0.2f * s + pr, y + 0.1f * s + pry, dark)
                g.fillCircle(x + 0.42f * s, y - 0.32f * s, 0.3f * s, 0xFFFFFFFF.toInt())
                g.fillCircle(x - 0.05f * s, y + 0.45f * s, 0.14f * s, 0xDDFFFFFF.toInt())
                g.strokeOval(x - 0.95f * s, y - ry, x + 0.95f * s, y + ry, 0.22f, dark)
            }
        }
    }

    private fun mouth(g: Gfx, l: Look, mood: Mood) {
        val mx = if (l.trunk) 1.6f else 2.1f
        val my = 1.55f
        val dark = 0xFF5A2A1E.toInt()
        when (mood) {
            Mood.HAPPY, Mood.SCARED -> {
                p.reset().moveTo(mx - 0.75f, my - 0.1f).quadTo(mx, my + 1.4f, mx + 0.75f, my - 0.1f).close()
                g.fillPath(p, dark)
                g.fillOval(mx - 0.4f, my + 0.45f, mx + 0.4f, my + 0.95f, 0xFFFF7A8A.toInt())
            }
            Mood.ANGRY -> {
                p.reset().moveTo(mx - 1.1f, my - 0.1f).quadTo(mx, my + 1.2f, mx + 1.1f, my - 0.1f).close()
                g.fillPath(p, dark)
                for (i in 0 until 3) {
                    val tx = mx - 0.7f + i * 0.7f
                    p.reset().moveTo(tx - 0.25f, my - 0.05f).lineTo(tx, my + 0.5f).lineTo(tx + 0.25f, my - 0.05f).close()
                    g.fillPath(p, 0xFFFFFFFF.toInt())
                }
            }
            Mood.HURT -> g.strokeArc(mx - 0.6f, my, mx + 0.6f, my + 1.0f, 200f, 140f, 0.3f, dark)
            Mood.NORMAL -> {
                p.reset().moveTo(mx - 0.6f, my).quadTo(mx - 0.3f, my + 0.5f, mx, my + 0.05f).quadTo(mx + 0.3f, my + 0.5f, mx + 0.6f, my)
                g.strokePath(p, 0.26f, dark)
            }
        }
    }

    private fun sparkles(g: Gfx, t: Float) {
        for (i in 0 until 3) {
            val k = (t * 1.3f + i * 0.33f) % 1f
            val x = -3f + i * 3f
            val y = -12f + k * 4f
            val s = sin(k * Math.PI.toFloat()) * 0.7f
            g.fillOval(x - s * 0.25f, y - s, x + s * 0.25f, y + s, 0xFFFFF59D.toInt())
            g.fillOval(x - s, y - s * 0.25f, x + s, y + s * 0.25f, 0xFFFFF59D.toInt())
        }
    }

    private val boar = Look(0xFF9C7A6A.toInt(), 0xFFD9BBAA.toInt(), Ears.POINTY, Tail.SHORT,
        faceMask = 0xFFF4B8B0.toInt(), nose = 0xFFB0574E.toInt(), blush = 0)

    /** A charging warthog, facing left (towards the runner). */
    fun drawBoar(g: Gfx, x: Float, y: Float, scale: Float, phase: Float, time: Float) {
        g.save()
        g.translate(x, y)
        g.scale(-scale * 0.75f, scale * 0.75f)
        g.rotate(-6f)
        quadruped(g, boar, phase, Pose.RUN, Mood.ANGRY, time)
        // tusks
        g.limb(5.6f, -6.6f, 6.6f, -7.6f, 0.4f, 0xFFFFFBF0.toInt(), boar.outline)
        g.restore()
    }

    /** A cute flapping parrot (jungle) or bat (elsewhere), facing left. */
    fun drawBird(g: Gfx, x: Float, y: Float, scale: Float, time: Float, parrot: Boolean) {
        val body = if (parrot) 0xFFE84A5F.toInt() else 0xFF6B5B95.toInt()
        val wing = if (parrot) 0xFF2F9BDB.toInt() else 0xFF4A3F6B.toInt()
        val outline = 0xFF2A1A12.toInt()
        val flap = sin(time * 18f) * 30f
        g.save()
        g.translate(x, y)
        g.scale(scale, scale)
        // far wing
        g.save(); g.rotate(-flap); g.oOval(0.6f, -1.8f, 0.9f, 1.9f, Colors.shade(wing, 0.8f), outline); g.restore()
        g.oOval(0f, 0f, 2.1f, 1.6f, body, outline)
        g.oCircle(-1.6f, -0.9f, 1.25f, body, outline)
        eye(g, -1.9f, -1.1f, 0.5f, Mood.NORMAL, false, false)
        p.reset().moveTo(-2.7f, -0.9f).quadTo(-3.9f, -0.6f, -2.8f, 0.0f).close()
        g.fillPath(p, 0xFFFFC23D.toInt())
        g.strokePath(p, 0.2f, outline)
        if (parrot) {
            p.reset().moveTo(1.8f, 0.2f).lineTo(4.2f, 1.2f).lineTo(3.8f, -0.2f).close()
            g.fillPath(p, 0xFF35B07A.toInt())
        }
        g.save(); g.rotate(flap); g.oOval(0.4f, -1.6f, 1.0f, 2.0f, wing, outline); g.restore()
        g.restore()
    }

    // ------------------------------------------------------------------ special chasers

    private fun bees(g: Gfx, l: Look, t: Float) {
        for (i in 0 until 5) {
            val bx = -3f + (i % 3) * 3.2f + sin(t * 7f + i * 2f) * 0.8f
            val by = -8.5f + (i / 3) * 3.6f + cos(t * 9f + i) * 0.9f
            val flap = abs(sin(t * 40f + i)) * 0.6f
            g.fillOval(bx - 1.2f, by - 2.0f - flap, bx + 0.2f, by - 0.5f, 0xBBE3F2FD.toInt())
            g.fillOval(bx - 0.4f, by - 2.1f - flap, bx + 1.0f, by - 0.6f, 0xBBE3F2FD.toInt())
            g.oOval(bx, by, 1.45f, 1.1f, l.body, l.outline)
            g.fillRect(bx - 0.5f, by - 1.05f, bx - 0.05f, by + 1.05f, 0xFF2A1A12.toInt())
            g.fillRect(bx - 1.3f, by - 0.6f, bx - 0.95f, by + 0.6f, 0xFF2A1A12.toInt())
            g.fillCircle(bx + 0.75f, by - 0.25f, 0.32f, 0xFF2A1A12.toInt())
            g.strokeLine(bx + 0.4f, by - 0.75f, bx + 1.1f, by - 0.5f, 0.2f, 0xFF2A1A12.toInt())
            g.fillOval(bx - 1.75f, by - 0.12f, bx - 1.35f, by + 0.12f, 0xFF2A1A12.toInt())
        }
    }

    private fun croc(g: Gfx, l: Look, ph: Float, t: Float) {
        val jaw = (sin(ph * 0.8f) + 1f) * 0.5f
        val walk = sin(ph) * 0.4f
        // Tail
        p.reset().moveTo(-3.2f, -2.9f).quadTo(-6.5f, -2.6f + walk, -9.4f, -0.9f).quadTo(-6.2f, -0.2f, -3.2f, -0.5f).close()
        g.strokePath(p, o * 2, l.outline)
        g.fillPath(p, l.body)
        for (i in 0 until 3) g.oCircle(-4.6f - i * 1.3f, -2.4f + i * 0.4f + walk * 0.3f * i, 0.32f, l.bodyDark, l.outline)
        // Legs
        g.limb(-2.2f, -1.2f, -2.6f - walk, -0.2f, 0.9f, l.bodyDark, l.outline)
        g.limb(2.0f, -1.2f, 2.4f + walk, -0.2f, 0.9f, l.bodyDark, l.outline)
        // Body
        g.oOval(-0.4f, -1.9f, 4.4f, 1.6f, l.body, l.outline, l.bodyGrad)
        g.fillOval(-3.6f, -1.6f, 3.2f, -0.4f, l.belly)
        for (i in 0 until 5) g.oCircle(-3.0f + i * 1.4f, -3.3f, 0.42f, l.bodyDark, l.outline)
        // Head with snapping jaws
        g.save(); g.translate(3.6f, -2.6f)
        g.save(); g.rotate(-jaw * 14f)
        g.oOval(1.8f, -0.5f, 3.0f, 0.95f, l.body, l.outline)
        for (i in 0 until 4) {
            val tx = 0.4f + i * 1.0f
            p.reset().moveTo(tx - 0.25f, 0.35f).lineTo(tx, 0.95f).lineTo(tx + 0.25f, 0.35f).close()
            g.fillPath(p, 0xFFFFFFFF.toInt())
        }
        g.oCircle(0.4f, -1.5f, 0.9f, l.body, l.outline)
        eye(g, 0.5f, -1.65f, 0.6f, Mood.ANGRY, false, true)
        g.restore()
        g.save(); g.rotate(jaw * 8f)
        g.oOval(1.8f, 0.7f, 2.8f, 0.6f, l.belly, l.outline)
        g.restore()
        g.restore()
    }
}
