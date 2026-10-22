package com.junglerun.game.data

import android.content.Context
import com.junglerun.game.core.AnimalSpec
import com.junglerun.game.core.Animals
import com.junglerun.game.core.UnlockType
import com.junglerun.game.game.Loadout
import com.junglerun.game.game.RunResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.LocalDate

/** What a finished run paid out, shown on the results screen. */
data class RunReward(
    val coins: Int,
    val gems: Int,
    val newBest: Boolean,
    val missionsCompleted: List<Mission>,
    val dailyCompleted: Boolean,
)

/**
 * Single source of truth for the player's save game. Local-first (SharedPreferences JSON);
 * cloud save can be layered on top later (e.g. Play Games Saved Games).
 */
class PlayerRepository(context: Context) {
    private val prefs = context.getSharedPreferences("jungle_run_save", Context.MODE_PRIVATE)
    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<Profile> = _profile.asStateFlow()
    val current: Profile get() = _profile.value

    init {
        refreshDailyState()
    }

    private fun load(): Profile {
        val raw = prefs.getString(KEY, null) ?: return Profile()
        return try {
            Profile.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            Profile()
        }
    }

    @Synchronized
    fun update(transform: (Profile) -> Profile): Profile {
        val next = transform(_profile.value)
        _profile.value = next
        prefs.edit().putString(KEY, next.toJson().toString()).apply()
        return next
    }

    fun today(): Long = LocalDate.now().toEpochDay()

    fun refreshDailyState() = update { p ->
        var next = p
        if (next.missions.isEmpty()) next = next.copy(missions = MissionLogic.generateSet(next.missionSet, next.owned))
        val day = today()
        if (next.daily?.epochDay != day) next = next.copy(daily = MissionLogic.dailyFor(day))
        next
    }

    fun loadoutFor(p: Profile = current): Loadout = Loadout(
        magnetLevel = p.level(Upgrade.MAGNET),
        shieldLevel = p.level(Upgrade.SHIELD),
        doublerLevel = p.level(Upgrade.DOUBLER),
        abilityLevel = p.level(Upgrade.ABILITY),
        scoreMultiplier = p.scoreMultiplier,
        vipCoinBoost = p.isVip(),
    )

    // ------------------------------------------------------------------ runs

    fun applyRun(r: RunResult, wonRace: Boolean = false): RunReward {
        var reward: RunReward? = null
        update { p ->
            val newBest = r.score > p.bestScore
            val missions = p.missions.map { MissionLogic.apply(it, r) }
            val justDone = missions.filterIndexed { i, m -> m.done && !p.missions[i].done }
            var daily = p.daily
            var dailyDone = false
            if (daily != null && !daily.done && r.animalId == daily.animalId) {
                daily = daily.copy(progress = (daily.progress + r.food).coerceAtMost(daily.target))
                dailyDone = daily.done
            }
            val missionCoins = justDone.size * MissionLogic.REWARD_PER_MISSION
            reward = RunReward(r.coins + missionCoins, r.gems, newBest, justDone, dailyDone)
            p.copy(
                coins = p.coins + r.coins + missionCoins,
                gems = p.gems + r.gems,
                bestScore = maxOf(p.bestScore, r.score),
                bestDistance = maxOf(p.bestDistance, r.distanceMeters),
                totalRuns = p.totalRuns + 1,
                totalFood = p.totalFood + r.food,
                totalDistance = p.totalDistance + r.distanceMeters,
                totalCoinsEarned = p.totalCoinsEarned + r.coins,
                wins = p.wins + if (wonRace) 1 else 0,
                missions = missions,
                daily = daily,
                highScores = (p.highScores + r.score).sortedDescending().take(10),
            )
        }
        return reward!!
    }

    /** When all three missions are done the player levels up their score multiplier and gets a new set. */
    fun completeMissionSet(): Boolean {
        var ok = false
        update { p ->
            if (p.missions.isEmpty() || !p.missions.all { it.done }) return@update p
            ok = true
            val set = p.missionSet + 1
            p.copy(
                missionSet = set,
                scoreMultiplier = (p.scoreMultiplier + 1).coerceAtMost(MissionLogic.MAX_MULTIPLIER),
                missions = MissionLogic.generateSet(set, p.owned),
                gems = p.gems + 3,
            )
        }
        return ok
    }

