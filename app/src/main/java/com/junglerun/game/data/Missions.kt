package com.junglerun.game.data

import com.junglerun.game.core.Animals
import com.junglerun.game.core.Rng
import com.junglerun.game.game.RunResult

enum class MissionType(val template: String, val perRun: Boolean) {
    COLLECT_FOOD("Collect %d treats in total", false),
    FOOD_ONE_RUN("Collect %d treats in one run", true),
    RUN_DISTANCE("Run %d m in one run", true),
    TOTAL_DISTANCE("Run %d m in total", false),
    JUMPS("Jump %d times", false),
    SLIDES("Slide %d times", false),
    USE_ABILITY("Use your animal ability %d times", false),
    POWERUPS("Pick up %d power-ups", false),
    SCORE("Score %d in one run", true),
    SMASH("Smash %d obstacles", false),
    PLAY_AS("Play %d runs as %s", false),
}

object MissionLogic {
    const val REWARD_PER_MISSION = 250
    const val MAX_MULTIPLIER = 30

    fun description(m: Mission): String =
        if (m.type == MissionType.PLAY_AS) {
            MissionType.PLAY_AS.template.format(m.target, Animals.get(m.animalId ?: Animals.DEFAULT_ID).displayName)
        } else {
            m.type.template.format(m.target)
        }

    /** Deterministic mission set for a given set number, getting harder over time. */
    fun generateSet(set: Int, owned: Set<String>): List<Mission> {
        val rng = Rng(set * 7919L + 17)
        val scale = 1f + set * 0.35f
        val types = MissionType.entries.toMutableList()
        val picked = ArrayList<Mission>()
        while (picked.size < 3) {
            val t = types.removeAt(rng.nextInt(types.size))
            val target = when (t) {
                MissionType.COLLECT_FOOD -> 100 * scale
                MissionType.FOOD_ONE_RUN -> 40 * scale
                MissionType.RUN_DISTANCE -> 300 * scale
                MissionType.TOTAL_DISTANCE -> 1500 * scale
                MissionType.JUMPS -> 40 * scale
                MissionType.SLIDES -> 20 * scale
                MissionType.USE_ABILITY -> 2 * scale
                MissionType.POWERUPS -> 4 * scale
                MissionType.SCORE -> 800 * scale
                MissionType.SMASH -> 5 * scale
                MissionType.PLAY_AS -> 3f
            }.toInt().coerceAtLeast(1)
            val animal = if (t == MissionType.PLAY_AS) owned.toList().sorted()[rng.nextInt(owned.size)] else null
            picked += Mission(t, target, 0, animal)
        }
        return picked
    }

    fun apply(m: Mission, r: RunResult): Mission {
        if (m.done) return m
        val inc: Int = when (m.type) {
            MissionType.COLLECT_FOOD -> r.food
            MissionType.FOOD_ONE_RUN -> r.food
            MissionType.RUN_DISTANCE -> r.distanceMeters
            MissionType.TOTAL_DISTANCE -> r.distanceMeters
            MissionType.JUMPS -> r.jumps
            MissionType.SLIDES -> r.slides
            MissionType.USE_ABILITY -> r.abilitiesUsed
            MissionType.POWERUPS -> r.powerUps
            MissionType.SCORE -> r.score.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            MissionType.SMASH -> r.smashed
            MissionType.PLAY_AS -> if (r.animalId == m.animalId) 1 else 0
        }
        val progress = if (m.type.perRun) maxOf(m.progress, inc) else m.progress + inc
        return m.copy(progress = progress.coerceAtMost(m.target))
    }

    /** Today's challenge: run as a specific animal and collect its food. */
    fun dailyFor(epochDay: Long): DailyChallenge {
        val rng = Rng(epochDay * 31 + 5)
        val pool = Animals.all.filter { it.productId == null }
        val animal = pool[rng.nextInt(pool.size)]
        return DailyChallenge(epochDay, animal.id, 60 + rng.nextInt(5) * 20)
    }
}

/** Long-term goals with gem rewards. */
data class Achievement(val id: String, val title: String, val target: Long, val gems: Int, val stat: (Profile) -> Long)

object Achievements {
    val all = listOf(
        Achievement("first_run", "First steps — finish a run", 1, 2) { it.totalRuns.toLong() },
        Achievement("runs_50", "Marathon — play 50 runs", 50, 10) { it.totalRuns.toLong() },
        Achievement("food_1000", "Hungry! — collect 1,000 treats", 1000, 10) { it.totalFood },
        Achievement("food_10000", "Feast — collect 10,000 treats", 10000, 40) { it.totalFood },
        Achievement("dist_1000", "Sprinter — reach 1,000 m in one run", 1000, 10) { it.bestDistance.toLong() },
        Achievement("dist_5000", "Legend — reach 5,000 m in one run", 5000, 50) { it.bestDistance.toLong() },
        Achievement("zoo_3", "Zookeeper — own 3 animals", 3, 10) { it.owned.size.toLong() },
        Achievement("zoo_all", "Whole jungle — own every animal", 9, 100) { it.owned.size.toLong() },
        Achievement("wins_5", "Champion — win 5 multiplayer races", 5, 20) { it.wins.toLong() },
    )
}

object DailyRewards {
    /** 7-day cycle: coins on most days, gems on days 3, 6 and 7. */
    data class Reward(val coins: Int, val gems: Int)

    val cycle = listOf(
        Reward(200, 0), Reward(300, 0), Reward(0, 5), Reward(500, 0),
        Reward(750, 0), Reward(0, 10), Reward(1500, 15),
    )
}

object Economy {
    fun upgradeCost(level: Int): Int = 400 * (1 shl level)
    const val MAX_UPGRADE = 5

    /** Gem cost for the n-th revive in a run (1, 2, 4, 8...). */
    fun reviveGemCost(revivesSoFar: Int): Int = 1 shl revivesSoFar.coerceAtMost(5)
}
