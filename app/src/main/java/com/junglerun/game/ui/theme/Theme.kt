package com.junglerun.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object JungleColors {
    val Leaf = Color(0xFF2E7D32)
    val DeepLeaf = Color(0xFF0B3D20)
    val Lime = Color(0xFF8BC34A)
    val Banana = Color(0xFFFFD93B)
    val Coin = Color(0xFFFFC107)
    val Gem = Color(0xFF40C4FF)
    val Wood = Color(0xFF6D4C41)
    val Danger = Color(0xFFE53935)
    val Card = Color(0xCC12321F)
    val CardLight = Color(0x3312321F)
    val Purple = Color(0xFFAB47BC)

    val background = Brush.verticalGradient(listOf(Color(0xFF1B5E20), Color(0xFF0B3D20), Color(0xFF052614)))
    val playButton = Brush.horizontalGradient(listOf(Color(0xFFFFC107), Color(0xFFFF9800)))
    val greenButton = Brush.horizontalGradient(listOf(Color(0xFF66BB6A), Color(0xFF2E7D32)))
    val blueButton = Brush.horizontalGradient(listOf(Color(0xFF29B6F6), Color(0xFF0277BD)))
    val purpleButton = Brush.horizontalGradient(listOf(Color(0xFFBA68C8), Color(0xFF7B1FA2)))
    val redButton = Brush.horizontalGradient(listOf(Color(0xFFEF5350), Color(0xFFC62828)))
}

private val colors = darkColorScheme(
    primary = JungleColors.Banana,
    onPrimary = Color(0xFF3E2723),
    secondary = JungleColors.Lime,
    background = JungleColors.DeepLeaf,
    surface = Color(0xFF12321F),
    onSurface = Color.White,
    onBackground = Color.White,
)

private val typography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 52.sp, letterSpacing = 2.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 1.sp),
)

@Composable
fun JungleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
