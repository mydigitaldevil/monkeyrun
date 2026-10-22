package com.junglerun.game.gfx

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/** [Gfx] backed by an Android [Canvas]. Reuses paints/paths so drawing allocates nothing per frame. */
class AndroidGfx : Gfx {
    var canvas: Canvas? = null
    private val c: Canvas get() = canvas!!

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boldFace = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val roundedFace: Typeface = try {
        Typeface.create("sans-serif-black", Typeface.NORMAL)
    } catch (_: Exception) {
        boldFace
    }
    private val path = Path()
    private val rect = RectF()

    private val alphaStack = FloatArray(64)
    private var depth = 0
    private var alpha = 1f

    fun begin(canvas: Canvas) {
        this.canvas = canvas
        depth = 0
        alpha = 1f
    }

    override fun save() {
        if (depth < alphaStack.size) alphaStack[depth] = alpha
        depth++
        c.save()
    }

    override fun restore() {
        depth--
        if (depth in alphaStack.indices) alpha = alphaStack[depth]
        c.restore()
    }

    override fun translate(dx: Float, dy: Float) = c.translate(dx, dy)
    override fun scale(sx: Float, sy: Float) = c.scale(sx, sy)
    override fun rotate(degrees: Float) = c.rotate(degrees)
    override fun clipRect(l: Float, t: Float, r: Float, b: Float) {
        c.clipRect(l, t, r, b)
    }

    override fun multiplyAlpha(a: Float) {
        alpha *= a.coerceIn(0f, 1f)
    }

    private fun shaderOf(g: Gradient): Shader {
        (g.platform as? Shader)?.let { return it }
        val s: Shader = if (g.radial) {
            RadialGradient(g.x0, g.y0, g.radius, g.colors, g.stops, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(g.x0, g.y0, g.x1, g.y1, g.colors, g.stops, Shader.TileMode.CLAMP)
        }
        g.platform = s
        return s
    }

    private fun fillPaint(color: Int, shader: Gradient?): Paint {
        if (shader != null) {
            fill.shader = shaderOf(shader)
            fill.color = 0xFFFFFFFF.toInt()
            fill.alpha = (255 * alpha).toInt()
        } else {
            fill.shader = null
            fill.color = color
            fill.alpha = ((color ushr 24) * alpha).toInt()
        }
        return fill
    }

    private fun strokePaint(width: Float, color: Int): Paint {
        stroke.strokeWidth = width
        stroke.color = color
        stroke.alpha = ((color ushr 24) * alpha).toInt()
        return stroke
    }

    override fun fillRect(l: Float, t: Float, r: Float, b: Float, color: Int, shader: Gradient?) =
        c.drawRect(l, t, r, b, fillPaint(color, shader))

    override fun fillRoundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, shader: Gradient?) {
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, radius, radius, fillPaint(color, shader))
    }

    override fun fillOval(l: Float, t: Float, r: Float, b: Float, color: Int, shader: Gradient?) {
        rect.set(l, t, r, b)
        c.drawOval(rect, fillPaint(color, shader))
    }

    override fun fillCircle(cx: Float, cy: Float, r: Float, color: Int, shader: Gradient?) =
        c.drawCircle(cx, cy, r, fillPaint(color, shader))

    override fun fillPath(p: GPath, color: Int, shader: Gradient?) = c.drawPath(toPath(p), fillPaint(color, shader))

    override fun strokeLine(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int) =
        c.drawLine(x1, y1, x2, y2, strokePaint(width, color))

    override fun strokePath(p: GPath, width: Float, color: Int) = c.drawPath(toPath(p), strokePaint(width, color))

    override fun strokeCircle(cx: Float, cy: Float, r: Float, width: Float, color: Int) =
        c.drawCircle(cx, cy, r, strokePaint(width, color))

    override fun strokeOval(l: Float, t: Float, r: Float, b: Float, width: Float, color: Int) {
        rect.set(l, t, r, b)
        c.drawOval(rect, strokePaint(width, color))
    }

    override fun strokeArc(l: Float, t: Float, r: Float, b: Float, startDeg: Float, sweepDeg: Float, width: Float, color: Int) {
        rect.set(l, t, r, b)
        c.drawArc(rect, startDeg, sweepDeg, false, strokePaint(width, color))
    }

    override fun strokeRoundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, width: Float, color: Int) {
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, radius, radius, strokePaint(width, color))
    }

    override fun text(s: String, x: Float, y: Float, size: Float, color: Int, align: Align, bold: Boolean, outline: Int, outlineWidth: Float) {
        textPaint.typeface = if (bold) roundedFace else Typeface.DEFAULT
        textPaint.textSize = size
        textPaint.textAlign = when (align) {
            Align.LEFT -> Paint.Align.LEFT
            Align.CENTER -> Paint.Align.CENTER
            Align.RIGHT -> Paint.Align.RIGHT
        }
        textPaint.shader = null
        if (outlineWidth > 0f && (outline ushr 24) != 0) {
            textPaint.style = Paint.Style.STROKE
            textPaint.strokeJoin = Paint.Join.ROUND
            textPaint.strokeWidth = outlineWidth
            textPaint.color = outline
            textPaint.alpha = ((outline ushr 24) * alpha).toInt()
            c.drawText(s, x, y, textPaint)
        }
        textPaint.style = Paint.Style.FILL
        textPaint.color = color
        textPaint.alpha = ((color ushr 24) * alpha).toInt()
        c.drawText(s, x, y, textPaint)
    }

    override fun textWidth(s: String, size: Float, bold: Boolean): Float {
        textPaint.typeface = if (bold) roundedFace else Typeface.DEFAULT
        textPaint.textSize = size
        return textPaint.measureText(s)
    }

    private fun toPath(p: GPath): Path {
        path.rewind()
        val ops = p.ops
        val cs = p.coords
        var k = 0
        for (i in 0 until p.opCount) {
            when (ops[i]) {
                GPath.MOVE -> { path.moveTo(cs[k], cs[k + 1]); k += 2 }
                GPath.LINE -> { path.lineTo(cs[k], cs[k + 1]); k += 2 }
                GPath.QUAD -> { path.quadTo(cs[k], cs[k + 1], cs[k + 2], cs[k + 3]); k += 4 }
                GPath.CUBIC -> { path.cubicTo(cs[k], cs[k + 1], cs[k + 2], cs[k + 3], cs[k + 4], cs[k + 5]); k += 6 }
                GPath.CLOSE -> path.close()
            }
        }
        return path
    }
}
