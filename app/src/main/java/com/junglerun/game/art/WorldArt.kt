package com.junglerun.game.art

import com.junglerun.game.core.Biome
import com.junglerun.game.core.Food
import com.junglerun.game.gfx.Colors
import com.junglerun.game.gfx.GPath
import com.junglerun.game.gfx.Gfx
import com.junglerun.game.gfx.Gradient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Screen-space layout of one viewport, shared by all art routines. */
class View {
    var left = 0f; var top = 0f; var right = 0f; var bottom = 0f
    /** Pixels per world unit. */
    var s = 1f
    /** Screen y of the ground line (world y = 0). */
    var groundY = 0f
    /** Screen x of the runner. */
    var playerX = 0f
    /** World x the camera follows (runner distance). */
    var camX = 0f
    var time = 0f
    val width get() = right - left
    val height get() = bottom - top

    fun sx(wx: Float) = playerX + (wx - camX) * s
    fun sy(wy: Float) = groundY - wy * s
}

/**
 * Backgrounds, terrain, obstacles and collectibles in a soft, modern "storybook" style:
 * gradients, rounded shapes, gentle outlines and atmospheric depth.
 */
class WorldArt {
    private val p = GPath()
    private val cache = HashMap<String, Gradient>()
    private val ink = 0xFF2A1A12.toInt()

    private fun grad(key: String, make: () -> Gradient): Gradient = cache.getOrPut(key, make)

    private fun hash(i: Int): Int {
        var x = i * 374761393 + 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return (x xor (x ushr 16)) and 0x7FFFFFFF
    }

    // ================================================================== background

    fun background(g: Gfx, th: Theme, biome: Biome, v: View, dist: Float) {
        val s = v.s
        val sky = grad("sky-${biome.name}-${v.top}-${v.groundY}") {
            Gradient.linear(0f, v.top, 0f, v.groundY, intArrayOf(th.skyTop, th.skyMid, th.skyHorizon), floatArrayOf(0f, 0.6f, 1f))
        }
        g.fillRect(v.left, v.top, v.right, v.groundY + 2f, 0, sky)

        // Sun with glow
        val sunX = v.right - v.width * 0.2f
        val sunY = max(v.top + 14f * s, v.groundY - 64f * s)
        val glow = grad("glow-${biome.name}-$sunX-$sunY-$s") {
            Gradient.radial(sunX, sunY, 26f * s, intArrayOf(Colors.withAlpha(th.sun, 0.75f), Colors.withAlpha(th.sun, 0.25f), Colors.withAlpha(th.sun, 0f)), floatArrayOf(0f, 0.35f, 1f))
        }
        g.fillCircle(sunX, sunY, 26f * s, 0, glow)
        g.fillCircle(sunX, sunY, 6.5f * s, th.sun)

        if (th.rays) rays(g, v, th)
        clouds(g, v, dist)
        hills(g, v, dist * 0.08f, 30f, 8f, 0.011f, th.hillFarHazed, 11)
        hills(g, v, dist * 0.18f, 17f, 5f, 0.02f, th.hillNearHazed, 23)
        trees(g, th, biome, v, dist * 0.42f)
        bushes(g, th, v, dist * 0.72f)
        if (th.canopy) canopy(g, th, v, dist * 0.55f)
    }

    private fun rays(g: Gfx, v: View, th: Theme) {
        val c = Colors.withAlpha(0xFFFFFFFF.toInt(), 0.10f)
        for (i in 0 until 4) {
            val x0 = v.left + v.width * (0.15f + i * 0.22f)
            val w = v.width * 0.05f
            p.reset().moveTo(x0, v.top).lineTo(x0 + w, v.top).lineTo(x0 + w * 4.5f, v.groundY).lineTo(x0 + w * 2.0f, v.groundY).close()
            g.fillPath(p, c)
        }
    }

    private fun clouds(g: Gfx, v: View, dist: Float) {
        val s = v.s
        val span = 70f * s
        val off = (dist * 0.04f * s) % span
        val rows = max(1, ((v.groundY - v.top) / (40f * s)).toInt())
        for (row in 0 until min(rows, 4)) {
            var cx = v.left - off - row * span * 0.37f
            var i = row * 7
            while (cx < v.right + span) {
                val h = hash(i)
                if (h % 3 != 0) {
                    val cy = v.groundY - (48f + row * 34f + (h % 14)) * s
                    if (cy > v.top - 10f * s) cloud(g, cx, cy, (0.8f + (h % 5) * 0.12f) * s)
                }
                cx += span
                i++
            }
        }
    }

    private fun cloud(g: Gfx, x: Float, y: Float, k: Float) {
        val shade = 0xFFDCEBF7.toInt()
        g.fillRoundRect(x - 7f * k, y - 1f * k, x + 9f * k, y + 3f * k, 2f * k, shade)
        g.fillCircle(x - 3.5f * k, y, 3.6f * k, shade)
        g.fillCircle(x + 1.5f * k, y - 2.2f * k, 5f * k, shade)
        g.fillCircle(x + 6f * k, y, 3.4f * k, shade)
        g.fillCircle(x - 3.5f * k, y - 0.6f * k, 3.3f * k, 0xFFFFFFFF.toInt())
        g.fillCircle(x + 1.5f * k, y - 2.8f * k, 4.6f * k, 0xFFFFFFFF.toInt())
        g.fillCircle(x + 6f * k, y - 0.6f * k, 3.0f * k, 0xFFFFFFFF.toInt())
    }

