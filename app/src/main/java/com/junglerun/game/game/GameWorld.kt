package com.junglerun.game.game

import com.junglerun.game.core.Ability
import com.junglerun.game.core.AnimalSpec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Physics {
    const val BASE_SPEED = 38f
    const val MAX_SPEED = 88f
    const val SPEED_PER_UNIT = 0.012f
    const val GRAVITY = 260f
    const val JUMP_VELOCITY = 95f
    const val PLAYER_W = 7f
    const val PLAYER_H = 11f
    const val SLIDE_H = 5.5f
    const val SLIDE_TIME = 0.65f
    const val UNITS_PER_METER = 3f

    /** Speed is a function of distance (not time) so world generation can plan fair gaps. */
    fun speedAt(distance: Float): Float = min(MAX_SPEED, BASE_SPEED + distance * SPEED_PER_UNIT)
}

/** Upgrade levels (0..5) bought in the shop, and the score multiplier from missions. */
data class Loadout(
    val magnetLevel: Int = 0,
    val shieldLevel: Int = 0,
    val doublerLevel: Int = 0,
    val abilityLevel: Int = 0,
    val scoreMultiplier: Int = 1,
    val vipCoinBoost: Boolean = false,
)

interface WorldListener {
    fun onJump() {}
    fun onSlide() {}
    fun onCollect(kind: PickupKind) {}
    fun onStumble() {}
    fun onSmash() {}
    fun onAbility() {}
    fun onDown(cause: DeathCause) {}
}

/**
 * Simulation for one runner. Pure game logic, no Android UI — rendering lives in [GameRenderer].
 * Coordinates: x = distance along the track, y = height above ground (up is positive).
 */
