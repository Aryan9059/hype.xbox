package com.xbox.hype.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbox.hype.settings.AppSettings
import com.xbox.hype.settings.PadItem
import com.xbox.hype.settings.Placement

private val CardShape = RoundedCornerShape(18.dp)
private val CardFill = Color(0xFF1A1C1A)

/** List of app settings. [onEditLayout] opens the drag-and-resize layout editor. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onEditLayout: (Offset) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(Color.Black).displayCutoutPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = Palette.XboxGreenBright, fontSize = 28.sp) }
            Spacer(Modifier.width(8.dp))
            Text("Settings", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingRow(
                title = "Vibration",
                subtitle = "Haptic feedback on button presses and game rumble",
                onClick = { settings.updateVibration(!settings.vibration) },
            ) {
                Switch(
                    checked = settings.vibration,
                    onCheckedChange = settings::updateVibration,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Palette.XboxGreen,
                        checkedThumbColor = Palette.XboxGreenBright,
                    ),
                )
            }
            SettingRow(
                title = "Controller layout",
                subtitle = "Move and resize every control",
                onClick = onEditLayout,
            ) { Text("›", color = Palette.XboxGreenBright, fontSize = 24.sp) }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: (Offset) -> Unit,
    trailing: @Composable () -> Unit,
) {
    var center = Offset.Zero
    Row(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { center = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) }
            .clip(CardShape).background(CardFill).clickable { onClick(center) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Palette.Text.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        trailing()
    }
}

/**
 * Replica of the controller screen where each control sits in a rounded placeholder.
 * Drag a box to move it; drag the corner handle to resize it. Changes save on exit.
 */
@Composable
fun LayoutEditorScreen(settings: AppSettings, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val draft = remember {
        mutableStateMapOf<PadItem, Placement>().apply { PadItem.entries.forEach { put(it, settings.placement(it)) } }
    }
    DisposableEffect(Unit) {
        onDispose { draft.forEach { (item, p) -> settings.setPlacement(item, p) } }
    }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().matteGrain()) {
        val screenW = maxWidth
        val screenH = maxHeight
        val screenWpx = with(density) { screenW.toPx() }
        val screenHpx = with(density) { screenH.toPx() }

        PadItem.entries.forEach { item ->
            val p = draft.getValue(item)
            Placed(
                item, p, screenW, screenH,
                overlay = {
                    // Drag surface + rounded placeholder border; drawn above the (inert) control preview.
                    Box(
                        Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.45f)), RoundedCornerShape(14.dp))
                            .pointerInput(item) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val cur = draft.getValue(item)
                                    draft[item] = cur.copy(
                                        cx = (cur.cx + drag.x / screenWpx).coerceIn(0f, 1f),
                                        cy = (cur.cy + drag.y / screenHpx).coerceIn(0f, 1f),
                                    )
                                }
                            },
                    )
                    Text(
                        item.label,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 3.dp),
                    )
                    // Resize handle (bottom-right corner), scales about the control's centre.
                    Canvas(
                        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(26.dp)
                            .pointerInput(item) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val cur = draft.getValue(item)
                                    val deltaDp = with(density) { ((drag.x + drag.y) / 2f).toDp() }
                                    val newW = item.w * cur.scale + deltaDp * 2f
                                    draft[item] = cur.copy(
                                        scale = (newW / item.w).coerceIn(AppSettings.MIN_SCALE, AppSettings.MAX_SCALE),
                                    )
                                }
                            },
                    ) {
                        // Only the right and bottom edges of a rounded square, drawn as one line.
                        val r = 8.dp.toPx()
                        val sw = 3.dp.toPx()
                        val x = size.width - sw / 2f
                        val y = size.height - sw / 2f
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(x, size.height * 0.15f)
                            lineTo(x, y - r)
                            arcTo(androidx.compose.ui.geometry.Rect(x - 2 * r, y - 2 * r, x, y), 0f, 90f, false)
                            lineTo(size.width * 0.15f, y)
                        }
                        drawPath(path, Color.White.copy(alpha = 0.6f), style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                },
            ) {
                // Static preview: touch input is blocked by the overlay above.
                PadItemContent(item, pad = null, host = "Connected")
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).zIndex(10f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Color(0xFF141414))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PillButton("Reset", Palette.Text, Color.Transparent) {
                PadItem.entries.forEach { draft[it] = it.default }
            }
            PillButton("Done", Color.White, Palette.XboxGreen, bold = true, onClick = onDone)
        }
    }
}

@Composable
private fun PillButton(text: String, color: Color, fill: Color, bold: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = if (bold) 15.sp else 13.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(fill).clickable(onClick = onClick)
            .padding(horizontal = if (bold) 28.dp else 16.dp, vertical = 8.dp),
    )
}
