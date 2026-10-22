package com.junglerun.game.gfx

/**
 * Tiny 2D drawing API used by all game art. The game draws through [AndroidGfx]; unit tests and
 * the preview tool draw through a Java2D implementation so the art can be rendered to PNG off-device.
 *
 * Colors are ARGB ints. Angles are degrees, clockwise (Android convention).
 */
interface Gfx {
    fun save()
    fun restore()
    fun translate(dx: Float, dy: Float)
    fun scale(sx: Float, sy: Float = sx)
    fun rotate(degrees: Float)
    fun clipRect(l: Float, t: Float, r: Float, b: Float)

    /** Multiplies the opacity of everything drawn until the matching [restore]. */
    fun multiplyAlpha(a: Float)

    fun fillRect(l: Float, t: Float, r: Float, b: Float, color: Int, shader: Gradient? = null)
    fun fillRoundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, shader: Gradient? = null)
    fun fillOval(l: Float, t: Float, r: Float, b: Float, color: Int, shader: Gradient? = null)
    fun fillCircle(cx: Float, cy: Float, r: Float, color: Int, shader: Gradient? = null)
    fun fillPath(p: GPath, color: Int, shader: Gradient? = null)

    fun strokeLine(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int)
    fun strokePath(p: GPath, width: Float, color: Int)
    fun strokeCircle(cx: Float, cy: Float, r: Float, width: Float, color: Int)
    fun strokeOval(l: Float, t: Float, r: Float, b: Float, width: Float, color: Int)
    fun strokeArc(l: Float, t: Float, r: Float, b: Float, startDeg: Float, sweepDeg: Float, width: Float, color: Int)
    fun strokeRoundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, width: Float, color: Int)

    /** Draws text with its baseline at [y]. Optional outline is drawn underneath. */
    fun text(
        s: String, x: Float, y: Float, size: Float, color: Int,
        align: Align = Align.LEFT, bold: Boolean = true, outline: Int = 0, outlineWidth: Float = 0f,
    )

    fun textWidth(s: String, size: Float, bold: Boolean = true): Float
}

enum class Align { LEFT, CENTER, RIGHT }

/** Linear or radial gradient. Platforms cache their native shader in [platform]. */
class Gradient private constructor(
    val radial: Boolean,
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val radius: Float,
    val colors: IntArray,
    val stops: FloatArray?,
) {
    @JvmField var platform: Any? = null

    companion object {
        fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, stops: FloatArray? = null) =
            Gradient(false, x0, y0, x1, y1, 0f, colors, stops)

        fun radial(cx: Float, cy: Float, radius: Float, colors: IntArray, stops: FloatArray? = null) =
            Gradient(true, cx, cy, cx, cy, radius.coerceAtLeast(0.001f), colors, stops)
    }
}

/** Recorded path (move/line/quad/cubic/close) replayed by each platform without allocation. */
class GPath {
    var ops = ByteArray(32); private set
    var opCount = 0; private set
    var coords = FloatArray(128); private set
    var coordCount = 0; private set

    fun reset(): GPath {
        opCount = 0
        coordCount = 0
        return this
    }

    private fun op(o: Byte, n: Int) {
        if (opCount == ops.size) ops = ops.copyOf(ops.size * 2)
        if (coordCount + n > coords.size) coords = coords.copyOf((coords.size * 2).coerceAtLeast(coordCount + n))
        ops[opCount++] = o
    }

    private fun c(v: Float) {
        coords[coordCount++] = v
    }

    fun moveTo(x: Float, y: Float): GPath { op(MOVE, 2); c(x); c(y); return this }
    fun lineTo(x: Float, y: Float): GPath { op(LINE, 2); c(x); c(y); return this }
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float): GPath { op(QUAD, 4); c(cx); c(cy); c(x); c(y); return this }
    fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float): GPath {
        op(CUBIC, 6); c(c1x); c(c1y); c(c2x); c(c2y); c(x); c(y); return this
    }
    fun close(): GPath { op(CLOSE, 0); return this }

    /** Smooth closed blob through the given points (Catmull-Rom → cubic). */
    fun blob(xs: FloatArray, ys: FloatArray, tension: Float = 1f): GPath {
        val n = xs.size
        moveTo(xs[0], ys[0])
        for (i in 0 until n) {
            val p0 = (i - 1 + n) % n
            val p2 = (i + 1) % n
            val p3 = (i + 2) % n
            val t = tension / 6f
            cubicTo(
                xs[i] + (xs[p2] - xs[p0]) * t, ys[i] + (ys[p2] - ys[p0]) * t,
                xs[p2] - (xs[p3] - xs[i]) * t, ys[p2] - (ys[p3] - ys[i]) * t,
                xs[p2], ys[p2],
            )
        }
        return close()
    }

    companion object {
        const val MOVE: Byte = 0
        const val LINE: Byte = 1
        const val QUAD: Byte = 2
        const val CUBIC: Byte = 3
        const val CLOSE: Byte = 4
    }
}

object Colors {
    fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

    fun withAlpha(color: Int, alpha: Float): Int =
        (((color ushr 24) * alpha.coerceIn(0f, 1f)).toInt() shl 24) or (color and 0xFFFFFF)

    fun shade(color: Int, f: Float): Int {
        val a = color ushr 24
        val r = ((color shr 16 and 0xFF) * f).toInt().coerceIn(0, 255)
        val g = ((color shr 8 and 0xFF) * f).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * f).toInt().coerceIn(0, 255)
        return argb(a, r, g, b)
    }

    /** Linear mix of two colors, t=0 → a, t=1 → b. */
    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - k) + ((b shr s) and 0xFF) * k).toInt()
        return argb(ch(24), ch(16), ch(8), ch(0))
    }

    fun lighten(color: Int, t: Float) = mix(color, (color and 0xFF000000.toInt()) or 0xFFFFFF, t)
}
