package com.xbox.hype.net

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wire protocol shared with the Windows server (windows/HypeXboxServer/Protocol.cs).
 * All multi-byte values are little-endian.
 */
object Protocol {
    const val STATE_PORT = 39500
    const val DISCOVERY_PORT = 39501

    private const val MAGIC_0: Byte = 'H'.code.toByte()
    private const val MAGIC_1: Byte = 'X'.code.toByte()
    const val VERSION: Byte = 1

    const val TYPE_STATE: Byte = 1
    const val TYPE_RUMBLE: Byte = 2
    const val TYPE_DISCOVER: Byte = 3
    const val TYPE_DISCOVER_REPLY: Byte = 4

    const val STATE_SIZE = 20

    fun encodeState(seq: Int, s: PadState): ByteArray =
        ByteBuffer.allocate(STATE_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(MAGIC_0); put(MAGIC_1); put(TYPE_STATE); put(VERSION)
            putInt(seq)
            putShort(s.buttons.toShort())
            put(s.leftTrigger.toByte()); put(s.rightTrigger.toByte())
            putShort(s.lx); putShort(s.ly); putShort(s.rx); putShort(s.ry)
        }.array()

    fun encodeDiscover(): ByteArray = byteArrayOf(MAGIC_0, MAGIC_1, TYPE_DISCOVER, VERSION)

    fun isPacket(data: ByteArray, length: Int, type: Byte): Boolean =
        length >= 3 && data[0] == MAGIC_0 && data[1] == MAGIC_1 && data[2] == type

    /** Rumble: magic, type, large motor (0-255), small motor (0-255). */
    fun decodeRumble(data: ByteArray, length: Int): Pair<Int, Int>? =
        if (isPacket(data, length, TYPE_RUMBLE) && length >= 5)
            (data[3].toInt() and 0xFF) to (data[4].toInt() and 0xFF)
        else null

    /** Discover reply: magic, type, version, UTF-8 machine name. */
    fun decodeDiscoverReply(data: ByteArray, length: Int): String? =
        if (isPacket(data, length, TYPE_DISCOVER_REPLY) && length >= 4)
            String(data, 4, length - 4, Charsets.UTF_8)
        else null
}

/** XInput button bits. */
object Buttons {
    const val DPAD_UP = 0x0001
    const val DPAD_DOWN = 0x0002
    const val DPAD_LEFT = 0x0004
    const val DPAD_RIGHT = 0x0008
    const val START = 0x0010
    const val BACK = 0x0020
    const val LEFT_THUMB = 0x0040
    const val RIGHT_THUMB = 0x0080
    const val LEFT_SHOULDER = 0x0100
    const val RIGHT_SHOULDER = 0x0200
    const val GUIDE = 0x0400
    const val A = 0x1000
    const val B = 0x2000
    const val X = 0x4000
    const val Y = 0x8000
}

/** Full gamepad snapshot. Sticks are -32768..32767 (Y up = positive), triggers 0..255. */
data class PadState(
    val buttons: Int = 0,
    val leftTrigger: Int = 0,
    val rightTrigger: Int = 0,
    val lx: Short = 0,
    val ly: Short = 0,
    val rx: Short = 0,
    val ry: Short = 0,
)
