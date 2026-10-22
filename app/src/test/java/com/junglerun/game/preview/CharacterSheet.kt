package com.junglerun.game.preview

import com.junglerun.game.art.CharacterArt
import com.junglerun.game.art.Mood
import com.junglerun.game.art.Pose
import com.junglerun.game.core.Chaser
import com.junglerun.game.core.Shape
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Renders every character in several poses to build/previews/characters.png. */
fun renderCharacterSheet(out: File) {
    val w = 1800
    val h = 1000
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g2 = img.createGraphics()
    val g = Java2DGfx(g2)
    g.fillRect(0f, 0f, w.toFloat(), h.toFloat(), 0xFFBFE8C9.toInt())
    val art = CharacterArt()
    val s = 11f
    Shape.entries.forEachIndexed { i, shape ->
        val x = 110f + i * 200f
        art.drawAnimal(g, shape, x, 200f, s, 0.8f, Pose.RUN, Mood.NORMAL, 1f)
        art.drawAnimal(g, shape, x, 400f, s, 2.4f, Pose.RUN, Mood.HAPPY, 1f)
        art.drawAnimal(g, shape, x, 580f, s, 0f, Pose.JUMP, Mood.SCARED, 1f)
    }
    art.drawAnimal(g, Shape.MONKEY, 110f + 8 * 200f, 200f, s, 0.8f, Pose.RUN, Mood.NORMAL, 0.3f, golden = true)
    art.drawAnimal(g, Shape.MONKEY, 110f + 8 * 200f, 400f, s, 0f, Pose.SLIDE, Mood.NORMAL, 1f)
    Chaser.entries.forEachIndexed { i, c ->
        art.drawChaser(g, c, 140f + i * 240f, 900f, s * 1.15f, 1.2f, 0.5f)
    }
    g2.dispose()
    out.parentFile.mkdirs()
    ImageIO.write(img, "png", out)
}

fun main(args: Array<String>) {
    val dir = File(args.getOrElse(0) { "build/previews" })
    renderCharacterSheet(File(dir, "characters.png"))
    println("wrote ${dir.absolutePath}")
}
