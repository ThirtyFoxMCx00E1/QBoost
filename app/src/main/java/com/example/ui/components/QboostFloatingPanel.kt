package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hardware.TelemetryFormat
import com.example.model.GameItem
import com.example.model.PerformanceMode
import com.example.model.PerformanceStats
import com.example.ui.theme.QboostHudBg
import com.example.ui.theme.QboostHudCard
import com.example.ui.theme.QboostHudAccent
import com.example.ui.theme.QboostHudAccentGlow
import com.example.ui.theme.QboostHudAccentLight
import com.example.ui.theme.QboostNeonCyan
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite

@Composable
fun QboostFloatingPanel(
    game: GameItem,
    stats: PerformanceStats,
    isSystemMonitorActive: Boolean,
    onModeSelect: (PerformanceMode) -> Unit,
    onToggleGameMode: () -> Unit,
    onToggleSystemMonitor: () -> Unit,
    onToggleFloatingMenu: () -> Unit,
    onToggleTouchBooster: () -> Unit,
    onOpenEditOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunchFloatingApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hud_radar")
    val radarPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_pulse"
    )

    Box(
        modifier = modifier
            .width(360.dp)
            .wrapContentHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(QboostHudBg)
            .clickable(enabled = true, onClick = { /* prevent tap propagation */ })
            .padding(18.dp)
            .testTag("qboost_floating_panel")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top HUD Row: CPU | FPS | Game Icon with concentric waves | RAM | GPU
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // CPU Vertical Bar & Label
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CPU",
                        color = TextGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    VerticalPillBar(
                        fillFraction = (stats.cpuUsagePercent / 100f).coerceIn(0.1f, 1f),
                        color = QboostHudAccent
                    )
                }

                // FPS Big Number
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "FPS",
                        color = TextGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = TelemetryFormat.fps(stats),
                        color = TextWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Center Game Icon Circle with Radar Ripples
                Box(
                    modifier = Modifier.size(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer radar ring
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .scale(radarPulse)
                            .clip(CircleShape)
                    )

                    // Inner circle
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(game.iconColor), Color(0xFF140D1E))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = game.initials,
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // RAM Big Number
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "RAM",
                        color = TextGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${stats.ramUsedPercent}",
                        color = TextWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // GPU Vertical Bar & Label
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "GPU",
                        color = TextGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    VerticalPillBar(
                        fillFraction = (stats.gpuUsagePercent / 100f).coerceIn(0.1f, 1f),
                        color = QboostHudAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Mode Selector Pills: [Performance] [Balanced] [Battery saver]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF171B24))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PerformanceMode.values().forEach { mode ->
                    val isSelected = game.performanceMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                if (isSelected) Brush.horizontalGradient(
                                    listOf(QboostHudAccentGlow, QboostHudAccent)
                                ) else SolidColor(Color.Transparent)
                            )
                            .qClickable { onModeSelect(mode) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.displayName,
                            color = if (isSelected) TextWhite else TextGray,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Four Big Action Buttons (2x2 Grid) matching Image 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Game Mode
                HudActionButton(
                    text = "Game mode",
                    isActive = game.gameModeActive,
                    onClick = onToggleGameMode,
                    modifier = Modifier.weight(1f)
                )

                // System Monitor (highlighted in red rectangle in Image 2)
                HudActionButton(
                    text = "System monitor",
                    isActive = isSystemMonitorActive,
                    onClick = onToggleSystemMonitor,
                    hasHighlightBorder = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Floating menu
                HudActionButton(
                    text = "Floating menu",
                    isActive = false,
                    onClick = onToggleFloatingMenu,
                    modifier = Modifier.weight(1f)
                )

                // Touch booster
                HudActionButton(
                    text = "Touch booster",
                    isActive = game.touchBooster,
                    onClick = onToggleTouchBooster,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Bottom Row: Floating Apps shortcuts + Edit + Settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141720))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Floating App Quick Icons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Browser
                    FloatingAppIcon(
                        bg = Color(0xFF2979FF),
                        onClick = { onLaunchFloatingApp("browser") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Browser",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // YouTube (Watch videos while gaming!)
                    FloatingAppIcon(
                        bg = Color(0xFFE50914),
                        onClick = { onLaunchFloatingApp("youtube") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "YouTube",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Phone / Voice
                    FloatingAppIcon(
                        bg = Color(0xFF00C853),
                        onClick = { onLaunchFloatingApp("phone") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Phone",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Notes / Calculator
                    FloatingAppIcon(
                        bg = Color(0xFFFF6D00),
                        onClick = { onLaunchFloatingApp("notes") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Notes",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Clock / Stopwatch
                    FloatingAppIcon(
                        bg = Color(0xFF5C6BC0),
                        onClick = { onLaunchFloatingApp("clock") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Clock",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(Color(0xFF2E3444))
                )

                // Tools: Edit overlay positions + Settings
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = clickSound(onOpenEditOverlay),
                        modifier = Modifier.size(32.dp).testTag("edit_overlay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Customize Overlay Position",
                            tint = QboostNeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = clickSound(onOpenSettings),
                        modifier = Modifier.size(32.dp).testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Qboost Game Settings",
                            tint = TextWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VerticalPillBar(
    fillFraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(12.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF262C3A)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fillFraction)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(QboostHudAccentLight, color)
                    )
                )
        )
    }
}

@Composable
fun HudActionButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasHighlightBorder: Boolean = false
) {
    val borderModifier = if (hasHighlightBorder) {
        Modifier
    } else if (isActive) {
        Modifier
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) Color(0xFF282D3D) else QboostHudCard)
            .then(borderModifier)
            .qClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) TextWhite else TextGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun FloatingAppIcon(
    bg: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .qClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
