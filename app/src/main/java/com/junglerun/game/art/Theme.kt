package com.junglerun.game.art

import com.junglerun.game.core.Biome
import com.junglerun.game.gfx.Colors

/** Rich colour themes per biome (the gameplay [Biome] enum only knows rules). */
class Theme(
    val skyTop: Int, val skyMid: Int, val skyHorizon: Int,
    val sun: Int,
    val hillFar: Int, val hillNear: Int,
    val tree: Int, val treeDark: Int, val trunk: Int,
    val grass: Int, val grassLight: Int,
    val dirt: Int, val dirtDark: Int,
    val water: Int, val waterLight: Int,
    val bush: Int,
    val flower1: Int, val flower2: Int,
    val canopy: Boolean,
    val rays: Boolean,
) {
    /** Hill colours fade into the sky with distance (atmospheric perspective). */
    val hillFarHazed = Colors.mix(hillFar, skyHorizon, 0.45f)
    val hillNearHazed = Colors.mix(hillNear, skyHorizon, 0.2f)

    companion object {
        private val themes = mapOf(
            Biome.JUNGLE to Theme(
                0xFF4FC3F7.toInt(), 0xFF8EDCF5.toInt(), 0xFFDDF7E3.toInt(), 0xFFFFF3B0.toInt(),
                0xFF4CAF7A.toInt(), 0xFF2E8B57.toInt(),
                0xFF34A853.toInt(), 0xFF1E6B3A.toInt(), 0xFF7A4E2D.toInt(),
                0xFF5BC236.toInt(), 0xFF9BE15D.toInt(), 0xFF8D5B3A.toInt(), 0xFF5E3A22.toInt(),
                0xFF1E88E5.toInt(), 0xFF90CAF9.toInt(), 0xFF2F9E44.toInt(),
                0xFFFF6B9A.toInt(), 0xFFFFD43B.toInt(), canopy = true, rays = true,
            ),
            Biome.SAVANNA to Theme(
                0xFFFF9A62.toInt(), 0xFFFFC27A.toInt(), 0xFFFFE9B8.toInt(), 0xFFFFF1C1.toInt(),
                0xFFE0A15A.toInt(), 0xFFC98642.toInt(),
                0xFF7E9F3A.toInt(), 0xFF566E24.toInt(), 0xFF6B4423.toInt(),
                0xFFD8B44A.toInt(), 0xFFF2D573.toInt(), 0xFFC08A4C.toInt(), 0xFF8A5A2B.toInt(),
                0xFF6D8E9B.toInt(), 0xFFB7D3DB.toInt(), 0xFFA3A83A.toInt(),
                0xFFFF7043.toInt(), 0xFFFFEE58.toInt(), canopy = false, rays = false,
            ),
            Biome.MEADOW to Theme(
                0xFF64B5F6.toInt(), 0xFFA5D8FF.toInt(), 0xFFF1FAFF.toInt(), 0xFFFFF8C4.toInt(),
                0xFF9CCC65.toInt(), 0xFF7CB342.toInt(),
                0xFF66BB6A.toInt(), 0xFF388E3C.toInt(), 0xFF8D6E63.toInt(),
                0xFF7ED957.toInt(), 0xFFB9F27C.toInt(), 0xFFA1724E.toInt(), 0xFF6F4A2E.toInt(),
                0xFF5D4037.toInt(), 0xFF8D6E63.toInt(), 0xFF5CB85C.toInt(),
                0xFFFF8FB1.toInt(), 0xFFFFFFFF.toInt(), canopy = false, rays = false,
            ),
            Biome.BAMBOO_FOREST to Theme(
                0xFF80DEEA.toInt(), 0xFFC5F1E9.toInt(), 0xFFF3FCEF.toInt(), 0xFFFFF7D1.toInt(),
                0xFFA5D6A7.toInt(), 0xFF81C784.toInt(),
                0xFF7CB342.toInt(), 0xFF4E8A2A.toInt(), 0xFF8BC34A.toInt(),
                0xFF8BCB4A.toInt(), 0xFFC5E86C.toInt(), 0xFF8D6E4F.toInt(), 0xFF5D4632.toInt(),
                0xFF26A69A.toInt(), 0xFFA7FFEB.toInt(), 0xFF689F38.toInt(),
                0xFFFFA4C4.toInt(), 0xFFFFFFFF.toInt(), canopy = true, rays = true,
            ),
            Biome.PINE_FOREST to Theme(
                0xFF5C7CBA.toInt(), 0xFF9DB4D8.toInt(), 0xFFE3EEF2.toInt(), 0xFFFFE9B0.toInt(),
                0xFF6A8CA0.toInt(), 0xFF4A7A6A.toInt(),
                0xFF2E7D5B.toInt(), 0xFF1B4D3A.toInt(), 0xFF5D4037.toInt(),
                0xFF4CAF50.toInt(), 0xFF8BD17C.toInt(), 0xFF6D4C3D.toInt(), 0xFF4A3228.toInt(),
                0xFF37474F.toInt(), 0xFF78909C.toInt(), 0xFF2E7D32.toInt(),
                0xFFFFB74D.toInt(), 0xFFE57373.toInt(), canopy = false, rays = true,
            ),
            Biome.RIVERBANK to Theme(
                0xFF29B6F6.toInt(), 0xFF81D4FA.toInt(), 0xFFE0F7FA.toInt(), 0xFFFFF59D.toInt(),
                0xFF4DB6AC.toInt(), 0xFF26A69A.toInt(),
                0xFF43A047.toInt(), 0xFF1B5E20.toInt(), 0xFF8D6E63.toInt(),
                0xFF66BB6A.toInt(), 0xFFA5D6A7.toInt(), 0xFFB08968.toInt(), 0xFF7F5539.toInt(),
                0xFF039BE5.toInt(), 0xFF81D4FA.toInt(), 0xFF2E7D32.toInt(),
                0xFFFFF176.toInt(), 0xFFFF8A65.toInt(), canopy = false, rays = false,
            ),
        )

        fun of(b: Biome): Theme = themes.getValue(b)
    }
}
