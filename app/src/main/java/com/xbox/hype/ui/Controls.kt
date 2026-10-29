package com.xbox.hype.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import com.xbox.hype.net.Buttons
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

object Palette {
    val Background = Color(0xFF080808)
    val Surface = Color(0xFF1C1C1C)
    val Key = Color(0xFF5A5A5A)
    val SurfacePressed = Color(0xFF3A3A3A)
    val XboxGreen = Color(0xFF107C10)
    val XboxGreenBright = Color(0xFF8CFF2E)
    val ButtonA = Color(0xFF6DB81E)
    val ButtonB = Color(0xFFD62A1E)
    val ButtonX = Color(0xFF1F78D1)
    val ButtonY = Color(0xFFF2A100)
    val Text = Color(0xFFD9D9D9)
}

private fun Color.darken(f: Float) = lerp(this, Color.Black, f)
private fun Color.lighten(f: Float) = lerp(this, Color.White, f)

/** Near-black backdrop with a fine, matte grain like textured controller plastic. */
fun Modifier.matteGrain(): Modifier = drawWithCache {
    val tile = 256
    val rnd = java.util.Random(7)
    val px = IntArray(tile * tile) {
        val v = rnd.nextInt(100)
        when {
            v < 38 -> ((rnd.nextInt(26) + 6) shl 24) or 0xFFFFFF   // light speck
            v < 70 -> (rnd.nextInt(90) + 30) shl 24                // dark speck
            else -> 0
        }
    }
    val bmp = android.graphics.Bitmap.createBitmap(px, tile, tile, android.graphics.Bitmap.Config.ARGB_8888)
    val grain = ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    val base = Brush.radialGradient(
        listOf(Color(0xFF1A1A1A), Color(0xFF0B0B0B), Color(0xFF020202)),
        center = Offset(size.width / 2f, size.height * 0.4f),
        radius = size.maxDimension * 0.75f,
    )
    onDrawBehind {
        drawRect(base)
        drawRect(grain)
    }
}

