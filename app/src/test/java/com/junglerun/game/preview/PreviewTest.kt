package com.junglerun.game.preview

import com.junglerun.game.game.CoachLevel
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Renders the art to app/build/previews/*.png so changes can be eyeballed without a device. */
class PreviewTest {
    @Test
    fun renderPreviews() {
        val dir = File("build/previews")
        renderCharacterSheet(File(dir, "characters.png"))
        renderScene(File(dir, "jungle_landscape.png"), "monkey", 1600, 720, 6f)
        renderScene(File(dir, "jungle_portrait.png"), "monkey", 720, 1560, 9f)
        renderScene(File(dir, "tutorial.png"), "monkey", 1600, 720, 20f, CoachLevel.TUTORIAL, waitForHint = true)
        assertTrue(File(dir, "characters.png").length() > 10_000)
    }
}
