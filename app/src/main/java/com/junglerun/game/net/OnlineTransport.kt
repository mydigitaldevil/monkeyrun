package com.junglerun.game.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class OnlineAction { CREATE, JOIN, QUICK_MATCH }

/**
 * Internet multiplayer through the small relay server in /server (Node.js + ws).
 * The server only pairs players into rooms and forwards messages; all gameplay is client-side.
 */
class OnlineTransport(
    private val serverUrl: String,
    private val action: OnlineAction,
    private val roomCode: String?,
    private val playerName: String,
    private val listener: Transport.Listener,
) : Transport {
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null
    @Volatile private var closedByUs = false

    fun open() {
        val req = Request.Builder().url(serverUrl).build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val hello = JSONObject().put("name", playerName).put("v", PROTOCOL_VERSION)
                when (action) {
                    OnlineAction.CREATE -> hello.put("t", "create")
                    OnlineAction.JOIN -> hello.put("t", "join").put("code", roomCode ?: "")
                    OnlineAction.QUICK_MATCH -> hello.put("t", "quick")
                }
                webSocket.send(hello.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    return
                }
                when (msg.optString("t")) {
                    "room" -> listener.onRoom(msg.getString("code"), msg.optString("role") == "host")
                    "peer_joined" -> listener.onPeerConnected()
                    "peer_left" -> listener.onPeerLeft()
                    "error" -> listener.onError(msg.optString("msg", "Server error"))
                    "relay" -> msg.optJSONObject("p")?.let { listener.onMessage(it) }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closedByUs) listener.onError("Connection failed: ${t.message ?: "network error"}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closedByUs) listener.onPeerLeft()
            }
        })
    }

    override fun send(msg: JSONObject) {
        socket?.send(JSONObject().put("t", "relay").put("p", msg).toString())
    }

    override fun close() {
        closedByUs = true
        socket?.close(1000, "bye")
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        const val PROTOCOL_VERSION = 1
    }
}
