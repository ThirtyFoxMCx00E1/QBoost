package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hardware.TelemetryFormat
import com.example.model.OverlayConfig
import com.example.model.PerformanceStats
import com.example.ui.theme.QboostHudBg
import com.example.ui.theme.QboostNeonCyan
import com.example.ui.theme.QboostNeonGreen
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import kotlin.math.roundToInt

@Composable
fun DraggableHudOverlay(
    stats: PerformanceStats,
    config: OverlayConfig,
    isEditMode: Boolean,
    onPositionChanged: (Float, Float) -> Unit,
    onDoneEditing: () -> Unit,
    modifier: Modifier = Modifier
) {
    var posX by remember(config.posX) { mutableFloatStateOf(config.posX) }
    var posY by remember(config.posY) { mutableFloatStateOf(config.posY) }

    Box(
        modifier = modifier
            .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
            .scale(config.scale)
            .clip(RoundedCornerShape(14.dp))
            .background(QboostHudBg.copy(alpha = config.alpha))
            .then(
                if (isEditMode) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            posX = (posX + dragAmount.x).coerceIn(0f, 700f)
                            posY = (posY + dragAmount.y).coerceIn(0f, 400f)
                            onPositionChanged(posX, posY)
                        }
                    }
                } else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isEditMode) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reposition",
                    tint = QboostNeonCyan,
                    modifier = Modifier.size(16.dp)
                )
            }

            // FPS
            if (config.isFpsVisible) {
                HudMetricChip(label = "FPS", value = TelemetryFormat.fps(stats), valueColor = QboostNeonGreen)
            }

            // CPU Temp
            if (config.isCpuTempVisible) {
                HudMetricChip(label = stats.tempSource, value = TelemetryFormat.temp(stats.cpuTempC), valueColor = Color(0xFFFFB74D))
            }

            // RAM
            if (config.isRamVisible) {
                HudMetricChip(label = "RAM", value = "${stats.ramUsedPercent}%", valueColor = Color(0xFFBA68C8))
            }

            // GPU
            if (config.isGpuVisible) {
                HudMetricChip(label = "GPU", value = TelemetryFormat.load(stats.gpuUsagePercent), valueColor = Color(0xFF64B5F6))
            }

            // Ping & Online/Offline
            if (config.isPingVisible) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (stats.isOnline) QboostNeonGreen else Color.Red)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (stats.isOnline) TelemetryFormat.ping(stats) else "OFFLINE",
                        color = if (stats.isOnline) TextWhite else Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isEditMode) {
                IconButton(
                    onClick = clickSound(onDoneEditing),
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done Repositioning",
                        tint = QboostNeonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HudMetricChip(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label ",
            color = TextGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