    private fun hills(g: Gfx, v: View, offset: Float, base: Float, amp: Float, freq: Float, color: Int, seed: Int) {
        val s = v.s
        p.reset().moveTo(v.left, v.groundY + 1f)
        var x = v.left
        val step = max(6f, v.width / 90f)
        while (x <= v.right + step) {
            val wx = (x - v.left) / s + offset + seed * 37f
            val h = base + sin(wx * freq * 2f * PI.toFloat()) * amp + sin(wx * freq * 5.7f + seed) * amp * 0.45f
            p.lineTo(x, v.groundY - h * s)
            x += step
        }
        p.lineTo(v.right, v.groundY + 1f).close()
        g.fillPath(p, color)
    }

    private fun trees(g: Gfx, th: Theme, biome: Biome, v: View, offset: Float) {
        val s = v.s
        val spacing = 24f
        val first = floor(offset / spacing).toInt() - 2
        val count = (v.width / s / spacing).toInt() + 5
        for (k in first until first + count) {
            val h = hash(k + 1000)
            if (h % 5 == 0) continue
            val x = v.left + (k * spacing + (h % 11) - offset) * s
            val size = 0.85f + (h % 7) * 0.06f
            g.save()
            g.translate(x, v.groundY + 0.5f * s)
            g.scale(s * size, s * size)
            when (biome) {
                Biome.JUNGLE -> jungleTree(g, th, h)
                Biome.RIVERBANK -> if (h % 2 == 0) palm(g, th, h) else jungleTree(g, th, h)
                Biome.SAVANNA -> acacia(g, th)
                Biome.BAMBOO_FOREST -> bambooClump(g, th, h)
                Biome.PINE_FOREST -> pine(g, th)
                Biome.MEADOW -> roundTree(g, th, h)
            }
            g.restore()
        }
    }

    private fun foliage(th: Theme) = grad("fol-${th.tree}") {
        Gradient.radial(-2f, -4f, 10f, intArrayOf(Colors.lighten(th.tree, 0.25f), th.tree, th.treeDark), floatArrayOf(0f, 0.5f, 1f))
    }

    private fun jungleTree(g: Gfx, th: Theme, h: Int) {
        g.fillRoundRect(-1.1f, -22f, 1.1f, 0f, 0.8f, th.trunk)
        g.fillRect(0.2f, -22f, 1.1f, 0f, Colors.shade(th.trunk, 0.8f))
        g.save(); g.translate(0f, -24f)
        val f = foliage(th)
        g.fillCircle(-5.5f, 2.5f, 5.5f, 0, f)
        g.fillCircle(5.5f, 2f, 6f, 0, f)
        g.fillCircle(0f, -2f, 7.5f, 0, f)
        g.fillCircle(-2f, -3.5f, 2.5f, Colors.withAlpha(0xFFFFFFFF.toInt(), 0.12f))
        if (h % 3 == 0) {
            g.strokeLine(3f, 5f, 3f, 14f, 0.45f, th.treeDark)
            g.fillOval(2.3f, 13.2f, 3.9f, 14.8f, th.tree)
        }
        g.restore()
    }

    private fun palm(g: Gfx, th: Theme, h: Int) {
        p.reset().moveTo(-0.8f, 0f).quadTo(-1.2f, -12f, 1.8f, -22f).lineTo(3.0f, -21.6f).quadTo(0.6f, -12f, 0.9f, 0f).close()
        g.fillPath(p, th.trunk)
        g.save(); g.translate(2.4f, -22f)
        for (i in 0 until 6) {
            g.save(); g.rotate(-160f + i * 34f + (h % 10))
            g.fillOval(0f, -1.2f, 9f, 1.2f, if (i % 2 == 0) th.tree else th.treeDark)
            g.restore()
        }
        g.fillCircle(0f, 0.6f, 1f, 0xFF6D4C41.toInt())
        g.restore()
    }

    private fun acacia(g: Gfx, th: Theme) {
        p.reset().moveTo(-0.7f, 0f).lineTo(-0.5f, -12f).lineTo(-5f, -17f).lineTo(-4.4f, -17.6f).lineTo(0f, -14f)
            .lineTo(4.6f, -18f).lineTo(5.1f, -17.3f).lineTo(0.7f, -12f).lineTo(0.8f, 0f).close()
        g.fillPath(p, th.trunk)
        val f = foliage(th)
        g.fillOval(-11f, -21.5f, 11f, -16.5f, 0, f)
        g.fillOval(-6f, -23f, 7f, -19f, 0, f)
    }

    private fun bambooClump(g: Gfx, th: Theme, h: Int) {
        for (j in 0 until 4) {
            val bx = -3f + j * 2f
            val bh = 26f + ((h shr j) % 9)
            val c = if (j % 2 == 0) th.tree else th.trunk
            g.fillRoundRect(bx - 0.6f, -bh, bx + 0.6f, 0f, 0.5f, c)
            var yy = 5f
            while (yy < bh) {
                g.fillRect(bx - 0.65f, -yy - 0.25f, bx + 0.65f, -yy + 0.25f, Colors.shade(c, 0.8f))
                yy += 5f
            }
            g.save(); g.translate(bx, -bh + 2f); g.rotate(-35f + j * 20f)
            g.fillOval(0f, -0.5f, 4.5f, 0.5f, th.treeDark)
            g.restore()
        }
    }

