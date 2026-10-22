package com.junglerun.game.net

import com.junglerun.game.game.GameWorld
import com.junglerun.game.game.GhostState
import com.junglerun.game.game.RunResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import kotlin.random.Random

enum class RoomPhase { CONNECTING, WAITING_FOR_PEER, LOBBY, IN_GAME, RESULTS, ERROR, CLOSED }

data class PeerResult(val score: Long, val meters: Int, val food: Int)

data class RoomState(
    val phase: RoomPhase = RoomPhase.CONNECTING,
    val isHost: Boolean = false,
    val code: String? = null,
    val peerName: String? = null,
    val peerAnimal: String? = null,
    val peerReady: Boolean = false,
    val localReady: Boolean = false,
    val seed: Long = 0,
    val peerResult: PeerResult? = null,
    val error: String? = null,
    val online: Boolean = false,
)

/**
 * Game-level protocol on top of a [Transport]. Both players race on the same seed;
 * positions are streamed ~12x/s and drawn as a ghost. Highest score wins.
 *
 * Messages: hello, ready, start{seed}, state{d,y,s,a,sc}, down{sc,m,f}, emote{e}, leave
 */
class RoomSession(
    private val localName: String,
    initialAnimal: String,
    online: Boolean,
) : Transport.Listener {
    private val _state = MutableStateFlow(RoomState(online = online))
    val state: StateFlow<RoomState> = _state.asStateFlow()

    private val _ghost = MutableStateFlow<GhostState?>(null)
    val ghost: StateFlow<GhostState?> = _ghost.asStateFlow()

    private val _starts = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 1)
    /** Emits the match seed when the race begins (on both devices). */
    val starts: SharedFlow<Long> = _starts.asSharedFlow()

    private val _emotes = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val emotes: SharedFlow<String> = _emotes.asSharedFlow()

    private var transport: Transport? = null
    private var localAnimal = initialAnimal

    fun attach(t: Transport) {
        transport = t
    }

    // ------------------------------------------------------------------ Transport.Listener

    override fun onRoom(code: String, isHost: Boolean) {
        _state.update { it.copy(code = code, isHost = isHost, phase = if (it.phase == RoomPhase.CONNECTING) RoomPhase.WAITING_FOR_PEER else it.phase) }
    }

    override fun onPeerConnected() {
        _state.update { it.copy(phase = RoomPhase.LOBBY, error = null) }
        sendHello()
    }

    override fun onMessage(msg: JSONObject) {
        when (msg.optString("t")) {
            "hello" -> _state.update { it.copy(peerName = msg.optString("name", "Rival"), peerAnimal = msg.optString("animal")) }
            "ready" -> _state.update {
                it.copy(peerReady = msg.optBoolean("ready"), peerAnimal = msg.optString("animal", it.peerAnimal ?: ""))
            }
            "start" -> {
                // Sent as a string: JSON relays (e.g. Node) would round a 64-bit number to a double.
                val seed = msg.optString("seed").toLongOrNull() ?: return
                beginMatch(seed)
            }
            "state" -> {
                val s = _state.value
                _ghost.value = GhostState(
                    name = s.peerName ?: "Rival",
                    animalId = s.peerAnimal ?: "monkey",
                    distance = msg.optDouble("d").toFloat(),
                    y = msg.optDouble("y").toFloat(),
                    sliding = msg.optBoolean("s"),
                    alive = msg.optBoolean("a", true),
                    score = msg.optLong("sc"),
                )
            }
            "down" -> {
                val r = PeerResult(msg.optLong("sc"), msg.optInt("m"), msg.optInt("f"))
                _state.update { it.copy(peerResult = r) }
                _ghost.value = _ghost.value?.copy(alive = false, score = r.score)
            }
            "emote" -> _emotes.tryEmit(msg.optString("e"))
            "leave" -> onPeerLeft()
        }
    }

    override fun onPeerLeft() {
        _state.update {
            if (it.phase == RoomPhase.IN_GAME || it.phase == RoomPhase.RESULTS) {
                // Keep playing solo; the rival is treated as finished.
                it.copy(peerResult = it.peerResult ?: PeerResult(_ghost.value?.score ?: 0, 0, 0), error = "Rival left the race")
            } else {
                it.copy(phase = RoomPhase.ERROR, error = "Rival left the room", peerName = null, peerReady = false)
            }
        }
        _ghost.value = _ghost.value?.copy(alive = false)
    }

    override fun onError(message: String) {
        _state.update { it.copy(phase = RoomPhase.ERROR, error = message) }
    }

    // ------------------------------------------------------------------ actions

    private fun sendHello() {
        transport?.send(JSONObject().put("t", "hello").put("name", localName).put("animal", localAnimal))
    }

    fun setReady(ready: Boolean, animalId: String) {
        localAnimal = animalId
        _state.update { it.copy(localReady = ready) }
        transport?.send(JSONObject().put("t", "ready").put("ready", ready).put("animal", animalId))
    }

    /** Host only: when both players are ready, pick a seed and start the race everywhere. */
    fun startMatch(): Boolean {
        val s = _state.value
        if (!s.isHost || !s.peerReady || !s.localReady) return false
        val seed = Random.nextLong()
        transport?.send(JSONObject().put("t", "start").put("seed", seed.toString()))
        beginMatch(seed)
        return true
    }

    private fun beginMatch(seed: Long) {
        _ghost.value = null
        _state.update { it.copy(phase = RoomPhase.IN_GAME, seed = seed, peerResult = null, error = null, localReady = false, peerReady = false) }
        _starts.tryEmit(seed)
    }

    fun sendState(w: GameWorld, alive: Boolean) {
        transport?.send(
            JSONObject().put("t", "state")
                .put("d", w.distance.toDouble())
                .put("y", w.y.toDouble())
                .put("s", w.sliding)
                .put("a", alive)
                .put("sc", w.score),
        )
    }

    fun sendDown(r: RunResult) {
        transport?.send(JSONObject().put("t", "down").put("sc", r.score).put("m", r.distanceMeters).put("f", r.food))
    }

    fun sendEmote(e: String) {
        transport?.send(JSONObject().put("t", "emote").put("e", e))
    }

    /** Back to the lobby for a rematch. */
    fun backToLobby() {
        _starts.resetReplayCache()
        _ghost.value = null
        _state.update { it.copy(phase = RoomPhase.LOBBY, localReady = false, peerResult = null) }
    }

    fun markResults() = _state.update { it.copy(phase = RoomPhase.RESULTS) }

    fun leave() {
        transport?.send(JSONObject().put("t", "leave"))
        transport?.close()
        transport = null
        _state.update { it.copy(phase = RoomPhase.CLOSED) }
    }
}
