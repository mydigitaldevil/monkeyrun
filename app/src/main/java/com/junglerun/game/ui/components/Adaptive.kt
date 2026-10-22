package com.junglerun.game.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junglerun.game.core.Animals
import com.junglerun.game.game.AutoPilot
import com.junglerun.game.game.Box
import com.junglerun.game.game.GameRenderer
import com.junglerun.game.game.GameWorld
import com.junglerun.game.game.Loadout
import com.junglerun.game.game.RunState
import com.junglerun.game.game.ViewportStyle
import com.junglerun.game.gfx.AndroidGfx

@Composable
fun isPortrait(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

/** Scrolls in landscape panels; in portrait the whole page scrolls instead (no nested scrolling). */
@Composable
fun Modifier.landscapeScroll(): Modifier = if (isPortrait()) this else this.verticalScroll(rememberScrollState())

/**
 * Side-by-side panels in landscape, stacked (and page-scrolling) in portrait.
 * Each panel receives the modifier it should use for its root.
 */
@Composable
fun AdaptiveRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    weights: List<Float> = emptyList(),
    panels: List<@Composable (Modifier) -> Unit>,
) {
    if (isPortrait()) {
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(spacing)) {
            panels.forEach { it(Modifier.fillMaxWidth()) }
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            panels.forEachIndexed { i, p -> p(Modifier.weight(weights.getOrElse(i) { 1f }).fillMaxHeight()) }
        }
    }
}

/** The real game running by itself — used as an animated menu background. */
@Composable
fun LiveScene(animalId: String, modifier: Modifier = Modifier) {
    val renderer = remember { GameRenderer() }
    val gfx = remember { AndroidGfx() }
    val box = remember { Box() }
    var world by remember(animalId) { mutableStateOf(GameWorld(Animals.get(animalId), 7, Loadout(), countdown = 0f)) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(animalId) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0f else ((t - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = t
                AutoPilot.drive(world)
                world.update(dt)
                // Keep the attract mode calm: restart before it gets too fast, or after a fall.
                if (world.state == RunState.DOWN || world.meters > 900) {
                    world = GameWorld(Animals.get(animalId), t, Loadout(), countdown = 0f)
                }
                frame = t
            }
        }
    }
    Canvas(modifier) {
        frame // redraw every frame
        drawIntoCanvas { c ->
            gfx.begin(c.nativeCanvas)
            renderer.draw(gfx, world, box.set(0f, 0f, size.width, size.height), ViewportStyle(showHud = false))
        }
    }
}

private val leagueColors = mapOf(
    "Bronze" to listOf(Color(0xFFD08C5A), Color(0xFF8D5524)),
    "Silver" to listOf(Color(0xFFE0E6EA), Color(0xFF8A99A6)),
    "Gold" to listOf(Color(0xFFFFE082), Color(0xFFFFA000)),
    "Diamond" to listOf(Color(0xFFB3E5FC), Color(0xFF0288D1)),
    "Legend" to listOf(Color(0xFFE1BEE7), Color(0xFF7B1FA2)),
)

@Composable
fun LeagueBadge(league: String, modifier: Modifier = Modifier, rank: Int = 0) {
    val cols = leagueColors[league] ?: leagueColors.getValue("Bronze")
    Text(
        (if (rank > 0) "#$rank  " else "") + "🏆 $league",
        modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(cols))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = if (league == "Silver" || league == "Gold") Color(0xFF3E2723) else Color.White,
        fontWeight = FontWeight.Black,
        fontSize = 12.sp,
    )
}
