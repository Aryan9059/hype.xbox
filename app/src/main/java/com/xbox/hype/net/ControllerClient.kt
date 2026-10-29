package com.xbox.hype.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class DiscoveredPc(val name: String, val host: String)

sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Searching : ConnectionStatus
    data class Connected(val host: String) : ConnectionStatus
}

/**
 * One UDP socket handles everything: discovery broadcasts, state packets out and
 * rumble packets in (the server replies to the source port of our state packets).
 */
class ControllerClient(
    private val scope: CoroutineScope,
    private val onRumble: (large: Int, small: Int) -> Unit,
) {
    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    val status: StateFlow<ConnectionStatus> = _status

    private val _discovered = MutableStateFlow<List<DiscoveredPc>>(emptyList())
    val discovered: StateFlow<List<DiscoveredPc>> = _discovered

    @Volatile private var state = PadState()
    private val kick = Channel<Unit>(Channel.CONFLATED)
    private var seq = 0

    private var socket: DatagramSocket? = null
    private var jobs = mutableListOf<Job>()

    /** Latest controller snapshot; sent immediately and then repeated as keep-alive. */
    fun update(newState: PadState) {
        state = newState
        kick.trySend(Unit)
    }

    fun startDiscovery() {
        stop()
        _discovered.value = emptyList()
        _status.value = ConnectionStatus.Searching
        val sock = open()
        jobs += scope.launch(Dispatchers.IO) {
            val probe = Protocol.encodeDiscover()
            val broadcast = InetAddress.getByName("255.255.255.255")
            while (isActive) {
                runCatching { sock.send(DatagramPacket(probe, probe.size, broadcast, Protocol.DISCOVERY_PORT)) }
                delay(1000)
            }
        }
        jobs += scope.launch(Dispatchers.IO) { receiveLoop(sock) }
    }

    fun connect(host: String) {
        stop()
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return
        val sock = open()
        _status.value = ConnectionStatus.Connected(host)
        jobs += scope.launch(Dispatchers.IO) { receiveLoop(sock) }
        jobs += scope.launch(Dispatchers.IO) {
            while (isActive) {
                withTimeoutOrNull(50) { kick.receive() }
                val bytes = Protocol.encodeState(seq++, state)
                runCatching { sock.send(DatagramPacket(bytes, bytes.size, address, Protocol.STATE_PORT)) }
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        socket?.close()
        socket = null
        _status.value = ConnectionStatus.Idle
    }

    private fun open(): DatagramSocket =
        DatagramSocket().apply {
            broadcast = true
            soTimeout = 500
            socket = this
        }

    private suspend fun receiveLoop(sock: DatagramSocket) {
        val buf = ByteArray(256)
        val packet = DatagramPacket(buf, buf.size)
        while (scope.isActive && !sock.isClosed) {
            try {
                sock.receive(packet)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: Exception) {
                break
            }
            Protocol.decodeRumble(buf, packet.length)?.let { (large, small) -> onRumble(large, small) }
            Protocol.decodeDiscoverReply(buf, packet.length)?.let { name ->
                val host = packet.address.hostAddress ?: return@let
                _discovered.update { list ->
                    if (list.any { it.host == host }) list else list + DiscoveredPc(name, host)
                }
            }
        }
    }
}
