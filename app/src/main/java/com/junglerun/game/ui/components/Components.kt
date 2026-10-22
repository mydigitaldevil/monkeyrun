package com.junglerun.game.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.junglerun.game.core.AnimalSpec
import com.junglerun.game.core.Food
import com.junglerun.game.art.CharacterArt
import com.junglerun.game.art.Mood
import com.junglerun.game.art.Pose
import com.junglerun.game.art.WorldArt
import com.junglerun.game.gfx.AndroidGfx
import com.junglerun.game.ui.theme.JungleColors

/** Jungle-themed screen background with a soft vignette. */
@Composable
fun JungleBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(JungleColors.background),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Decorative leaves in the corners
            val leaf = Color(0x2266BB6A)
            drawCircle(leaf, radius = size.minDimension * 0.35f, center = androidx.compose.ui.geometry.Offset(0f, 0f))
            drawCircle(leaf, radius = size.minDimension * 0.28f, center = androidx.compose.ui.geometry.Offset(size.width, size.height))
            drawCircle(Color(0x1100E676), radius = size.minDimension * 0.2f, center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.1f))
        }
        content()
    }
}

/** Big chunky game title: thick dark outline under a warm gradient fill. */
@Composable
fun OutlinedTitle(text: String, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    Box(modifier) {
        Text(
            text,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = size,
                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = 14f, join = androidx.compose.ui.graphics.StrokeJoin.Round),
                color = Color(0xFF3E2210),
            ),
        )
        Text(
            text,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = size,
                brush = Brush.verticalGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFC107), Color(0xFFFF8F00))),
            ),
        )
    }
}

@Composable
fun GameButton(
    text: String,
    modifier: Modifier = Modifier,
    brush: Brush = JungleColors.greenButton,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    textColor: Color = Color.White,
    pulse: Boolean = false,
    onClick: () -> Unit,
) {
    val scale = if (pulse) {
        val t = rememberInfiniteTransition(label = "pulse")
        val s by t.animateFloat(1f, 1.05f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulseScale")
        s
    } else 1f
    Box(
        modifier
            .scale(scale)
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(brush)
            .border(2.dp, Color(0x55FFFFFF), RoundedCornerShape(50))
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(JungleColors.Card)
            .border(1.dp, Color(0x3366BB6A), RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun TopBar(title: String, coins: Long, gems: Int, onBack: () -> Unit, onShop: (() -> Unit)? = null) {
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.weight(1f))
        CurrencyBar(coins, gems, onShop)
    }
}

@Composable
fun CurrencyBar(coins: Long, gems: Int, onShop: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CurrencyChip("●", JungleColors.Coin, formatNumber(coins), onShop)
        CurrencyChip("◆", JungleColors.Gem, gems.toString(), onShop)
    }
}

@Composable
private fun CurrencyChip(icon: String, color: Color, value: String, onAdd: (() -> Unit)?) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x66000000))
            .padding(start = 10.dp, end = if (onAdd != null) 2.dp else 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = color, fontSize = 18.sp)
        Spacer(Modifier.width(6.dp))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold)
        if (onAdd != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(JungleColors.Lime)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Get more", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Live, animated, procedurally drawn animal — same art as in-game. */
@Composable
fun AnimalPortrait(
    spec: AnimalSpec,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    running: Boolean = true,
    mood: Mood = Mood.NORMAL,
) {
    val art = remember { CharacterArt() }
    val gfx = remember { AndroidGfx() }
    val t = rememberInfiniteTransition(label = "run")
    val phase by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(650, easing = LinearEasing)), label = "phase")
    val clock by t.animateFloat(0f, 100f, infiniteRepeatable(tween(100_000, easing = LinearEasing)), label = "clock")
    Canvas(modifier) {
        val s = size.height / 19f
        drawIntoCanvas { c ->
            gfx.begin(c.nativeCanvas)
            art.drawAnimal(
                gfx, spec.shape, size.width / 2f - s * 0.6f, size.height * 0.94f, s,
                if (running) phase else clock, if (running) Pose.RUN else Pose.IDLE,
                mood, clock, golden = spec.id == "golden_monkey", alpha = if (locked) 0.35f else 1f,
            )
            if (locked) {
                // padlock badge
                val r = size.minDimension * 0.16f
                val cx = size.width / 2f
                val cy = size.height * 0.55f
                gfx.fillRoundRect(cx - r, cy - r * 0.2f, cx + r, cy + r * 1.1f, r * 0.25f, 0xFF37474F.toInt())
                gfx.strokeArc(cx - r * 0.6f, cy - r * 1.1f, cx + r * 0.6f, cy + r * 0.3f, 180f, 180f, r * 0.22f, 0xFF37474F.toInt())
                gfx.fillCircle(cx, cy + r * 0.4f, r * 0.18f, 0xFFFFD54F.toInt())
            }
        }
    }
}

/** Small icon of an animal's food, drawn with the in-game art. */
@Composable
fun FoodIcon(food: Food, modifier: Modifier = Modifier.size(22.dp)) {
    val art = remember { WorldArt() }
    val gfx = remember { AndroidGfx() }
    Canvas(modifier) {
        drawIntoCanvas { c ->
            gfx.begin(c.nativeCanvas)
            gfx.save()
            gfx.translate(size.width / 2f, size.height / 2f)
            gfx.scale(size.minDimension / 2.6f)
            art.food(gfx, food, false, 0f)
            gfx.restore()
        }
    }
}

@Composable
fun ProgressRow(label: String, progress: Int, target: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row {
            Text(label, color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("$progress / $target", color = JungleColors.Banana, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (progress.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier
                .padding(top = 2.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .fillMaxWidth(),
            color = JungleColors.Lime,
            trackColor = Color(0x44FFFFFF),
        )
    }
}

fun formatNumber(n: Long): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000f)
    n >= 10_000 -> String.format("%.1fK", n / 1_000f)
    else -> "%,d".format(n)
}