    private fun pine(g: Gfx, th: Theme) {
        g.fillRect(-0.8f, -5f, 0.8f, 0f, th.trunk)
        for (j in 0 until 4) {
            val base = -4f - j * 5.5f
            val w = 7.5f - j * 1.6f
            p.reset().moveTo(-w, base).quadTo(-w * 0.3f, base - 3f, 0f, base - 8f).quadTo(w * 0.3f, base - 3f, w, base).close()
            g.fillPath(p, if (j % 2 == 0) th.tree else th.treeDark)
        }
        g.fillCircle(-1.2f, -20f, 0.8f, Colors.withAlpha(0xFFFFFFFF.toInt(), 0.5f))
    }

    private fun roundTree(g: Gfx, th: Theme, h: Int) {
        g.fillRoundRect(-0.8f, -12f, 0.8f, 0f, 0.6f, th.trunk)
        val f = foliage(th)
        g.fillCircle(0f, -15f, 6.5f, 0, f)
        g.fillCircle(-4f, -12f, 4.2f, 0, f)
        g.fillCircle(4f, -12.5f, 4.5f, 0, f)
        if (h % 2 == 0) for (i in 0 until 4) g.fillCircle(-3f + i * 2f, -14f + (i % 2) * 2f, 0.6f, 0xFFE53935.toInt())
    }

    private fun bushes(g: Gfx, th: Theme, v: View, offset: Float) {
        val s = v.s
        val spacing = 17f
        val first = floor(offset / spacing).toInt() - 2
        val count = (v.width / s / spacing).toInt() + 5
        val base = Colors.mix(th.bush, th.skyHorizon, 0.08f)
        for (k in first until first + count) {
            val h = hash(k + 5000)
            if (h % 3 == 0) continue
            val x = v.left + (k * spacing + (h % 9) - offset) * s
            val r = (2.4f + (h % 5) * 0.5f) * s
            g.fillCircle(x, v.groundY - r * 0.3f, r, base)
            g.fillCircle(x + r * 0.9f, v.groundY - r * 0.15f, r * 0.75f, Colors.shade(base, 0.9f))
            g.fillCircle(x - r * 0.3f, v.groundY - r * 0.7f, r * 0.35f, Colors.lighten(base, 0.15f))
            if (h % 4 == 1) {
                g.fillCircle(x - r * 0.3f, v.groundY - r * 0.8f, 0.55f * s, th.flower1)
                g.fillCircle(x + r * 0.5f, v.groundY - r * 0.55f, 0.5f * s, th.flower2)
            }
        }
    }

    private fun canopy(g: Gfx, th: Theme, v: View, offset: Float) {
        val s = v.s
        val top = v.top
        val spacing = 13f
        val first = floor(offset / spacing).toInt() - 2
        val count = (v.width / s / spacing).toInt() + 5
        for (k in first until first + count) {
            val h = hash(k + 9000)
            val x = v.left + (k * spacing - offset) * s
            val r = (5f + (h % 4)) * s
            g.fillCircle(x, top - r * 0.25f, r, th.treeDark)
            g.fillCircle(x + r * 0.6f, top - r * 0.45f, r * 0.8f, th.tree)
            if (h % 2 == 0) {
                val len = (8f + h % 14) * s
                p.reset().moveTo(x + r * 0.2f, top + r * 0.4f).quadTo(x + r * 0.6f, top + len * 0.5f, x + r * 0.1f, top + len)
                g.strokePath(p, 0.5f * s, th.treeDark)
                g.fillOval(x - 0.6f * s, top + len - 0.8f * s, x + 0.9f * s, top + len + 0.8f * s, th.tree)
            }
        }
    }

    // ================================================================== ground

    /** [pits] are (screenLeft, screenRight) pairs of gaps currently on screen. */
    fun ground(g: Gfx, th: Theme, biome: Biome, v: View, pits: FloatArray, pitCount: Int) {
        val s = v.s
        val dirt = grad("dirt-${biome.name}-${v.groundY}-${v.bottom}") {
            Gradient.linear(0f, v.groundY, 0f, v.bottom, intArrayOf(th.dirt, th.dirtDark), null)
        }
        g.fillRect(v.left, v.groundY, v.right, v.bottom, 0, dirt)

        // Pebbles & roots scrolling with the world
        val span = 7f
        val first = floor((v.camX - (v.playerX - v.left) / s) / span).toInt() - 1
        val last = first + (v.width / s / span).toInt() + 3
        for (k in first..last) {
            val h = hash(k)
            val px = v.sx(k * span + (h % 5))
            val py = v.groundY + (4.5f + (h % 13)) * s
            if (py > v.bottom) continue
            if (h % 4 == 0) {
                g.fillOval(px - 0.9f * s, py - 0.55f * s, px + 0.9f * s, py + 0.55f * s, Colors.shade(th.dirt, 0.82f))
                g.fillOval(px - 0.7f * s, py - 0.55f * s, px + 0.3f * s, py - 0.1f * s, Colors.lighten(th.dirt, 0.15f))
            } else if (h % 4 == 1) {
                g.fillCircle(px, py, 0.35f * s, Colors.shade(th.dirt, 0.75f))
            }
        }

        // Grass lip with a wavy edge
        val grass = grad("grass-${biome.name}-${v.groundY}-$s") {
            Gradient.linear(0f, v.groundY - 1.5f * s, 0f, v.groundY + 3f * s, intArrayOf(th.grassLight, th.grass), null)
        }
        p.reset().moveTo(v.left, v.groundY + 2.6f * s)
        var x = v.left
        val step = 1.6f * s
        var i = floor((v.camX * s) / step).toInt()
        val phase = (v.camX * s) % step
        x -= phase
        p.lineTo(x, v.groundY)
        while (x < v.right + step) {
            val bump = if (hash(i) % 3 == 0) 1.1f else 0.4f
            p.quadTo(x + step * 0.5f, v.groundY - bump * s, x + step, v.groundY)
            x += step
            i++
        }
        p.lineTo(v.right + step, v.groundY + 2.6f * s)
        val wave = 0.7f * s
        x = v.right + step
        while (x > v.left - step) {
            p.quadTo(x - step * 0.5f, v.groundY + 2.6f * s + wave, x - step, v.groundY + 2.6f * s)
            x -= step
        }
        p.close()
        g.fillPath(p, 0, grass)

        // Pits / rivers
        for (n in 0 until pitCount) {
            val l = pits[n * 2]
            val r = pits[n * 2 + 1]
            pit(g, th, biome, v, l, r)
        }
    }