/**
 * Tracks the first pointer of a gesture until it lifts, reporting each change.
 * Every control owns its gesture loop, so controls are touched independently (multi-touch).
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.trackPointer(
    onMove: (PointerInputChange) -> Unit,
) {
    val down = awaitFirstDown(requireUnconsumed = false)
    onMove(down)
    down.consume()
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        onMove(change)
        change.consume()
    }
}

@Composable
fun HoldButton(
    label: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    color: Color = Palette.Key,
    textColor: Color = Palette.Text,
    fontSize: Int = 22,
    ring: Color? = null,
    onChange: (Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val sc = 1f - 0.09f * press
                scaleX = sc
                scaleY = sc
            }
            .drawBehind {
                val r = this.size.minDimension / 2f
                // dark bezel
                drawCircle(Brush.verticalGradient(listOf(Color(0xFF2E2E2E), Color(0xFF050505))), r)
                if (ring != null) {
                    drawCircle(ring.copy(alpha = 0.85f + 0.15f * press), r - 2.dp.toPx(), style = Stroke(3.dp.toPx()))
                }
                // glossy dome
                val ir = r * 0.8f
                drawCircle(
                    Brush.radialGradient(
                        listOf(color.lighten(0.4f), color, color.darken(0.4f)),
                        center = Offset(center.x - ir * 0.3f, center.y - ir * 0.4f),
                        radius = ir * 1.6f,
                    ),
                    ir,
                )
                drawCircle(Color.Black.copy(alpha = 0.3f * press), ir)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onChange(true)
                    do {
                        val e = awaitPointerEvent(PointerEventPass.Main)
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                    pressed = false
                    onChange(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
    }
}

/** Rectangular shoulder button / trigger. Dragging up eases a trigger off; a plain press is full. */
@Composable
fun TriggerButton(
    label: String,
    modifier: Modifier = Modifier,
    analog: Boolean,
    onValue: (Float) -> Unit,
) {
    var target by remember { mutableStateOf(0f) }
    val value by animateFloatAsState(target, tween(70), label = "trigger")
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(width = 130.dp, height = if (analog) 72.dp else 52.dp)
            .graphicsLayer {
                val sc = 1f - 0.05f * value
                scaleX = sc
                scaleY = sc
            }
            .drawBehind {
                val cr = CornerRadius(14.dp.toPx())
                drawRoundRect(Color.Black, cornerRadius = cr)
                val inset = 2.dp.toPx()
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(lerp(Color(0xFF666666), Color(0xFF9A9A9A), value), lerp(Color(0xFF343434), Color(0xFF5A5A5A), value)),
                    ),
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - 2 * inset, this.size.height - 2 * inset),
                    cornerRadius = cr,
                )
                drawRoundRect(
                    Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(inset, inset),
                    size = Size(this.size.width - 2 * inset, this.size.height - 2 * inset),
                    cornerRadius = cr,
                    style = Stroke(1.dp.toPx()),
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    val startY = down.position.y
                    val range = 90.dp.toPx()
                    fun apply(y: Float) {
                        target = if (analog) (1f - (startY - y).coerceAtLeast(0f) / range).coerceIn(0f, 1f) else 1f
                        onValue(target)
                    }
                    apply(startY)
                    down.consume()
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Main)
                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        apply(c.position.y)
                        c.consume()
                    }
                    target = 0f
                    onValue(0f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** Floating thumbstick. A quick second touch after a release also holds the thumb-click (L3/R3). */
@Composable
fun Stick(
    modifier: Modifier = Modifier,
    zoneSize: androidx.compose.ui.unit.Dp = 230.dp,
    onMove: (x: Float, y: Float) -> Unit,
    onClick: (Boolean) -> Unit,
) {
    var origin by remember { mutableStateOf<Offset?>(null) }
    var knob by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    // snap in on touch, glide back to rest on release
    val a by animateFloatAsState(
        if (active) 1f else 0f,
        if (active) snap() else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "stick",
    )
    var lastRelease by remember { mutableStateOf(0L) }
    Canvas(
        modifier = modifier
            .size(zoneSize)
            .pointerInput(Unit) {
                val radius = zoneSize.toPx() * 0.32f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val doubleTap = down.uptimeMillis - lastRelease < 300
                    origin = down.position
                    knob = down.position
                    active = true
                    if (doubleTap) onClick(true)
                    down.consume()
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Main)
                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) {
                            lastRelease = c.uptimeMillis
                            break
                        }
                        val d = c.position - down.position
                        val len = hypot(d.x, d.y)
                        val k = if (len > radius) d * (radius / len) else d
                        knob = down.position + k
                        onMove(k.x / radius, -k.y / radius)
                        c.consume()
                    }
                    if (doubleTap) onClick(false)
                    active = false
                    onMove(0f, 0f)
                }
            },
    ) {
        val r = min(size.width, size.height) * 0.32f
        val anchor = origin ?: center
        val c = androidx.compose.ui.geometry.lerp(center, anchor, a)
        // recessed well
        drawCircle(Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF4A4A4A)), c.y - r, c.y + r), r, c)
        drawCircle(Color(0xFF9A9A9A).copy(alpha = 0.6f + 0.4f * a.coerceIn(0f, 1f)), r, c, style = Stroke(2.dp.toPx()))
        // rubber thumb cap
        val k = c + (knob - anchor) * a
        val cap = r * 0.62f
        drawCircle(Color.Black, cap * 1.1f, k + Offset(0f, 3.dp.toPx()))
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF7A7A7A), Color(0xFF444444), Color(0xFF222222)),
                center = k - Offset(cap * 0.25f, cap * 0.3f),
                radius = cap * 1.4f,
            ),
            cap, k,
        )
        drawCircle(Color.Black.copy(alpha = 0.6f), cap * 0.78f, k, style = Stroke(1.5.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.5f), cap * 0.5f, k, style = Stroke(1.5.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.3f + 0.15f * a.coerceIn(0f, 1f)), cap, k, style = Stroke(1.dp.toPx()))
    }
}

