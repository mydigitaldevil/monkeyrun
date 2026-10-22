package com.junglerun.game.game

/** Plays the game by itself — used for the animated menu background (attract mode). */
object AutoPilot {
    fun drive(w: GameWorld) {
        if (w.state != RunState.RUNNING) return
        var next: Hazard? = null
        for (h in w.hazards) {
            if (!h.active || h.smashed || h.x + h.w < w.distance - 3f) continue
            if (next == null || h.x < next.x) next = h
        }
        val h = next ?: return
        val dx = h.x - w.distance
        when (h.kind) {
            HazardKind.OVERHEAD, HazardKind.FLYER -> if (dx < w.speed * 0.2f + 6f && w.onGround) w.slide()
            HazardKind.PIT -> if (dx < 4f && w.onGround) w.jump()
            HazardKind.MOVING -> if (dx < w.speed * 0.32f + 6f && w.onGround) w.jump()
            else -> if (dx < w.speed * 0.2f + 4f && w.onGround) w.jump()
        }
        if (w.abilityReady) w.useAbility()
    }
}
