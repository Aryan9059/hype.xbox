package com.xbox.hype.settings

import android.content.SharedPreferences
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Position (centre, as a fraction of the screen) and scale of one on-screen control. */
data class Placement(val cx: Float, val cy: Float, val scale: Float = 1f)

/** Every movable control. [w]/[h] are the natural (scale = 1) size; defaults mirror the original layout. */
enum class PadItem(val label: String, val w: Dp, val h: Dp, val default: Placement) {
    LT("LT", 130.dp, 72.dp, Placement(0.13f, 0.13f)),
    LB("LB", 130.dp, 52.dp, Placement(0.32f, 0.105f)),
    RT("RT", 130.dp, 72.dp, Placement(0.87f, 0.13f)),
    RB("RB", 130.dp, 52.dp, Placement(0.68f, 0.105f)),
    LEFT_STICK("Left stick", 230.dp, 230.dp, Placement(0.16f, 0.43f)),
    DPAD("D-pad", 150.dp, 150.dp, Placement(0.37f, 0.75f)),
    FACE("Face buttons", 190.dp, 190.dp, Placement(0.83f, 0.575f)),
    RIGHT_STICK("Right stick", 230.dp, 230.dp, Placement(0.57f, 0.68f)),
    STATUS("Status", 150.dp, 32.dp, Placement(0.5f, 0.1f)),
    BACK("Back", 48.dp, 48.dp, Placement(0.41f, 0.36f)),
    GUIDE("Guide", 40.dp, 40.dp, Placement(0.5f, 0.31f)),
    START("Start", 48.dp, 48.dp, Placement(0.59f, 0.36f)),
}

class AppSettings(private val prefs: SharedPreferences) {
    var vibration by mutableStateOf(prefs.getBoolean(KEY_VIBRATION, true))
        private set

    private val placements = mutableStateMapOf<PadItem, Placement>().apply {
        PadItem.entries.forEach { item ->
            prefs.getString("layout_${item.name}", null)?.let(::parse)?.let { put(item, it) }
        }
    }

    fun placement(item: PadItem): Placement = placements[item] ?: item.default

    fun updateVibration(enabled: Boolean) {
        vibration = enabled
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    fun setPlacement(item: PadItem, p: Placement) {
        val clamped = Placement(p.cx.coerceIn(0f, 1f), p.cy.coerceIn(0f, 1f), p.scale.coerceIn(MIN_SCALE, MAX_SCALE))
        placements[item] = clamped
        prefs.edit().putString("layout_${item.name}", "${clamped.cx},${clamped.cy},${clamped.scale}").apply()
    }

    fun resetLayout() {
        placements.clear()
        prefs.edit().apply { PadItem.entries.forEach { remove("layout_${it.name}") } }.apply()
    }

    private fun parse(s: String): Placement? {
        val parts = s.split(',').mapNotNull { it.toFloatOrNull() }
        return if (parts.size == 3) Placement(parts[0], parts[1], parts[2]) else null
    }

    companion object {
        private const val KEY_VIBRATION = "vibration"
        const val MIN_SCALE = 0.6f
        const val MAX_SCALE = 2f
    }
}

val LocalVibrationEnabled = compositionLocalOf { true }