    private fun pit(g: Gfx, th: Theme, biome: Biome, v: View, l: Float, r: Float) {
        val s = v.s
        val water = biome == Biome.JUNGLE || biome == Biome.RIVERBANK || biome == Biome.BAMBOO_FOREST || biome == Biome.PINE_FOREST
        val top = v.groundY - 0.3f * s
        if (water) {
            val wg = grad("water-${biome.name}-${v.groundY}-${v.bottom}") {
                Gradient.linear(0f, v.groundY, 0f, v.bottom, intArrayOf(th.waterLight, th.water, Colors.shade(th.water, 0.6f)), floatArrayOf(0f, 0.3f, 1f))
            }
            g.fillRect(l, top, r, v.bottom, Colors.shade(th.dirtDark, 0.8f))
            g.fillRect(l + 0.4f * s, v.groundY + 2.2f * s, r - 0.4f * s, v.bottom, 0, wg)
            // waves
            val t = v.time
            var wx = l + 1f * s
            var k = 0
            while (wx < r - 2f * s) {
                val wy = v.groundY + (3.4f + sin(t * 2.5f + k)) * s
                g.strokeLine(wx, wy, wx + 2f * s, wy, 0.35f * s, Colors.withAlpha(0xFFFFFFFF.toInt(), 0.65f))
                wx += 4.5f * s
                k++
            }
            g.fillOval(l, v.groundY + 1.6f * s, l + 2.2f * s, v.groundY + 2.8f * s, 0xCCFFFFFF.toInt())
            g.fillOval(r - 2.2f * s, v.groundY + 1.6f * s, r, v.groundY + 2.8f * s, 0xCCFFFFFF.toInt())
        } else {
            val hole = grad("hole-${biome.name}-${v.groundY}-${v.bottom}") {
                Gradient.linear(0f, v.groundY, 0f, v.groundY + 10f * v.s, intArrayOf(th.dirtDark, 0xFF1A0F0A.toInt()), null)
            }
            g.fillRect(l, top, r, v.bottom, 0, hole)
        }
        // grassy rounded lips
        g.fillCircle(l, v.groundY + 0.6f * s, 1.3f * s, th.grass)
        g.fillCircle(r, v.groundY + 0.6f * s, 1.3f * s, th.grass)
    }

    fun crocEyes(g: Gfx, v: View, cx: Float) {
        val s = v.s
        val y = v.groundY + 3.4f * s
        for (dx in floatArrayOf(-1.4f, 1.4f)) {
            g.fillCircle(cx + dx * s, y, 1.2f * s, 0xFF5E9B4A.toInt())
            g.fillOval(cx + dx * s - 0.6f * s, y - 0.9f * s, cx + dx * s + 0.6f * s, y - 0.1f * s, 0xFFFFF176.toInt())
            g.fillRect(cx + dx * s - 0.12f * s, y - 0.85f * s, cx + dx * s + 0.12f * s, y - 0.15f * s, ink)
        }
    }

    // ================================================================== obstacles (local unit space, origin at hazard bottom-left)

    fun log(g: Gfx, w: Float, h: Float, mossy: Boolean) {
        val bark = 0xFF8B5A3C.toInt()
        val lg = grad("log") { Gradient.linear(0f, -6.5f, 0f, 0f, intArrayOf(0xFFB07A52.toInt(), bark, 0xFF6A4128.toInt()), null) }
        g.fillRoundRect(-0.3f, -h - 0.3f, w + 0.3f, 0.3f, h * 0.5f, ink)
        g.fillRoundRect(0f, -h, w, 0f, h * 0.48f, 0, lg)
        g.strokeLine(1.2f, -h * 0.35f, w - 2.6f, -h * 0.35f, 0.25f, 0xFF6A4128.toInt())
        g.strokeLine(1.6f, -h * 0.7f, w - 3.0f, -h * 0.7f, 0.25f, 0xFF6A4128.toInt())
        // end grain
        g.fillOval(w - h * 0.62f, -h + 0.25f, w - 0.1f, -0.25f, 0xFFE8C59A.toInt())
        g.strokeOval(w - h * 0.5f, -h * 0.75f, w - h * 0.18f, -h * 0.25f, 0.22f, 0xFFB98A5E.toInt())
        if (mossy) {
            g.fillOval(0.6f, -h - 0.5f, w * 0.55f, -h + 0.9f, 0xFF7CB342.toInt())
            g.fillCircle(w * 0.3f, -h - 0.2f, 0.7f, 0xFF9CCC65.toInt())
        }
    }

