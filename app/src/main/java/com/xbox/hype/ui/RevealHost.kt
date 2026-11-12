package com.xbox.hype.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/** Circle growing from the connection chip at the top centre of the controller screen. */
private class RevealShape(private val progress: Float, private val originY: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val centre = Offset(size.width / 2f, originY)
        val full = hypot(size.width / 2f, size.height - originY)
        val r = (full * progress).coerceAtLeast(0f)
        return Outline.Generic(Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(centre, r))
        })
    }
}

/**
 * Cross-screen transition: the controller springs open from the connected card, and springs
 * shut back into it on disconnect. [host] is null while disconnected.
 */
@Composable
fun RevealHost(
    host: String?,
    connect: @Composable () -> Unit,
    controller: @Composable (host: String) -> Unit,
) {
    val progress = remember { Animatable(if (host != null) 1f else 0f) }
    val lastHost = remember { arrayOf(host ?: "") }
    if (host != null) lastHost[0] = host

    LaunchedEffect(host != null) {
        progress.animateTo(
            if (host != null) 1f else 0f,
            spring(dampingRatio = 0.72f, stiffness = 110f),
        )
    }

    val settledOpen = host != null && progress.value >= 1f && !progress.isRunning
    val chipY = with(androidx.compose.ui.platform.LocalDensity.current) { 32.dp.toPx() }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        if (!settledOpen) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val s = 1f - 0.05f * progress.value.coerceIn(0f, 1f)
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.7f * progress.value.coerceIn(0f, 1f)
                },
            ) { connect() }
        }
        if (progress.value > 0f || host != null) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    shape = RevealShape(progress.value, chipY)
                    clip = true
                },
            ) { controller(lastHost[0]) }
        }
    }
}

private class PointRevealShape(private val progress: Float, private val origin: Offset) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val full = maxOf(
            hypot(origin.x, origin.y),
            hypot(size.width - origin.x, origin.y),
            hypot(origin.x, size.height - origin.y),
            hypot(size.width - origin.x, size.height - origin.y),
        )
        val r = (full * progress).coerceAtLeast(0f)
        return Outline.Generic(Path().apply { addOval(androidx.compose.ui.geometry.Rect(origin, r)) })
    }
}

/** A full-screen layer that springs open from [origin] (px) when [visible], and shut back into it. */
@Composable
fun RevealLayer(
    visible: Boolean,
    origin: Offset,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val lastOrigin = remember { arrayOf(origin) }
    if (visible) lastOrigin[0] = origin
    LaunchedEffect(visible) {
        progress.animateTo(if (visible) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 160f))
    }
    if (visible || progress.value > 0f) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                shape = PointRevealShape(progress.value, lastOrigin[0])
                clip = true
            },
        ) { content() }
    }
}
