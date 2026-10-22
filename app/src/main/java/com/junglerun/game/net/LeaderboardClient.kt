package com.junglerun.game.net

import com.junglerun.game.data.RunRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BoardEntry(val rank: Int, val name: String, val score: Long, val animal: String, val league: String, val me: Boolean)

data class Board(
    val scope: String,
    val week: String,
    val total: Int,
    val entries: List<BoardEntry>,
    val myRank: Int?,
    val myScore: Long?,
    val myLeague: String?,
)

data class SubmitResult(val ok: Boolean, val league: String?, val rank: Int?, val weekBest: Long?)

/** REST client for the score/league API served by the relay server (see server/leaderboard.js). */
class LeaderboardClient(wsUrl: String) {
    private val base = wsUrl.trimEnd('/').replaceFirst("wss://", "https://").replaceFirst("ws://", "http://")
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun submit(playerId: String, name: String, r: RunRecord): SubmitResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("playerId", playerId).put("name", name).put("animal", r.animalId).put("mode", r.mode)
            .put("score", r.score).put("meters", r.meters).put("food", r.food)
            .put("durationSec", r.durationSec.toDouble()).put("multiplier", r.multiplier).put("runId", r.id)
            .toString().toRequestBody("application/json".toMediaType())
        http.newCall(Request.Builder().url("$base/api/scores").post(body).build()).execute().use { res ->
            val text = res.body?.string() ?: "{}"
            val o = JSONObject(text)
            // 422 = rejected as implausible: drop it rather than retrying forever.
            if (res.code == 422) return@withContext SubmitResult(true, null, null, null)
            if (!res.isSuccessful) throw java.io.IOException("HTTP ${res.code}")
            SubmitResult(
                o.optBoolean("ok"),
                o.optString("league").ifEmpty { null },
                if (o.isNull("rank")) null else o.optInt("rank"),
                o.optLong("weekBest"),
            )
        }
    }

    suspend fun board(scope: String, playerId: String): Board = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$base/api/leaderboard?scope=$scope&limit=50&playerId=$playerId").build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw java.io.IOException("HTTP ${res.code}")
            val o = JSONObject(res.body?.string() ?: "{}")
            val arr = o.optJSONArray("entries")
            val list = (0 until (arr?.length() ?: 0)).map { i ->
                val e = arr!!.getJSONObject(i)
                BoardEntry(e.getInt("rank"), e.getString("name"), e.getLong("score"), e.optString("animal"), e.optString("league"), e.optBoolean("me"))
            }
            val me = o.optJSONObject("me")
            Board(
                o.optString("scope"), o.optString("week"), o.optInt("total"), list,
                me?.optInt("rank"), me?.optLong("score"), me?.optString("league"),
            )
        }
    }
}