    fun rock(g: Gfx, w: Float, h: Float, tint: Int = 0xFF9EA7B0.toInt()) {
        val rg = grad("rock-$tint") { Gradient.radial(1.6f, -5f, 8f, intArrayOf(Colors.lighten(tint, 0.35f), tint, Colors.shade(tint, 0.7f)), floatArrayOf(0f, 0.5f, 1f)) }
        p.reset().blob(
            floatArrayOf(0f, w * 0.15f, w * 0.45f, w * 0.85f, w, w * 0.5f),
            floatArrayOf(0f, -h * 0.75f, -h, -h * 0.8f, 0f, 0.2f),
        )
        g.strokePath(p, 0.6f, ink)
        g.fillPath(p, 0, rg)
        g.fillOval(w * 0.25f, -h * 0.82f, w * 0.5f, -h * 0.6f, 0x88FFFFFF.toInt())
    }

    fun bush(g: Gfx, w: Float, h: Float, th: Theme, thorny: Boolean) {
        val c = if (thorny) 0xFF9E9D24.toInt() else th.bush
        val bg = grad("bush-$c") { Gradient.radial(1f, -5f, 7f, intArrayOf(Colors.lighten(c, 0.3f), c, Colors.shade(c, 0.72f)), floatArrayOf(0f, 0.5f, 1f)) }
        for ((cx, cy, r) in listOf(Triple(w * 0.25f, -h * 0.4f, h * 0.45f), Triple(w * 0.75f, -h * 0.42f, h * 0.45f), Triple(w * 0.5f, -h * 0.62f, h * 0.5f))) {
            g.fillCircle(cx, cy, r + 0.3f, ink)
        }
        for ((cx, cy, r) in listOf(Triple(w * 0.25f, -h * 0.4f, h * 0.45f), Triple(w * 0.75f, -h * 0.42f, h * 0.45f), Triple(w * 0.5f, -h * 0.62f, h * 0.5f))) {
            g.fillCircle(cx, cy, r, 0, bg)
        }
        if (thorny) {
            for (i in 0 until 7) {
                val a = PI.toFloat() * (0.1f + i * 0.13f)
                val cx = w * 0.5f
                val cy = -h * 0.45f
                g.strokeLine(cx + cos(a) * h * 0.5f, cy - sin(a) * h * 0.5f, cx + cos(a) * h * 0.8f, cy - sin(a) * h * 0.8f, 0.3f, 0xFF5D4037.toInt())
            }
        } else {
            g.fillCircle(w * 0.3f, -h * 0.6f, 0.55f, th.flower1)
            g.fillCircle(w * 0.65f, -h * 0.75f, 0.55f, th.flower2)
            g.fillCircle(w * 0.5f, -h * 0.35f, 0.5f, th.flower1)
        }
    }

    fun fence(g: Gfx, w: Float, h: Float) {
        val wood = 0xFFC8956A.toInt()
        for (k in 0..2) {
            val x = k * (w - 1.2f) / 2f
            g.fillRoundRect(x - 0.25f, -h - 0.25f, x + 1.45f, 0.2f, 0.5f, ink)
            g.fillRoundRect(x, -h, x + 1.2f, 0f, 0.45f, wood)
        }
        for (y in floatArrayOf(-h * 0.75f, -h * 0.35f)) {
            g.fillRoundRect(-0.4f, y - 0.65f, w + 0.4f, y + 0.65f, 0.4f, ink)
            g.fillRoundRect(-0.2f, y - 0.45f, w + 0.2f, y + 0.45f, 0.3f, Colors.lighten(wood, 0.1f))
        }
    }

    fun stump(g: Gfx, w: Float, h: Float) {
        val bark = 0xFF7A4E2D.toInt()
        g.fillRoundRect(-0.3f, -h - 0.3f, w + 0.3f, 0.3f, 1.2f, ink)
        g.fillRoundRect(0f, -h, w, 0f, 1f, bark, grad("stump") {
            Gradient.linear(0f, 0f, 7f, 0f, intArrayOf(0xFF9A6A45.toInt(), bark, 0xFF5C3A20.toInt()), null)
        })
        g.fillOval(-0.2f, -h - 1.2f, w + 0.2f, -h + 1.2f, 0xFFE8C59A.toInt())
        g.strokeOval(w * 0.25f, -h - 0.6f, w * 0.75f, -h + 0.6f, 0.2f, 0xFFB98A5E.toInt())
        // mushrooms
        g.fillRect(0.6f, -2.2f, 1.1f, 0f, 0xFFFFF3E0.toInt())
        g.fillOval(-0.2f, -3.2f, 1.9f, -1.8f, 0xFFE53935.toInt())
        g.fillCircle(0.5f, -2.8f, 0.25f, 0xFFFFFFFF.toInt())
    }

    fun termiteMound(g: Gfx, w: Float, h: Float) {
        p.reset().moveTo(0f, 0f).quadTo(w * 0.1f, -h * 0.6f, w * 0.4f, -h).quadTo(w * 0.55f, -h * 1.04f, w * 0.65f, -h * 0.85f)
            .quadTo(w * 0.95f, -h * 0.4f, w, 0f).close()
        g.strokePath(p, 0.6f, ink)
        g.fillPath(p, 0, grad("mound") { Gradient.linear(0f, -12f, 7f, 0f, intArrayOf(0xFFD7A86E.toInt(), 0xFFAE7A45.toInt()), null) })
        g.fillCircle(w * 0.45f, -h * 0.5f, 0.6f, 0xFF5D4037.toInt())
    }

