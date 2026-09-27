package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.GameItem
import com.example.model.OverlayConfig
import com.example.ui.theme.QboostHudAccent
import com.example.ui.theme.QboostNeonCyan
import com.example.ui.theme.QboostBlue
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import kotlin.math.roundToInt

@Composable
fun GameSettingsSheet(
    game: GameItem,
    overlayConfig: OverlayConfig,
    onDismiss: () -> Unit,
    onSaveGameSettings: (GameItem) -> Unit,
    onSaveOverlayConfig: (OverlayConfig) -> Unit
) {
    var saturation by remember { mutableFloatStateOf(game.saturation) }
    var touchSensitivity by remember { mutableIntStateOf(game.touchSensitivity) }
    var blockNotifications by remember { mutableStateOf(game.blockNotifications) }
    var blockCalls by remember { mutableStateOf(game.blockCalls) }
    var targetFps by remember { mutableIntStateOf(game.targetFps) }
    var touchBooster by remember { mutableStateOf(game.touchBooster) }

    // Overlay settings
    var showFps by remember { mutableStateOf(overlayConfig.isFpsVisible) }
    var showCpu by remember { mutableStateOf(overlayConfig.isCpuTempVisible) }
    var showRam by remember { mutableStateOf(overlayConfig.isRamVisible) }
    var showGpu by remember { mutableStateOf(overlayConfig.isGpuVisible) }
    var showPing by remember { mutableStateOf(overlayConfig.isPingVisible) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF131722))
                .padding(20.dp)
                .testTag("game_settings_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = QboostNeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${game.name} Tuning",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = clickSound(onDismiss), modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Saturation Slider (User requested feature!)
                SettingsCard(
                    icon = Icons.Default.ColorLens,
                    title = "Visual Vibrancy & Saturation",
                    subtitle = "Adjust color vibrancy and display saturation in real-time"
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Saturation Multiplier", color = TextGray, fontSize = 11.sp)
                            Text(
                                text = "${(saturation * 100).roundToInt()}%",
                                color = QboostNeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = saturation,
                            onValueChange = { saturation = (it * 20).roundToInt() / 20f },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = QboostNeonCyan,
                                activeTrackColor = QboostNeonCyan,
                                inactiveTrackColor = Color(0xFF272F3E)
                            ),
                            modifier = Modifier.testTag("saturation_slider")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Touch Sensitivity Slider
                SettingsCard(
                    icon = Icons.Default.TouchApp,
                    title = "Touch Response & Sensitivity",
                    subtitle = "Fine-tune digitizer response curve & touch polling"
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sensitivity Level", color = TextGray, fontSize = 11.sp)
                            Text(
                                text = "Level $touchSensitivity / 10",
                                color = QboostHudAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = touchSensitivity.toFloat(),
                            onValueChange = { touchSensitivity = it.roundToInt() },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = QboostHudAccent,
                                activeTrackColor = QboostHudAccent,
                                inactiveTrackColor = Color(0xFF272F3E)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Target Frame Rate
                SettingsCard(
                    icon = Icons.Default.Speed,
                    title = "Target Refresh Rate & FPS Cap",
                    subtitle = "Hardware display sync target"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(60, 90, 120, 144).forEach { fps ->
                            val isSelected = targetFps == fps
                            FilterChip(
                                selected = isSelected,
                                onClick = clickSound { targetFps = fps },
                                label = { Text("${fps}Hz", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = QboostBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1E2433),
                                    labelColor = TextGray
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Notification & Call Blocks
                SettingsCard(
                    icon = Icons.Default.NotificationsOff,
                    title = "Distraction Shield",
                    subtitle = "Eliminate accidental popups during intense sessions"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Block Incoming Notifications", color = TextWhite, fontSize = 12.sp)
                            Switch(
                                checked = blockNotifications,
                                onCheckedChange = { blockNotifications = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = QboostBlue
                                )
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Block Incoming Calls & Alarms", color = TextWhite, fontSize = 12.sp)
                            Switch(
                                checked = blockCalls,
                                onCheckedChange = { blockCalls = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = QboostBlue
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. Overlay Window Chips Toggles (FPS, CPU, RAM, GPU, Ping)
                SettingsCard(
                    icon = Icons.Default.Speed,
                    title = "Overlay Metrics & Network Ping",
                    subtitle = "Configure visible stats in floating window"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricToggleChip(label = "FPS", checked = showFps, onToggle = { showFps = !showFps })
                        MetricToggleChip(label = "CPU", checked = showCpu, onToggle = { showCpu = !showCpu })
                        MetricToggleChip(label = "RAM", checked = showRam, onToggle = { showRam = !showRam })
                        MetricToggleChip(label = "GPU", checked = showGpu, onToggle = { showGpu = !showGpu })
                        MetricToggleChip(label = "Ping", checked = showPing, onToggle = { showPing = !showPing })
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Save & Apply
                Button(
                    onClick = clickSound {
                        onSaveGameSettings(
                            game.copy(
                                saturation = saturation,
                                touchSensitivity = touchSensitivity,
                                targetFps = targetFps,
                                blockNotifications = blockNotifications,
                                blockCalls = blockCalls,
                                touchBooster = touchBooster
                            )
                        )
                        onSaveOverlayConfig(
                            overlayConfig.copy(
                                isFpsVisible = showFps,
                                isCpuTempVisible = showCpu,
                                isRamVisible = showRam,
                                isGpuVisible = showGpu,
                                isPingVisible = showPing
                            )
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QboostBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("save_settings_button")
                ) {
                    Text("Apply Game Tuning", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F1219))
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = TextGray, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = title, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(text = subtitle, color = TextGray, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun MetricToggleChip(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) QboostBlue.copy(alpha = 0.25f) else Color(0xFF1B202D))
            .qClickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (checked) Color.White else TextGray,
            fontSize = 11.sp,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
        )
    }
}