/** Eight-way D-pad: direction comes from the angle of the touch relative to the centre. */
@Composable
fun DPad(
    modifier: Modifier = Modifier,
    onChange: (bits: Int) -> Unit,
) {
    var bits by remember { mutableStateOf(0) }
    val view = LocalView.current
    fun lit(mask: Int) = if (bits and mask != 0) 1f else 0f
    val litUp by animateFloatAsState(lit(Buttons.DPAD_UP), tween(90), label = "up")
    val litDown by animateFloatAsState(lit(Buttons.DPAD_DOWN), tween(90), label = "down")
    val litLeft by animateFloatAsState(lit(Buttons.DPAD_LEFT), tween(90), label = "left")
    val litRight by animateFloatAsState(lit(Buttons.DPAD_RIGHT), tween(90), label = "right")
    fun litOf(mask: Int) = when (mask) {
        Buttons.DPAD_UP -> litUp
        Buttons.DPAD_DOWN -> litDown
        Buttons.DPAD_LEFT -> litLeft
        else -> litRight
    }
    Canvas(
        modifier = modifier
            .size(150.dp)
            .pointerInput(Unit) {
                val dead = 14.dp.toPx()
                fun bitsFor(p: Offset, center: Offset): Int {
                    val d = p - center
                    if (hypot(d.x, d.y) < dead) return 0
                    // 0 = right, increasing clockwise (screen y points down)
                    val deg = Math.toDegrees(atan2(d.y.toDouble(), d.x.toDouble())).let { if (it < 0) it + 360 else it }
                    var b = 0
                    // Each axis owns a 135 degree sector, so diagonals overlap.
                    if (deg < 67.5 || deg >= 292.5) b = b or Buttons.DPAD_RIGHT
                    if (deg in 112.5..247.5) b = b or Buttons.DPAD_LEFT
                    if (deg in 22.5..157.5) b = b or Buttons.DPAD_DOWN
                    if (deg in 202.5..337.5) b = b or Buttons.DPAD_UP
                    return b
                }
                awaitEachGesture {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    trackPointer { c ->
                        val next = bitsFor(c.position, center)
                        if (next != bits) {
                            if (next != 0) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            bits = next
                            onChange(next)
                        }
                    }
                    bits = 0
                    onChange(0)
                }
            },
    ) {
        val arm = size.width / 3f
        val mid = size.width / 2f
        val centre = Offset(mid, mid)
        // circular well
        drawCircle(Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF4A4A4A))), mid)
        drawCircle(Color(0xFF9A9A9A).copy(alpha = 0.8f), mid - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        val corner = CornerRadius(8.dp.toPx())
        val sz = Size(arm, arm)
        val inset = mid * 0.12f
        fun arm(mask: Int, topLeft: Offset) {
            val t = litOf(mask)
            drawRoundRect(Color.Black, topLeft, sz, corner)
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(lerp(Color(0xFF666666), Color(0xFF8A8A8A), t), lerp(Color(0xFF383838), Color(0xFF555555), t)),
                    topLeft.y, topLeft.y + arm,
                ),
                topLeft + Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                Size(arm - 3.dp.toPx(), arm - 3.dp.toPx()),
                corner,
            )
        }
        arm(Buttons.DPAD_UP, Offset(arm, inset))
        arm(Buttons.DPAD_DOWN, Offset(arm, size.height - arm - inset))
        arm(Buttons.DPAD_LEFT, Offset(inset, arm))
        arm(Buttons.DPAD_RIGHT, Offset(size.width - arm - inset, arm))
        drawRoundRect(Color(0xFF444444), Offset(arm, arm), sz, corner)
        drawCircle(Color.Black.copy(alpha = 0.7f), arm * 0.3f, centre, style = Stroke(1.5.dp.toPx()))
        // direction arrows
        val t = 5.dp.toPx()
        fun arrow(cx: Float, cy: Float, dx: Float, dy: Float, mask: Int) {
            val path = Path().apply {
                moveTo(cx + dx * t, cy + dy * t)
                lineTo(cx - dy * t - dx * t, cy + dx * t - dy * t)
                lineTo(cx + dy * t - dx * t, cy - dx * t - dy * t)
                close()
            }
            drawPath(path, lerp(Color(0xFFC8C8C8), Palette.XboxGreenBright, litOf(mask)))
        }
        arrow(mid, inset + arm / 2, 0f, -1f, Buttons.DPAD_UP)
        arrow(mid, size.height - inset - arm / 2, 0f, 1f, Buttons.DPAD_DOWN)
        arrow(inset + arm / 2, mid, -1f, 0f, Buttons.DPAD_LEFT)
        arrow(size.width - inset - arm / 2, mid, 1f, 0f, Buttons.DPAD_RIGHT)
    }
}
