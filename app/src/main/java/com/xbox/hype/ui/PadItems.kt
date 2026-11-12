package com.xbox.hype.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xbox.hype.controller.ControllerState
import com.xbox.hype.net.Buttons
import com.xbox.hype.settings.PadItem
import com.xbox.hype.settings.Placement

private val DPAD_BITS = Buttons.DPAD_UP or Buttons.DPAD_DOWN or Buttons.DPAD_LEFT or Buttons.DPAD_RIGHT

/**
 * Positions [content] so its centre sits at the placement's fraction of the screen, scaled about
 * that centre. The outer box has exactly the scaled size, so touch areas match what is drawn.
 */
@Composable
fun Placed(
    item: PadItem,
    placement: Placement,
    screenWidth: Dp,
    screenHeight: Dp,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val w = item.w * placement.scale
    val h = item.h * placement.scale
    Box(
        modifier
            .offset(screenWidth * placement.cx - w / 2, screenHeight * placement.cy - h / 2)
            .size(w, h),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.requiredSize(item.w, item.h).graphicsLayer {
                scaleX = placement.scale
                scaleY = placement.scale
            },
            contentAlignment = Alignment.Center,
        ) { content() }
        overlay()
    }
}

/** The visual for one control. With a null [pad] it is a static preview (used by the layout editor). */
@Composable
fun PadItemContent(
    item: PadItem,
    pad: ControllerState?,
    host: String = "",
    onDisconnect: () -> Unit = {},
) {
    when (item) {
        PadItem.LT -> TriggerButton("LT", analog = true) { pad?.setLeftTrigger(it) }
        PadItem.LB -> TriggerButton("LB", analog = false) { pad?.setButton(Buttons.LEFT_SHOULDER, it > 0f) }
        PadItem.RT -> TriggerButton("RT", analog = true) { pad?.setRightTrigger(it) }
        PadItem.RB -> TriggerButton("RB", analog = false) { pad?.setButton(Buttons.RIGHT_SHOULDER, it > 0f) }
        PadItem.LEFT_STICK -> Stick(
            onMove = { x, y -> pad?.setLeftStick(x, y) },
            onClick = { pad?.setButton(Buttons.LEFT_THUMB, it) },
        )
        PadItem.RIGHT_STICK -> Stick(
            onMove = { x, y -> pad?.setRightStick(x, y) },
            onClick = { pad?.setButton(Buttons.RIGHT_THUMB, it) },
        )
        PadItem.DPAD -> DPad { pad?.setButtons(DPAD_BITS, it) }
        PadItem.FACE -> Box(Modifier.size(190.dp)) {
            HoldButton("Y", Modifier.align(Alignment.TopCenter), color = Palette.ButtonY, textColor = Color(0xFF7A4A00)) { pad?.setButton(Buttons.Y, it) }
            HoldButton("A", Modifier.align(Alignment.BottomCenter), color = Palette.ButtonA, textColor = Color(0xFF2F5A0A)) { pad?.setButton(Buttons.A, it) }
            HoldButton("X", Modifier.align(Alignment.CenterStart), color = Palette.ButtonX, textColor = Color(0xFF0B3A73)) { pad?.setButton(Buttons.X, it) }
            HoldButton("B", Modifier.align(Alignment.CenterEnd), color = Palette.ButtonB, textColor = Color(0xFF6E0F08)) { pad?.setButton(Buttons.B, it) }
        }
        PadItem.STATUS -> ConnectionChip(host.ifEmpty { "Connected" }, Modifier, onDisconnect)
        PadItem.BACK -> HoldButton("⧉", size = 48.dp, fontSize = 16) { pad?.setButton(Buttons.BACK, it) }
        PadItem.GUIDE -> HoldButton("X", size = 40.dp, color = Color(0xFFE4E7E4), textColor = Color(0xFF3A3F3A), ring = Palette.XboxGreenBright, fontSize = 15) { pad?.setButton(Buttons.GUIDE, it) }
        PadItem.START -> HoldButton("≡", size = 48.dp, fontSize = 18) { pad?.setButton(Buttons.START, it) }
    }
}
