package com.junglerun.game.preview

import com.junglerun.game.core.Animals
import com.junglerun.game.game.Box
import com.junglerun.game.game.CoachLevel
import com.junglerun.game.game.GameRenderer
import com.junglerun.game.game.GameWorld
import com.junglerun.game.game.Hint
import com.junglerun.game.game.Loadout
import com.junglerun.game.game.ViewportStyle
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Simulates a run and renders a frame, e.g. to check how a biome looks in portrait or landscape. */
fun renderScene(out: File, animalId: String, w: Int, h: Int, seconds: Float, coach: CoachLevel = CoachLevel.NONE, waitForHint: Boolean = false) {
    val world = GameWorld(Animals.get(animalId), 1234, Loadout(), countdown = 0.01f, coach = coach)
    val renderer = GameRenderer()
    world.viewAhead = 140f
    var t = 0f
    while (t < seconds) {
        world.update(1f / 60f)
        t += 1f / 60f
        if (waitForHint && world.hint != Hint.NONE && world.timeScale < 0.3f) break
        // simple autopilot so the runner survives
        val next = world.hazards.filter { it.active && !it.smashed && it.x + it.w > world.distance - 3f }.minByOrNull { it.x }
        if (!waitForHint && next != null) {
            val dx = next.x - world.distance
            when (next.kind.name) {
                "OVERHEAD", "FLYER" -> if (dx < world.speed * 0.2f + 6f && world.onGround) world.slide()
                "PIT" -> if (dx < 4f && world.onGround) world.jump()
                else -> if (dx < world.speed * 0.2f + 4f && world.onGround) world.jump()
            }
        }
    }
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g2 = img.createGraphics()
    renderer.draw(Java2DGfx(g2), world, Box(0f, 0f, w.toFloat(), h.toFloat()), ViewportStyle())
    g2.dispose()
    out.parentFile.mkdirs()
    ImageIO.write(img, "png", out)
}

/** Renders all previews into the given folder (default: preview/build/previews). */
fun main(args: Array<String>) {
    val dir = File(args.getOrElse(0) { "build/previews" })
    renderCharacterSheet(File(dir, "characters.png"))
    renderScene(File(dir, "tutorial.png"), "monkey", 1600, 720, 20f, CoachLevel.TUTORIAL, waitForHint = true)
    renderScene(File(dir, "portrait.png"), "monkey", 720, 1560, 9f)
    for (a in listOf("monkey", "lion", "rabbit", "panda", "bear", "elephant", "cheetah", "giraffe")) {
        renderScene(File(dir, "${a}.png"), a, 1600, 720, 11f)
    }
    println("Previews written to ${dir.absolutePath}")
}
