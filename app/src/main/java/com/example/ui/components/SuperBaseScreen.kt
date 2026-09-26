package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hardware.TelemetryFormat
import com.example.model.PerformanceStats
import com.example.ui.theme.QboostHudAccent
import com.example.ui.theme.QboostNeonCyan
import com.example.ui.theme.QboostNeonGreen
import com.example.ui.theme.QboostBlue
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.launch

@Composable
fun SuperBaseScreen(
    stats: PerformanceStats,
    onClearProcesses: () -> Unit,
    hasOverlayPermission: Boolean = false,
    onOpenRestrictedSettingsGuide: () -> Unit = {},
    onOpenAppInfo: () -> Unit = {},
    onOpenOverlaySettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Banner / Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "QBOOST SUPER BASE",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Hardware Telemetry, ABI Engine & Memory Optimization",
                    color = TextGray,
                    fontSize = 11.sp
                )
            }

            // Quick Clear Process Button
            Button(
                onClick = clickSound(onClearProcesses),
                colors = ButtonDefaults.buttonColors(containerColor = QboostBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("boost_ram_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Turbo RAM Boost", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Feedback Banner when processes cleared
        if (stats.processesCleared > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(QboostNeonGreen.copy(alpha = 0.15f))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Memory, contentDescription = null, tint = QboostNeonGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Asked Android to stop ${stats.processesCleared} background apps • Freed ${stats.freedRamMb} MB (measured)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Row 1: CPU Governor & RAM Usage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CPU Governor Card
            SuperBaseCard(
                title = "CPU Governor",
                subtitle = "${stats.cpuCores}-core • ${stats.deviceModel.ifBlank { "this device" }}",
                icon = Icons.Default.Memory,
                modifier = Modifier.weight(1f)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (stats.cpuLoadEstimated) "Load (from real clock speeds)" else "Utilization", color = TextGray, fontSize = 11.sp)
                        Text(TelemetryFormat.load(stats.cpuUsagePercent, stats.cpuLoadEstimated), color = QboostBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { stats.cpuUsagePercent.coerceAtLeast(0) / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = QboostBlue,
                        trackColor = Color(0xFF222838)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (stats.cpuFreqMhz > 0) "Fastest core now: ${TelemetryFormat.ghz(stats.cpuFreqMhz)}" else "Clock speed: N/A on this ROM",
                        color = TextGray,
                        fontSize = 10.sp
                    )
                }
            }

            // RAM Usage Card
            SuperBaseCard(
                title = "Memory Pool",
                subtitle = "Physical RAM (live)",
                icon = Icons.Default.Computer,
                modifier = Modifier.weight(1f)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Used / Total", color = TextGray, fontSize = 11.sp)
                        Text("${stats.ramUsedMb} / ${stats.ramTotalMb} MB", color = QboostHudAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { stats.ramUsedPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = QboostHudAccent,
                        trackColor = Color(0xFF222838)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("In use: ${stats.ramUsedPercent}% • Swap/zRAM: ${TelemetryFormat.swap(stats)}", color = TextGray, fontSize = 10.sp)
                }
            }
        }

        // Row 2: Thermals & Network Stability
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thermals Card
            SuperBaseCard(
                title = "Thermal Monitor",
                subtitle = if (stats.tempSource == "CPU") "CPU thermal sensor" else "Battery temperature sensor",
                icon = Icons.Default.Thermostat,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = TelemetryFormat.temp(stats.cpuTempC),
                            color = if (stats.cpuTempC > 42f) Color(0xFFFF5252) else QboostNeonGreen,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = when {
                                stats.cpuTempC >= 46f -> "Status: Hot"
                                stats.cpuTempC >= 40f -> "Status: Warm"
                                else -> "Status: Cool"
                            },
                            color = TextGray,
                            fontSize = 10.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2536)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Thermostat, contentDescription = null, tint = QboostNeonCyan, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Network Stability Card
            SuperBaseCard(
                title = "Network Stability",
                subtitle = if (stats.isOnline) "Online" else "Offline",
                icon = Icons.Default.Wifi,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (stats.isOnline) (if (stats.pingMs >= 0) "${stats.pingMs} ms" else "-- ms") else "Offline",
                            color = if (stats.isOnline) QboostNeonGreen else Color.Red,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text("Measured to 8.8.8.8 (DNS)", color = TextGray, fontSize = 10.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2536)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, tint = QboostNeonGreen, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Android 13+ Restricted Settings & Floating HUD Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF131824))
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (hasOverlayPermission) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (hasOverlayPermission) Color(0xFF00E676) else Color(0xFFFF9100),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FLOATING HUD & SIDELOAD PERMISSION",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (hasOverlayPermission) Color(0xFF00E676).copy(alpha = 0.2f)
                                else Color(0xFFFF9100).copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (hasOverlayPermission) "HUD READY" else "NEEDS PERMISSION",
                            color = if (hasOverlayPermission) Color(0xFF00E676) else Color(0xFFFFB74D),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (hasOverlayPermission) {
                        "Qboost v9.4 Game Space is enabled! Open the in-game panel from the left edge for the System Monitor (FPS, CPU, GPU, RAM), saturation, Upscaler, Frame gen and floating apps."
                    } else {
                        "Getting 'App was denied access' when turning on 'Display over other apps'? This is Android 13+ protection for apps downloaded from Chrome."
                    },
                    color = TextGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                if (!hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = clickSound(onOpenRestrictedSettingsGuide),
                            colors = ButtonDefaults.buttonColors(containerColor = QboostBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fix 'Denied Access'", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = clickSound(onOpenAppInfo),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2437)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = QboostNeonCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open App Info (⋮)", color = QboostNeonCyan, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuperBaseCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF121620))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = QboostBlue, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = subtitle, color = TextGray, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
