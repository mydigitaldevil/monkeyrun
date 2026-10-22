package com.junglerun.game.net

import org.json.JSONObject

/** Low-level connection to the other player. Implementations call [Listener] from background threads. */
interface Transport {
    interface Listener {
        /** Room is open and we know its code (host), or we joined one (guest). */
        fun onRoom(code: String, isHost: Boolean) {}
        fun onPeerConnected() {}
        fun onMessage(msg: JSONObject)
        fun onPeerLeft() {}
        fun onError(message: String)
    }

    fun send(msg: JSONObject)
    fun close()
}