    /** Skip a mission for gems — a classic soft monetisation hook. */
    fun skipMission(index: Int, gemCost: Int = 5): Boolean {
        var ok = false
        update { p ->
            if (p.gems < gemCost || index !in p.missions.indices) return@update p
            ok = true
            val list = p.missions.toMutableList()
            list[index] = list[index].copy(progress = list[index].target)
            p.copy(gems = p.gems - gemCost, missions = list)
        }
        return ok
    }

    fun claimDailyChallenge(): Boolean {
        var ok = false
        update { p ->
            val d = p.daily ?: return@update p
            if (!d.done || d.claimed) return@update p
            ok = true
            p.copy(daily = d.copy(claimed = true), coins = p.coins + 500, gems = p.gems + 5)
        }
        return ok
    }

    fun canClaimDailyReward(p: Profile = current): Boolean = p.lastRewardDay != today()

    /** Returns the reward granted (doubled if the player watched an ad), or null if already claimed today. */
    fun claimDailyReward(doubled: Boolean): DailyRewards.Reward? {
        var granted: DailyRewards.Reward? = null
        update { p ->
            val day = today()
            if (p.lastRewardDay == day) return@update p
            val streak = if (p.lastRewardDay == day - 1) p.rewardStreak + 1 else 1
            val base = DailyRewards.cycle[(streak - 1) % DailyRewards.cycle.size]
            val mul = if (doubled) 2 else 1
            val vipGems = if (p.isVip()) 10 else 0
            granted = DailyRewards.Reward(base.coins * mul, base.gems * mul + vipGems)
            p.copy(
                lastRewardDay = day,
                rewardStreak = streak,
                coins = p.coins + base.coins * mul,
                gems = p.gems + base.gems * mul + vipGems,
            )
        }
        return granted
    }

    fun claimAchievement(a: Achievement): Boolean {
        var ok = false
        update { p ->
            if (a.id in p.claimedAchievements || a.stat(p) < a.target) return@update p
            ok = true
            p.copy(claimedAchievements = p.claimedAchievements + a.id, gems = p.gems + a.gems)
        }
        return ok
    }

    // ------------------------------------------------------------------ shop

    fun buyAnimal(spec: AnimalSpec): Boolean {
        var ok = false
        update { p ->
            if (spec.id in p.owned) return@update p
            when (spec.unlockType) {
                UnlockType.COINS -> if (p.coins >= spec.price) {
                    ok = true
                    p.copy(coins = p.coins - spec.price, owned = p.owned + spec.id, selected = spec.id)
                } else p
                UnlockType.GEMS -> if (p.gems >= spec.price) {
                    ok = true
                    p.copy(gems = p.gems - spec.price, owned = p.owned + spec.id, selected = spec.id)
                } else p
                else -> p
            }
        }
        return ok
    }

    fun select(id: String) = update { p -> if (id in p.owned) p.copy(selected = id) else p }

    fun buyUpgrade(u: Upgrade): Boolean {
        var ok = false
        update { p ->
            val lvl = p.level(u)
            val cost = Economy.upgradeCost(lvl)
            if (lvl >= Economy.MAX_UPGRADE || p.coins < cost) return@update p
            ok = true
            p.copy(coins = p.coins - cost, upgrades = p.upgrades + (u to lvl + 1))
        }
        return ok
    }

    fun spendGems(n: Int): Boolean {
        var ok = false
        update { p -> if (p.gems >= n) { ok = true; p.copy(gems = p.gems - n) } else p }
        return ok
    }

    fun addCoins(n: Int) = update { it.copy(coins = it.coins + n) }
    fun addGems(n: Int) = update { it.copy(gems = it.gems + n) }

    fun selectedAnimal(): AnimalSpec = Animals.get(current.selected)

    fun updateSettings(transform: (Settings) -> Settings) = update { it.copy(settings = transform(it.settings)) }

    fun markLaunched() = update { if (it.firstLaunch) it.copy(firstLaunch = false) else it }

    companion object {
        private const val KEY = "profile_v1"
    }
}
