package com.junglerun.game.game

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.junglerun.game.core.Animals
import com.junglerun.game.core.Biome
import com.junglerun.game.core.Food
import kotlin.math.PI
import kotlin.math.abs
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

/**
 * Draws one [GameWorld] into a rectangular viewport. Split-screen simply calls [draw] twice.
 * The world is 100 units tall; ground sits at 80% of the viewport height.
 */
class GameRenderer(private val density: Float) {
    private val painter = AnimalPainter()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = 0xFFFFFFFF.toInt()
    }
    private val textOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        style = Paint.Style.STROKE
        color = 0xCC000000.toInt()
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    private var skyShader: Shader? = null
    private var skyKey = ""

    // Layout values for the viewport currently being drawn
    private var s = 1f
    private var groundY = 0f
    private var playerX = 0f
    private var camX = 0f
    private val vp = RectF()

    private fun sx(wx: Float) = playerX + (wx - camX) * s
    private fun sy(wy: Float) = groundY - wy * s

    fun draw(c: Canvas, world: GameWorld, viewport: RectF, style: ViewportStyle) {
        vp.set(viewport)
        s = vp.height() / 100f
        groundY = vp.top + vp.height() * 0.8f
        playerX = vp.left + min(vp.width() * 0.24f, 34f * s)
        camX = world.distance
        world.viewAhead = (vp.right - playerX) / s

        c.save()
        c.clipRect(vp)
        drawBackground(c, world.biome, world.distance)
        drawGround(c, world)
        drawHazards(c, world)
        drawPickups(c, world)
        style.ghost?.let { drawGhost(c, world, it) }
        drawChaser(c, world)
        drawRunner(c, world)
        drawParticles(c, world)
        drawHud(c, world, style)
        c.restore()
    }

    // ------------------------------------------------------------------ background

    private fun drawBackground(c: Canvas, biome: Biome, dist: Float) {
        val key = "${biome.name}-${vp.top}-${vp.height()}"
        if (key != skyKey) {
            skyKey = key
            skyShader = LinearGradient(0f, vp.top, 0f, groundY, biome.skyTop, biome.skyBottom, Shader.TileMode.CLAMP)
        }
        fill.shader = skyShader
        c.drawRect(vp, fill)
        fill.shader = null

        // Sun
        fill.color = 0x66FFF59D
        c.drawCircle(vp.right - 40f * s, vp.top + 18f * s, 10f * s, fill)
        fill.color = 0xFFFFF176.toInt()
        c.drawCircle(vp.right - 40f * s, vp.top + 18f * s, 6.5f * s, fill)

        // Clouds (very slow parallax)
        fill.color = 0xDDFFFFFF.toInt()
        val cloudSpan = 90f * s
        val off = (dist * 0.05f * s) % cloudSpan
        var cx = vp.left - off
        var i = 0
        while (cx < vp.right + cloudSpan) {
            val cy = vp.top + (10f + ((i * 37) % 17)) * s
            c.drawCircle(cx, cy, 4f * s, fill)
            c.drawCircle(cx + 4.5f * s, cy - 1.5f * s, 5f * s, fill)
            c.drawCircle(cx + 9f * s, cy, 3.6f * s, fill)
            cx += cloudSpan
            i++
        }

        // Far hills
        hills(c, dist * 0.12f, 34f, 9f, 0.018f, biome.farHills)
        // Near tree line
        trees(c, biome, dist * 0.4f)
    }

    private fun hills(c: Canvas, offset: Float, baseHeight: Float, amp: Float, freq: Float, color: Int) {
        path.reset()
        path.moveTo(vp.left, groundY)
        var x = vp.left
        val step = 12f
        while (x <= vp.right + step) {
            val wx = (x - vp.left) / s + offset
            val h = baseHeight + sin(wx * freq * 2f * PI.toFloat()) * amp + sin(wx * freq * 5.3f) * amp * 0.4f
            path.lineTo(x, groundY - h * s)
            x += step
        }
        path.lineTo(vp.right, groundY)
        path.close()
        fill.color = color
        c.drawPath(path, fill)
    }

    private fun trees(c: Canvas, biome: Biome, offset: Float) {
        val spacing = 26f
        val first = kotlin.math.floor(offset / spacing).toInt() - 1
        val count = ((vp.width() / s) / spacing).toInt() + 3
        for (k in first until first + count) {
            val hash = (k * 2654435761L).toInt() ushr 16
            val wx = k * spacing + (hash % 9)
            val x = vp.left + (wx - offset) * s
            val h = 22f + (hash % 13)
            when (biome) {
                Biome.JUNGLE, Biome.RIVERBANK -> palmOrJungle(c, x, h, biome, hash)
                Biome.SAVANNA -> acacia(c, x, h * 0.8f, biome)
                Biome.BAMBOO_FOREST -> bamboo(c, x, h + 8f, biome, hash)
                Biome.PINE_FOREST -> pine(c, x, h + 4f, biome)
                Biome.MEADOW -> bush(c, x, h * 0.5f, biome, hash)
            }
        }
    }

    private fun trunk(c: Canvas, x: Float, h: Float, w: Float, color: Int) {
        fill.color = color
        rect.set(x - w * s / 2f, groundY - h * s, x + w * s / 2f, groundY)
        c.drawRect(rect, fill)
    }

    private fun palmOrJungle(c: Canvas, x: Float, h: Float, biome: Biome, hash: Int) {
        trunk(c, x, h, 2.2f, 0xFF5D4037.toInt())
        fill.color = biome.nearTrees
        val top = groundY - h * s
        c.drawCircle(x, top, 8f * s, fill)
        c.drawCircle(x - 6f * s, top + 3f * s, 6f * s, fill)
        c.drawCircle(x + 6f * s, top + 2f * s, 6.5f * s, fill)
        if (hash % 3 == 0) {
            // Hanging vine
            stroke.color = 0xFF2E7D32.toInt()
            stroke.strokeWidth = 0.6f * s
            c.drawLine(x + 4f * s, top + 4f * s, x + 4f * s, top + 18f * s, stroke)
        }
    }

    private fun acacia(c: Canvas, x: Float, h: Float, biome: Biome) {
        trunk(c, x, h, 1.6f, 0xFF6D4C41.toInt())
        fill.color = biome.nearTrees
        rect.set(x - 12f * s, groundY - (h + 3f) * s, x + 12f * s, groundY - (h - 2f) * s)
        c.drawOval(rect, fill)
    }

    private fun bamboo(c: Canvas, x: Float, h: Float, biome: Biome, hash: Int) {
        for (j in 0 until 3) {
            val bx = x + (j * 3.5f - 3.5f) * s
            val bh = h - (hash + j * 7) % 10
            fill.color = if (j == 1) biome.nearTrees else biome.farHills
            rect.set(bx - 0.9f * s, groundY - bh * s, bx + 0.9f * s, groundY)
            c.drawRect(rect, fill)
            fill.color = 0x33000000
            var yy = 6f
            while (yy < bh) {
                rect.set(bx - 0.9f * s, groundY - yy * s, bx + 0.9f * s, groundY - (yy - 0.4f) * s)
                c.drawRect(rect, fill)
                yy += 6f
            }
        }
    }

    private fun pine(c: Canvas, x: Float, h: Float, biome: Biome) {
        trunk(c, x, 6f, 1.8f, 0xFF4E342E.toInt())
        fill.color = biome.nearTrees
        for (j in 0 until 3) {
            val base = groundY - (5f + j * (h - 5f) / 3.2f) * s
            val w = (9f - j * 2.2f) * s
            path.reset()
            path.moveTo(x - w, base)
            path.lineTo(x, base - (h / 2.4f) * s)
            path.lineTo(x + w, base)
            path.close()
            c.drawPath(path, fill)
        }
    }

    private fun bush(c: Canvas, x: Float, h: Float, biome: Biome, hash: Int) {
        fill.color = biome.nearTrees
        c.drawCircle(x, groundY - h * 0.4f * s, h * 0.6f * s, fill)
        c.drawCircle(x + h * 0.5f * s, groundY - h * 0.3f * s, h * 0.45f * s, fill)
        if (hash % 2 == 0) {
            fill.color = if (hash % 4 == 0) 0xFFFF80AB.toInt() else 0xFFFFF176.toInt()
            c.drawCircle(x - 1f * s, groundY - h * 0.7f * s, 0.9f * s, fill)
            c.drawCircle(x + 2f * s, groundY - h * 0.5f * s, 0.9f * s, fill)
        }
    }

    // ------------------------------------------------------------------ ground & pits

    private fun drawGround(c: Canvas, world: GameWorld) {
        val biome = world.biome
        fill.color = biome.ground
        c.drawRect(vp.left, groundY, vp.right, vp.bottom, fill)
        fill.color = biome.groundTop
        c.drawRect(vp.left, groundY, vp.right, groundY + 2.5f * s, fill)

        // Pebbles / grass tufts scrolling at full speed
        fill.color = 0x33000000
        val span = 9f
        val first = kotlin.math.floor((camX - 40f) / span).toInt()
        val last = first + ((vp.width() / s) / span).toInt() + 8
        for (k in first..last) {
            val wx = k * span + (k * 7 % 5)
            val px = sx(wx)
            c.drawCircle(px, groundY + (5f + (k * 13 % 9)) * s, 0.7f * s, fill)
        }

        // Pits
        for (h in world.hazards) {
            if (h.kind != HazardKind.PIT || !h.active) continue
            val l = sx(h.x)
            val r = sx(h.x + h.w)
            if (r < vp.left || l > vp.right) continue
            fill.color = biome.pitColor
            c.drawRect(l, groundY, r, vp.bottom, fill)
            val water = biome == Biome.RIVERBANK || biome == Biome.JUNGLE || biome == Biome.BAMBOO_FOREST
            if (water) {
                fill.color = 0x55FFFFFF
                val t = world.animTime
                var wx = l
                while (wx < r) {
                    val wy = groundY + (4f + sin(t * 3f + wx * 0.05f)) * s
                    c.drawRect(wx, wy, min(r, wx + 3f * s), wy + 0.5f * s, fill)
                    wx += 6f * s
                }
                if (biome == Biome.RIVERBANK && h.variant == 0) {
                    // Croc eyes watching from the water
                    fill.color = 0xFF4E7D32.toInt()
                    val ex = (l + r) / 2f
                    c.drawCircle(ex, groundY + 4f * s, 1.3f * s, fill)
                    c.drawCircle(ex + 2.6f * s, groundY + 4f * s, 1.3f * s, fill)
                    fill.color = 0xFFFFEB3B.toInt()
                    c.drawCircle(ex, groundY + 3.6f * s, 0.5f * s, fill)
                    c.drawCircle(ex + 2.6f * s, groundY + 3.6f * s, 0.5f * s, fill)
                }
            } else {
                fill.color = 0x44000000
                c.drawRect(l, groundY, r, groundY + 3f * s, fill)
            }
            // Edges
            fill.color = biome.groundTop
            c.drawRect(l - 0.6f * s, groundY, l, groundY + 6f * s, fill)
            c.drawRect(r, groundY, r + 0.6f * s, groundY + 6f * s, fill)
        }
    }

    // ------------------------------------------------------------------ hazards

    private fun drawHazards(c: Canvas, world: GameWorld) {
        val biome = world.biome
        for (h in world.hazards) {
            if (!h.active || h.smashed || h.kind == HazardKind.PIT) continue
            val l = sx(h.x)
            val r = sx(h.x + h.w)
            if (r < vp.left - 20f || l > vp.right + 20f) continue
            when (h.kind) {
                HazardKind.LOW -> lowObstacle(c, h, l, r, biome)
                HazardKind.TALL -> tallObstacle(c, h, l, r, biome)
                HazardKind.OVERHEAD -> overhead(c, h, l, r, biome)
                HazardKind.FALLING -> falling(c, h, l, r, biome)
                HazardKind.MOVING -> moving(c, h, l, r, biome, world.animTime)
                HazardKind.FLYER -> flyer(c, h, l, r, biome, world.animTime)
                HazardKind.PIT -> Unit
            }
        }
    }

    private fun lowObstacle(c: Canvas, h: Hazard, l: Float, r: Float, biome: Biome) {
        val top = sy(h.y + h.h)
        val bottom = sy(h.y)
        when (if (biome == Biome.SAVANNA && h.variant == 2) 3 else h.variant) {
            0 -> { // Log
                fill.color = 0xFF795548.toInt()
                rect.set(l, top, r, bottom)
                c.drawRoundRect(rect, 2f * s, 2f * s, fill)
                fill.color = 0xFFD7B899.toInt()
                c.drawCircle(r - 1.6f * s, (top + bottom) / 2f, (bottom - top) / 2.6f, fill)
                stroke.color = 0xFF8D6E63.toInt(); stroke.strokeWidth = 0.4f * s
                c.drawCircle(r - 1.6f * s, (top + bottom) / 2f, (bottom - top) / 5f, stroke)
            }
            1 -> { // Rock
                fill.color = 0xFF9E9E9E.toInt()
                rect.set(l, top, r, bottom + 1f * s)
                c.drawOval(rect, fill)
                fill.color = 0xFFBDBDBD.toInt()
                rect.set(l + 1f * s, top + 0.8f * s, l + (r - l) * 0.55f, top + 2.4f * s)
                c.drawOval(rect, fill)
            }
            2 -> { // Bush / biome plant
                fill.color = if (biome == Biome.MEADOW) 0xFF8D6E63.toInt() else darken(biome.nearTrees)
                if (biome == Biome.MEADOW) {
                    // Little wooden fence
                    for (k in 0..2) c.drawRect(l + k * (r - l) / 2.4f, top, l + k * (r - l) / 2.4f + 1.2f * s, bottom, fill)
                    c.drawRect(l, top + 1.2f * s, r, top + 2.2f * s, fill)
                    c.drawRect(l, top + 3.8f * s, r, top + 4.8f * s, fill)
                } else {
                    c.drawCircle((l + r) / 2f, bottom - 3f * s, 3.6f * s, fill)
                    c.drawCircle(l + 1.5f * s, bottom - 2f * s, 2.4f * s, fill)
                    c.drawCircle(r - 1.5f * s, bottom - 2f * s, 2.4f * s, fill)
                }
            }
            else -> { // Thorn bush
                fill.color = 0xFF827717.toInt()
                c.drawCircle((l + r) / 2f, bottom - 3f * s, 3.5f * s, fill)
                stroke.color = 0xFF3E2723.toInt(); stroke.strokeWidth = 0.35f * s
                for (k in 0 until 6) {
                    val a = k * 1.05f
                    val cx = (l + r) / 2f; val cy = bottom - 3f * s
                    c.drawLine(cx, cy, cx + kotlin.math.cos(a) * 4.6f * s, cy - kotlin.math.abs(sin(a)) * 4.6f * s, stroke)
                }
            }
        }
    }

    private fun tallObstacle(c: Canvas, h: Hazard, l: Float, r: Float, biome: Biome) {
        val top = sy(h.y + h.h)
        val bottom = sy(h.y)
        if (biome == Biome.SAVANNA) {
            // Termite mound
            fill.color = 0xFFA1887F.toInt()
            path.reset(); path.moveTo(l, bottom); path.lineTo((l + r) / 2f - 1f * s, top); path.lineTo((l + r) / 2f + 1.4f * s, top + 1f * s); path.lineTo(r, bottom); path.close()
            c.drawPath(path, fill)
        } else if (biome == Biome.BAMBOO_FOREST) {
            fill.color = 0xFF689F38.toInt()
            for (k in 0..2) {
                val bx = l + k * (r - l) / 3f
                rect.set(bx, top + k * 1.2f * s, bx + (r - l) / 3.4f, bottom)
                c.drawRect(rect, fill)
            }
        } else {
            // Stacked boulders / tree stump
            fill.color = 0xFF6D4C41.toInt()
            rect.set(l, top + 2f * s, r, bottom)
            c.drawRoundRect(rect, 1.2f * s, 1.2f * s, fill)
            fill.color = 0xFFD7B899.toInt()
            rect.set(l, top, r, top + 3.6f * s)
            c.drawOval(rect, fill)
            fill.color = 0xFF388E3C.toInt()
            c.drawCircle(l + 1.4f * s, top + 3f * s, 1.3f * s, fill)
        }
    }

    private fun overhead(c: Canvas, h: Hazard, l: Float, r: Float, biome: Biome) {
        val bottom = sy(h.y)
        val barTop = sy(h.y + 3f)
        when (biome) {
            Biome.PINE_FOREST -> {
                // Beehive hanging on a branch
                stroke.color = 0xFF4E342E.toInt(); stroke.strokeWidth = 1.2f * s
                c.drawLine(l - 4f * s, vp.top, r + 4f * s, sy(h.y + 9f), stroke)
                fill.color = 0xFFFFB300.toInt()
                rect.set(l, sy(h.y + 9f), r, bottom)
                c.drawOval(rect, fill)
                fill.color = 0xFF8D6E00.toInt()
                for (k in 1..3) c.drawRect(l + 0.5f * s, sy(h.y + k * 2.2f), r - 0.5f * s, sy(h.y + k * 2.2f) + 0.4f * s, fill)
            }
            Biome.BAMBOO_FOREST -> {
                fill.color = 0xFF7CB342.toInt()
                c.drawRect(l - 1f * s, barTop, r + 1f * s, bottom, fill)
                fill.color = 0xFF558B2F.toInt()
                c.drawRect(l - 1.5f * s, vp.top, l, groundY, fill)
                c.drawRect(r, vp.top, r + 1.5f * s, groundY, fill)
            }
            else -> {
                // Fallen tree branch with leaves and vines
                fill.color = 0xFF6D4C41.toInt()
                rect.set(l - 2f * s, barTop, r + 2f * s, bottom)
                c.drawRoundRect(rect, 1.4f * s, 1.4f * s, fill)
                stroke.color = 0xFF2E7D32.toInt(); stroke.strokeWidth = 0.6f * s
                var vx = l
                while (vx < r) {
                    c.drawLine(vx, vp.top, vx, barTop, stroke)
                    vx += 3f * s
                }
                fill.color = biome.nearTrees
                c.drawCircle(l, barTop, 2.2f * s, fill)
                c.drawCircle(r, barTop, 2.6f * s, fill)
            }
        }
    }

    private fun falling(c: Canvas, h: Hazard, l: Float, r: Float, biome: Biome) {
        val cx = (l + r) / 2f
        if (h.y > 0.5f && h.x - camX < 160f) {
            // Shadow warns where it lands
            val k = 1f - min(1f, h.y / 70f)
            fill.color = ((0x30 + (k * 0x60).toInt()) shl 24)
            rect.set(cx - (2f + k * 2f) * s, groundY - 0.6f * s, cx + (2f + k * 2f) * s, groundY + 0.6f * s)
            c.drawOval(rect, fill)
        }
        val cy = sy(h.y + h.h / 2f)
        val r0 = h.h / 2f * s
        when (biome) {
            Biome.JUNGLE, Biome.RIVERBANK -> {
                fill.color = 0xFF6D4C41.toInt(); c.drawCircle(cx, cy, r0, fill)
                fill.color = 0xFF3E2723.toInt()
                c.drawCircle(cx - r0 * 0.3f, cy - r0 * 0.2f, r0 * 0.18f, fill)
                c.drawCircle(cx + r0 * 0.15f, cy - r0 * 0.35f, r0 * 0.18f, fill)
            }
            Biome.PINE_FOREST -> {
                fill.color = 0xFF8D6E63.toInt()
                rect.set(cx - r0 * 0.7f, cy - r0, cx + r0 * 0.7f, cy + r0); c.drawOval(rect, fill)
                stroke.color = 0xFF4E342E.toInt(); stroke.strokeWidth = 0.3f * s
                for (k in -1..1) c.drawLine(cx - r0 * 0.7f, cy + k * r0 * 0.45f, cx + r0 * 0.7f, cy + k * r0 * 0.45f, stroke)
            }
            else -> {
                fill.color = 0xFF9E9E9E.toInt(); c.drawCircle(cx, cy, r0, fill)
                fill.color = 0xFFBDBDBD.toInt(); c.drawCircle(cx - r0 * 0.3f, cy - r0 * 0.3f, r0 * 0.4f, fill)
            }
        }
    }

    private fun moving(c: Canvas, h: Hazard, l: Float, r: Float, biome: Biome, t: Float) {
        if (biome == Biome.SAVANNA || biome == Biome.MEADOW) {
            // Charging warthog facing the runner (mirror the painter)
            c.save()
            c.scale(-1f, 1f, (l + r) / 2f, groundY)
            painter.drawChaser(c, com.junglerun.game.core.Chaser.HYENA, (l + r) / 2f, sy(h.y), s * 0.6f, h.phase)
            c.restore()
            fill.color = 0xFFFFFFFF.toInt()
            c.drawCircle(l + 0.6f * s, sy(h.y + 3.4f), 0.5f * s, fill)
        } else {
            // Rolling boulder
            val cx = (l + r) / 2f
            val cy = sy(h.y + h.h / 2f)
            val rr = h.h / 2f * s * 1.1f
            fill.color = 0xFF8D8D8D.toInt(); c.drawCircle(cx, cy, rr, fill)
            stroke.color = 0xFF5F5F5F.toInt(); stroke.strokeWidth = 0.5f * s
            val a = h.phase * 0.6f
            c.drawLine(cx - kotlin.math.cos(a) * rr, cy - sin(a) * rr, cx + kotlin.math.cos(a) * rr, cy + sin(a) * rr, stroke)
        }
        // Dust behind it
        fill.color = 0x44FFFFFF
        c.drawCircle(r + 1.5f * s, groundY - 1f * s, (1f + abs(sin(t * 10f))) * s, fill)
    }

    private fun flyer(c: Canvas, h: Hazard, l: Float, r: Float, biome: Biome, t: Float) {
        val cx = (l + r) / 2f
        val cy = sy(h.y + h.h / 2f)
        if (biome == Biome.PINE_FOREST || biome == Biome.MEADOW) {
            painter.drawChaser(c, com.junglerun.game.core.Chaser.BEES, cx - 2f * s, cy + 9f * s * 0.5f, s * 0.5f, t * 6f)
        } else {
            // Parrot / bat with flapping wings
            val flap = sin(t * 18f + h.phase) * 2.2f * s
            fill.color = if (biome == Biome.JUNGLE) 0xFFE53935.toInt() else 0xFF5D4037.toInt()
            rect.set(cx - 2.6f * s, cy - 1.4f * s, cx + 2.6f * s, cy + 1.4f * s)
            c.drawOval(rect, fill)
            path.reset(); path.moveTo(cx - 1f * s, cy); path.lineTo(cx + 0.5f * s, cy - 3.5f * s - flap); path.lineTo(cx + 1.8f * s, cy); path.close()
            fill.color = if (biome == Biome.JUNGLE) 0xFF1E88E5.toInt() else 0xFF3E2723.toInt()
            c.drawPath(path, fill)
            fill.color = 0xFFFFC107.toInt()
            path.reset(); path.moveTo(cx - 2.6f * s, cy - 0.4f * s); path.lineTo(cx - 4f * s, cy); path.lineTo(cx - 2.6f * s, cy + 0.4f * s); path.close()
            c.drawPath(path, fill)
        }
    }

    // ------------------------------------------------------------------ pickups

    private fun drawPickups(c: Canvas, world: GameWorld) {
        val food = world.animal.food
        for (p in world.pickups) {
            val x = sx(p.x)
            if (x < vp.left - 10f || x > vp.right + 10f) continue
            val bob = sin(world.animTime * 4f + p.bobPhase) * 0.6f
            var scale = 1f
            var alpha = 255
            if (p.collected) {
                scale = 1f + p.collectTime * 3f
                alpha = max(0, (255 * (1f - p.collectTime / 0.35f)).toInt())
                if (alpha == 0) continue
            }
            val y = sy(p.y + bob)
            when (p.kind) {
                PickupKind.FOOD -> drawFood(c, food, x, y, 1.6f * s * scale, alpha, false)
                PickupKind.GOLDEN_FOOD -> drawFood(c, food, x, y, 2.2f * s * scale, alpha, true)
                PickupKind.GEM -> gem(c, x, y, 2f * s * scale, alpha)
                else -> powerUp(c, p.kind, x, y, 2.8f * s * scale, alpha, world.animTime)
            }
        }
    }

    fun drawFood(c: Canvas, food: Food, x: Float, y: Float, r: Float, alpha: Int, golden: Boolean) {
        val main = if (golden) 0xFFFFD700.toInt() else food.color
        val acc = if (golden) 0xFFFFF59D.toInt() else food.accent
        if (golden) {
            fill.shader = RadialGradient(x, y, r * 2f, 0x88FFF176.toInt(), 0x00FFF176, Shader.TileMode.CLAMP)
            fill.alpha = alpha
            c.drawCircle(x, y, r * 2f, fill)
            fill.shader = null
        }
        fill.color = main; fill.alpha = alpha
        stroke.color = acc; stroke.alpha = alpha
        when (food) {
            Food.BANANA -> {
                stroke.color = main; stroke.alpha = alpha; stroke.strokeWidth = r * 0.75f
                rect.set(x - r, y - r * 1.2f, x + r, y + r * 0.8f)
                c.drawArc(rect, 20f, 140f, false, stroke)
                fill.color = acc; fill.alpha = alpha
                c.drawCircle(x + r * 0.92f, y - r * 0.05f, r * 0.18f, fill)
            }
            Food.MEAT -> {
                stroke.strokeWidth = r * 0.35f; stroke.color = 0xFFF5F5F5.toInt(); stroke.alpha = alpha
                c.drawLine(x, y, x + r * 1.1f, y - r * 0.8f, stroke)
                c.drawCircle(x + r * 1.15f, y - r * 0.95f, r * 0.25f, fillWith(0xFFF5F5F5.toInt(), alpha))
                rect.set(x - r, y - r * 0.6f, x + r * 0.5f, y + r * 0.8f)
                c.drawOval(rect, fillWith(main, alpha))
            }
            Food.CARROT -> {
                path.reset(); path.moveTo(x - r * 0.5f, y - r * 0.7f); path.lineTo(x + r * 0.5f, y - r * 0.7f); path.lineTo(x, y + r * 1.1f); path.close()
                c.drawPath(path, fillWith(main, alpha))
                stroke.strokeWidth = r * 0.25f
                c.drawLine(x, y - r * 0.7f, x - r * 0.4f, y - r * 1.4f, stroke)
                c.drawLine(x, y - r * 0.7f, x + r * 0.4f, y - r * 1.4f, stroke)
            }
            Food.BAMBOO -> {
                rect.set(x - r * 0.35f, y - r * 1.2f, x + r * 0.35f, y + r * 1.2f)
                c.drawRoundRect(rect, r * 0.2f, r * 0.2f, fillWith(main, alpha))
                stroke.strokeWidth = r * 0.15f
                c.drawLine(x - r * 0.35f, y, x + r * 0.35f, y, stroke)
                c.drawLine(x + r * 0.35f, y - r * 0.6f, x + r, y - r, stroke)
            }
            Food.HONEY -> {
                rect.set(x - r * 0.9f, y - r * 0.6f, x + r * 0.9f, y + r)
                c.drawOval(rect, fillWith(main, alpha))
                rect.set(x - r * 0.7f, y - r, x + r * 0.7f, y - r * 0.45f)
                c.drawRoundRect(rect, r * 0.2f, r * 0.2f, fillWith(acc, alpha))
            }
            Food.PEANUT -> {
                c.drawCircle(x - r * 0.4f, y, r * 0.6f, fillWith(main, alpha))
                c.drawCircle(x + r * 0.45f, y - r * 0.1f, r * 0.55f, fillWith(main, alpha))
                stroke.strokeWidth = r * 0.12f
                c.drawLine(x, y - r * 0.5f, x, y + r * 0.4f, stroke)
            }
            Food.LEAVES -> {
                path.reset(); path.moveTo(x - r, y); path.quadTo(x, y - r * 1.2f, x + r, y); path.quadTo(x, y + r * 1.2f, x - r, y); path.close()
                c.drawPath(path, fillWith(main, alpha))
                stroke.strokeWidth = r * 0.12f
                c.drawLine(x - r, y, x + r * 0.8f, y, stroke)
            }
        }
        stroke.alpha = 255
        fill.alpha = 255
    }

    private fun fillWith(color: Int, alpha: Int): Paint {
        fill.color = color
        fill.alpha = alpha
        return fill
    }

    private fun gem(c: Canvas, x: Float, y: Float, r: Float, alpha: Int) {
        path.reset()
        path.moveTo(x, y - r); path.lineTo(x + r * 0.8f, y - r * 0.2f); path.lineTo(x, y + r); path.lineTo(x - r * 0.8f, y - r * 0.2f); path.close()
        c.drawPath(path, fillWith(0xFF00B0FF.toInt(), alpha))
        path.reset()
        path.moveTo(x, y - r); path.lineTo(x + r * 0.35f, y - r * 0.2f); path.lineTo(x, y + r * 0.3f); path.lineTo(x - r * 0.35f, y - r * 0.2f); path.close()
        c.drawPath(path, fillWith(0xFFB3E5FC.toInt(), alpha))
    }

    private fun powerUp(c: Canvas, kind: PickupKind, x: Float, y: Float, r: Float, alpha: Int, t: Float) {
        val color = when (kind) {
            PickupKind.MAGNET -> 0xFFE53935.toInt()
            PickupKind.SHIELD -> 0xFF1E88E5.toInt()
            PickupKind.DOUBLER -> 0xFFFFB300.toInt()
            else -> 0xFF8E24AA.toInt()
        }
        val pulse = 1f + sin(t * 6f) * 0.06f
        c.drawCircle(x, y, r * pulse, fillWith(0xCCFFFFFF.toInt(), alpha))
        stroke.color = color; stroke.alpha = alpha; stroke.strokeWidth = r * 0.16f
        c.drawCircle(x, y, r * pulse, stroke)
        val label = when (kind) {
            PickupKind.MAGNET -> "U"
            PickupKind.SHIELD -> "◆"
            PickupKind.DOUBLER -> "2x"
            else -> "?"
        }
        text.textSize = r * 1.1f
        text.color = color
        text.alpha = alpha
        text.textAlign = Paint.Align.CENTER
        c.drawText(label, x, y + r * 0.38f, text)
        text.alpha = 255
        stroke.alpha = 255
    }

    // ------------------------------------------------------------------ characters

    private fun runnerPose(world: GameWorld): Pose = when {
        world.state == RunState.COUNTDOWN && world.distance < 1f -> Pose.IDLE
        world.sliding -> Pose.SLIDE
        !world.onGround -> Pose.JUMP
        else -> Pose.RUN
    }

    private fun drawRunner(c: Canvas, world: GameWorld) {
        val a = world.animal
        val x = playerX
        val y = sy(world.y)
        // Shadow
        val shadowScale = max(0.3f, 1f - world.y / 30f)
        fill.color = 0x33000000
        rect.set(x - 3.5f * s * shadowScale, groundY - 0.7f * s, x + 3.5f * s * shadowScale, groundY + 0.7f * s)
        if (world.y >= -0.5f) c.drawOval(rect, fill)

        if (a.id == "golden_monkey" || world.abilityActive) {
            fill.shader = RadialGradient(x, y - 6f * s, 10f * s, 0x66FFEB3B, 0x00FFEB3B, Shader.TileMode.CLAMP)
            c.drawCircle(x, y - 6f * s, 10f * s, fill)
            fill.shader = null
        }
        val flashing = world.stumbleFlash > 0f && ((world.animTime * 12f).toInt() % 2 == 0)
        val alpha = if (flashing) 110 else 255
        painter.drawAnimal(c, a.shape, a.bodyColor, a.accentColor, x, y, s, world.distance * 0.32f, runnerPose(world), alpha)

        if (world.invincible) {
            stroke.color = if (world.shieldTime > 0f || world.reviveShield > 0f) 0xAA42A5F5.toInt() else 0xAAFFD54F.toInt()
            stroke.strokeWidth = 0.7f * s
            c.drawCircle(x + 0.5f * s, y - 5.5f * s, 8.5f * s, stroke)
            fill.color = 0x2242A5F5
            c.drawCircle(x + 0.5f * s, y - 5.5f * s, 8.5f * s, fill)
        }
        if (world.magnetTime > 0f) {
            stroke.color = 0x66E53935
            stroke.strokeWidth = 0.4f * s
            val rr = (6f + (world.animTime * 20f) % 8f) * s
            c.drawCircle(x, y - 5.5f * s, rr, stroke)
        }
    }

    private fun drawChaser(c: Canvas, world: GameWorld) {
        val chase = world.chase
        val caught = world.state == RunState.DOWN && world.cause == DeathCause.CAUGHT
        val gapUnits = if (caught) 4f else 9f + (1f - chase) * 45f
        val x = playerX - gapUnits * s
        if (x < vp.left - 15f * s) return
        val bob = abs(sin(world.animTime * 9f)) * 0.8f
        painter.drawChaser(c, world.animal.chaser, x, groundY - bob * s, s * 1.15f, world.animTime * 11f)
    }

    private fun drawGhost(c: Canvas, world: GameWorld, ghost: GhostState) {
        val spec = Animals.get(ghost.animalId)
        val gx = sx(ghost.distance)
        val nameY = vp.top + 22f * s
        text.textSize = 3.6f * s
        text.textAlign = Paint.Align.CENTER
        if (gx < vp.left + 6f * s || gx > vp.right - 6f * s) {
            // Off-screen indicator
            val ahead = gx > vp.right
            val ex = if (ahead) vp.right - 10f * s else vp.left + 10f * s
            val diff = ((ghost.distance - world.distance) / Physics.UNITS_PER_METER).toInt()
            outlinedText(c, "${ghost.name} ${if (diff > 0) "+" else ""}${diff}m ${if (ahead) "▶" else "◀"}", ex, nameY, 0xFFFFEB3B.toInt())
            return
        }
        val pose = when {
            !ghost.alive -> Pose.IDLE
            ghost.sliding -> Pose.SLIDE
            ghost.y > 0.5f -> Pose.JUMP
            else -> Pose.RUN
        }
        painter.drawAnimal(c, spec.shape, spec.bodyColor, spec.accentColor, gx, sy(ghost.y), s, ghost.distance * 0.32f, pose, 120)
        outlinedText(c, ghost.name, gx, sy(ghost.y + 15f), 0xFFFFEB3B.toInt())
    }

    private fun drawParticles(c: Canvas, world: GameWorld) {
        for (p in world.particles) {
            fill.color = p.color
            fill.alpha = (255 * (p.life / p.maxLife)).toInt().coerceIn(0, 255)
            c.drawCircle(sx(p.x), sy(p.y), p.size * s, fill)
        }
        fill.alpha = 255
        text.textAlign = Paint.Align.CENTER
        for (t in world.texts) {
            text.textSize = 4f * s
            text.alpha = (255 * t.life.coerceIn(0f, 1f)).toInt()
            outlinedText(c, t.text, sx(t.x), sy(t.y), t.color)
        }
        text.alpha = 255
    }

    // ------------------------------------------------------------------ HUD

    private fun outlinedText(c: Canvas, str: String, x: Float, y: Float, color: Int) {
        textOutline.textSize = text.textSize
        textOutline.textAlign = text.textAlign
        textOutline.strokeWidth = text.textSize * 0.16f
        textOutline.alpha = text.alpha
        c.drawText(str, x, y, textOutline)
        val a = text.alpha
        text.color = color
        text.alpha = a
        c.drawText(str, x, y, text)
    }

    /** Ability button rect for a viewport — shared with input handling. */
    fun abilityButton(viewport: RectF, out: RectF): RectF {
        val sc = viewport.height() / 100f
        val r = 9f * sc
        val cx = viewport.right - 14f * sc
        val cy = viewport.bottom - 13f * sc
        out.set(cx - r, cy - r, cx + r, cy + r)
        return out
    }

    fun pauseButton(viewport: RectF, out: RectF): RectF {
        val sc = viewport.height() / 100f
        val r = 5.5f * sc
        val cx = viewport.right - 9f * sc
        val cy = viewport.top + 9f * sc
        out.set(cx - r, cy - r, cx + r, cy + r)
        return out
    }

    private val btn = RectF()

    private fun drawHud(c: Canvas, world: GameWorld, style: ViewportStyle) {
        // Danger vignette when the chaser is close
        if (world.chase > 0.45f && world.state == RunState.RUNNING) {
            val k = ((world.chase - 0.45f) / 0.55f).coerceIn(0f, 1f)
            val a = ((0.25f + 0.2f * sin(world.animTime * 10f)) * k * 255).toInt()
            fill.shader = LinearGradient(vp.left, 0f, vp.left + vp.width() * 0.25f, 0f, (a shl 24) or 0xD50000, 0x00D50000, Shader.TileMode.CLAMP)
            c.drawRect(vp.left, vp.top, vp.left + vp.width() * 0.25f, vp.bottom, fill)
            fill.shader = null
        }

        val pad = 4f * s
        text.textAlign = Paint.Align.LEFT
        text.textSize = 7f * s
        outlinedText(c, world.score.toString(), vp.left + pad, vp.top + pad + 6f * s, 0xFFFFFFFF.toInt())
        text.textSize = 4f * s
        outlinedText(c, "${world.meters} m", vp.left + pad, vp.top + pad + 11.5f * s, 0xFFE0F7FA.toInt())

        // Food & coins
        val fx = vp.left + pad + 2.5f * s
        val fy = vp.top + pad + 16.5f * s
        drawFood(c, world.animal.food, fx, fy, 1.6f * s, 255, false)
        outlinedText(c, "${world.food}", fx + 3.5f * s, fy + 1.4f * s, 0xFFFFF59D.toInt())
        fill.color = 0xFFFFC107.toInt()
        c.drawCircle(fx + 17f * s, fy, 1.6f * s, fill)
        outlinedText(c, "${world.coins}", fx + 19.5f * s, fy + 1.4f * s, 0xFFFFE082.toInt())
        if (world.gems > 0) {
            gem(c, fx + 33f * s, fy, 1.6f * s, 255)
            outlinedText(c, "${world.gems}", fx + 35.5f * s, fy + 1.4f * s, 0xFF81D4FA.toInt())
        }

        // Active power-up timers
        var ty = fy + 6f * s
        fun timer(label: String, t: Float, max: Float, color: Int) {
            if (t <= 0f) return
            fill.color = 0x66000000
            rect.set(vp.left + pad, ty - 1.6f * s, vp.left + pad + 24f * s, ty + 1.6f * s)
            c.drawRoundRect(rect, 1.6f * s, 1.6f * s, fill)
            fill.color = color
            rect.set(vp.left + pad, ty - 1.6f * s, vp.left + pad + 24f * s * (t / max).coerceIn(0f, 1f), ty + 1.6f * s)
            c.drawRoundRect(rect, 1.6f * s, 1.6f * s, fill)
            text.textSize = 2.6f * s
            outlinedText(c, label, vp.left + pad + 1.2f * s, ty + 1f * s, 0xFFFFFFFF.toInt())
            ty += 4.4f * s
        }
        timer("MAGNET", world.magnetTime, 18f, 0xFFE53935.toInt())
        timer("SHIELD", world.shieldTime, 13.5f, 0xFF1E88E5.toInt())
        timer("2X COINS", world.doublerTime, 20f, 0xFFFFB300.toInt())
        timer(world.animal.ability.displayName.uppercase(), world.abilityTime, world.animal.ability.baseDuration * 1.75f, 0xFFAB47BC.toInt())

        // Player label (split screen)
        style.label?.let {
            text.textAlign = Paint.Align.CENTER
            text.textSize = 5f * s
            outlinedText(c, it, vp.centerX(), vp.top + 8f * s, style.labelColor)
        }
        // Opponent score (online / LAN)
        style.ghost?.let { g ->
            text.textAlign = Paint.Align.CENTER
            text.textSize = 3.6f * s
            val status = if (g.alive) "${g.score}" else "${g.score} (out)"
            outlinedText(c, "VS ${g.name}: $status", vp.centerX(), vp.top + 15f * s, 0xFFFFEB3B.toInt())
        }

        // Pause button
        if (style.showPause) {
            pauseButton(vp, btn)
            fill.color = 0x66000000
            c.drawCircle(btn.centerX(), btn.centerY(), btn.width() / 2f, fill)
            fill.color = 0xFFFFFFFF.toInt()
            val bw = btn.width() * 0.12f
            c.drawRect(btn.centerX() - bw * 2f, btn.centerY() - bw * 2.5f, btn.centerX() - bw * 0.7f, btn.centerY() + bw * 2.5f, fill)
            c.drawRect(btn.centerX() + bw * 0.7f, btn.centerY() - bw * 2.5f, btn.centerX() + bw * 2f, btn.centerY() + bw * 2.5f, fill)
        }

        // Ability button with charge ring
        abilityButton(vp, btn)
        val ready = world.abilityReady
        fill.color = if (ready) 0xEEAB47BC.toInt() else 0x88000000.toInt()
        val pulse = if (ready) 1f + sin(world.animTime * 8f) * 0.06f else 1f
        c.drawCircle(btn.centerX(), btn.centerY(), btn.width() / 2f * pulse, fill)
        stroke.color = 0xFFFFD54F.toInt()
        stroke.strokeWidth = 1.2f * s
        rect.set(btn)
        rect.inset(0.6f * s, 0.6f * s)
        c.drawArc(rect, -90f, 360f * world.abilityCharge, false, stroke)
        drawFood(c, world.animal.food, btn.centerX(), btn.centerY() - 1f * s, 2.6f * s, 255, ready)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 2.4f * s
        outlinedText(c, if (ready) "TAP!" else "${(world.abilityCharge * 100).toInt()}%", btn.centerX(), btn.bottom - 1.6f * s, 0xFFFFFFFF.toInt())

        // Countdown / state banners
        text.textAlign = Paint.Align.CENTER
        when (world.state) {
            RunState.COUNTDOWN -> {
                val n = kotlin.math.ceil(world.countdown).toInt()
                text.textSize = 18f * s
                outlinedText(c, if (n > 0) "$n" else "GO!", vp.centerX(), vp.centerY(), 0xFFFFEB3B.toInt())
                if (world.distance < 1f) {
                    text.textSize = 3.6f * s
                    outlinedText(c, "Tap = Jump   •   Swipe down = Slide   •   Escape the ${world.animal.chaser.displayName}!", vp.centerX(), vp.centerY() + 10f * s, 0xFFFFFFFF.toInt())
                }
            }
            RunState.DOWN, RunState.FINISHED -> {
                text.textSize = 9f * s
                val msg = when {
                    world.state == RunState.FINISHED -> "FINISHED"
                    world.cause == DeathCause.FELL -> "SPLASH!"
                    else -> "CAUGHT BY ${world.animal.chaser.displayName.uppercase()}!"
                }
                outlinedText(c, msg, vp.centerX(), vp.centerY(), 0xFFFF7043.toInt())
            }
            RunState.RUNNING -> Unit
        }
    }

    private fun darken(color: Int): Int {
        val r = ((color shr 16 and 0xFF) * 0.8f).toInt()
        val g = ((color shr 8 and 0xFF) * 0.8f).toInt()
        val b = ((color and 0xFF) * 0.8f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
