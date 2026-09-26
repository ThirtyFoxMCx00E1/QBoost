package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.GameItem
import com.example.model.OverlayConfig
import com.example.model.PerformanceMode
import com.example.model.PerformanceStats
import com.example.ui.theme.QboostHudAccent
import com.example.ui.theme.QboostNeonCyan
import com.example.ui.theme.QboostBlue
import com.example.ui.theme.TextWhite

@Composable
fun InGameCockpitScreen(
    game: GameItem,
    stats: PerformanceStats,
    overlayConfig: OverlayConfig,
    onBackToLobby: () -> Unit,
    onUpdateGameSettings: (GameItem) -> Unit,
    onUpdateOverlayConfig: (OverlayConfig) -> Unit,
    onOptimizeProcesses: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPanelVisible by remember { mutableStateOf(false) }
    var isSystemMonitorActive by remember { mutableStateOf(true) }
    var isEditOverlayMode by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var activeFloatingApp by remember { mutableStateOf<String?>(null) }

    // Visual Saturation Color Matrix
    val colorMatrix = remember(game.saturation) {
        val s = game.saturation
        val lumR = 0.213f * (1 - s)
        val lumG = 0.715f * (1 - s)
        val lumB = 0.072f * (1 - s)
        ColorMatrix(
            floatArrayOf(
                lumR + s, lumG, lumB, 0f, 0f,
                lumR, lumG + s, lumB, 0f, 0f,
                lumR, lumG, lumB + s, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Detect swipe from left edge to open Qboost panel, or tap outside to close!
            .pointerInput(isPanelVisible) {
                detectDragGestures { change, dragAmount ->
                    // Left edge swipe detection
                    if (change.position.x < 140 && dragAmount.x > 18) {
                        isPanelVisible = true
                        change.consume()
                    }
                }
            }
            .pointerInput(isPanelVisible) {
                detectTapGestures {
                    if (isPanelVisible) {
                        isPanelVisible = false
                    }
                }
            }
    ) {
        // 1. Simulated Game Scenery (Wuthering Waves / Action RPG art) with dynamic saturation filter!
        Image(
            painter = painterResource(id = R.drawable.game_art_bg),
            contentDescription = "Game Scenery",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(colorMatrix)
        )

        // 2. Back to Lobby Button & Active Game Badge
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 28.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = clickSound(onBackToLobby),
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC141720))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Lobby",
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xAA10131B))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "RUNNING: ${game.name.uppercase()}",
                    color = TextWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 3. Left Edge Swipe Indicator Tab (Visual affordance for opening panel)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(18.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .background(if (isPanelVisible) QboostHudAccent else Color(0xCC141822))
                .qClickable { isPanelVisible = !isPanelVisible }
                .testTag("left_edge_tab"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Swipe or Tap to Open Qboost",
                tint = TextWhite,
                modifier = Modifier.size(16.dp)
            )
        }

        // 4. In-Game Draggable HUD Overlay (FPS, CPU, RAM, GPU, Ping)
        if (isSystemMonitorActive) {
            DraggableHudOverlay(
                stats = stats,
                config = overlayConfig,
                isEditMode = isEditOverlayMode,
                onPositionChanged = { x, y ->
                    onUpdateOverlayConfig(overlayConfig.copy(posX = x, posY = y))
                },
                onDoneEditing = { isEditOverlayMode = false },
                modifier = Modifier.align(Alignment.TopStart)
            )
        }

        // 5. Floating Multitask Window (e.g. YouTube Video, Browser)
        activeFloatingApp?.let { appType ->
            FloatingMultitaskWindow(
                appType = appType,
                onClose = { activeFloatingApp = null },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 6. Sliding Qboost Floating Square / Panel (Image Reference 2)
        AnimatedVisibility(
            visible = isPanelVisible,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            QboostFloatingPanel(
                game = game,
                stats = stats,
                isSystemMonitorActive = isSystemMonitorActive,
                onModeSelect = { newMode ->
                    onUpdateGameSettings(game.copy(performanceMode = newMode))
                },
                onToggleGameMode = {
                    onUpdateGameSettings(game.copy(gameModeActive = !game.gameModeActive))
                },
                onToggleSystemMonitor = {
                    isSystemMonitorActive = !isSystemMonitorActive
                },
                onToggleFloatingMenu = {
                    activeFloatingApp = if (activeFloatingApp == null) "youtube" else null
                },
                onToggleTouchBooster = {
                    onUpdateGameSettings(game.copy(touchBooster = !game.touchBooster))
                },
                onOpenEditOverlay = {
                    isEditOverlayMode = true
                    isPanelVisible = false
                },
                onOpenSettings = {
                    isSettingsOpen = true
                },
                onLaunchFloatingApp = { appType ->
                    activeFloatingApp = appType
                }
            )
        }

        // 7. Game Settings Dialog
        if (isSettingsOpen) {
            GameSettingsSheet(
                game = game,
                overlayConfig = overlayConfig,
                onDismiss = { isSettingsOpen = false },
                onSaveGameSettings = onUpdateGameSettings,
                onSaveOverlayConfig = onUpdateOverlayConfig
            )
        }
    }
}