class GameWorld(
    val animal: AnimalSpec,
    seed: Long,
    private val loadout: Loadout,
    countdown: Float = 3f,
    var listener: WorldListener? = null,
) {
    val biome = animal.biome
    private val gen = WorldGen(seed, biome)

    val hazards = ArrayList<Hazard>()
    val pickups = ArrayList<Pickup>()
    val particles = ArrayList<Particle>()
    val texts = ArrayList<FloatingText>()

    var state = RunState.COUNTDOWN
        private set
    var countdown = countdown
        private set
    var cause: DeathCause? = null
        private set

    // Runner
    var distance = 0f
        private set
    var speed = Physics.BASE_SPEED
        private set
    var y = 0f
        private set
    var vy = 0f
        private set
    var onGround = true
        private set
    var slideTime = 0f
        private set
    var stumbleFlash = 0f
        private set
    private var jumpsUsed = 0
    private var speedFactor = 1f
    private var fastFall = false
    var animTime = 0f
        private set

    // Chaser: 1 = right behind you, 0 = far away
    var chase = 0.85f
        private set

    // Effects (seconds remaining)
    var magnetTime = 0f
        private set
    var shieldTime = 0f
        private set
    var doublerTime = 0f
        private set
    var abilityTime = 0f
        private set
    var abilityCharge = 0.3f
        private set
    var reviveShield = 0f
        private set

    // Stats
    var food = 0
        private set
    var coins = 0
        private set
    var gems = 0
        private set
    var jumps = 0
        private set
    var slides = 0
        private set
    var abilitiesUsed = 0
        private set
    var powerUps = 0
        private set
    var smashed = 0
        private set
    var revives = 0
        private set
    private var bonusScore = 0L
    var runTime = 0f
        private set

    /** Width of the visible world in units; set by the renderer so screen-wide abilities know their reach. */
    var viewAhead = 140f

    val sliding: Boolean get() = slideTime > 0f
    val height: Float get() = if (sliding) Physics.SLIDE_H else Physics.PLAYER_H
    val abilityReady: Boolean get() = abilityCharge >= 1f && abilityTime <= 0f && state == RunState.RUNNING
    val abilityActive: Boolean get() = abilityTime > 0f
    val meters: Int get() = (distance / Physics.UNITS_PER_METER).toInt()

    val invincible: Boolean
        get() = shieldTime > 0f || reviveShield > 0f ||
            (abilityActive && animal.ability in INVINCIBLE_ABILITIES)

    val score: Long
        get() = ((meters + bonusScore) * max(1, loadout.scoreMultiplier)).toLong()

    init {
        gen.ensure(260f, hazards, pickups)
    }

    // ---------------------------------------------------------------- input

    fun jump() {
        if (state != RunState.RUNNING) return
        val maxJumps = if (animal.doubleJump) 2 else 1
        if (onGround || jumpsUsed < maxJumps) {
            val moon = abilityActive && animal.ability == Ability.MOON_JUMP
            val power = Physics.JUMP_VELOCITY * animal.jumpMultiplier * (if (moon) 1.2f else 1f)
            vy = if (jumpsUsed == 0) power else power * 0.85f
            jumpsUsed++
            onGround = false
            slideTime = 0f
            fastFall = false
            jumps++
            listener?.onJump()
            puff(distance - 2f, y, 0xFFFFFFFF.toInt(), 4)
        }
    }

    fun slide() {
        if (state != RunState.RUNNING) return
        if (!onGround) {
            fastFall = true
            vy = min(vy, -120f)
        }
        if (slideTime <= 0f) {
            slides++
            listener?.onSlide()
        }
        slideTime = Physics.SLIDE_TIME
    }

    fun useAbility(): Boolean {
        if (!abilityReady) return false
        abilityCharge = 0f
        abilitiesUsed++
        abilityTime = animal.ability.baseDuration * (1f + loadout.abilityLevel * 0.15f)
        listener?.onAbility()
        when (animal.ability) {
            Ability.FOOD_FRENZY -> {
                for (h in hazards) {
                    if (h.solid && h.x > distance - 5f && h.x < distance + viewAhead) {
                        h.active = false
                        for (i in 0 until 3) pickups += Pickup(PickupKind.FOOD, h.x + i * 3f, 3f + i * 2f)
                        puff(h.x, h.y + h.h.coerceAtMost(10f) / 2f, animal.food.color, 8)
                    }
                }
            }
            Ability.ROAR -> {
                for (h in hazards) {
                    if (h.solid && h.x > distance - 5f && h.x < distance + viewAhead) smash(h)
                }
                chase = 0f
                texts += FloatingText(distance + 6f, 22f, "ROAR!", 0xFFFFD54F.toInt(), 1.2f)
            }
            Ability.HONEY_MAGNET -> magnetTime = max(magnetTime, abilityTime)
            else -> Unit
        }
        return true
    }

    // ---------------------------------------------------------------- update

    fun update(dt: Float) {
        animTime += dt
        updateParticles(dt)
        when (state) {
            RunState.COUNTDOWN -> {
                countdown -= dt
                if (countdown <= 0f) state = RunState.RUNNING
                return
            }
            RunState.DOWN, RunState.FINISHED -> return
            RunState.RUNNING -> Unit
        }
        runTime += dt

        // Speed
        speedFactor = min(1f, speedFactor + dt * 0.5f)
        val dash = abilityActive && animal.ability == Ability.DASH
        speed = Physics.speedAt(distance) * animal.speedMultiplier * speedFactor * (if (dash) 1.6f else 1f)
        distance += speed * dt
        gen.ensure(distance + 260f, hazards, pickups)

        // Timers
        magnetTime = max(0f, magnetTime - dt)
        shieldTime = max(0f, shieldTime - dt)
        doublerTime = max(0f, doublerTime - dt)
        if (abilityTime > 0f && abilityTime - dt <= 0f) {
            // Short grace period so an ability never ends with you mid-air over a gap or inside a log.
            reviveShield = max(reviveShield, 1.2f)
        }
        abilityTime = max(0f, abilityTime - dt)
        reviveShield = max(0f, reviveShield - dt)
        stumbleFlash = max(0f, stumbleFlash - dt)
        if (onGround) slideTime = max(0f, slideTime - dt)
        chase = max(0f, chase - dt * 0.085f)

        updateRunner(dt)
        if (state != RunState.RUNNING) return
        updateHazards(dt)
        collideHazards()
        if (state != RunState.RUNNING) return
        collectPickups(dt)
        cleanup()
    }

    private fun updateRunner(dt: Float) {
        val moon = abilityActive && animal.ability == Ability.MOON_JUMP
        val g = Physics.GRAVITY * (if (moon) 0.55f else 1f) * (if (fastFall) 2.2f else 1f)
        vy -= g * dt
        y += vy * dt

        val ground = groundUnder(distance)
        if (y <= 0f) {
            if (ground) {
                if (!onGround && vy < -40f) puff(distance, 0f, biome.groundTop, 3)
                y = 0f
                vy = 0f
                onGround = true
                jumpsUsed = 0
                fastFall = false
            } else {
                onGround = false
                if (invincible || (abilityActive && animal.ability == Ability.STOMP)) {
                    // Protected runners bounce over gaps instead of falling in.
                    if (y < -1f) {
                        vy = Physics.JUMP_VELOCITY
                        jumpsUsed = 1
                    }
                } else if (y < -14f) {
                    goDown(DeathCause.FELL)
                }
            }
        } else {
            onGround = false
        }
    }

    /** True if there is ground (not a pit) under the runner's feet at [x]. */
    private fun groundUnder(x: Float): Boolean {
        for (h in hazards) {
            if (h.kind != HazardKind.PIT || !h.active) continue
            // Small forgiveness at the edges
            if (x > h.x + 1.5f && x < h.x + h.w - 1.5f) return false
        }
        return true
    }

    private fun updateHazards(dt: Float) {
        for (h in hazards) {
            if (h.smashed) {
                h.smashTime += dt
                continue
            }
            when (h.kind) {
                HazardKind.FALLING -> {
                    if (!h.triggered && h.x - distance < speed * 1.3f) h.triggered = true
                    if (h.triggered && h.y > 0f) {
                        h.vy -= 220f * dt
                        h.y = max(0f, h.y + h.vy * dt)
                        if (h.y == 0f) puff(h.x + h.w / 2f, 0f, biome.ground, 6)
                    }
                }
                HazardKind.MOVING -> {
                    if (h.x - distance < 150f) {
                        h.x -= 22f * dt
                        h.phase += dt * 14f
                    }
                }
                HazardKind.FLYER -> {
                    h.phase += dt * 3f
                    h.y = h.baseY + 2.5f + sin(h.phase) * 2.5f
                }
                else -> Unit
            }
        }
    }

    private fun collideHazards() {
        val left = distance - Physics.PLAYER_W / 2f + 0.8f
        val right = distance + Physics.PLAYER_W / 2f - 0.8f
        val bottom = y + 0.5f
        val top = y + height - 0.6f
        val smasher = invincible || (abilityActive && animal.ability == Ability.STOMP)
        for (h in hazards) {
            if (!h.solid) continue
            if (h.x > right || h.x + h.w < left) continue
            if (h.y > top || h.y + h.h < bottom) continue
            if (smasher) {
                smash(h)
            } else {
                stumble(h)
                if (state != RunState.RUNNING) return
            }
        }
    }

    private fun smash(h: Hazard) {
        h.smashed = true
        smashed++
        bonusScore += 10
        listener?.onSmash()
        puff(h.x + h.w / 2f, h.y + min(h.h, 10f) / 2f, biome.nearTrees, 10)
    }

    private fun stumble(h: Hazard) {
        h.active = false
        puff(h.x, h.y + min(h.h, 10f) / 2f, 0xFFFFFFFF.toInt(), 8)
        if (chase >= 0.45f) {
            goDown(DeathCause.CAUGHT)
        } else {
            chase = 1f
            speedFactor = 0.55f
            stumbleFlash = 1f
            listener?.onStumble()
        }
    }

    private fun goDown(c: DeathCause) {
        state = RunState.DOWN
        cause = c
        if (c == DeathCause.CAUGHT) chase = 1f
        listener?.onDown(c)
    }

    private fun collectPickups(dt: Float) {
        val longNeck = abilityActive && animal.ability == Ability.LONG_NECK
        val magnetRadius = when {
            abilityActive && animal.ability == Ability.HONEY_MAGNET -> viewAhead
            magnetTime > 0f -> 30f
            longNeck -> 14f
            else -> 0f
        }
        val cx = distance
        val cy = y + height / 2f
        for (p in pickups) {
            if (p.collected) {
                p.collectTime += dt
                continue
            }
            val dx = p.x - cx
            val dy = p.y - cy
            if (magnetRadius > 0f && (p.kind == PickupKind.FOOD || p.kind == PickupKind.GOLDEN_FOOD || p.kind == PickupKind.GEM)) {
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < magnetRadius && dist > 0.01f) {
                    val pull = min(dist, 140f * dt)
                    p.x -= dx / dist * pull
                    p.y -= dy / dist * pull
                }
            }
            val hit = if (longNeck) abs(p.x - cx) < 4f
            else p.x > distance - Physics.PLAYER_W / 2f - 2f && p.x < distance + Physics.PLAYER_W / 2f + 2f &&
                p.y > y - 2f && p.y < y + height + 2f
            if (hit) collect(p)
        }
    }

    private fun collect(p: Pickup) {
        p.collected = true
        val coinMul = animal.coinMultiplier * (if (doublerTime > 0f) 2f else 1f) * (if (loadout.vipCoinBoost) 2f else 1f)
        when (p.kind) {
            PickupKind.FOOD -> {
                food++
                coins += max(1, coinMul.toInt())
                bonusScore += 5
                abilityCharge = min(1f, abilityCharge + 0.025f)
            }
            PickupKind.GOLDEN_FOOD -> {
                food++
                val c = (10 * coinMul).toInt()
                coins += c
                bonusScore += 50
                abilityCharge = min(1f, abilityCharge + 0.15f)
                texts += FloatingText(p.x, p.y + 3f, "+$c", 0xFFFFD700.toInt())
            }
            PickupKind.GEM -> {
                gems++
                bonusScore += 100
                texts += FloatingText(p.x, p.y + 3f, "+1 GEM", 0xFF40C4FF.toInt())
            }
            PickupKind.MAGNET -> {
                powerUps++
                magnetTime = 8f + loadout.magnetLevel * 2f
                texts += FloatingText(p.x, p.y + 3f, "MAGNET", 0xFFE53935.toInt())
            }
            PickupKind.SHIELD -> {
                powerUps++
                shieldTime = 6f + loadout.shieldLevel * 1.5f
                texts += FloatingText(p.x, p.y + 3f, "SHIELD", 0xFF42A5F5.toInt())
            }
            PickupKind.DOUBLER -> {
                powerUps++
                doublerTime = 10f + loadout.doublerLevel * 2f
                texts += FloatingText(p.x, p.y + 3f, "2X COINS", 0xFFFFC107.toInt())
            }
            PickupKind.MYSTERY -> {
                powerUps++
                val reward = 50 + ((distance.toInt() * 7) % 200)
                coins += reward
                abilityCharge = 1f
                texts += FloatingText(p.x, p.y + 3f, "+$reward & ABILITY!", 0xFFAB47BC.toInt())
            }
        }
        listener?.onCollect(p.kind)
        val color = when (p.kind) {
            PickupKind.FOOD, PickupKind.GOLDEN_FOOD -> animal.food.color
            PickupKind.GEM -> 0xFF40C4FF.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
        puff(p.x, p.y, color, if (p.kind == PickupKind.FOOD) 3 else 10)
    }

    private fun cleanup() {
        val behind = distance - 60f
        hazards.removeAll { it.x + it.w < behind || (it.smashed && it.smashTime > 1f) }
        pickups.removeAll { it.x < behind || (it.collected && it.collectTime > 0.4f) }
    }

    // ---------------------------------------------------------------- effects

    private fun updateParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) {
                it.remove()
                continue
            }
            p.vy += p.gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        val tt = texts.iterator()
        while (tt.hasNext()) {
            val t = tt.next()
            t.life -= dt
            t.y += dt * 8f
            if (t.life <= 0f) tt.remove()
        }
    }

    private var puffSeed = 1
    private fun puff(x: Float, y: Float, color: Int, count: Int) {
        if (particles.size > 220) return
        for (i in 0 until count) {
            puffSeed = puffSeed * 1103515245 + 12345
            val a = ((puffSeed ushr 8) and 0xFFFF) / 65535f
            puffSeed = puffSeed * 1103515245 + 12345
            val b = ((puffSeed ushr 8) and 0xFFFF) / 65535f
            particles += Particle(
                x, y, (a - 0.5f) * 50f + speed * 0.3f, 20f + b * 40f,
                0.5f + b * 0.4f, 0.9f, color, 0.6f + a * 0.9f,
            )
        }
    }

    // ---------------------------------------------------------------- revive / results

    /** Second chance after a rewarded ad or gems: clears the danger and grants a short shield. */
    fun revive() {
        if (state != RunState.DOWN) return
        revives++
        hazards.removeAll { it.x < distance + 70f && it.x + it.w > distance - 10f }
        y = max(0f, y)
        vy = 0f
        onGround = true
        jumpsUsed = 0
        chase = 0f
        speedFactor = 0.7f
        reviveShield = 3f
        cause = null
        state = RunState.COUNTDOWN
        countdown = 1.5f
    }

    fun finish() {
        if (state != RunState.FINISHED) state = RunState.FINISHED
    }

    fun result(): RunResult = RunResult(
        animalId = animal.id,
        score = score,
        distanceMeters = meters,
        food = food,
        coins = coins,
        gems = gems,
        jumps = jumps,
        slides = slides,
        abilitiesUsed = abilitiesUsed,
        powerUps = powerUps,
        smashed = smashed,
        revives = revives,
        durationSec = runTime,
        cause = cause,
    )

    companion object {
        val INVINCIBLE_ABILITIES = setOf(Ability.ROLL_SHIELD, Ability.DASH, Ability.MOON_JUMP)
    }
}