    fun bambooWall(g: Gfx, w: Float, h: Float) {
        for (k in 0..2) {
            val bx = k * w / 3f
            val bh = h - k * 1.3f
            g.fillRoundRect(bx - 0.2f, -bh - 0.2f, bx + w / 3.2f + 0.2f, 0.2f, 0.6f, ink)
            g.fillRoundRect(bx, -bh, bx + w / 3.2f, 0f, 0.5f, 0xFF8BC34A.toInt())
            var y = 3f
            while (y < bh) {
                g.fillRect(bx, -y - 0.2f, bx + w / 3.2f, -y + 0.2f, 0xFF558B2F.toInt())
                y += 3.5f
            }
        }
    }

    /** Overhead obstacle: [h] is the clearance bar thickness; the support reaches up to [reach]. */
    fun branch(g: Gfx, w: Float, reach: Float, th: Theme) {
        val bark = 0xFF7A4E2D.toInt()
        // vines up to the canopy
        var x = 0.8f
        while (x < w) {
            g.strokeLine(x, -2.5f, x + 0.3f, -reach, 0.45f, th.treeDark)
            x += 2.6f
        }
        g.fillRoundRect(-2.3f, -3.3f, w + 2.3f, 0.3f, 1.8f, ink)
        g.fillRoundRect(-2f, -3f, w + 2f, 0f, 1.5f, bark, grad("branch") {
            Gradient.linear(0f, -3f, 0f, 0f, intArrayOf(0xFFA87A55.toInt(), bark, 0xFF5C3A20.toInt()), null)
        })
        for ((lx, ly) in listOf(-1.8f to -2.8f, w * 0.4f to -3.2f, w + 1.6f to -2.6f)) {
            g.fillCircle(lx, ly, 2.0f, th.treeDark)
            g.fillCircle(lx + 0.5f, ly - 0.6f, 1.5f, th.tree)
        }
        // dangling leaves under the branch
        for (i in 0 until 3) g.fillOval(w * (0.2f + i * 0.3f) - 0.5f, -0.2f, w * (0.2f + i * 0.3f) + 0.5f, 1.6f, th.tree)
    }

    fun beehive(g: Gfx, w: Float, reach: Float) {
        g.strokeLine(w / 2f, -10f, w / 2f, -reach, 0.6f, 0xFF5D4037.toInt())
        val hive = 0xFFFFB300.toInt()
        g.fillOval(-0.3f, -10.3f, w + 0.3f, 0.3f, ink)
        g.fillOval(0f, -10f, w, 0f, hive, grad("hive") { Gradient.radial(3f, -7f, 8f, intArrayOf(0xFFFFE082.toInt(), hive, 0xFFE08E00.toInt()), floatArrayOf(0f, 0.5f, 1f)) })
        for (k in 1..3) g.strokeArc(0.4f, -10f + k * 2.3f - 1f, w - 0.4f, -10f + k * 2.3f + 1f, 10f, 160f, 0.3f, 0xFFB26A00.toInt())
        g.fillOval(w / 2f - 0.8f, -3f, w / 2f + 0.8f, -1.6f, 0xFF4E342E.toInt())
    }

    fun bambooBar(g: Gfx, w: Float, reach: Float) {
        g.fillRoundRect(-1.6f, -reach, -0.2f, 0f, 0.6f, 0xFF689F38.toInt())
        g.fillRoundRect(w + 0.2f, -reach, w + 1.6f, 0f, 0.6f, 0xFF689F38.toInt())
        g.fillRoundRect(-2f, -2.4f, w + 2f, 0.2f, 1.2f, ink)
        g.fillRoundRect(-1.8f, -2.2f, w + 1.8f, 0f, 1f, 0xFF9CCC65.toInt())
        var x = 1f
        while (x < w) {
            g.fillRect(x, -2.2f, x + 0.3f, 0f, 0xFF689F38.toInt())
            x += 3.5f
        }
    }

    fun coconut(g: Gfx, r: Float, spin: Float) {
        g.save(); g.rotate(spin)
        g.fillCircle(0f, 0f, r + 0.3f, ink)
        g.fillCircle(0f, 0f, r, 0xFF8D5B3A.toInt(), grad("coco") { Gradient.radial(-1f, -1f, 4f, intArrayOf(0xFFB98058.toInt(), 0xFF7A4A2C.toInt()), null) })
        g.fillCircle(-0.6f, -0.4f, 0.35f, 0xFF3E2215.toInt())
        g.fillCircle(0.4f, -0.7f, 0.35f, 0xFF3E2215.toInt())
        g.fillCircle(0f, 0.3f, 0.35f, 0xFF3E2215.toInt())
        g.restore()
    }

    fun pinecone(g: Gfx, r: Float, spin: Float) {
        g.save(); g.rotate(spin)
        g.fillOval(-r * 0.75f - 0.3f, -r - 0.3f, r * 0.75f + 0.3f, r + 0.3f, ink)
        g.fillOval(-r * 0.75f, -r, r * 0.75f, r, 0xFFA1673C.toInt())
        for (k in -1..1) g.strokeArc(-r * 0.7f, k * r * 0.5f - 0.6f, r * 0.7f, k * r * 0.5f + 0.6f, 20f, 140f, 0.25f, 0xFF5D3A1A.toInt())
        g.restore()
    }

