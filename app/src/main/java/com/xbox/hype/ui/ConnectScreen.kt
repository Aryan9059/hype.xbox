package com.xbox.hype.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbox.hype.R
import com.xbox.hype.net.DiscoveredPc

private val CardShape = RoundedCornerShape(18.dp)
private val CardFill = Color(0xFF1A1C1A)

@Composable
fun ConnectScreen(
    discovered: List<DiscoveredPc>,
    lastHost: String,
    onConnect: (String) -> Unit,
    onRescan: () -> Unit,
) {
    var manual by remember { mutableStateOf(lastHost) }
    Row(
        Modifier.fillMaxSize().background(Color.Black).displayCutoutPadding().padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Nearby PCs", Modifier.weight(1f).fillMaxHeight(), action = {
            IconButton(onClick = onRescan, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.refresh),
                    tint = Palette.XboxGreenBright,
                    contentDescription = "Refresh"
                )
            }
        }) {
            if (discovered.isEmpty()) {
                SearchingIndicator()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(discovered, key = { it.host }) { pc ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Palette.XboxGreen.copy(alpha = 0.14f))
                                .border(BorderStroke(1.dp, Palette.XboxGreen.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
                                .clickable { onConnect(pc.host) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(Palette.XboxGreenBright))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pc.name, color = Palette.Text, fontWeight = FontWeight.SemiBold)
                                Text(pc.host, color = Palette.Text.copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                            Text("›", color = Palette.XboxGreenBright, fontSize = 22.sp)
                        }
                    }
                }
            }
        }

        Column(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            SectionTitle("Manual connection")
            Spacer(Modifier.height(4.dp))
            Text("Enter the IP address shown in the PC app.", color = Palette.Text.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it.trim() },
                singleLine = true,
                label = { Text("IP address") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Palette.Text,
                    unfocusedTextColor = Palette.Text,
                    focusedBorderColor = Palette.XboxGreenBright,
                    unfocusedBorderColor = Palette.Text.copy(alpha = 0.6f),
                    focusedLabelColor = Palette.XboxGreenBright,
                    unfocusedLabelColor = Palette.Text.copy(alpha = 0.6f),
                    cursorColor = Palette.XboxGreenBright,
                ),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (manual.isNotBlank()) onConnect(manual) },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.XboxGreen, contentColor = Color.White),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Connect") }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.clip(CardShape).background(CardFill).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(title, Modifier.weight(1f))
            action?.invoke()
        }
        content()
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SearchingIndicator() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(
                color = Palette.XboxGreenBright,
                trackColor = Palette.XboxGreen.copy(alpha = 0.25f),
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp),
            )
            Text("Searching for PCs", color = Palette.Text.copy(alpha = 0.7f), fontSize = 13.sp)
        }
    }
}

/** Circular-arrow refresh glyph. */
@Composable
private fun RefreshIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = 2.2.dp.toPx()
        val inset = w + 1.dp.toPx()
        val d = Size(size.width - 2 * inset, size.height - 2 * inset)
        // 300 degree arc, leaving a gap at the top right for the arrowhead
        drawArc(tint, startAngle = -30f, sweepAngle = 300f, useCenter = false,
            topLeft = Offset(inset, inset), size = d, style = Stroke(w, cap = StrokeCap.Round))
        val c = Offset(size.width / 2f, size.height / 2f)
        val rad = d.width / 2f
        val a = Math.toRadians(-30.0)
        val tip = Offset(c.x + rad * Math.cos(a).toFloat(), c.y + rad * Math.sin(a).toFloat())
        val h = 4.5.dp.toPx()
        drawPath(
            Path().apply {
                moveTo(tip.x + h * 1.1f, tip.y - h * 0.2f)
                lineTo(tip.x - h * 0.9f, tip.y - h * 1.0f)
                lineTo(tip.x - h * 0.2f, tip.y + h * 1.1f)
                close()
            },
            tint,
        )
    }
}
