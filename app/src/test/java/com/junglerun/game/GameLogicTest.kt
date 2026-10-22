package com.junglerun.game

import com.junglerun.game.core.Animals
import com.junglerun.game.core.Rng
import com.junglerun.game.data.Mission
import com.junglerun.game.data.MissionLogic
import com.junglerun.game.data.MissionType
import com.junglerun.game.data.Profile
import com.junglerun.game.game.GameWorld
import com.junglerun.game.game.HazardKind
import com.junglerun.game.game.Loadout
import com.junglerun.game.game.RunResult
import com.junglerun.game.game.RunState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLogicTest {

    @Test
    fun sameSeedProducesSameCourse() {
        // Multiplayer fairness: both racers must see identical hazards.
        val a = GameWorld(Animals.get("monkey"), 42, Loadout(), countdown = 0f)
        val b = GameWorld(Animals.get("lion"), 42, Loadout(), countdown = 0f)
        // Different animals → different biomes, so compare two runners of the same animal instead.
        val c = GameWorld(Animals.get("monkey"), 42, Loadout(), countdown = 0f)
        assertEquals(a.hazards.map { it.kind to it.x }, c.hazards.map { it.kind to it.x })
        assertTrue(b.hazards.isNotEmpty())
    }

    @Test
    fun rngIsDeterministicAndInRange() {
        val r1 = Rng(7)
        val r2 = Rng(7)
        repeat(1000) {
            val f = r1.nextFloat()
            assertEquals(f, r2.nextFloat(), 0f)
            assertTrue(f >= 0f && f < 1f)
        }
    }

    @Test
    fun hittingAnObstacleTwiceGetsYouCaught() {
        val w = GameWorld(Animals.get("monkey"), 1, Loadout(), countdown = 0f)
        w.update(0.01f)
        // Run straight without jumping until we go down.
        var t = 0f
        while (w.state == RunState.RUNNING && t < 120f) {
            w.update(1f / 120f)
            t += 1f / 120f
        }
        assertEquals(RunState.DOWN, w.state)
        assertTrue(w.cause != null)
    }

    @Test
    fun reviveClearsDangerAndResumes() {
        val w = GameWorld(Animals.get("monkey"), 3, Loadout(), countdown = 0f)
        while (w.state != RunState.DOWN) w.update(1f / 120f)
        w.revive()
        assertEquals(0f, w.chase, 0f)
        assertTrue(w.hazards.none { it.x < w.distance + 60f && it.x + it.w > w.distance && it.kind != HazardKind.PIT })
        repeat(300) { w.update(1f / 120f) }
        assertEquals(RunState.RUNNING, w.state)
    }

    @Test
    fun missionsProgress() {
        val run = RunResult("monkey", 900, 350, 45, 45, 0, 12, 3, 1, 2, 0, 0, 40f, null)
        val perRun = MissionLogic.apply(Mission(MissionType.RUN_DISTANCE, 300), run)
        assertTrue(perRun.done)
        val total = MissionLogic.apply(Mission(MissionType.COLLECT_FOOD, 100, progress = 60), run)
        assertEquals(100, total.progress)
        val playAs = MissionLogic.apply(Mission(MissionType.PLAY_AS, 3, animalId = "lion"), run)
        assertEquals(0, playAs.progress)
    }

    @Test
    fun profileJsonRoundTrip() {
        val p = Profile(coins = 1234, gems = 9, owned = setOf("monkey", "lion"), selected = "lion",
            missions = MissionLogic.generateSet(2, setOf("monkey", "lion")), removeAds = true)
        val back = Profile.fromJson(JSONObject(p.toJson().toString()))
        assertEquals(p, back)
    }
}
