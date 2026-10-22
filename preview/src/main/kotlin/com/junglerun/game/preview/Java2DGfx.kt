package com.junglerun.game.preview

import com.junglerun.game.gfx.Align
import com.junglerun.game.gfx.GPath
import com.junglerun.game.gfx.Gfx
import com.junglerun.game.gfx.Gradient
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.MultipleGradientPaint
import java.awt.Paint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

/** Java2D [Gfx] used to render the game's art to PNG on a desktop JVM (previews & tests). */
class Java2DGfx(private val g: Graphics2D) : Gfx {
    private class State(val tx: AffineTransform, val clip: Shape?, val alpha: Float)

    private val stack = ArrayList<State>()
    private var alpha = 1f

    init {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }

    override fun save() {
        stack += State(g.transform, g.clip, alpha)
    }

    override fun restore() {
        val s = stack.removeAt(stack.lastIndex)
        g.transform = s.tx
        g.clip = s.clip
        alpha = s.alpha
    }

    override fun translate(dx: Float, dy: Float) = g.translate(dx.toDouble(), dy.toDouble())
    override fun scale(sx: Float, sy: Float) = g.scale(sx.toDouble(), sy.toDouble())
    override fun rotate(degrees: Float) = g.rotate(Math.toRadians(degrees.toDouble()))
    override fun clipRect(l: Float, t: Float, r: Float, b: Float) = g.clip(Rectangle2D.Float(l, t, r - l, b - t))
    override fun multiplyAlpha(a: Float) {
        alpha *= a.coerceIn(0f, 1f)
    }

    private fun color(c: Int) = Color(c, true)

    private fun paintOf(color: Int, shader: Gradient?): Paint {
        if (shader == null) return color(color)
        (shader.platform as? Paint)?.let { return it }
        val stops = shader.stops ?: FloatArray(shader.colors.size) { it / (shader.colors.size - 1f) }
        // Java2D needs strictly increasing fractions
        val fr = FloatArray(stops.size)
        for (i in stops.indices) fr[i] = if (i > 0 && stops[i] <= fr[i - 1]) fr[i - 1] + 0.0001f else stops[i]
        val cols = shader.colors.map { color(it) }.toTypedArray()
        val p: Paint = if (shader.radial) {
            RadialGradientPaint(Point2D.Float(shader.x0, shader.y0), shader.radius, fr, cols, MultipleGradientPaint.CycleMethod.NO_CYCLE)
        } else {
            val same = shader.x0 == shader.x1 && shader.y0 == shader.y1
            LinearGradientPaint(
                Point2D.Float(shader.x0, shader.y0),
                Point2D.Float(shader.x1 + if (same) 0.001f else 0f, shader.y1), fr, cols,
            )
        }
        shader.platform = p
        return p
    }

    private fun fill(s: Shape, color: Int, shader: Gradient?) {
        val a = if (shader == null) alpha else alpha
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a.coerceIn(0f, 1f))
        g.paint = paintOf(color, shader)
        g.fill(s)
    }

    private fun stroke(s: Shape, width: Float, color: Int) {
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))
        g.paint = color(color)
        g.stroke = BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(s)
    }

    override fun fillRect(l: Float, t: Float, r: Float, b: Float, color: Int, shader: Gradient?) =
        fill(Rectangle2D.Float(l, t, r - l, b - t), color, shader)

    override fun fillRoundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, shader: Gradient?) =
        fill(RoundRectangle2D.Float(l, t, r - l, b - t, radius * 2, radius * 2), color, shader)

    override fun fillOval(l: Float, t: Float, r: Float, b: Float, color: Int, shader: Gradient?) =
        fill(Ellipse2D.Float(l, t, r - l, b - t), color, shader)

    override fun fillCircle(cx: Float, cy: Float, r: Float, color: Int, shader: Gradient?) =
        fill(Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2), color, shader)

    override fun fillPath(p: GPath, color: Int, shader: Gradient?) = fill(toPath(p), color, shader)

    override fun strokeLine(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int) =
        stroke(Line2D.Float(x1, y1, x2, y2), width, color)

    override fun strokePath(p: GPath, width: Float, color: Int) = stroke(toPath(p), width, color)

    override fun strokeCircle(cx: Float, cy: Float, r: Float, width: Float, color: Int) =
        stroke(Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2), width, color)

    override fun strokeOval(l: Float, t: Float, r: Float, b: Float, width: Float, color: Int) =
        stroke(Ellipse2D.Float(l, t, r - l, b - t), width, color)

    override fun strokeArc(l: Float, t: Float, r: Float, b: Float, startDeg: Float, sweepDeg: Float, width: Float, color: Int) =
        stroke(Arc2D.Float(l, t, r - l, b - t, -startDeg, -sweepDeg, Arc2D.OPEN), width, color)

    override fun strokeRoundRect(l: Float, t: Float, r: Float, b: Float, radius: Float, width: Float, color: Int) =
        stroke(RoundRectangle2D.Float(l, t, r - l, b - t, radius * 2, radius * 2), width, color)

    private fun font(size: Float, bold: Boolean) = Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, 12).deriveFont(size)

    override fun text(s: String, x: Float, y: Float, size: Float, color: Int, align: Align, bold: Boolean, outline: Int, outlineWidth: Float) {
        if (s.isEmpty()) return
        val f = font(size, bold)
        val layout = TextLayout(s, f, g.fontRenderContext)
        val w = layout.advance
        val dx = when (align) {
            Align.LEFT -> 0f
            Align.CENTER -> -w / 2f
            Align.RIGHT -> -w
        }
        val shape = layout.getOutline(AffineTransform.getTranslateInstance((x + dx).toDouble(), y.toDouble()))
        if (outlineWidth > 0f && (outline ushr 24) != 0) stroke(shape, outlineWidth, outline)
        fill(shape, color, null)
    }

    override fun textWidth(s: String, size: Float, bold: Boolean): Float =
        if (s.isEmpty()) 0f else TextLayout(s, font(size, bold), g.fontRenderContext).advance

    private val images = HashMap<String, java.awt.image.BufferedImage>()

    override fun cached(key: String, w: Int, h: Int, l: Float, t: Float, r: Float, b: Float, render: (Gfx) -> Unit) {
        if (w <= 0 || h <= 0) return
        val img = images.getOrPut(key) {
            java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB).also { im ->
                val g2 = im.createGraphics()
                render(Java2DGfx(g2))
                g2.dispose()
            }
        }
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(img, l.toInt(), t.toInt(), (r - l).toInt(), (b - t).toInt(), null)
    }

    private fun toPath(p: GPath): Path2D.Float {
        val path = Path2D.Float()
        val cs = p.coords
        var k = 0
        for (i in 0 until p.opCount) {
            when (p.ops[i]) {
                GPath.MOVE -> { path.moveTo(cs[k], cs[k + 1]); k += 2 }
                GPath.LINE -> { path.lineTo(cs[k], cs[k + 1]); k += 2 }
                GPath.QUAD -> { path.quadTo(cs[k], cs[k + 1], cs[k + 2], cs[k + 3]); k += 4 }
                GPath.CUBIC -> { path.curveTo(cs[k], cs[k + 1], cs[k + 2], cs[k + 3], cs[k + 4], cs[k + 5]); k += 6 }
                GPath.CLOSE -> path.closePath()
            }
        }
        return path
    }
}
