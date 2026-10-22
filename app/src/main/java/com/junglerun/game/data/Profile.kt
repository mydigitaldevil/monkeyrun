package com.junglerun.game.data

import com.junglerun.game.core.Animals
import org.json.JSONArray
import org.json.JSONObject

enum class Upgrade(val displayName: String, val description: String) {
    MAGNET("Magnet", "Magnet lasts longer"),
    SHIELD("Shield", "Shield lasts longer"),
    DOUBLER("2x Coins", "Coin doubler lasts longer"),
    ABILITY("Ability Power", "Animal ability lasts longer"),
}

data class Settings(
    val sound: Boolean = true,
    val music: Boolean = true,
    val haptics: Boolean = true,
    val playerName: String = "Player",
)

data class Mission(
    val type: MissionType,
    val target: Int,
    val progress: Int = 0,
    val animalId: String? = null,
) {
    val done: Boolean get() = progress >= target
}

data class DailyChallenge(val epochDay: Long, val animalId: String, val target: Int, val progress: Int = 0, val claimed: Boolean = false) {
    val done: Boolean get() = progress >= target
}

data class Profile(
    val coins: Long = 500,
    val gems: Int = 5,
    val owned: Set<String> = setOf(Animals.DEFAULT_ID),
    val selected: String = Animals.DEFAULT_ID,
    val upgrades: Map<Upgrade, Int> = emptyMap(),
    val bestScore: Long = 0,
    val bestDistance: Int = 0,
    val totalRuns: Int = 0,
    val totalFood: Long = 0,
    val totalDistance: Long = 0,
    val totalCoinsEarned: Long = 0,
    val wins: Int = 0,
    val scoreMultiplier: Int = 1,
    val missions: List<Mission> = emptyList(),
    val missionSet: Int = 0,
    val daily: DailyChallenge? = null,
    val lastRewardDay: Long = -1,
    val rewardStreak: Int = 0,
    val removeAds: Boolean = false,
    val vipUntil: Long = 0,
    val grantedTokens: Set<String> = emptySet(),
    /** Non-consumable / subscription product ids the player owns. */
    val ownedProducts: Set<String> = emptySet(),
    val claimedAchievements: Set<String> = emptySet(),
    val highScores: List<Long> = emptyList(),
    val settings: Settings = Settings(),
    val firstLaunch: Boolean = true,
) {
    fun level(u: Upgrade): Int = upgrades[u] ?: 0
    fun isVip(now: Long = System.currentTimeMillis()): Boolean = vipUntil > now
    fun adsRemoved(now: Long = System.currentTimeMillis()): Boolean = removeAds || isVip(now)

    fun toJson(): JSONObject = JSONObject().apply {
        put("coins", coins)
        put("gems", gems)
        put("owned", JSONArray(owned.toList()))
        put("selected", selected)
        put("upgrades", JSONObject().apply { upgrades.forEach { (k, v) -> put(k.name, v) } })
        put("bestScore", bestScore)
        put("bestDistance", bestDistance)
        put("totalRuns", totalRuns)
        put("totalFood", totalFood)
        put("totalDistance", totalDistance)
        put("totalCoinsEarned", totalCoinsEarned)
        put("wins", wins)
        put("scoreMultiplier", scoreMultiplier)
        put("missions", JSONArray().apply {
            missions.forEach { m ->
                put(JSONObject().put("type", m.type.name).put("target", m.target).put("progress", m.progress).put("animal", m.animalId ?: ""))
            }
        })
        put("missionSet", missionSet)
        daily?.let {
            put("daily", JSONObject().put("day", it.epochDay).put("animal", it.animalId).put("target", it.target)
                .put("progress", it.progress).put("claimed", it.claimed))
        }
        put("lastRewardDay", lastRewardDay)
        put("rewardStreak", rewardStreak)
        put("removeAds", removeAds)
        put("vipUntil", vipUntil)
        put("grantedTokens", JSONArray(grantedTokens.toList()))
        put("ownedProducts", JSONArray(ownedProducts.toList()))
        put("claimedAchievements", JSONArray(claimedAchievements.toList()))
        put("highScores", JSONArray(highScores))
        put("settings", JSONObject().put("sound", settings.sound).put("music", settings.music)
            .put("haptics", settings.haptics).put("name", settings.playerName))
        put("firstLaunch", firstLaunch)
    }

    companion object {
        fun fromJson(o: JSONObject): Profile {
            fun strings(key: String): Set<String> {
                val a = o.optJSONArray(key) ?: return emptySet()
                return (0 until a.length()).map { a.getString(it) }.toSet()
            }
            val ups = HashMap<Upgrade, Int>()
            o.optJSONObject("upgrades")?.let { u ->
                Upgrade.entries.forEach { if (u.has(it.name)) ups[it] = u.getInt(it.name) }
            }
            val missions = ArrayList<Mission>()
            o.optJSONArray("missions")?.let { a ->
                for (i in 0 until a.length()) {
                    val m = a.getJSONObject(i)
                    val type = runCatching { MissionType.valueOf(m.getString("type")) }.getOrNull() ?: continue
                    missions += Mission(type, m.getInt("target"), m.optInt("progress"), m.optString("animal").ifEmpty { null })
                }
            }
            val daily = o.optJSONObject("daily")?.let {
                DailyChallenge(it.getLong("day"), it.getString("animal"), it.getInt("target"), it.optInt("progress"), it.optBoolean("claimed"))
            }
            val hs = o.optJSONArray("highScores")?.let { a -> (0 until a.length()).map { a.getLong(it) } } ?: emptyList()
            val s = o.optJSONObject("settings")
            val owned = strings("owned").ifEmpty { setOf(Animals.DEFAULT_ID) }
            return Profile(
                coins = o.optLong("coins", 500),
                gems = o.optInt("gems", 5),
                owned = owned,
                selected = o.optString("selected", Animals.DEFAULT_ID).takeIf { it in owned } ?: Animals.DEFAULT_ID,
                upgrades = ups,
                bestScore = o.optLong("bestScore"),
                bestDistance = o.optInt("bestDistance"),
                totalRuns = o.optInt("totalRuns"),
                totalFood = o.optLong("totalFood"),
                totalDistance = o.optLong("totalDistance"),
                totalCoinsEarned = o.optLong("totalCoinsEarned"),
                wins = o.optInt("wins"),
                scoreMultiplier = o.optInt("scoreMultiplier", 1),
                missions = missions,
                missionSet = o.optInt("missionSet"),
                daily = daily,
                lastRewardDay = o.optLong("lastRewardDay", -1),
                rewardStreak = o.optInt("rewardStreak"),
                removeAds = o.optBoolean("removeAds"),
                vipUntil = o.optLong("vipUntil"),
                grantedTokens = strings("grantedTokens"),
                ownedProducts = strings("ownedProducts"),
                claimedAchievements = strings("claimedAchievements"),
                highScores = hs,
                settings = Settings(
                    sound = s?.optBoolean("sound", true) ?: true,
                    music = s?.optBoolean("music", true) ?: true,
                    haptics = s?.optBoolean("haptics", true) ?: true,
                    playerName = s?.optString("name", "Player") ?: "Player",
                ),
                firstLaunch = o.optBoolean("firstLaunch", true),
            )
        }
    }
}
