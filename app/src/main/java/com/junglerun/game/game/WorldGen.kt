package com.junglerun.game.game

import com.junglerun.game.core.Biome
import com.junglerun.game.core.Rng

/**
 * Procedural track generator. Purely a function of (seed, biome) so every player
 * in a race sees exactly the same course.
 */
class WorldGen(seed: Long, private val biome: Biome) {
    private val rng = Rng(seed)
    private var nextX = 70f
    private var segment = 0

    fun ensure(until: Float, hazards: MutableList<Hazard>, pickups: MutableList<Pickup>) {
        while (nextX < until) {
            spawnSegment(hazards, pickups)
        }
    }

    private fun spawnSegment(hazards: MutableList<Hazard>, pickups: MutableList<Pickup>) {
        segment++
        val x = nextX
        val speed = Physics.speedAt(x)
        val difficulty = (x / 4000f).coerceIn(0f, 1f)

        val kind = pickKind(difficulty)
        val endX: Float = when (kind) {
            HazardKind.LOW -> {
                hazards += Hazard(kind, x, 0f, 6f, 6.5f, rng.nextInt(3))
                arcFood(pickups, x + 3f, 4f + 13f)
                x + 6f
            }
            HazardKind.TALL -> {
                hazards += Hazard(kind, x, 0f, 7f, 12f, rng.nextInt(3))
                arcFood(pickups, x + 3.5f, 4f + 17f)
                x + 7f
            }
            HazardKind.OVERHEAD -> {
                val w = rng.range(8f, 14f)
                hazards += Hazard(kind, x, 7.5f, w, 60f, rng.nextInt(3))
                rowFood(pickups, x - 4f, x + w + 4f, 2.5f)
                x + w
            }
            HazardKind.PIT -> {
                val w = (speed * rng.range(0.22f, 0.36f + difficulty * 0.1f)).coerceIn(10f, 30f)
                hazards += Hazard(kind, x, 0f, w, 0f, rng.nextInt(3))
                arcFood(pickups, x + w / 2f, 4f + 14f, spread = w / 5f)
                x + w
            }
            HazardKind.FALLING -> {
                hazards += Hazard(kind, x, 70f, 5f, 5f, rng.nextInt(3))
                arcFood(pickups, x + 2.5f, 4f + 12f)
                x + 5f
            }
            HazardKind.MOVING -> {
                hazards += Hazard(kind, x, 0f, 7f, 6f, rng.nextInt(3))
                x + 7f
            }
            HazardKind.FLYER -> {
                hazards += Hazard(kind, x, 8f, 6f, 4.5f, rng.nextInt(3)).also { it.phase = rng.range(0f, 6.28f) }
                rowFood(pickups, x - 3f, x + 9f, 2.5f)
                x + 6f
            }
        }

        // Combo: a second hazard right after the first at higher difficulty (jump → slide etc.)
        var tail = endX
        if (difficulty > 0.35f && rng.chance(0.18f + difficulty * 0.2f) && kind != HazardKind.PIT) {
            val comboX = tail + speed * rng.range(0.55f, 0.75f)
            if (kind == HazardKind.OVERHEAD || kind == HazardKind.FLYER) {
                hazards += Hazard(HazardKind.LOW, comboX, 0f, 6f, 6.5f, rng.nextInt(3))
                tail = comboX + 6f
            } else {
                hazards += Hazard(HazardKind.OVERHEAD, comboX, 7.5f, 9f, 60f, rng.nextInt(3))
                tail = comboX + 9f
            }
        }

        // Gap to the next hazard scales with speed so reaction time stays fair.
        val gapSeconds = rng.range(0.85f, 1.6f - difficulty * 0.45f)
        val gap = speed * gapSeconds + 10f

        // Treats between hazards
        if (rng.chance(0.75f)) rowFood(pickups, tail + 8f, tail + gap - 8f, if (rng.chance(0.3f)) 9f else 2.5f)

        // Power-ups / gems
        when {
            segment % 9 == 0 -> pickups += Pickup(powerUpKind(), tail + gap * 0.5f, 9f)
            rng.chance(0.035f) -> pickups += Pickup(PickupKind.GEM, tail + gap * 0.5f, 12f)
            rng.chance(0.08f) -> pickups += Pickup(PickupKind.GOLDEN_FOOD, tail + gap * 0.45f, 10f)
        }

        nextX = tail + gap
    }

    private fun pickKind(difficulty: Float): HazardKind {
        val w = biome.hazardWeights.copyOf()
        // Ease players in: no tall walls, combos or fast movers in the first stretch.
        if (difficulty < 0.08f) {
            w[HazardKind.TALL.ordinal] = 0f
            w[HazardKind.MOVING.ordinal] *= 0.3f
            w[HazardKind.FLYER.ordinal] *= 0.3f
        }
        return HazardKind.entries[rng.weighted(w)]
    }

    private fun powerUpKind(): PickupKind = when (rng.nextInt(4)) {
        0 -> PickupKind.MAGNET
        1 -> PickupKind.SHIELD
        2 -> PickupKind.DOUBLER
        else -> PickupKind.MYSTERY
    }

    private fun rowFood(pickups: MutableList<Pickup>, from: Float, to: Float, y: Float) {
        if (to - from < 4f) return
        var x = from
        var n = 0
        while (x <= to && n < 8) {
            pickups += Pickup(PickupKind.FOOD, x, y)
            x += 4.5f
            n++
        }
    }

    private fun arcFood(pickups: MutableList<Pickup>, centerX: Float, peakY: Float, spread: Float = 4f) {
        for (i in -2..2) {
            val t = i / 2.5f
            pickups += Pickup(PickupKind.FOOD, centerX + i * spread, 3f + (peakY - 3f) * (1f - t * t))
        }
    }
}
