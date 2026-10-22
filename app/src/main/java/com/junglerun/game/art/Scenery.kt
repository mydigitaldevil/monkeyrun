package com.junglerun.game.art

import com.junglerun.game.core.Biome
import com.junglerun.game.gfx.Colors
import com.junglerun.game.gfx.GPath
import com.junglerun.game.gfx.Gfx
import com.junglerun.game.gfx.Gradient
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Painterly, layered scenery. Each parallax layer is a seamless horizontal tile rendered once into
 * an offscreen bitmap ([Gfx.cached]) with lots of detail — foliage clusters with light and shadow,
 * bark, vines, ferns, grass blades — and atmospheric haze that increases with distance.
 * Per frame we only blit the tiles and add light rays, floating motes and a vignette.
 */
class Scenery {
    private val p = GPath()
    private val q = GPath()
    private val grads = HashMap<String, Gradient>()

    private fun rnd(i: Int, salt: Int): Float {
        var x = i * 374761393 + salt * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0xFFFFFF) / 16777215f
    }

    /** Layer = (parallax factor, height in world units above ground, depth below ground, render resolution). */
    private class Layer(val name: String, val factor: Float, val up: Float, val down: Float, val res: Float)

    private val layers = listOf(
        Layer("mountains", 0.04f, 70f, 2f, 0.4f),
        Layer("far", 0.12f, 48f, 2f, 0.45f),
        Layer("mid", 0.27f, 62f, 2f, 0.55f),
        Layer("near", 0.5f, 95f, 3f, 0.7f),
        Layer("under", 0.78f, 16f, 2f, 0.8f),
    )

    fun drawBackground(g: Gfx, th: Theme, biome: Biome, v: View, dist: Float) {
        val s = v.s
        val sky = grads.getOrPut("sky-${biome.name}-${v.top}-${v.groundY}") {
            Gradient.linear(0f, v.top, 0f, v.groundY, intArrayOf(th.skyTop, th.skyMid, th.skyHorizon), floatArrayOf(0f, 0.55f, 1f))
        }
        g.fillRect(v.left, v.top, v.right, v.groundY + 2f, 0, sky)
        sun(g, th, biome, v)
        clouds(g, th, v, dist)
        for ((i, l) in layers.withIndex()) {
            tiled(g, v, "${biome.name}-${l.name}", l.factor, l.up, l.down, l.res, dist) { gg, ps, tw, base ->
                when (l.name) {
                    "mountains" -> mountains(gg, th, biome, ps, tw, base)
                    "far" -> farForest(gg, th, biome, ps, tw, base)
                    "mid" -> midForest(gg, th, biome, ps, tw, base)
                    "near" -> nearTrees(gg, th, biome, ps, tw, base)
                    else -> undergrowth(gg, th, biome, ps, tw, base)
                }
            }
            // Mist between the distant layers sells the depth
            if (i in 1..2) haze(g, th, v, l.up * 0.45f, if (i == 1) 0.35f else 0.18f)
        }
        if (th.rays) rays(g, v, dist)
    }

    /** Foreground: dark, oversized leaves that pass in front of the camera. */
    fun drawForeground(g: Gfx, th: Theme, biome: Biome, v: View, dist: Float) {
        tiled(g, v, "${biome.name}-fg", 1.35f, 18f, 30f, 0.6f, dist) { gg, ps, tw, base -> foreground(gg, th, biome, ps, tw, base) }
        motes(g, th, v)
        grade(g, th, v)
        vignette(g, v)
    }

    /** Warm light from the sun side, cool shade on the other — a cheap cinematic colour grade. */
    private fun grade(g: Gfx, th: Theme, v: View) {
        val key = "grade-${th.sun}-${v.left}-${v.top}-${v.width}-${v.height}"
        val warm = grads.getOrPut(key) {
            Gradient.radial(v.right - v.width * 0.2f, v.top, max(v.width, v.height) * 0.9f,
                intArrayOf(Colors.withAlpha(th.sun, 0.22f), Colors.withAlpha(th.sun, 0.06f), 0x00000000), floatArrayOf(0f, 0.5f, 1f))
        }
        g.fillRect(v.left, v.top, v.right, v.bottom, 0, warm)
        val cool = grads.getOrPut("$key-cool") {
            Gradient.linear(0f, v.groundY, 0f, v.bottom, intArrayOf(0x00102040, 0x33102040), null)
        }
        g.fillRect(v.left, v.groundY, v.right, v.bottom, 0, cool)
    }

    // ------------------------------------------------------------------ tiling

    private fun tiled(
        g: Gfx, v: View, key: String, factor: Float, up: Float, down: Float, res: Float, dist: Float,
        paint: (Gfx, Float, Float, Float) -> Unit,
    ) {
        val s = v.s
        val tileWpx = max(v.width, 900f)
        val top = v.groundY - up * s
        val bottom = v.groundY + down * s
        val hPx = bottom - top
        val bw = (tileWpx * res).toInt()
        val bh = (hPx * res).toInt()
        val tileWu = tileWpx / s
        val ps = s * res
        val cacheKey = "$key-${tileWpx.toInt()}-${hPx.toInt()}-${s.toInt()}"
        val off = ((dist * factor * s) % tileWpx + tileWpx) % tileWpx
        var x = v.left - off
        while (x < v.right) {
            g.cached(cacheKey, bw, bh, x, top, x + tileWpx, bottom) { gg -> paint(gg, ps, tileWu, up * ps) }
            x += tileWpx
        }
    }

    /** Calls [draw] at x, x-W and x+W so objects crossing the tile edge wrap seamlessly. */
    private inline fun wrap(x: Float, tw: Float, draw: (Float) -> Unit) {
        draw(x)
        draw(x - tw)
        draw(x + tw)
    }

    // ------------------------------------------------------------------ sky

    private fun sun(g: Gfx, th: Theme, biome: Biome, v: View) {
        val s = v.s
        val x = v.right - v.width * 0.22f
        val y = max(v.top + 16f * s, v.groundY - 66f * s)
        val glow = grads.getOrPut("sun-${biome.name}-$x-$y-$s") {
            Gradient.radial(x, y, 40f * s, intArrayOf(Colors.withAlpha(th.sun, 0.9f), Colors.withAlpha(th.sun, 0.35f), Colors.withAlpha(th.sun, 0.08f), 0x00FFFFFF), floatArrayOf(0f, 0.12f, 0.45f, 1f))
        }
        g.fillCircle(x, y, 40f * s, 0, glow)
        g.fillCircle(x, y, 5.5f * s, 0xFFFFFDF2.toInt())
    }

    private fun clouds(g: Gfx, th: Theme, v: View, dist: Float) {
        val s = v.s
        val span = 95f * s
        val off = (dist * 0.025f * s) % span
        val rows = min(4, max(1, ((v.groundY - v.top) / (38f * s)).toInt()))
        for (row in 0 until rows) {
            var cx = v.left - off - row * span * 0.41f
            var i = row * 11
            while (cx < v.right + span) {
                if (rnd(i, 3) > 0.35f) {
                    val cy = v.groundY - (52f + row * 32f + rnd(i, 4) * 12f) * s
                    if (cy > v.top - 12f * s) cloud(g, th, cx, cy, (0.9f + rnd(i, 5) * 0.6f) * s, row)
                }
                cx += span
                i++
            }
        }
    }

    private fun cloud(g: Gfx, th: Theme, x: Float, y: Float, k: Float, row: Int) {
        val shadow = Colors.mix(0xFFC9D8E8.toInt(), th.skyMid, 0.35f)
        val alpha = 0.92f - row * 0.12f
        val sh = Colors.withAlpha(shadow, alpha)
        val wh = Colors.withAlpha(0xFFFFFFFF.toInt(), alpha)
        g.fillRoundRect(x - 9f * k, y - 1f * k, x + 11f * k, y + 3.4f * k, 2.2f * k, sh)
        g.fillCircle(x - 5f * k, y, 4f * k, sh)
        g.fillCircle(x + 1f * k, y - 3f * k, 6f * k, sh)
        g.fillCircle(x + 7f * k, y - 0.5f * k, 4.4f * k, sh)
        g.fillCircle(x - 5f * k, y - 0.9f * k, 3.6f * k, wh)
        g.fillCircle(x + 0.6f * k, y - 3.8f * k, 5.4f * k, wh)
        g.fillCircle(x + 6.6f * k, y - 1.3f * k, 3.8f * k, wh)
        g.fillCircle(x - 1.4f * k, y - 5.2f * k, 2.2f * k, Colors.withAlpha(0xFFFFFFFF.toInt(), alpha))
    }

    private fun haze(g: Gfx, th: Theme, v: View, heightUnits: Float, strength: Float) {
        val s = v.s
        val top = v.groundY - heightUnits * s
        val key = "haze-${th.skyHorizon}-$top-${v.groundY}-$strength"
        val gr = grads.getOrPut(key) {
            Gradient.linear(0f, top, 0f, v.groundY, intArrayOf(Colors.withAlpha(th.skyHorizon, 0f), Colors.withAlpha(th.skyHorizon, strength)), null)
        }
        g.fillRect(v.left, top, v.right, v.groundY, 0, gr)
    }

    private fun rays(g: Gfx, v: View, dist: Float) {
        val sway = sin(dist * 0.004f) * 0.03f
        for (i in 0 until 5) {
            val x0 = v.left + v.width * (0.08f + i * 0.2f + sway)
            val w = v.width * (0.03f + rnd(i, 9) * 0.04f)
            val key = "ray-$i-${v.top}-${v.groundY}"
            val gr = grads.getOrPut(key) {
                Gradient.linear(0f, v.top, 0f, v.groundY, intArrayOf(0x30FFFFE8, 0x14FFFFE8, 0x00FFFFE8), floatArrayOf(0f, 0.6f, 1f))
            }
            p.reset().moveTo(x0, v.top).lineTo(x0 + w, v.top).lineTo(x0 + w * 4.2f, v.groundY).lineTo(x0 + w * 1.6f, v.groundY).close()
            g.fillPath(p, 0, gr)
        }
    }

    private fun motes(g: Gfx, th: Theme, v: View) {
        val s = v.s
        val t = v.time
        for (i in 0 until 18) {
            val k = (t * (0.03f + rnd(i, 1) * 0.04f) + rnd(i, 2)) % 1f
            val x = v.left + v.width * ((rnd(i, 3) + k * 0.5f) % 1f)
            val y = v.groundY - (8f + rnd(i, 4) * 50f) * s + sin(t * 1.3f + i) * 3f * s
            val a = 0.35f + 0.35f * sin(t * 2f + i * 1.7f)
            g.fillCircle(x, y, (0.25f + rnd(i, 5) * 0.35f) * s, Colors.withAlpha(if (th.rays) 0xFFFFF8C4.toInt() else 0xFFFFFFFF.toInt(), max(0f, a)))
        }
    }

    private fun vignette(g: Gfx, v: View) {
        val key = "vig-${v.left}-${v.top}-${v.width}-${v.height}"
        val r = max(v.width, v.height) * 0.75f
        val gr = grads.getOrPut(key) {
            Gradient.radial(v.left + v.width / 2f, v.top + v.height / 2f, r, intArrayOf(0x00000000, 0x00000000, 0x3A000000), floatArrayOf(0f, 0.62f, 1f))
        }
        g.fillRect(v.left, v.top, v.right, v.bottom, 0, gr)
    }

    // ------------------------------------------------------------------ painting helpers (tile pixel space)

    /** A lumpy foliage clump lit from the upper left: shadow, base, mid-tone and highlight lobes. */
    private fun foliage(g: Gfx, cx: Float, cy: Float, r: Float, base: Int, seed: Int, light: Float = 1f) {
        val dark = Colors.shade(base, 0.68f)
        val mid = Colors.lighten(base, 0.12f * light)
        val hi = Colors.lighten(base, 0.32f * light)
        blobAt(g, cx + r * 0.08f, cy + r * 0.12f, r, dark, seed)
        blobAt(g, cx, cy, r * 0.93f, base, seed + 1)
        blobAt(g, cx - r * 0.18f, cy - r * 0.2f, r * 0.62f, mid, seed + 2)
        blobAt(g, cx - r * 0.3f, cy - r * 0.36f, r * 0.3f, hi, seed + 3)
        // leaf tips on the rim for texture
        for (k in 0 until 9) {
            val a = (k / 9f) * 2f * PI.toFloat() + rnd(seed, k) * 0.5f
            val lx = cx + cos(a) * r * 0.92f
            val ly = cy + sin(a) * r * 0.8f
            leaf(g, lx, ly, r * 0.22f, a * 57.3f, if (sin(a) < -0.2f) mid else base)
        }
    }

    private fun blobAt(g: Gfx, cx: Float, cy: Float, r: Float, color: Int, seed: Int) {
        val n = 9
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        for (i in 0 until n) {
            val a = i * 2f * PI.toFloat() / n
            val rr = r * (0.82f + rnd(seed, i) * 0.3f)
            xs[i] = cx + cos(a) * rr
            ys[i] = cy + sin(a) * rr * 0.86f
        }
        q.reset().blob(xs, ys)
        g.fillPath(q, color)
    }

    /** A pointed leaf of length [len] at an angle (degrees). */
    private fun leaf(g: Gfx, x: Float, y: Float, len: Float, angle: Float, color: Int) {
        g.save()
        g.translate(x, y)
        g.rotate(angle)
        p.reset().moveTo(0f, 0f).quadTo(len * 0.5f, -len * 0.32f, len, 0f).quadTo(len * 0.5f, len * 0.32f, 0f, 0f).close()
        g.fillPath(p, color)
        g.restore()
    }

    private fun fern(g: Gfx, x: Float, y: Float, len: Float, angle: Float, color: Int) {
        g.save()
        g.translate(x, y)
        g.rotate(angle)
        p.reset().moveTo(0f, 0f).quadTo(len * 0.5f, -len * 0.18f, len, 0f)
        g.strokePath(p, len * 0.025f, Colors.shade(color, 0.8f))
        val n = 9
        for (i in 1 until n) {
            val t = i / n.toFloat()
            val px = len * t
            val py = -len * 0.18f * 4f * t * (1f - t) * 0.5f
            val ll = len * 0.22f * (1f - t * 0.7f)
            leaf(g, px, py, ll, -60f, color)
            leaf(g, px, py, ll, 60f, Colors.shade(color, 0.9f))
        }
        g.restore()
    }

    private fun trunk(g: Gfx, x: Float, base: Float, top: Float, w: Float, bark: Int, buttress: Boolean, seed: Int) {
        val gr = grads.getOrPut("bark-$bark-${w.toInt()}") {
            Gradient.linear(-w, 0f, w, 0f, intArrayOf(Colors.lighten(bark, 0.18f), bark, Colors.shade(bark, 0.6f)), floatArrayOf(0f, 0.45f, 1f))
        }
        g.save()
        g.translate(x, 0f)
        p.reset().moveTo(-w * 0.5f, base).cubicTo(-w * 0.45f, base - (base - top) * 0.5f, -w * 0.35f, top + (base - top) * 0.2f, -w * 0.3f, top)
            .lineTo(w * 0.3f, top).cubicTo(w * 0.35f, top + (base - top) * 0.2f, w * 0.45f, base - (base - top) * 0.5f, w * 0.5f, base).close()
        g.fillPath(p, 0, gr)
        // bark streaks
        for (k in 0 until 4) {
            val bx = (rnd(seed, k) - 0.5f) * w * 0.6f
            g.strokeLine(bx, base - (base - top) * rnd(seed, k + 10) * 0.4f, bx * 0.8f, top + (base - top) * rnd(seed, k + 20) * 0.5f, w * 0.05f, Colors.shade(bark, 0.7f))
        }
        if (buttress) {
            for (sgn in floatArrayOf(-1f, 1f)) {
                p.reset().moveTo(sgn * w * 0.3f, base - w * 1.6f).quadTo(sgn * w * 0.6f, base - w * 0.5f, sgn * w * 1.3f, base)
                    .lineTo(sgn * w * 0.4f, base).close()
                g.fillPath(p, Colors.shade(bark, if (sgn < 0) 1f else 0.7f))
            }
        }
        g.restore()
    }

    private fun vine(g: Gfx, x: Float, y0: Float, len: Float, color: Int, leafColor: Int, seed: Int) {
        val sway = (rnd(seed, 1) - 0.5f) * len * 0.15f
        p.reset().moveTo(x, y0).quadTo(x + sway, y0 + len * 0.5f, x + sway * 0.3f, y0 + len)
        g.strokePath(p, max(1f, len * 0.012f), color)
        val n = (len / (len * 0.12f + 1f)).toInt().coerceIn(3, 10)
        for (i in 1..n) {
            val t = i / n.toFloat()
            val vx = x + sway * (2f * t * (1f - t)) + sway * 0.3f * t * t
            leaf(g, vx, y0 + len * t, len * 0.07f, if (i % 2 == 0) 30f else 150f, leafColor)
        }
    }

    // ------------------------------------------------------------------ layers

    private fun mountains(g: Gfx, th: Theme, biome: Biome, ps: Float, tw: Float, base: Float) {
        val wpx = tw * ps
        val farC = Colors.mix(th.hillFar, th.skyHorizon, 0.62f)
        val nearC = Colors.mix(th.hillFar, th.skyHorizon, 0.4f)
        ridge(g, wpx, base, ps, 40f, 13f, 2, farC, 0)
        if (biome == Biome.JUNGLE || biome == Biome.RIVERBANK) {
            // A distant waterfall on a cliff
            val wx = wpx * 0.62f
            g.fillRect(wx - 3.5f * ps, base - 44f * ps, wx + 3.5f * ps, base - 20f * ps, Colors.mix(farC, 0xFF000000.toInt(), 0.12f))
            g.fillRect(wx - 1.6f * ps, base - 44f * ps, wx + 1.6f * ps, base - 18f * ps, Colors.withAlpha(0xFFFFFFFF.toInt(), 0.7f))
            g.fillOval(wx - 5f * ps, base - 20f * ps, wx + 5f * ps, base - 15f * ps, Colors.withAlpha(0xFFFFFFFF.toInt(), 0.5f))
        }
        ridge(g, wpx, base, ps, 27f, 9f, 3, nearC, 7)
    }

    private fun ridge(g: Gfx, wpx: Float, base: Float, ps: Float, h: Float, amp: Float, waves: Int, color: Int, seed: Int) {
        p.reset().moveTo(0f, base + 2f * ps)
        val steps = 80
        for (i in 0..steps) {
            val x = wpx * i / steps
            val t = i / steps.toFloat() * 2f * PI.toFloat()
            // periodic in t so the tile is seamless
            val y = h + sin(t * waves + seed) * amp + sin(t * waves * 3 + seed * 2) * amp * 0.25f + sin(t * 19f + seed) * amp * 0.06f
            p.lineTo(x, base - y * ps)
        }
        p.lineTo(wpx, base + 2f * ps).close()
        g.fillPath(p, color)
    }

    private fun farForest(g: Gfx, th: Theme, biome: Biome, ps: Float, tw: Float, base: Float) {
        val wpx = tw * ps
        val c = Colors.mix(th.treeDark, th.skyHorizon, 0.58f)
        val c2 = Colors.mix(th.tree, th.skyHorizon, 0.55f)
        g.fillRect(0f, base - 6f * ps, wpx, base + 2f * ps, c)
        val n = (tw / 6f).toInt()
        for (i in 0 until n) {
            val x = (i + rnd(i, 1)) * wpx / n
            wrap(x, wpx) { xx ->
                when (biome) {
                    Biome.SAVANNA -> {
                        if (rnd(i, 2) > 0.6f) {
                            val h = (8f + rnd(i, 3) * 6f) * ps
                            g.fillRect(xx - 0.4f * ps, base - h, xx + 0.4f * ps, base, c)
                            g.fillOval(xx - 6f * ps, base - h - 2f * ps, xx + 6f * ps, base - h + 1f * ps, c)
                        }
                    }
                    Biome.PINE_FOREST -> {
                        val h = (16f + rnd(i, 3) * 14f) * ps
                        p.reset().moveTo(xx - 3.5f * ps, base).lineTo(xx, base - h).lineTo(xx + 3.5f * ps, base).close()
                        g.fillPath(p, if (i % 2 == 0) c else c2)
                    }
                    Biome.BAMBOO_FOREST -> {
                        val h = (22f + rnd(i, 3) * 18f) * ps
                        g.fillRect(xx - 0.5f * ps, base - h, xx + 0.5f * ps, base, c2)
                        leaf(g, xx, base - h + 2f * ps, 5f * ps, -30f - rnd(i, 4) * 40f, c)
                    }
                    else -> {
                        val r = (6f + rnd(i, 3) * 6f) * ps
                        val h = (14f + rnd(i, 4) * 12f) * ps
                        blobAt(g, xx, base - h, r, if (i % 2 == 0) c else c2, i)
                        blobAt(g, xx - r * 0.3f, base - h - r * 0.3f, r * 0.5f, Colors.lighten(c2, 0.12f), i + 5)
                        g.fillRect(xx - r * 0.12f, base - h, xx + r * 0.12f, base, c)
                    }
                }
            }
        }
    }

    private fun midForest(g: Gfx, th: Theme, biome: Biome, ps: Float, tw: Float, base: Float) {
        val wpx = tw * ps
        val tree = Colors.mix(th.tree, th.skyHorizon, 0.38f)
        val dark = Colors.mix(th.treeDark, th.skyHorizon, 0.36f)
        val bark = Colors.mix(th.trunk, th.skyHorizon, 0.42f)
        val n = (tw / 13f).toInt()
        for (i in 0 until n) {
            val x = (i + rnd(i, 1) * 0.6f) * wpx / n
            val k = 0.75f + rnd(i, 2) * 0.5f
            wrap(x, wpx) { xx ->
                when (biome) {
                    Biome.SAVANNA -> if (rnd(i, 5) > 0.45f) acacia(g, xx, base, ps * k, tree, bark, i) else grassTuft(g, xx, base, ps * 1.5f, Colors.mix(th.grass, th.skyHorizon, 0.2f), i)
                    Biome.PINE_FOREST -> conifer(g, xx, base, ps * k, tree, dark, bark, i)
                    Biome.BAMBOO_FOREST -> bamboo(g, xx, base, ps * k, tree, dark, i)
                    else -> {
                        val h = (26f + rnd(i, 3) * 14f) * ps * k
                        trunk(g, xx, base, base - h, 2.2f * ps * k, bark, false, i)
                        foliage(g, xx, base - h, 9f * ps * k, if (i % 2 == 0) tree else dark, i * 7, 0.7f)
                        foliage(g, xx - 6f * ps * k, base - h + 5f * ps * k, 6f * ps * k, dark, i * 7 + 3, 0.6f)
                        foliage(g, xx + 6.5f * ps * k, base - h + 4f * ps * k, 6.5f * ps * k, tree, i * 7 + 5, 0.6f)
                    }
                }
            }
        }
    }

    private fun nearTrees(g: Gfx, th: Theme, biome: Biome, ps: Float, tw: Float, base: Float) {
        val wpx = tw * ps
        val n = max(3, (tw / 30f).toInt())
        for (i in 0 until n) {
            val x = (i + 0.2f + rnd(i, 1) * 0.6f) * wpx / n
            val k = 0.85f + rnd(i, 2) * 0.35f
            wrap(x, wpx) { xx ->
                when (biome) {
                    Biome.JUNGLE -> rainforestTree(g, th, xx, base, ps * k, i)
                    Biome.RIVERBANK -> if (i % 2 == 0) palm(g, th, xx, base, ps * k, i) else rainforestTree(g, th, xx, base, ps * k * 0.85f, i)
                    Biome.SAVANNA -> acacia(g, xx, base, ps * k * 1.4f, th.tree, th.trunk, i)
                    Biome.BAMBOO_FOREST -> { bamboo(g, xx, base, ps * k * 1.3f, th.tree, th.treeDark, i); bamboo(g, xx + 6f * ps, base, ps * k * 1.15f, th.tree, th.treeDark, i + 3) }
                    Biome.PINE_FOREST -> conifer(g, xx, base, ps * k * 1.5f, th.tree, th.treeDark, th.trunk, i)
                    Biome.MEADOW -> oak(g, th, xx, base, ps * k, i)
                }
            }
        }
        if (th.canopy) {
            // Canopy & vines hanging from above
            val m = (tw / 9f).toInt()
            for (i in 0 until m) {
                val x = (i + rnd(i, 30)) * wpx / m
                wrap(x, wpx) { xx ->
                    foliage(g, xx, 4f * ps, (7f + rnd(i, 31) * 5f) * ps, if (i % 2 == 0) Colors.shade(th.treeDark, 0.8f) else Colors.mix(th.tree, th.treeDark, 0.5f), i * 13, 0.8f)
                    if (rnd(i, 32) > 0.45f) vine(g, xx + 2f * ps, 6f * ps, (20f + rnd(i, 33) * 45f) * ps, Colors.shade(th.treeDark, 0.8f), th.treeDark, i)
                }
            }
        }
    }

    private fun rainforestTree(g: Gfx, th: Theme, x: Float, base: Float, ps: Float, seed: Int) {
        val h = (58f + rnd(seed, 3) * 18f) * ps
        trunk(g, x, base, base - h, 4.2f * ps, th.trunk, true, seed)
        // branches & canopy
        g.strokeLine(x, base - h * 0.75f, x - 12f * ps, base - h * 0.88f, 1.4f * ps, th.trunk)
        g.strokeLine(x, base - h * 0.8f, x + 13f * ps, base - h * 0.92f, 1.4f * ps, th.trunk)
        val deep = Colors.shade(th.treeDark, 0.72f)
        val rich = Colors.shade(Colors.mix(th.tree, th.treeDark, 0.6f), 0.88f)
        foliage(g, x - 13f * ps, base - h * 0.9f, 10f * ps, deep, seed * 3, 1f)
        foliage(g, x + 14f * ps, base - h * 0.93f, 11f * ps, rich, seed * 3 + 1, 1f)
        foliage(g, x, base - h, 14f * ps, rich, seed * 3 + 2, 1.15f)
        vine(g, x - 9f * ps, base - h * 0.85f, h * 0.5f, th.treeDark, th.tree, seed)
        vine(g, x + 10f * ps, base - h * 0.9f, h * 0.35f, th.treeDark, th.tree, seed + 1)
        // epiphyte / flowers
        g.fillCircle(x + 1.5f * ps, base - h * 0.45f, 1f * ps, th.flower1)
        g.fillCircle(x + 2.6f * ps, base - h * 0.47f, 0.8f * ps, th.flower2)
    }

    private fun palm(g: Gfx, th: Theme, x: Float, base: Float, ps: Float, seed: Int) {
        val h = (40f + rnd(seed, 3) * 10f) * ps
        val lean = 6f * ps
        p.reset().moveTo(x - 1.4f * ps, base).quadTo(x - 1.2f * ps, base - h * 0.5f, x + lean - 0.8f * ps, base - h)
            .lineTo(x + lean + 0.8f * ps, base - h).quadTo(x + 1.2f * ps, base - h * 0.5f, x + 1.4f * ps, base).close()
        g.fillPath(p, th.trunk)
        for (k in 1 until 10) {
            val t = k / 10f
            val yy = base - h * t
            val xx = x + lean * t * t
            g.strokeLine(xx - 1.2f * ps, yy, xx + 1.2f * ps, yy - 0.4f * ps, 0.3f * ps, Colors.shade(th.trunk, 0.7f))
        }
        val tx = x + lean
        val ty = base - h
        for (k in 0 until 8) {
            val a = -170f + k * 24f + rnd(seed, k) * 10f
            g.save(); g.translate(tx, ty); g.rotate(a)
            p.reset().moveTo(0f, 0f).quadTo(8f * ps, -4f * ps, 16f * ps, 2f * ps).quadTo(8f * ps, 1f * ps, 0f, 0f).close()
            g.fillPath(p, if (k % 2 == 0) th.tree else th.treeDark)
            g.restore()
        }
        for (k in 0 until 3) g.fillCircle(tx - 1f * ps + k * 1.1f * ps, ty + 1.2f * ps, 1f * ps, 0xFF6D4C2A.toInt())
    }

    private fun acacia(g: Gfx, x: Float, base: Float, ps: Float, leaves: Int, bark: Int, seed: Int) {
        val h = 18f * ps
        g.strokeLine(x, base, x - 0.5f * ps, base - h * 0.6f, 1.6f * ps, bark)
        g.strokeLine(x - 0.5f * ps, base - h * 0.6f, x - 6f * ps, base - h, 1f * ps, bark)
        g.strokeLine(x - 0.5f * ps, base - h * 0.6f, x + 5f * ps, base - h * 1.02f, 1f * ps, bark)
        val dark = Colors.shade(leaves, 0.75f)
        for (k in 0 until 7) {
            val cx = x + (-11f + k * 3.6f) * ps
            g.fillOval(cx - 4f * ps, base - h - 2.4f * ps, cx + 4f * ps, base - h + 1.6f * ps, dark)
            g.fillOval(cx - 3.6f * ps, base - h - 3.2f * ps, cx + 3.6f * ps, base - h + 0.4f * ps, leaves)
            g.fillOval(cx - 2f * ps, base - h - 3.3f * ps, cx + 1.2f * ps, base - h - 1.6f * ps, Colors.lighten(leaves, 0.25f))
        }
    }

    private fun conifer(g: Gfx, x: Float, base: Float, ps: Float, c1: Int, c2: Int, bark: Int, seed: Int) {
        g.fillRect(x - 0.8f * ps, base - 6f * ps, x + 0.8f * ps, base, bark)
        val h = (26f + rnd(seed, 3) * 10f) * ps
        for (j in 0 until 5) {
            val by = base - 4f * ps - j * h / 6f
            val w = (8f - j * 1.4f) * ps
            p.reset().moveTo(x - w, by).quadTo(x - w * 0.3f, by - 2f * ps, x, by - h / 3.2f).quadTo(x + w * 0.3f, by - 2f * ps, x + w, by)
                .quadTo(x, by + 1.2f * ps, x - w, by).close()
            g.fillPath(p, c2)
            p.reset().moveTo(x - w * 0.8f, by - 0.6f * ps).quadTo(x - w * 0.3f, by - 2.2f * ps, x, by - h / 3.2f).lineTo(x, by - 0.4f * ps).close()
            g.fillPath(p, c1)
        }
    }

    private fun bamboo(g: Gfx, x: Float, base: Float, ps: Float, c1: Int, c2: Int, seed: Int) {
        for (j in 0 until 3) {
            val bx = x + (j - 1) * 2.4f * ps
            val h = (55f + rnd(seed, j) * 30f) * ps
            val w = 1.4f * ps
            val col = if (j == 1) c1 else Colors.mix(c1, c2, 0.5f)
            g.fillRect(bx - w / 2f, base - h, bx + w / 2f, base, col)
            g.fillRect(bx - w / 2f, base - h, bx - w / 6f, base, Colors.lighten(col, 0.25f))
            var yy = 6f * ps
            while (yy < h) {
                g.fillRoundRect(bx - w * 0.6f, base - yy - 0.3f * ps, bx + w * 0.6f, base - yy + 0.3f * ps, 0.3f * ps, Colors.shade(col, 0.75f))
                yy += (6f + rnd(seed, j + 7) * 2f) * ps
            }
            for (k in 0 until 4) {
                val ly = base - h * (0.55f + k * 0.12f)
                leaf(g, bx, ly, 7f * ps, if (k % 2 == 0) -25f else -155f, c2)
                leaf(g, bx, ly - 1f * ps, 6f * ps, if (k % 2 == 0) -50f else -130f, c1)
            }
        }
    }

    private fun oak(g: Gfx, th: Theme, x: Float, base: Float, ps: Float, seed: Int) {
        val h = 24f * ps
        trunk(g, x, base, base - h, 3f * ps, th.trunk, false, seed)
        foliage(g, x - 7f * ps, base - h, 8f * ps, th.treeDark, seed * 5, 1f)
        foliage(g, x + 7f * ps, base - h - 1f * ps, 8.5f * ps, th.tree, seed * 5 + 1, 1f)
        foliage(g, x, base - h - 7f * ps, 10f * ps, th.tree, seed * 5 + 2, 1.1f)
        if (seed % 2 == 0) for (k in 0 until 6) g.fillCircle(x + (-8f + k * 3f) * ps, base - h - (k % 3) * 3f * ps, 0.9f * ps, 0xFFE53935.toInt())
    }

    private fun grassTuft(g: Gfx, x: Float, base: Float, ps: Float, c: Int, seed: Int) {
        for (k in 0 until 9) {
            val bx = x + (k - 4) * 0.6f * ps
            val h = (3f + rnd(seed, k) * 4f) * ps
            val lean = (rnd(seed, k + 9) - 0.5f) * 2f * ps
            p.reset().moveTo(bx - 0.35f * ps, base).quadTo(bx + lean * 0.3f, base - h * 0.6f, bx + lean, base - h).quadTo(bx + lean * 0.2f, base - h * 0.5f, bx + 0.35f * ps, base).close()
            g.fillPath(p, if (k % 2 == 0) c else Colors.lighten(c, 0.15f))
        }
    }

    private fun undergrowth(g: Gfx, th: Theme, biome: Biome, ps: Float, tw: Float, base: Float) {
        val wpx = tw * ps
        val n = (tw / 7f).toInt()
        for (i in 0 until n) {
            val x = (i + rnd(i, 1)) * wpx / n
            wrap(x, wpx) { xx ->
                when {
                    biome == Biome.SAVANNA -> grassTuft(g, xx, base, ps * 1.3f, if (i % 2 == 0) th.grass else th.bush, i)
                    rnd(i, 2) < 0.4f -> {
                        fern(g, xx, base, 9f * ps, -150f + rnd(i, 3) * 20f, th.bush)
                        fern(g, xx, base, 10f * ps, -30f - rnd(i, 4) * 20f, Colors.lighten(th.bush, 0.1f))
                        fern(g, xx, base, 8f * ps, -90f + (rnd(i, 5) - 0.5f) * 30f, Colors.shade(th.bush, 0.9f))
                    }
                    rnd(i, 2) < 0.75f -> {
                        foliage(g, xx, base - 2.5f * ps, 3.6f * ps, th.bush, i * 17, 1f)
                        if (rnd(i, 6) > 0.5f) {
                            g.fillCircle(xx - 1f * ps, base - 4f * ps, 0.7f * ps, th.flower1)
                            g.fillCircle(xx + 1.5f * ps, base - 3f * ps, 0.6f * ps, th.flower2)
                        }
                    }
                    else -> {
                        val rc = Colors.mix(0xFF9E9E9E.toInt(), th.dirt, 0.3f)
                        g.fillOval(xx - 3f * ps, base - 2.5f * ps, xx + 3f * ps, base + 0.5f * ps, rc)
                        g.fillOval(xx - 2f * ps, base - 2.4f * ps, xx + 0.5f * ps, base - 1.2f * ps, Colors.lighten(rc, 0.3f))
                        g.fillOval(xx - 2.6f * ps, base - 2.8f * ps, xx - 0.4f * ps, base - 2f * ps, Colors.withAlpha(th.grass, 0.9f))
                    }
                }
            }
        }
    }

    private fun foreground(g: Gfx, th: Theme, biome: Biome, ps: Float, tw: Float, base: Float) {
        val wpx = tw * ps
        val c = Colors.withAlpha(Colors.shade(th.treeDark, 0.42f), 0.96f)
        val c2 = Colors.withAlpha(Colors.shade(th.treeDark, 0.55f), 0.96f)
        val n = 3
        for (i in 0 until n) {
            if (rnd(i, 1) < 0.3f) continue
            val x = (i + rnd(i, 2)) * wpx / n
            val y = base + 32f * ps
            wrap(x, wpx) { xx ->
                if (biome == Biome.SAVANNA || biome == Biome.MEADOW) {
                    grassTuft(g, xx, y, ps * 5f, c2, i)
                } else {
                    for (k in 0 until 5) {
                        val a = -150f + k * 28f + (rnd(i, k + 3) - 0.5f) * 12f
                        bigLeaf(g, xx, y, (16f + rnd(i, k + 9) * 8f) * ps, a, if (k % 2 == 0) c else c2)
                    }
                }
            }
        }
    }

    /** Broad tropical leaf with a midrib — used close to the camera. */
    private fun bigLeaf(g: Gfx, x: Float, y: Float, len: Float, angle: Float, color: Int) {
        g.save()
        g.translate(x, y)
        g.rotate(angle)
        p.reset().moveTo(0f, 0f).cubicTo(len * 0.25f, -len * 0.42f, len * 0.75f, -len * 0.36f, len, 0f)
            .cubicTo(len * 0.75f, len * 0.3f, len * 0.25f, len * 0.36f, 0f, 0f).close()
        g.fillPath(p, color)
        g.strokeLine(0f, 0f, len * 0.95f, 0f, len * 0.025f, Colors.lighten(color, 0.18f))
        g.restore()
    }

    // ------------------------------------------------------------------ ground

    fun groundTile(g: Gfx, th: Theme, biome: Biome, v: View) {
        tiled(g, v, "${biome.name}-ground", 1f, 2.5f, (v.bottom - v.groundY) / v.s, 0.75f, v.camX) { gg, ps, tw, base ->
            val wpx = tw * ps
            val h = base + (v.bottom - v.groundY) * 0.75f
            val dirt = Gradient.linear(0f, base, 0f, h, intArrayOf(th.dirt, Colors.mix(th.dirt, th.dirtDark, 0.6f), th.dirtDark), floatArrayOf(0f, 0.35f, 1f))
            gg.fillRect(0f, base, wpx, h, 0, dirt)
            // strata & stones
            val n = (tw / 3f).toInt()
            for (i in 0 until n) {
                val x = (i + rnd(i, 1)) * wpx / n
                val y = base + (4f + rnd(i, 2) * 18f) * ps
                if (y > h) continue
                wrap(x, wpx) { xx ->
                    when {
                        rnd(i, 3) < 0.25f -> {
                            val r = (0.6f + rnd(i, 4) * 1.2f) * ps
                            val sc = Colors.mix(0xFFB0A090.toInt(), th.dirt, 0.4f)
                            gg.fillOval(xx - r * 1.3f, y - r, xx + r * 1.3f, y + r, Colors.shade(sc, 0.75f))
                            gg.fillOval(xx - r * 1.1f, y - r * 0.95f, xx + r * 0.9f, y + r * 0.5f, sc)
                            gg.fillOval(xx - r * 0.7f, y - r * 0.8f, xx, y - r * 0.2f, Colors.lighten(sc, 0.3f))
                        }
                        rnd(i, 3) < 0.4f -> {
                            p.reset().moveTo(xx - 3f * ps, y).quadTo(xx, y - 1.2f * ps, xx + 4f * ps, y + 0.6f * ps)
                            gg.strokePath(p, 0.45f * ps, Colors.shade(th.dirt, 0.65f))
                        }
                        else -> gg.fillCircle(xx, y, 0.3f * ps, Colors.shade(th.dirt, 0.72f))
                    }
                }
            }
            // fallen leaves & twigs on the path
            val litter = (tw / 2.5f).toInt()
            val leafCols = intArrayOf(0xFFB5651D.toInt(), 0xFFD4A017.toInt(), 0xFF8B4513.toInt(), Colors.shade(th.grass, 0.7f))
            for (i in 0 until litter) {
                val x = (i + rnd(i, 21)) * wpx / litter
                val y = base + (2.2f + rnd(i, 22) * 7f) * ps
                wrap(x, wpx) { xx ->
                    if (rnd(i, 23) < 0.8f) leaf(gg, xx, y, (1.2f + rnd(i, 24) * 1.2f) * ps, rnd(i, 25) * 360f, leafCols[i % leafCols.size])
                    else gg.strokeLine(xx, y, xx + 3f * ps, y + 0.4f * ps, 0.3f * ps, Colors.shade(th.trunk, 0.8f))
                }
            }
            // top soil shadow under the grass
            gg.fillRect(0f, base, wpx, base + 3.5f * ps, 0, Gradient.linear(0f, base, 0f, base + 3.5f * ps, intArrayOf(0x55000000, 0x00000000), null))
            // grass lip with blades
            gg.fillRect(0f, base - 0.2f * ps, wpx, base + 1.6f * ps, th.grass)
            val blades = (tw * 3.2f).toInt()
            for (i in 0 until blades) {
                val x = (i + rnd(i, 7)) * wpx / blades
                val bh = (1.2f + rnd(i, 8) * 2.4f) * ps
                val lean = (rnd(i, 9) - 0.4f) * 1.6f * ps
                val col = when (i % 3) { 0 -> th.grass; 1 -> th.grassLight; else -> Colors.shade(th.grass, 0.85f) }
                wrap(x, wpx) { xx ->
                    p.reset().moveTo(xx - 0.35f * ps, base + 1.4f * ps).quadTo(xx + lean * 0.3f, base - bh * 0.4f, xx + lean, base - bh)
                        .quadTo(xx + lean * 0.2f, base - bh * 0.3f, xx + 0.35f * ps, base + 1.4f * ps).close()
                    gg.fillPath(p, col)
                }
            }
            // hanging grass/roots over the edge
            for (i in 0 until (tw / 2f).toInt()) {
                val x = (i + rnd(i, 11)) * wpx / (tw / 2f).toInt()
                wrap(x, wpx) { xx ->
                    gg.fillOval(xx - 1.1f * ps, base + 0.6f * ps, xx + 1.1f * ps, base + (2.2f + rnd(i, 12) * 1.4f) * ps, th.grass)
                }
            }
        }
    }
}
