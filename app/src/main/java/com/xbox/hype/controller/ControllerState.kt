package com.xbox.hype.controller

import com.xbox.hype.net.PadState
import kotlin.math.roundToInt

/**
 * Mutable gamepad state fed by the touch controls. Every change pushes a fresh
 * snapshot to [onChange]; unchanged states are not re-sent.
 */
class ControllerState(private val onChange: (PadState) -> Unit) {
    private var state = PadState()
    private val lock = Any()

    fun setButton(mask: Int, down: Boolean) = mutate {
        copy(buttons = if (down) buttons or mask else buttons and mask.inv())
    }

    /** Sets a whole group of bits at once (D-pad directions). */
    fun setButtons(groupMask: Int, bits: Int) = mutate {
        copy(buttons = (buttons and groupMask.inv()) or (bits and groupMask))
    }

    /** Trigger value 0f..1f. */
    fun setLeftTrigger(v: Float) = mutate { copy(leftTrigger = (v.coerceIn(0f, 1f) * 255).roundToInt()) }
    fun setRightTrigger(v: Float) = mutate { copy(rightTrigger = (v.coerceIn(0f, 1f) * 255).roundToInt()) }

    /** Stick position in -1f..1f, y up positive. */
    fun setLeftStick(x: Float, y: Float) = mutate { copy(lx = axis(x), ly = axis(y)) }
    fun setRightStick(x: Float, y: Float) = mutate { copy(rx = axis(x), ry = axis(y)) }

    fun reset() = mutate { PadState() }

    private fun axis(v: Float): Short = (v.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()

    private inline fun mutate(block: PadState.() -> PadState) {
        val next = synchronized(lock) {
            val n = state.block()
            if (n == state) return
            state = n
            n
        }
        onChange(next)
    }
}