    fun boulder(g: Gfx, r: Float, spin: Float) {
        g.save(); g.rotate(spin)
        g.fillCircle(0f, 0f, r + 0.3f, ink)
        g.fillCircle(0f, 0f, r, 0xFF9E9E9E.toInt(), grad("boulder") { Gradient.radial(-1.2f, -1.2f, 5f, intArrayOf(0xFFD0D0D0.toInt(), 0xFF8E8E8E.toInt(), 0xFF6E6E6E.toInt()), floatArrayOf(0f, 0.6f, 1f)) })
        g.strokeArc(-r * 0.6f, -r * 0.6f, r * 0.6f, r * 0.6f, 200f, 90f, 0.3f, 0xFF616161.toInt())
        g.fillCircle(r * 0.35f, r * 0.3f, 0.4f, 0xFF757575.toInt())
        g.restore()
    }

    fun shadow(g: Gfx, cx: Float, groundY: Float, rx: Float, alpha: Float) {
        g.fillOval(cx - rx, groundY - rx * 0.22f, cx + rx, groundY + rx * 0.22f, Colors.withAlpha(0xFF000000.toInt(), alpha))
    }

    // ================================================================== collectibles (local, radius ≈ 1)

    fun food(g: Gfx, food: Food, golden: Boolean, t: Float) {
        val main = if (golden) 0xFFFFD54F.toInt() else food.color
        val acc = if (golden) 0xFFFFF59D.toInt() else food.accent
        val dark = Colors.shade(main, 0.7f)
        val line = 0.16f
        when (food) {
            Food.BANANA -> {
                p.reset().moveTo(-1.0f, -0.6f).quadTo(-0.6f, 1.1f, 1.1f, 0.6f).quadTo(1.2f, 0.25f, 0.9f, 0.3f)
                    .quadTo(-0.3f, 0.6f, -0.55f, -0.8f).close()
                g.strokePath(p, line * 2, ink)
                g.fillPath(p, main)
                p.reset().moveTo(-0.7f, -0.3f).quadTo(-0.35f, 0.65f, 0.7f, 0.5f)
                g.strokePath(p, 0.16f, Colors.lighten(main, 0.5f))
                g.fillRoundRect(-1.0f, -1.0f, -0.6f, -0.5f, 0.12f, 0xFF6D4C41.toInt())
            }
            Food.MEAT -> {
                g.save(); g.rotate(-35f)
                g.fillRoundRect(0.2f, -0.18f, 1.25f, 0.18f, 0.15f, 0xFFF5F0E6.toInt())
                g.fillCircle(1.3f, -0.22f, 0.25f, 0xFFF5F0E6.toInt())
                g.fillCircle(1.3f, 0.22f, 0.25f, 0xFFF5F0E6.toInt())
                g.fillOval(-1.15f, -0.85f, 0.55f, 0.85f, ink)
                g.fillOval(-1.0f, -0.72f, 0.42f, 0.72f, main)
                g.fillOval(-0.75f, -0.5f, -0.05f, -0.05f, Colors.lighten(main, 0.4f))
                g.restore()
            }
            Food.CARROT -> {
                p.reset().moveTo(-0.45f, -0.55f).quadTo(0f, -0.75f, 0.45f, -0.55f).lineTo(0.05f, 1.15f).lineTo(-0.05f, 1.15f).close()
                g.strokePath(p, line * 2, ink)
                g.fillPath(p, main)
                g.strokeLine(-0.25f, -0.1f, 0.05f, -0.05f, 0.1f, dark)
                g.strokeLine(-0.1f, 0.4f, 0.15f, 0.42f, 0.1f, dark)
                for (a in floatArrayOf(-0.35f, 0f, 0.35f)) g.fillOval(a - 0.18f, -1.35f, a + 0.18f, -0.5f, acc)
            }
            Food.BAMBOO -> {
                g.save(); g.rotate(20f)
                g.fillRoundRect(-0.42f, -1.15f, 0.42f, 1.15f, 0.25f, ink)
                g.fillRoundRect(-0.3f, -1.05f, 0.3f, 1.05f, 0.18f, main)
                g.fillRect(-0.32f, -0.08f, 0.32f, 0.08f, dark)
                g.fillRect(-0.15f, -0.9f, 0.0f, 0.9f, Colors.lighten(main, 0.4f))
                g.fillOval(0.2f, -1.0f, 1.2f, -0.6f, acc)
                g.restore()
            }
            Food.HONEY -> {
                g.fillOval(-0.95f, -0.65f, 0.95f, 1.05f, ink)
                g.fillOval(-0.82f, -0.52f, 0.82f, 0.92f, main)
                g.fillRoundRect(-0.7f, -0.95f, 0.7f, -0.45f, 0.15f, 0xFFF5E6C8.toInt())
                g.fillRoundRect(-0.55f, -0.1f, 0.55f, 0.45f, 0.1f, 0xFFFFF8E1.toInt())
                p.reset().moveTo(-0.2f, -0.45f).quadTo(-0.25f, -0.1f, -0.05f, 0.0f)
                g.strokePath(p, 0.18f, acc)
                g.fillCircle(-0.45f, -0.15f, 0.12f, 0xAAFFFFFF.toInt())
            }
            Food.PEANUT -> {
                g.save(); g.rotate(-25f)
                g.fillOval(-1.05f, -0.6f, 0.15f, 0.6f, ink)
                g.fillOval(-0.15f, -0.55f, 1.05f, 0.55f, ink)
                g.fillOval(-0.95f, -0.5f, 0.05f, 0.5f, main)
                g.fillOval(-0.05f, -0.45f, 0.95f, 0.45f, main)
                for (x in floatArrayOf(-0.55f, 0.45f)) g.fillCircle(x, -0.15f, 0.1f, dark)
                g.restore()
            }
            Food.LEAVES -> {
                g.save(); g.rotate(-30f)
                p.reset().moveTo(-1.1f, 0f).quadTo(0f, -1.1f, 1.1f, 0f).quadTo(0f, 1.1f, -1.1f, 0f).close()
                g.strokePath(p, line * 2, ink)
                g.fillPath(p, main)
                g.strokeLine(-1.0f, 0f, 0.9f, 0f, 0.12f, acc)
                g.strokeLine(-0.2f, 0f, 0.2f, -0.45f, 0.08f, acc)
                g.strokeLine(0.2f, 0f, 0.55f, 0.4f, 0.08f, acc)
                g.restore()
            }
        }
        if (golden) twinkle(g, 0.9f, -0.9f, 0.45f + 0.2f * sin(t * 6f))
    }

