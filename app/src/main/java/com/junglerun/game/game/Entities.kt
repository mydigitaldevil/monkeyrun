package com.junglerun.game.game

enum class HazardKind { LOW, TALL, OVERHEAD, PIT, FALLING, MOVING, FLYER }

/**
 * An obstacle on the track. x/y are world units: x is distance along the track (left edge),
 * y is height above the ground (bottom edge). PITs are gaps in the ground of width [w].
 */
class Hazard(
    val kind: HazardKind,
    var x: Float,
    var y: Float,
    val w: Float,
    val h: Float,
    val variant: Int = 0,
) {
    var active = true
    var smashed = false
    var smashTime = 0f
    var triggered = false
    var vy = 0f
    var baseY = y
    var phase = 0f

    val solid: Boolean get() = active && !smashed && kind != HazardKind.PIT
}

enum class PickupKind { FOOD, GOLDEN_FOOD, GEM, MAGNET, SHIELD, DOUBLER, MYSTERY }

class Pickup(val kind: PickupKind, var x: Float, var y: Float) {
    var collected = false
    var collectTime = 0f
    val bobPhase = (x * 0.37f) % 6.28f
}

class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float,
    val color: Int, val size: Float,
    val gravity: Float = -120f,
)

class FloatingText(var x: Float, var y: Float, val text: String, val color: Int, var life: Float = 1f)

enum class RunState { COUNTDOWN, RUNNING, DOWN, FINISHED }

enum class DeathCause { CAUGHT, FELL }

/** Final tally for one run, used for rewards, missions and multiplayer results. */
data class RunResult(
    val animalId: String,
    val score: Long,
    val distanceMeters: Int,
    val food: Int,
    val coins: Int,
    val gems: Int,
    val jumps: Int,
    val slides: Int,
    val abilitiesUsed: Int,
    val powerUps: Int,
    val smashed: Int,
    val revives: Int,
    val durationSec: Float,
    val cause: DeathCause?,
)
