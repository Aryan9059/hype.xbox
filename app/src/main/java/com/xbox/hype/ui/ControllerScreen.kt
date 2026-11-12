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
import androidx.compose.foundation.layout.BoxWithConstraints
import com.xbox.hype.controller.ControllerState
import com.xbox.hype.settings.AppSettings
import com.xbox.hype.settings.PadItem
import com.xbox.hype.net.Buttons

@Composable
fun ControllerScreen(
    pad: ControllerState,
    host: String,
    settings: AppSettings,
    onDisconnect: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().matteGrain()) {
        PadItem.entries.forEach { item ->
            Placed(item, settings.placement(item), maxWidth, maxHeight) {
                PadItemContent(item, pad, host, onDisconnect)
            }
        }
    }
}


/** Status pill: green dot, alternating between "Connected" and the host IP. Tap to disconnect. */
@Composable
fun ConnectionChip(host: String, modifier: Modifier, onDisconnect: () -> Unit) {
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