    fun twinkle(g: Gfx, x: Float, y: Float, r: Float) {
        g.fillOval(x - r * 0.22f, y - r, x + r * 0.22f, y + r, 0xFFFFFFFF.toInt())
        g.fillOval(x - r, y - r * 0.22f, x + r, y + r * 0.22f, 0xFFFFFFFF.toInt())
    }

    fun gem(g: Gfx, t: Float) {
        p.reset().moveTo(-1f, -0.35f).lineTo(-0.5f, -0.95f).lineTo(0.5f, -0.95f).lineTo(1f, -0.35f).lineTo(0f, 1.05f).close()
        g.strokePath(p, 0.3f, ink)
        g.fillPath(p, 0xFF29B6F6.toInt(), grad("gem") { Gradient.linear(-1f, -1f, 1f, 1f, intArrayOf(0xFFB3E5FC.toInt(), 0xFF29B6F6.toInt(), 0xFF0277BD.toInt()), null) })
        p.reset().moveTo(-0.5f, -0.95f).lineTo(-0.2f, -0.35f).lineTo(0.2f, -0.35f).lineTo(0.5f, -0.95f).close()
        g.fillPath(p, 0xAAFFFFFF.toInt())
        g.strokeLine(-1f, -0.35f, 1f, -0.35f, 0.08f, 0x880277BD.toInt())
        twinkle(g, 0.6f, -0.8f, 0.35f + 0.15f * sin(t * 7f))
    }

    /** Power-up bubble with an icon. kind: 0 magnet, 1 shield, 2 doubler, 3 mystery. */
    fun powerUp(g: Gfx, kind: Int, t: Float) {
        val color = when (kind) {
            0 -> 0xFFE53950.toInt()
            1 -> 0xFF2F80ED.toInt()
            2 -> 0xFFFFA726.toInt()
            else -> 0xFF9B51E0.toInt()
        }
        val pulse = 1f + sin(t * 5f) * 0.05f
        g.save(); g.scale(pulse, pulse)
        g.fillCircle(0f, 0f, 1.25f, Colors.withAlpha(color, 0.25f))
        g.fillCircle(0f, 0f, 1.05f, 0xFFFFFFFF.toInt(), grad("bubble-$color") {
            Gradient.radial(-0.3f, -0.4f, 1.3f, intArrayOf(0xFFFFFFFF.toInt(), Colors.lighten(color, 0.75f)), null)
        })
        g.strokeCircle(0f, 0f, 1.05f, 0.14f, color)
        when (kind) {
            0 -> { // magnet
                p.reset().moveTo(-0.45f, -0.45f).lineTo(-0.45f, 0.05f).quadTo(-0.45f, 0.55f, 0f, 0.55f).quadTo(0.45f, 0.55f, 0.45f, 0.05f).lineTo(0.45f, -0.45f)
                g.strokePath(p, 0.3f, color)
                g.strokeLine(-0.45f, -0.45f, -0.45f, -0.25f, 0.3f, 0xFFCFD8DC.toInt())
                g.strokeLine(0.45f, -0.45f, 0.45f, -0.25f, 0.3f, 0xFFCFD8DC.toInt())
            }
            1 -> { // shield
                p.reset().moveTo(0f, -0.6f).lineTo(0.5f, -0.4f).quadTo(0.5f, 0.3f, 0f, 0.62f).quadTo(-0.5f, 0.3f, -0.5f, -0.4f).close()
                g.fillPath(p, color)
                g.strokeLine(0f, -0.45f, 0f, 0.45f, 0.1f, 0xAAFFFFFF.toInt())
            }
            2 -> g.text("2x", 0f, 0.3f, 0.85f, color, com.junglerun.game.gfx.Align.CENTER)
            else -> g.text("?", 0f, 0.33f, 0.95f, color, com.junglerun.game.gfx.Align.CENTER)
        }
        g.fillOval(-0.65f, -0.8f, -0.15f, -0.5f, 0xBBFFFFFF.toInt())
        g.restore()
    }

    fun sparkleBurst(g: Gfx, x: Float, y: Float, r: Float, color: Int) {
        g.fillOval(x - r * 0.25f, y - r, x + r * 0.25f, y + r, color)
        g.fillOval(x - r, y - r * 0.25f, x + r, y + r * 0.25f, color)
    }

}
