package com.junglerun.game.net

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.random.Random

/** A room advertised on the local Wi-Fi network. */
data class LanRoom(val code: String, val hostName: String, val address: String, val port: Int, val seenAt: Long)

const val LAN_BEACON_PORT = 47821
private const val BEACON_PREFIX = "JUNGLERUN1"

/** Line-delimited JSON over a TCP socket, shared by host and guest. */
private class SocketLink(private val socket: Socket, private val listener: Transport.Listener) {
    private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
    private val sendExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false

    fun startReading() {
        Thread({
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                while (!closed) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    try {
                        listener.onMessage(JSONObject(line))
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
            if (!closed) listener.onPeerLeft()
            close()
        }, "JungleRun-LanReader").start()
    }

    fun send(msg: JSONObject) {
        if (closed) return
        sendExecutor.execute {
            try {
                writer.write(msg.toString())
                writer.write("\n")
                writer.flush()
            } catch (_: Exception) {
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        sendExecutor.shutdown()
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }
}

/**
 * Hosts a room on the local network: TCP server for the game link, UDP broadcast beacon so
 * nearby phones can see the room, and a 4-digit room code to pick the right one.
 */
class LanHost(
    private val context: Context,
    private val hostName: String,
    private val listener: Transport.Listener,
) : Transport {
    val code: String = (1000 + Random.nextInt(9000)).toString()
    private var server: ServerSocket? = null
    private var link: SocketLink? = null
    @Volatile private var closed = false

    fun open() {
        Thread({
            try {
                val ss = ServerSocket(0)
                server = ss
                listener.onRoom(code, isHost = true)
                startBeacon(ss.localPort)
                val socket = ss.accept()
                socket.tcpNoDelay = true
                val l = SocketLink(socket, listener)
                link = l
                l.startReading()
                listener.onPeerConnected()
            } catch (e: Exception) {
                if (!closed) listener.onError("Could not host room: ${e.message}")
            }
        }, "JungleRun-LanHost").start()
    }

    private fun startBeacon(port: Int) {
        Thread({
            try {
                DatagramSocket().use { ds ->
                    ds.broadcast = true
                    val payload = "$BEACON_PREFIX|$code|${hostName.replace("|", " ")}|$port".toByteArray()
                    val targets = broadcastAddresses()
                    while (!closed && link == null) {
                        for (addr in targets) {
                            try {
                                ds.send(DatagramPacket(payload, payload.size, addr, LAN_BEACON_PORT))
                            } catch (_: Exception) {
                            }
                        }
                        Thread.sleep(1000)
                    }
                }
            } catch (_: Exception) {
            }
        }, "JungleRun-LanBeacon").start()
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val list = ArrayList<InetAddress>()
        try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (!ni.isUp || ni.isLoopback) return@forEach
                ni.interfaceAddresses.forEach { ia -> ia.broadcast?.let { list += it } }
            }
        } catch (_: Exception) {
        }
        list += InetAddress.getByName("255.255.255.255")
        return list.distinct()
    }

    override fun send(msg: JSONObject) {
        link?.send(msg)
    }

    override fun close() {
        closed = true
        link?.close()
        try {
            server?.close()
        } catch (_: Exception) {
        }
    }
}

/** Connects to a [LanRoom] found by [LanDiscovery] (or typed in manually as ip:port). */
class LanGuest(private val room: LanRoom, private val listener: Transport.Listener) : Transport {
    private var link: SocketLink? = null

    fun open() {
        Thread({
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(room.address, room.port), 5000)
                socket.tcpNoDelay = true
                val l = SocketLink(socket, listener)
                link = l
                l.startReading()
                listener.onRoom(room.code, isHost = false)
                listener.onPeerConnected()
            } catch (e: Exception) {
                listener.onError("Could not join room ${room.code}: ${e.message}")
            }
        }, "JungleRun-LanGuest").start()
    }

    override fun send(msg: JSONObject) {
        link?.send(msg)
    }

    override fun close() {
        link?.close()
    }
}

/** Listens for room beacons on the local network. */
class LanDiscovery(private val context: Context) {
    private val _rooms = MutableStateFlow<List<LanRoom>>(emptyList())
    val rooms: StateFlow<List<LanRoom>> = _rooms
    @Volatile private var running = false
    private var socket: DatagramSocket? = null
    private var lock: WifiManager.MulticastLock? = null

    fun start() {
        if (running) return
        running = true
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        lock = wifi?.createMulticastLock("jungle-run-discovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        Thread({
            try {
                val ds = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 1500
                    bind(InetSocketAddress(LAN_BEACON_PORT))
                }
                socket = ds
                val buf = ByteArray(512)
                while (running) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        ds.receive(pkt)
                        val parts = String(pkt.data, 0, pkt.length).split("|")
                        if (parts.size == 4 && parts[0] == BEACON_PREFIX) {
                            val room = LanRoom(parts[1], parts[2], pkt.address.hostAddress ?: continue,
                                parts[3].toIntOrNull() ?: continue, System.currentTimeMillis())
                            val now = System.currentTimeMillis()
                            _rooms.value = (_rooms.value.filter { it.code != room.code && now - it.seenAt < 4000 } + room)
                                .sortedBy { it.code }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        val now = System.currentTimeMillis()
                        _rooms.value = _rooms.value.filter { now - it.seenAt < 4000 }
                    }
                }
            } catch (_: Exception) {
            }
        }, "JungleRun-LanDiscovery").start()
    }

    fun stop() {
        running = false
        socket?.close()
        socket = null
        try {
            lock?.release()
        } catch (_: Exception) {
        }
        lock = null
    }
}
