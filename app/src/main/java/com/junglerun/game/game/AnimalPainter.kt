package com.junglerun.game.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.junglerun.game.core.Chaser
import com.junglerun.game.core.Shape
import kotlin.math.cos
import kotlin.math.sin

enum class Pose { RUN, JUMP, SLIDE, IDLE }

/**
 * Procedural, asset-free character art. All drawing happens in a local space where (0, 0)
 * is the centre of the feet and negative y points up; 1 unit = 1 world unit (runner ≈ 11 tall).
 */
class AnimalPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val oval = RectF()
    private var alpha = 255

    fun drawAnimal(
        c: Canvas, shape: Shape, body: Int, accent: Int,
        x: Float, y: Float, scale: Float, phase: Float, pose: Pose, alpha: Int = 255,
    ) {
        this.alpha = alpha
        c.save()
        c.translate(x, y)
        c.scale(scale, scale)
        if (pose == Pose.SLIDE) {
            c.scale(1.2f, 0.52f)
        }
        val ph = if (pose == Pose.RUN) phase else if (pose == Pose.JUMP) 0.9f else 0f
        val tuck = pose == Pose.JUMP
        with(c) { when (shape) {
            Shape.MONKEY -> monkey(body, accent, ph, tuck)
            Shape.RABBIT -> rabbit(body, accent, ph, tuck)
            Shape.PANDA -> panda(ph, tuck)
            Shape.LION -> lion(body, accent, ph, tuck)
            Shape.BEAR -> bear(body, accent, ph, tuck)
            Shape.ELEPHANT -> elephant(body, accent, ph, tuck)
            Shape.CHEETAH -> cheetah(body, accent, ph, tuck)
            Shape.GIRAFFE -> giraffe(body, accent, ph, tuck)
        } }
        c.restore()
    }

    fun drawChaser(c: Canvas, chaser: Chaser, x: Float, y: Float, scale: Float, phase: Float, alpha: Int = 255) {
        this.alpha = alpha
        c.save()
        c.translate(x, y)
        c.scale(scale, scale)
        with(c) { when (chaser) {
            Chaser.LION -> lion(chaser.body, chaser.accent, phase, false)
            Chaser.TIGER -> tiger(chaser.body, chaser.accent, phase)
            Chaser.FOX -> fox(chaser.body, chaser.accent, phase)
            Chaser.RHINO -> rhino(chaser.body, chaser.accent, phase)
            Chaser.BEES -> bees(chaser.body, chaser.accent, phase)
            Chaser.CROCODILE -> croc(chaser.body, chaser.accent, phase)
            Chaser.HYENA -> hyena(chaser.body, chaser.accent, phase)
        } }
        c.restore()
    }

    // ------------------------------------------------------------------ helpers

    private fun Canvas.circle(cx: Float, cy: Float, r: Float, color: Int) {
        fill.color = color
        fill.alpha = (fill.alpha * alpha) / 255
        drawCircle(cx, cy, r, fill)
    }

    private fun Canvas.ellipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        fill.color = color
        fill.alpha = (fill.alpha * alpha) / 255
        oval.set(cx - rx, cy - ry, cx + rx, cy + ry)
        drawOval(oval, fill)
    }

    private fun Canvas.line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        stroke.color = color
        stroke.alpha = (stroke.alpha * alpha) / 255
        stroke.strokeWidth = w
        drawLine(x1, y1, x2, y2, stroke)
    }

    private fun Canvas.curve(x1: Float, y1: Float, cx: Float, cy: Float, x2: Float, y2: Float, w: Float, color: Int) {
        stroke.color = color
        stroke.alpha = (stroke.alpha * alpha) / 255
        stroke.strokeWidth = w
        path.reset()
        path.moveTo(x1, y1)
        path.quadTo(cx, cy, x2, y2)
        drawPath(path, stroke)
    }

    private fun Canvas.eye(cx: Float, cy: Float, r: Float = 0.38f) {
        circle(cx, cy, r * 1.5f, WHITE)
        circle(cx + r * 0.3f, cy, r, BLACK)
    }

    private fun darker(color: Int, f: Float = 0.75f): Int {
        val r = ((color shr 16 and 0xFF) * f).toInt()
        val g = ((color shr 8 and 0xFF) * f).toInt()
        val b = ((color and 0xFF) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Four animated legs hanging from a body; returns nothing, just draws. */
    private fun Canvas.quadLegs(
        frontX: Float, backX: Float, hipY: Float, len: Float, w: Float, color: Int, ph: Float, tuck: Boolean,
    ) {
        val swing = if (tuck) 0f else 1.8f
        val lift = if (tuck) len * 0.45f else 0f
        val back = darker(color)
        val s = sin(ph)
        line(backX, hipY, backX - s * swing - (if (tuck) 1.2f else 0f), hipY + len - lift, w, back)
        line(frontX, hipY, frontX + s * swing + (if (tuck) 1.2f else 0f), hipY + len - lift, w, back)
        line(backX + 0.6f, hipY, backX + 0.6f + s * swing - (if (tuck) 1.5f else 0f), hipY + len - lift, w, color)
        line(frontX + 0.6f, hipY, frontX + 0.6f - s * swing + (if (tuck) 1.5f else 0f), hipY + len - lift, w, color)
    }

    // ------------------------------------------------------------------ playable animals

    private fun Canvas.monkeyBody(body: Int, accent: Int, ph: Float, tuck: Boolean) {
        val s = sin(ph)
        // Curly tail
        curve(-2.2f, -4.2f, -6.5f, -4f, -5.8f, -8.6f, 0.8f, body)
        circle(-5.6f, -8.9f, 0.7f, body)
        // Legs
        val footY = if (tuck) -2f else 0f
        line(-0.4f, -3.6f, -s * 2.2f, footY, 1.3f, darker(body))
        line(0.2f, -3.6f, s * 2.2f, footY, 1.3f, body)
        // Torso
        ellipse(0f, -5.6f, 2.6f, 3.1f, body)
        ellipse(0.7f, -5.3f, 1.6f, 2.2f, accent)
        // Arms
        val armY = if (tuck) -10.5f else -4.5f
        line(0.2f, -7f, 2.6f + s * 1.2f, armY, 1.0f, body)
        line(-0.4f, -7f, -1.8f - s * 1.2f, armY + 0.8f, 1.0f, darker(body))
        // Head
        circle(-0.7f, -9.6f, 0.95f, body)
        circle(-0.7f, -9.6f, 0.5f, accent)
        circle(1.2f, -9.4f, 2.4f, body)
        ellipse(2.1f, -9.0f, 1.6f, 1.4f, accent)
        eye(2.5f, -9.8f)
        curve(2.2f, -8.3f, 2.9f, -7.9f, 3.4f, -8.5f, 0.25f, darker(body, 0.5f))
    }

    private fun Canvas.monkey(body: Int, accent: Int, ph: Float, tuck: Boolean) = monkeyBody(body, accent, ph, tuck)

    private fun Canvas.rabbit(body: Int, accent: Int, ph: Float, tuck: Boolean) {
        val hop = if (tuck) 0f else kotlin.math.abs(sin(ph)) * 1.2f
        translate(0f, -hop)
        circle(-3.6f, -4.6f, 1.1f, WHITE)
        // Hind foot & front paw
        ellipse(-1.6f + sin(ph) * 0.8f, -0.5f, 1.8f, 0.6f, darker(body, 0.9f))
        line(1.8f, -3f, 2.2f + cos(ph) * 0.8f, -0.3f, 0.8f, body)
        ellipse(-0.4f, -4f, 3.3f, 2.6f, body)
        // Ears
        save(); rotate(-12f, 1.6f, -9f)
        ellipse(1.6f, -10.6f, 0.75f, 2.7f, body); ellipse(1.6f, -10.6f, 0.4f, 2.1f, accent)
        restore()
        save(); rotate(8f, 2.6f, -9f)
        ellipse(2.6f, -10.4f, 0.75f, 2.6f, darker(body, 0.92f)); ellipse(2.6f, -10.4f, 0.4f, 2.0f, accent)
        restore()
        circle(2.5f, -6.8f, 2.0f, body)
        eye(3.2f, -7.3f)
        circle(4.4f, -6.6f, 0.4f, accent)
    }

    private fun Canvas.panda(ph: Float, tuck: Boolean) {
        quadLegs(2f, -2.5f, -4.5f, 4.5f, 1.8f, BLACK, ph, tuck)
        ellipse(-0.3f, -5.4f, 4.1f, 3.1f, WHITE)
        ellipse(1.2f, -6.2f, 1.3f, 2.4f, BLACK) // shoulder band
        circle(3.4f, -8.4f, 0.9f, BLACK)
        circle(5.4f, -8.2f, 0.85f, BLACK)
        circle(4.4f, -6.6f, 2.4f, WHITE)
        ellipse(5.2f, -6.9f, 0.75f, 0.95f, BLACK)
        circle(5.35f, -7.0f, 0.3f, WHITE)
        circle(6.6f, -6.0f, 0.4f, BLACK)
    }

    private fun Canvas.bear(body: Int, accent: Int, ph: Float, tuck: Boolean) {
        quadLegs(2f, -2.6f, -4.5f, 4.5f, 1.8f, body, ph, tuck)
        ellipse(-0.3f, -5.5f, 4.2f, 3.2f, body)
        circle(3.0f, -8.6f, 0.9f, body)
        circle(3.0f, -8.6f, 0.45f, accent)
        circle(4.0f, -6.8f, 2.4f, body)
        ellipse(5.7f, -6.2f, 1.3f, 1.0f, accent)
        circle(6.7f, -6.4f, 0.45f, BLACK)
        eye(4.6f, -7.6f)
    }

    private fun Canvas.lion(body: Int, accent: Int, ph: Float, tuck: Boolean) {
        // Tail with tuft
        curve(-4f, -6f, -6.5f, -6.5f, -7f, -9f, 0.6f, body)
        circle(-7f, -9.2f, 0.8f, accent)
        quadLegs(2.2f, -2.6f, -4.6f, 4.6f, 1.5f, body, ph, tuck)
        ellipse(-0.3f, -5.8f, 4.3f, 2.6f, body)
        circle(3.6f, -7.3f, 3.3f, accent) // mane
        circle(4.2f, -7.1f, 2.1f, body)
        ellipse(5.6f, -6.4f, 1.1f, 0.8f, 0xFFF5DEB3.toInt())
        circle(6.4f, -6.7f, 0.4f, darker(accent, 0.6f))
        eye(4.8f, -7.8f)
    }

    private fun Canvas.elephant(body: Int, accent: Int, ph: Float, tuck: Boolean) {
        curve(-4.4f, -6f, -5.5f, -5f, -5.4f, -3.5f, 0.5f, body)
        quadLegs(2.0f, -2.8f, -3.8f, 3.8f, 2.1f, body, ph, tuck)
        ellipse(-0.4f, -5.6f, 4.5f, 3.4f, body)
        circle(3.7f, -7.3f, 2.6f, body)
        // Trunk swings a little while running
        val sw = sin(ph) * 0.6f
        curve(5.6f, -6.6f, 7.4f + sw, -5f, 6.6f + sw, -2.4f, 1.3f, body)
        line(5.2f, -5.6f, 6.4f, -5.0f, 0.5f, WHITE)
        ellipse(2.4f, -7.1f, 1.5f, 2.2f, darker(body, 0.85f))
        ellipse(2.4f, -7.1f, 0.9f, 1.5f, accent)
        eye(4.5f, -8.0f)
    }

    private fun Canvas.cheetah(body: Int, accent: Int, ph: Float, tuck: Boolean) {
        curve(-4.4f, -6.2f, -7.5f, -6f, -7.6f, -9.3f, 0.6f, body)
        quadLegs(2.4f, -2.8f, -5f, 5f, 1.1f, body, ph * 1.2f, tuck)
        ellipse(-0.3f, -6.1f, 4.4f, 2.1f, body)
        for (i in 0 until 6) circle(-3f + i * 1.1f, -6.3f + (i % 2) * 0.8f, 0.35f, accent)
        circle(4.2f, -7.4f, 1.7f, body)
        circle(3.6f, -8.9f, 0.5f, body)
        ellipse(5.4f, -7.0f, 0.8f, 0.6f, 0xFFFFF8E1.toInt())
        eye(4.6f, -7.9f, 0.3f)
        line(4.7f, -7.6f, 5.0f, -6.6f, 0.2f, accent)
    }

    private fun Canvas.giraffe(body: Int, accent: Int, ph: Float, tuck: Boolean) {
        curve(-3.6f, -7f, -4.6f, -6.5f, -4.8f, -4.6f, 0.4f, body)
        quadLegs(1.6f, -2.4f, -5f, 5f, 0.9f, body, ph, tuck)
        ellipse(-0.6f, -6.4f, 3.4f, 2.1f, body)
        line(1.8f, -7.2f, 3.8f, -13.4f, 1.5f, body)
        for (i in 0 until 4) circle(-2.4f + i * 1.4f, -6.5f + (i % 2) * 0.6f, 0.5f, accent)
        circle(2.6f, -9.6f, 0.4f, accent)
        circle(3.3f, -11.8f, 0.35f, accent)
        line(3.8f, -14.4f, 3.6f, -15.8f, 0.3f, accent)
        line(4.4f, -14.4f, 4.4f, -15.8f, 0.3f, accent)
        ellipse(4.6f, -13.8f, 1.6f, 0.95f, body)
        eye(4.8f, -14.1f, 0.28f)
    }

    // ------------------------------------------------------------------ chasers

    private fun Canvas.tiger(body: Int, accent: Int, ph: Float) {
        curve(-4.2f, -6f, -7f, -6f, -7.4f, -8.8f, 0.6f, body)
        quadLegs(2.2f, -2.6f, -4.6f, 4.6f, 1.6f, body, ph, false)
        ellipse(-0.3f, -5.8f, 4.4f, 2.7f, body)
        for (i in 0 until 5) line(-3f + i * 1.3f, -8.2f, -2.6f + i * 1.3f, -6.4f, 0.4f, accent)
        circle(4.1f, -7.2f, 2.2f, body)
        circle(3.4f, -9.1f, 0.6f, body)
        ellipse(5.5f, -6.6f, 1.0f, 0.7f, WHITE)
        line(3.6f, -8.6f, 4.0f, -7.8f, 0.3f, accent)
        eye(4.8f, -7.7f)
    }

    private fun Canvas.fox(body: Int, accent: Int, ph: Float) {
        curve(-3.6f, -5.6f, -7.4f, -5.2f, -8f, -7.8f, 1.6f, body)
        circle(-8f, -8f, 0.9f, accent)
        quadLegs(2f, -2.2f, -4f, 4f, 0.9f, 0xFF3E2723.toInt(), ph, false)
        ellipse(-0.3f, -5.2f, 3.6f, 2.0f, body)
        circle(3.6f, -6.4f, 1.8f, body)
        path.reset(); path.moveTo(4.6f, -7f); path.lineTo(7.2f, -5.8f); path.lineTo(4.6f, -5.2f); path.close()
        fill.color = accent; fill.alpha = alpha; drawPath(path, fill)
        path.reset(); path.moveTo(2.6f, -7.6f); path.lineTo(3.1f, -9.8f); path.lineTo(4.0f, -7.8f); path.close()
        fill.color = body; fill.alpha = alpha; drawPath(path, fill)
        circle(7.1f, -5.8f, 0.35f, BLACK)
        eye(4.2f, -7f, 0.3f)
    }

    private fun Canvas.rhino(body: Int, accent: Int, ph: Float) {
        quadLegs(2.2f, -2.8f, -4.2f, 4.2f, 2.1f, body, ph, false)
        ellipse(-0.4f, -5.8f, 5.0f, 3.3f, body)
        ellipse(4.6f, -6.0f, 2.6f, 2.0f, body)
        path.reset(); path.moveTo(6.0f, -7.2f); path.lineTo(7.6f, -10.2f); path.lineTo(7.2f, -6.8f); path.close()
        fill.color = accent; fill.alpha = alpha; drawPath(path, fill)
        circle(3.4f, -8.0f, 0.6f, body)
        eye(5.0f, -6.8f, 0.3f)
    }

    private fun Canvas.bees(body: Int, accent: Int, ph: Float) {
        for (i in 0 until 7) {
            val bx = -3f + (i % 4) * 2.6f + sin(ph * 1.7f + i) * 1.2f
            val by = -9f + (i / 4) * 3.4f + cos(ph * 2.1f + i * 1.3f) * 1.5f
            ellipse(bx, by - 1.0f, 0.9f, 0.6f, 0x99FFFFFF.toInt())
            ellipse(bx, by, 1.2f, 0.8f, body)
            line(bx - 0.3f, by - 0.7f, bx - 0.3f, by + 0.7f, 0.35f, accent)
            line(bx + 0.4f, by - 0.7f, bx + 0.4f, by + 0.7f, 0.35f, accent)
        }
    }

    private fun Canvas.croc(body: Int, accent: Int, ph: Float) {
        val jaw = (sin(ph * 0.8f) + 1f) * 0.6f
        quadLegs(2.6f, -3.2f, -1.6f, 1.6f, 1.1f, body, ph, false)
        curve(-4.5f, -2.6f, -7.5f, -2.4f, -9.5f, -1.6f, 1.6f, body)
        ellipse(-0.5f, -2.6f, 5f, 1.6f, body)
        for (i in 0 until 5) circle(-3.4f + i * 1.5f, -4f, 0.45f, darker(body))
        // Jaws
        path.reset(); path.moveTo(3.5f, -3.4f); path.lineTo(9.0f, -3.4f - jaw); path.lineTo(9.0f, -2.6f - jaw); path.lineTo(3.5f, -2.4f); path.close()
        fill.color = body; fill.alpha = alpha; drawPath(path, fill)
        path.reset(); path.moveTo(3.5f, -2.2f); path.lineTo(9.0f, -1.6f + jaw * 0.4f); path.lineTo(9.0f, -1.0f + jaw * 0.4f); path.lineTo(3.5f, -1.2f); path.close()
        drawPath(path, fill)
        for (i in 0 until 4) line(4.5f + i * 1.2f, -2.5f - jaw * 0.2f * i, 4.5f + i * 1.2f, -2.0f, 0.3f, accent)
        eye(4.2f, -4.0f, 0.35f)
    }

    private fun Canvas.hyena(body: Int, accent: Int, ph: Float) {
        curve(-3.8f, -5.2f, -5.4f, -4.6f, -5.6f, -3f, 0.6f, accent)
        quadLegs(2.2f, -2.4f, -5f, 5f, 1.1f, body, ph, false)
        // Sloped back: high shoulders, low hips
        save(); rotate(-10f, 0f, -5.6f)
        ellipse(-0.2f, -5.6f, 4.0f, 2.2f, body)
        restore()
        for (i in 0 until 5) circle(-2.4f + i * 1.2f, -5.5f - i * 0.25f, 0.4f, accent)
        circle(4.0f, -7.8f, 1.9f, body)
        ellipse(5.6f, -7.2f, 1.2f, 0.75f, darker(body, 0.85f))
        circle(3.2f, -9.6f, 0.7f, accent)
        circle(6.6f, -7.4f, 0.35f, BLACK)
        eye(4.6f, -8.3f, 0.3f)
    }

    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF1A1A1A.toInt()
    }
}
