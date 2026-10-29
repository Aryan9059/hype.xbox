package com.xbox.hype.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbox.hype.controller.ControllerState
import com.xbox.hype.net.Buttons

private val DPAD_BITS = Buttons.DPAD_UP or Buttons.DPAD_DOWN or Buttons.DPAD_LEFT or Buttons.DPAD_RIGHT

@Composable
fun ControllerScreen(
    pad: ControllerState,
    host: String,
    onDisconnect: () -> Unit,
) {
    Box(Modifier.fillMaxSize().matteGrain()) {
        // Shoulders and triggers
        TriggerButton("LT", Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 12.dp), analog = true) { pad.setLeftTrigger(it) }
        TriggerButton("LB", Modifier.align(Alignment.TopStart).padding(start = 190.dp, top = 12.dp), analog = false) { pad.setButton(Buttons.LEFT_SHOULDER, it > 0f) }
        TriggerButton("RT", Modifier.align(Alignment.TopEnd).padding(end = 40.dp, top = 12.dp), analog = true) { pad.setRightTrigger(it) }
        TriggerButton("RB", Modifier.align(Alignment.TopEnd).padding(end = 190.dp, top = 12.dp), analog = false) { pad.setButton(Buttons.RIGHT_SHOULDER, it > 0f) }

        // Left stick (upper left) and D-pad (lower left)
        Stick(
            Modifier.align(Alignment.CenterStart).padding(start = 16.dp).offset(y = (-24).dp),
            onMove = { x, y -> pad.setLeftStick(x, y) },
            onClick = { pad.setButton(Buttons.LEFT_THUMB, it) },
        )
        DPad(Modifier.align(Alignment.BottomStart).padding(start = 220.dp, bottom = 16.dp)) { pad.setButtons(DPAD_BITS, it) }

        // Face buttons (upper right) and right stick (lower right)
        val face = Modifier.align(Alignment.TopEnd).padding(top = 112.dp, end = 40.dp)
        Box(face.size(190.dp)) {
            HoldButton("Y", Modifier.align(Alignment.TopCenter), color = Palette.ButtonY, textColor = Color(0xFF7A4A00)) { pad.setButton(Buttons.Y, it) }
            HoldButton("A", Modifier.align(Alignment.BottomCenter), color = Palette.ButtonA, textColor = Color(0xFF2F5A0A)) { pad.setButton(Buttons.A, it) }
            HoldButton("X", Modifier.align(Alignment.CenterStart), color = Palette.ButtonX, textColor = Color(0xFF0B3A73)) { pad.setButton(Buttons.X, it) }
            HoldButton("B", Modifier.align(Alignment.CenterEnd), color = Palette.ButtonB, textColor = Color(0xFF6E0F08)) { pad.setButton(Buttons.B, it) }
        }
        Stick(
            Modifier.align(Alignment.BottomEnd).padding(end = 230.dp),
            onMove = { x, y -> pad.setRightStick(x, y) },
            onClick = { pad.setButton(Buttons.RIGHT_THUMB, it) },
        )

        // Centre cluster
        ConnectionChip(host, Modifier.align(Alignment.TopCenter).padding(top = 18.dp), onDisconnect)
        HoldButton("⧉", Modifier.align(Alignment.Center).offset(x = (-70).dp, y = (-50).dp), size = 48.dp, fontSize = 16) { pad.setButton(Buttons.BACK, it) }
        HoldButton("X", Modifier.align(Alignment.Center).offset(y = (-70).dp), size = 40.dp, color = Color(0xFFE4E7E4), textColor = Color(0xFF3A3F3A), ring = Palette.XboxGreenBright, fontSize = 15) { pad.setButton(Buttons.GUIDE, it) }
        HoldButton("≡", Modifier.align(Alignment.Center).offset(x = 70.dp, y = (-50).dp), size = 48.dp, fontSize = 18) { pad.setButton(Buttons.START, it) }
    }
}


/** Status pill: green dot, alternating between "Connected" and the host IP. Tap to disconnect. */
@Composable
private fun ConnectionChip(host: String, modifier: Modifier, onDisconnect: () -> Unit) {
    var showHost by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            showHost = !showHost
        }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dot",
    )
    Row(
        modifier.clip(RoundedCornerShape(50)).clickable(onClick = onDisconnect)
            .background(Color(0xCC141414)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.XboxGreenBright.copy(alpha = pulse)))
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = showHost,
            transitionSpec = {
                (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 2 }) togetherWith
                    (fadeOut(tween(250)) + slideOutVertically(tween(250)) { -it / 2 })
            },
            label = "chipText",
        ) { hostShown ->
            Text(
                if (hostShown) host else "Connected",
                color = Palette.Text.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
