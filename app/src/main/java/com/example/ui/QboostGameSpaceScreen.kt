package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.example.i18n.tr
import com.example.input.GamepadAction
import com.example.model.GameItem
import com.example.model.InstalledAppItem
import com.example.model.PerformanceMode
import com.example.model.PerformanceStats
import com.example.model.SaturationUiState
import com.example.ui.components.AddGameDialog
import com.example.ui.components.RestrictedSettingsGuideDialog
import com.example.ui.components.SaturationDialog
import com.example.ui.components.SuperBaseScreen
import com.example.ui.components.clickSound
import com.example.ui.components.qClickable
import com.example.ui.theme.CyberDarkBg
import com.example.ui.theme.QboostBlue
import com.example.ui.theme.QboostBlueDark
import com.example.ui.theme.QboostBlueGlow
import com.example.ui.theme.QboostNeonCyan
import com.example.ui.theme.QboostNeonGreen
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Width / height of a game tile. The library art is a wide banner, so tiles are landscape. */
private const val TILE_ASPECT = 1.6f

enum class GameSpaceTab {
    LIBRARY,
    SUPER_BASE,
    FAVORITES
}

/**
 * Qboost Game Space (v6.0): a console-style library. The background is the selected game's
 * library art, the games are a row of art tiles, and everything is flat blue with no outlines.
 */
@Composable
fun QboostGameSpaceScreen(
    games: List<GameItem>,
    selectedGame: GameItem,
    stats: PerformanceStats,
    isMusicMuted: Boolean,
    isVibrationEnabled: Boolean,
    isTurboFanActive: Boolean,
    beatIntensity: Float,
    installedApps: List<InstalledAppItem>,
    onSelectGame: (GameItem) -> Unit,
    onStartGame: (GameItem) -> Unit,
    onAddGame: (String, String) -> Unit,
    onToggleMuteMusic: () -> Unit,
    onToggleVibration: () -> Unit,
    onToggleTurboFan: () -> Unit,
    onClearProcesses: () -> Unit,
    notInstalledGame: GameItem? = null,
    launchToast: String? = null,
    onDismissNotInstalledPrompt: () -> Unit = {},
    onOpenPlayStore: (String) -> Unit = {},
    onLaunchCockpit: (GameItem) -> Unit = {},
    onClearToast: () -> Unit = {},
    hasOverlayPermission: Boolean = false,
    overlayPermissionPromptGame: GameItem? = null,
    showRestrictedSettingsDialog: Boolean = false,
    onRequestOverlayPermission: () -> Unit = {},
    onDismissOverlayPrompt: () -> Unit = {},
    onOpenRestrictedSettingsGuide: () -> Unit = {},
    onDismissRestrictedSettingsGuide: () -> Unit = {},
    onOpenAppInfo: () -> Unit = {},
    onLaunchDirectly: (GameItem) -> Unit = {},
    onTriggerOverlayService: () -> Unit = {},
    isClickSoundEnabled: Boolean = true,
    onToggleClickSound: () -> Unit = {},
    saturationState: SaturationUiState = SaturationUiState(),
    onApplySaturation: (Float) -> Unit = {},
    onSaveGameSettings: (GameItem) -> Unit = {},
    onRemoveGame: (GameItem) -> Unit = {},
    showControllerHints: Boolean = false,
    isGamepadConnected: Boolean = false,
    gamepadActions: Flow<GamepadAction> = emptyFlow(),
    onOpenSettings: () -> Unit = {},
    onViewDetails: (GameItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(GameSpaceTab.LIBRARY) }
    // Order the physical LB/RB buttons (and their on-screen badges) actually cycle through — matches the
    // left-to-right order of the tab labels themselves, so RB always lands on the tab visually to the right.
    val tabOrder = remember { listOf(GameSpaceTab.LIBRARY, GameSpaceTab.SUPER_BASE, GameSpaceTab.FAVORITES) }
    fun cycleTab(forward: Boolean) {
        val index = tabOrder.indexOf(activeTab).let { if (it < 0) 0 else it }
        val step = if (forward) 1 else -1
        activeTab = tabOrder[(index + step + tabOrder.size) % tabOrder.size]
    }
    var isControllerMenuOpen by remember { mutableStateOf(false) }
    var isAddGameDialogOpen by remember { mutableStateOf(false) }
    var isSaturationDialogOpen by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var currentTime by remember { mutableStateOf(timeFormat.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = timeFormat.format(Date())
            delay(15_000)
        }
    }

    val visibleGames = remember(games, searchQuery) {
        if (searchQuery.isBlank()) games
        else games.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // ---- Controller buttons (only ever arrive when a controller is connected) ----
    val currentVisibleGames by rememberUpdatedState(visibleGames)
    val currentSelectedGame by rememberUpdatedState(selectedGame)
    val currentOnSelectGame by rememberUpdatedState(onSelectGame)
    val currentOnStartGame by rememberUpdatedState(onStartGame)
    LaunchedEffect(gamepadActions) {
        gamepadActions.collect { action ->
            when (action) {
                GamepadAction.PREV_TAB -> cycleTab(forward = false)
                GamepadAction.NEXT_TAB -> cycleTab(forward = true)
                GamepadAction.SEARCH -> {
                    if (isSearching) {
                        isSearching = false
                        searchQuery = ""
                    } else {
                        isSearching = true
                        activeTab = GameSpaceTab.LIBRARY
                    }
                }
                GamepadAction.MENU -> isControllerMenuOpen = true
                GamepadAction.LAUNCH -> {
                    if (activeTab == GameSpaceTab.LIBRARY) currentOnStartGame(currentSelectedGame)
                }
                GamepadAction.LEFT, GamepadAction.RIGHT -> {
                    val list = currentVisibleGames
                    val index = list.indexOfFirst { it.id == currentSelectedGame.id }
                    val step = if (action == GamepadAction.LEFT) -1 else 1
                    if (activeTab == GameSpaceTab.LIBRARY && list.isNotEmpty()) {
                        val target = (index + step).coerceIn(0, list.lastIndex)
                        if (target != index) currentOnSelectGame(list[target])
                    }
                }
                GamepadAction.BACK -> {
                    if (isSearching) {
                        isSearching = false
                        searchQuery = ""
                    } else if (activeTab != GameSpaceTab.LIBRARY) {
                        activeTab = GameSpaceTab.LIBRARY
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkBg)
    ) {
        // The selected game's library art is the background (blurred + darkened, like a console UI)
        GameSpaceBackground(game = selectedGame)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xEB070A12), Color(0x99070A12), Color(0x59070A12))
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x8C070A12), Color.Transparent, Color(0xD9070A12))
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            GameSpaceTopBar(
                activeTab = activeTab,
                onTabChange = { activeTab = it },
                onCycleTab = { forward -> cycleTab(forward) },
                isSearching = isSearching,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = {
                    if (isSearching) {
                        isSearching = false
                        searchQuery = ""
                    } else {
                        isSearching = true
                        activeTab = GameSpaceTab.LIBRARY
                    }
                },
                stats = stats,
                currentTime = currentTime,
                showControllerHints = showControllerHints,
                isGamepadConnected = isGamepadConnected,
                onOpenSettings = onOpenSettings,
                hasOverlayPermission = hasOverlayPermission,
                onHudClick = {
                    if (hasOverlayPermission) onTriggerOverlayService()
                    else onOpenRestrictedSettingsGuide()
                },
                onAddGame = { isAddGameDialogOpen = true },
                onPurgeRam = onClearProcesses,
                onFixAccess = onOpenRestrictedSettingsGuide,
                onOpenSuperBase = { activeTab = GameSpaceTab.SUPER_BASE }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (activeTab == GameSpaceTab.LIBRARY) {
                    LibraryContent(
                        visibleGames = visibleGames,
                        selectedGame = selectedGame,
                        beatIntensity = if (isMusicMuted) 0f else beatIntensity,
                        showControllerHints = showControllerHints,
                        onSelectGame = onSelectGame,
                        onStartGame = onStartGame,
                        onAddGame = { isAddGameDialogOpen = true },
                        onOpenSaturation = { isSaturationDialogOpen = true },
                        onViewDetails = { onViewDetails(selectedGame) },
                        onRemoveGame = onRemoveGame,
                        onToggleFavorite = { game -> onSaveGameSettings(game.copy(isFavorite = !game.isFavorite)) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (activeTab == GameSpaceTab.FAVORITES) {
                    val favoriteGames = remember(games) { games.filter { it.isFavorite } }
                    LibraryContent(
                        visibleGames = favoriteGames,
                        selectedGame = selectedGame,
                        beatIntensity = if (isMusicMuted) 0f else beatIntensity,
                        showControllerHints = showControllerHints,
                        onSelectGame = onSelectGame,
                        onStartGame = onStartGame,
                        onAddGame = { isAddGameDialogOpen = true },
                        onOpenSaturation = { isSaturationDialogOpen = true },
                        onViewDetails = { onViewDetails(selectedGame) },
                        onRemoveGame = onRemoveGame,
                        onToggleFavorite = { game -> onSaveGameSettings(game.copy(isFavorite = !game.isFavorite)) },
                        emptyMessage = tr("no_favorite_games"),
                        showAddTile = false,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SuperBaseScreen(
                        stats = stats,
                        onClearProcesses = onClearProcesses,
                        hasOverlayPermission = hasOverlayPermission,
                        onOpenRestrictedSettingsGuide = onOpenRestrictedSettingsGuide,
                        onOpenAppInfo = onOpenAppInfo,
                        onOpenOverlaySettings = onRequestOverlayPermission,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            GameSpaceBottomBar(
                isMusicMuted = isMusicMuted,
                isVibrationEnabled = isVibrationEnabled,
                isTurboFanActive = isTurboFanActive,
                isClickSoundEnabled = isClickSoundEnabled,
                onToggleMuteMusic = onToggleMuteMusic,
                onToggleVibration = onToggleVibration,
                onToggleTurboFan = onToggleTurboFan,
                onToggleClickSound = onToggleClickSound,
                showControllerHints = showControllerHints,
                isMenuOpen = isControllerMenuOpen,
                onMenuOpenChange = { isControllerMenuOpen = it },
                onSearch = {
                    if (isSearching) {
                        isSearching = false
                        searchQuery = ""
                    } else {
                        isSearching = true
                        activeTab = GameSpaceTab.LIBRARY
                    }
                },
                onAddGame = { isAddGameDialogOpen = true },
                onPurgeRam = onClearProcesses,
                onFixAccess = onOpenRestrictedSettingsGuide,
                onOpenSuperBase = { activeTab = GameSpaceTab.SUPER_BASE }
            )
        }

        // Add Game Dialog
        if (isAddGameDialogOpen) {
            AddGameDialog(
                installedApps = installedApps,
                onDismiss = { isAddGameDialogOpen = false },
                onAddGame = { name, pkg ->
                    onAddGame(name, pkg)
                    isAddGameDialogOpen = false
                }
            )
        }

        // Saturation slider dialog
        if (isSaturationDialogOpen) {
            SaturationDialog(
                game = selectedGame,
                state = saturationState,
                onApply = onApplySaturation,
                onSave = { value -> onSaveGameSettings(selectedGame.copy(saturation = value)) },
                onDismiss = { isSaturationDialogOpen = false }
            )
        }

        // Game Not Installed Dialog Prompt
        if (notInstalledGame != null) {
            Dialog(onDismissRequest = onDismissNotInstalledPrompt) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF141722))
                        .padding(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = QboostBlue, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Game Not on Real Device", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (notInstalledGame.packageName.isBlank()) {
                                "${notInstalledGame.name} is not linked to an installed app yet. Add the app from your installed apps to launch it from here."
                            } else {
                                "${notInstalledGame.name} (${notInstalledGame.packageName}) is not downloaded on this device yet."
                            },
                            color = TextWhite,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Download it directly from Google Play Store or test the cockpit optimization overlay:",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = clickSound { onOpenPlayStore(notInstalledGame.packageName) },
                                enabled = notInstalledGame.packageName.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = QboostBlue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download on Google Play", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = clickSound { onLaunchCockpit(notInstalledGame) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282F42)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test In-Game Cockpit Overlay", color = TextWhite)
                            }
                            Button(
                                onClick = clickSound {
                                    onDismissNotInstalledPrompt()
                                    isAddGameDialogOpen = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2437)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Pick from Installed Device Apps (+)", color = QboostNeonCyan)
                            }
                        }
                    }
                }
            }
        }

        // Overlay Permission Required Dialog Prompt (e.g. for Minecraft & launched games)
        if (overlayPermissionPromptGame != null) {
            Dialog(onDismissRequest = onDismissOverlayPrompt) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF121622))
                        .padding(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Enable Floating Cockpit HUD", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "To display the real-time Qboost floating performance panel and FPS monitor over ${overlayPermissionPromptGame.name}, please allow 'Display over other apps' in Android settings.",
                            color = TextWhite,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1A2234))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "⚠️ Saw 'App was denied access'?",
                                    color = Color(0xFFFFB74D),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Android 13+ restricts sideloaded APKs from Chrome. Tap 'Fix Denied Access' below to unlock it in 2 taps via App Info (⋮)!",
                                    color = TextWhite.copy(alpha = 0.85f),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = clickSound(onOpenRestrictedSettingsGuide),
                                colors = ButtonDefaults.buttonColors(containerColor = QboostBlue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Fix 'Denied Access' & Allow Overlay", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = clickSound(onRequestOverlayPermission),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E283C)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open 'Display Over Other Apps' Direct", color = Color(0xFF00E5FF), fontSize = 12.sp)
                            }
                            Button(
                                onClick = clickSound { onLaunchDirectly(overlayPermissionPromptGame) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF171C28)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Launch Game Without Overlay", color = TextWhite)
                            }
                            Button(
                                onClick = clickSound(onDismissOverlayPrompt),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B2437)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel", color = TextGray)
                            }
                        }
                    }
                }
            }
        }

        // Dedicated Restricted Settings / Chrome Sideload Unlock Guide
        if (showRestrictedSettingsDialog) {
            RestrictedSettingsGuideDialog(
                game = overlayPermissionPromptGame,
                onOpenAppInfo = onOpenAppInfo,
                onOpenOverlaySettings = onRequestOverlayPermission,
                onLaunchDirectly = {
                    overlayPermissionPromptGame?.let { onLaunchDirectly(it) }
                },
                onDismiss = onDismissRestrictedSettingsGuide
            )
        }

        // Active Launch Toast / HUD Banner
        if (launchToast != null) {
            LaunchedEffect(launchToast) {
                delay(3500)
                onClearToast()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xF0121622))
                        .padding(horizontal = 22.dp, vertical = 10.dp)
                ) {
                    Text(launchToast, color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

    }
}

// ============================================================================================
//  Background
// ============================================================================================

@Composable
private fun GameSpaceBackground(game: GameItem) {
    val art = LibraryArt.forGame(game) ?: 0
    Crossfade(
        targetState = art,
        animationSpec = tween(durationMillis = 500),
        label = "game_space_background"
    ) { resId ->
        if (resId != 0) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(18.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(Color(game.iconColor).copy(alpha = 0.32f), CyberDarkBg)
                        )
                    )
            )
        }
    }
}

// ============================================================================================
//  Top bar:  ≡  LB  Library  Super Base  RB                    HUD  Hz  TURBO  🔍  🔋  time
// ============================================================================================

@Composable
private fun GameSpaceTopBar(
    activeTab: GameSpaceTab,
    onTabChange: (GameSpaceTab) -> Unit,
    onCycleTab: (Boolean) -> Unit,
    isSearching: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    stats: PerformanceStats,
    currentTime: String,
    showControllerHints: Boolean,
    isGamepadConnected: Boolean,
    onOpenSettings: () -> Unit,
    hasOverlayPermission: Boolean,
    onHudClick: () -> Unit,
    onAddGame: () -> Unit,
    onPurgeRam: () -> Unit,
    onFixAccess: () -> Unit,
    onOpenSuperBase: () -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    val batteryIcon = when {
        stats.isCharging -> Icons.Default.BatteryChargingFull
        stats.batteryPercent > 80 -> Icons.Default.BatteryFull
        stats.batteryPercent > 50 -> Icons.Default.Battery5Bar
        stats.batteryPercent > 20 -> Icons.Default.Battery3Bar
        else -> Icons.Default.BatteryAlert
    }
    val batteryColor = when {
        stats.isCharging -> QboostNeonGreen
        stats.batteryPercent <= 20 -> Color(0xFFFF5252)
        stats.batteryPercent <= 40 -> Color(0xFFFFB300)
        else -> TextWhite
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // ---------- LEFT: menu, LB, tabs, RB (or the search field) ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TopIconButton(
                    icon = Icons.Default.Menu,
                    description = tr("menu"),
                    tag = "main_menu_button",
                    onClick = { isMenuOpen = true }
                )
                MainMenu(
                    expanded = isMenuOpen,
                    onDismiss = { isMenuOpen = false },
                    onAddGame = onAddGame,
                    onPurgeRam = onPurgeRam,
                    onFixAccess = onFixAccess,
                    onOpenSuperBase = onOpenSuperBase
                )
            }
            Spacer(modifier = Modifier.width(10.dp))

            if (isSearching) {
                SearchField(query = searchQuery, onQueryChange = onSearchQueryChange)
            } else {
                if (showControllerHints) {
                    PadBadge(text = "LB", onClick = { onCycleTab(false) })
                    Spacer(modifier = Modifier.width(10.dp))
                }
                TabLabel(
                    text = tr("tab_library"),
                    selected = activeTab == GameSpaceTab.LIBRARY,
                    tag = "tab_library",
                    onClick = { onTabChange(GameSpaceTab.LIBRARY) }
                )
                Spacer(modifier = Modifier.width(20.dp))
                TabLabel(
                    text = tr("tab_super_base"),
                    selected = activeTab == GameSpaceTab.SUPER_BASE,
                    tag = "tab_super_base",
                    onClick = { onTabChange(GameSpaceTab.SUPER_BASE) }
                )
                Spacer(modifier = Modifier.width(20.dp))
                TabLabel(
                    text = tr("tab_favorites"),
                    selected = activeTab == GameSpaceTab.FAVORITES,
                    tag = "tab_favorites",
                    onClick = { onTabChange(GameSpaceTab.FAVORITES) }
                )
                if (showControllerHints) {
                    Spacer(modifier = Modifier.width(10.dp))
                    PadBadge(text = "RB", onClick = { onCycleTab(true) })
                }
            }
        }

        // ---------- RIGHT: search, battery, clock ----------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControllerStatusIcon(isConnected = isGamepadConnected)

            TopIconButton(
                icon = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                description = tr("search_games"),
                tag = "search_button",
                onClick = onToggleSearch
            )

            TopIconButton(
                icon = Icons.Default.Settings,
                description = tr("settings"),
                tag = "settings_button",
                onClick = onOpenSettings
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                WifiStatusIcon(isOnline = stats.isOnline)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = batteryIcon,
                    contentDescription = "Battery",
                    tint = batteryColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "${stats.batteryPercent}%",
                    color = batteryColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = currentTime,
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ControllerStatusIcon(isConnected: Boolean) {
    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.SportsEsports,
            contentDescription = tr("controller_status"),
            tint = if (isConnected) TextWhite else TextGray,
            modifier = Modifier.size(22.dp)
        )
        // Only shown once a real USB/OTG or Bluetooth controller is actually detected — no dot at all
        // otherwise, matching the reference: plain icon when offline, green dot once one is plugged in.
        if (isConnected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(QboostNeonGreen)
            )
        }
    }
}

@Composable
private fun WifiStatusIcon(isOnline: Boolean) {
    Icon(
        imageVector = Icons.Default.Wifi,
        contentDescription = tr("network_status"),
        tint = if (isOnline) TextWhite else TextGray.copy(alpha = 0.45f),
        modifier = Modifier.size(17.dp)
    )
}

@Composable
private fun TopIconButton(icon: ImageVector, description: String, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .qClickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = TextWhite,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** LB / RB style badge next to the tabs. Tapping it switches tab. */
@Composable
private fun PadBadge(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0x40FFFFFF))
            .qClickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) TextWhite else TextGray,
        fontSize = 16.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .qClickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .testTag(tag)
    )
}

@Composable
private fun StatusChip(
    text: String,
    color: Color,
    dotColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .then(if (onClick != null) Modifier.qClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(text = text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(color = TextWhite, fontSize = 13.sp),
        cursorBrush = SolidColor(QboostBlueGlow),
        modifier = Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x33FFFFFF))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .focusRequester(focusRequester)
            .testTag("game_search_field"),
        decorationBox = { innerTextField ->
            Box {
                if (query.isEmpty()) {
                    Text(text = tr("search_games"), color = TextGray, fontSize = 13.sp)
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun MainMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddGame: () -> Unit,
    onPurgeRam: () -> Unit,
    onFixAccess: () -> Unit,
    onOpenSuperBase: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(tr("add_game")) },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            onClick = clickSound {
                onDismiss()
                onAddGame()
            }
        )
        DropdownMenuItem(
            text = { Text(tr("tab_super_base")) },
            leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
            onClick = clickSound {
                onDismiss()
                onOpenSuperBase()
            }
        )
        DropdownMenuItem(
            text = { Text(tr("purge_ram")) },
            leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null) },
            onClick = clickSound {
                onDismiss()
                onPurgeRam()
            }
        )
        DropdownMenuItem(
            text = { Text(tr("hud_help")) },
            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            onClick = clickSound {
                onDismiss()
                onFixAccess()
            }
        )
    }
}

// ============================================================================================
//  Library: tile row + selected game info
// ============================================================================================

@Composable
private fun LibraryContent(
    visibleGames: List<GameItem>,
    selectedGame: GameItem,
    beatIntensity: Float,
    showControllerHints: Boolean,
    onSelectGame: (GameItem) -> Unit,
    onStartGame: (GameItem) -> Unit,
    onAddGame: () -> Unit,
    onOpenSaturation: () -> Unit,
    onViewDetails: () -> Unit,
    onRemoveGame: (GameItem) -> Unit,
    onToggleFavorite: (GameItem) -> Unit = {},
    emptyMessage: String? = null,
    showAddTile: Boolean = true,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val selectedIndex = visibleGames.indexOfFirst { it.id == selectedGame.id }
    var hasScrolledToSelection by remember { mutableStateOf(false) }
    LaunchedEffect(selectedGame.id, visibleGames.size) {
        if (selectedIndex >= 0) {
            if (hasScrolledToSelection) {
                listState.animateScrollToItem(selectedIndex)
            } else {
                // First time on screen: jump straight to the selected game
                listState.scrollToItem(selectedIndex)
                hasScrolledToSelection = true
            }
        }
    }

    var isGameMenuOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // ---------- Tile row ----------
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val selectedHeight = (maxHeight - 8.dp).coerceIn(84.dp, 138.dp)
            val normalHeight = selectedHeight * 0.84f

            if (visibleGames.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = emptyMessage ?: tr("no_games_match"), color = TextGray, fontSize = 13.sp)
                }
            } else {
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(visibleGames, key = { it.id }) { game ->
                        val isSelected = game.id == selectedGame.id
                        val tileHeight by animateDpAsState(
                            targetValue = if (isSelected) selectedHeight else normalHeight,
                            animationSpec = tween(durationMillis = 220),
                            label = "tile_height"
                        )
                        GameTile(
                            game = game,
                            isSelected = isSelected,
                            tileHeight = tileHeight,
                            beatIntensity = beatIntensity,
                            showControllerHints = showControllerHints,
                            onClick = {
                                // Like a console launcher: tap to focus, tap the focused tile to launch
                                if (isSelected) onStartGame(game) else onSelectGame(game)
                            }
                        )
                    }
                    item(key = "add_game_tile") {
                        if (showAddTile) AddGameTile(tileHeight = normalHeight, onClick = onAddGame)
                    }
                }
            }
        }

        // ---------- Selected game info ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, bottom = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (selectedGame.isInstalled) Icons.Default.PhoneAndroid else Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = TextGray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedGame.name,
                    color = TextWhite,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${selectedGame.performanceMode.label()} • ${selectedGame.targetFps} ${tr("fps_cap")} • ",
                    color = TextGray,
                    fontSize = 11.sp,
                    maxLines = 1
                )
                val installed = selectedGame.isInstalled
                Text(
                    text = when {
                        installed -> tr("installed_on_device")
                        selectedGame.packageName.isBlank() -> tr("not_linked")
                        else -> tr("not_installed")
                    },
                    color = if (installed) QboostNeonGreen else Color(0xFFFFB300),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start Game
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(QboostBlueDark, QboostBlue)))
                        .qClickable { onStartGame(selectedGame) }
                        .padding(horizontal = 22.dp)
                        .testTag("start_game_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedGame.isInstalled) tr("start_game") else tr("install_play"),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // View details
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33FFFFFF))
                        .qClickable { onViewDetails() }
                        .padding(horizontal = 16.dp)
                        .testTag("view_details_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("view_details"),
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // More (...)
                Box {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .qClickable { isGameMenuOpen = true }
                            .testTag("game_menu_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "More",
                            tint = Color(0xFF10141C),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = isGameMenuOpen,
                        onDismissRequest = { isGameMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(tr("saturation")) },
                            leadingIcon = { Icon(Icons.Default.ColorLens, contentDescription = null) },
                            onClick = clickSound {
                                isGameMenuOpen = false
                                onOpenSaturation()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (selectedGame.isFavorite) tr("remove_from_favorites") else tr("add_to_favorites")) },
                            leadingIcon = {
                                Icon(
                                    if (selectedGame.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null
                                )
                            },
                            onClick = clickSound {
                                isGameMenuOpen = false
                                onToggleFavorite(selectedGame)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(tr("add_game")) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = clickSound {
                                isGameMenuOpen = false
                                onAddGame()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(tr("remove_library")) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = clickSound {
                                isGameMenuOpen = false
                                onRemoveGame(selectedGame)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameTile(
    game: GameItem,
    isSelected: Boolean,
    tileHeight: Dp,
    beatIntensity: Float,
    showControllerHints: Boolean,
    onClick: () -> Unit
) {
    val art = LibraryArt.forGame(game)
    val icon = if (art == null) rememberAppIcon(game.packageName, game.isInstalled) else null

    Box(
        modifier = Modifier
            .size(width = tileHeight * TILE_ASPECT, height = tileHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF131A29))
            .selectionOutline(enabled = isSelected)
            .qClickable(onClick = onClick)
            .testTag("game_tile_${game.id}")
    ) {
        if (art != null) {
            Image(
                painter = painterResource(id = art),
                contentDescription = game.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            FallbackTileArt(game = game, icon = icon, showName = !isSelected)
        }

        // Non-focused tiles are dimmed, the focused tile is at full brightness and a bit larger
        if (!isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x59000000))
            )
        }

        if (isSelected) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(start = 10.dp, end = 10.dp, top = 22.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showControllerHints) {
                    // The "A" button hint only appears when a controller is connected
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "A", color = Color(0xFF10141C), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = if (game.isInstalled) tr("launch_game") else tr("install_play"),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }

        if (!game.isInstalled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Not installed",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        if (game.isFavorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFF5C7A),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
@Composable
private fun FallbackTileArt(game: GameItem, icon: ImageBitmap?, showName: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(game.iconColor).copy(alpha = 0.85f), Color(0xFF0E1422))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = game.name,
                modifier = Modifier
                    .fillMaxHeight(0.52f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
            )
        } else {
            Text(
                text = game.initials,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )
        }
        if (showName) {
            Text(
                text = game.name,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun rememberAppIcon(packageName: String, isInstalled: Boolean): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName, isInstalled) {
        if (!isInstalled || packageName.isBlank()) {
            null
        } else {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmap(160, 160).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Composable
private fun AddGameTile(tileHeight: Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(width = tileHeight, height = tileHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x33FFFFFF))
            .qClickable(onClick = onClick)
            .testTag("add_game_launcher_button"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Game",
            tint = TextWhite,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = tr("add_game"), color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ============================================================================================
//  Bottom bar:  🎵 📳 🌀 👆                                   Ⓨ Search   ≡ Menu
// ============================================================================================

@Composable
private fun GameSpaceBottomBar(
    isMusicMuted: Boolean,
    isVibrationEnabled: Boolean,
    isTurboFanActive: Boolean,
    isClickSoundEnabled: Boolean,
    onToggleMuteMusic: () -> Unit,
    onToggleVibration: () -> Unit,
    onToggleTurboFan: () -> Unit,
    onToggleClickSound: () -> Unit,
    showControllerHints: Boolean,
    isMenuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onAddGame: () -> Unit,
    onPurgeRam: () -> Unit,
    onFixAccess: () -> Unit,
    onOpenSuperBase: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RoundToggle(
                icon = if (isMusicMuted) Icons.Default.MusicOff else Icons.Default.MusicNote,
                active = !isMusicMuted,
                description = "Toggle Music",
                tag = "mute_music_button",
                onClick = onToggleMuteMusic
            )
            RoundToggle(
                icon = Icons.Default.Vibration,
                active = isVibrationEnabled,
                description = "Toggle Haptics",
                tag = "toggle_vibration_button",
                onClick = onToggleVibration
            )
            RoundToggle(
                icon = Icons.Default.Air,
                active = isTurboFanActive,
                description = "Toggle Turbo Fan",
                tag = "toggle_turbo_fan_button",
                onClick = onToggleTurboFan
            )
            RoundToggle(
                icon = Icons.Default.TouchApp,
                active = isClickSoundEnabled,
                description = "Toggle Click Sound",
                tag = "toggle_click_sound_button",
                onClick = onToggleClickSound
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (showControllerHints) {
                ButtonHint(letter = "Y", label = tr("search"), icon = null, onClick = onSearch)
            }
            // The menu anchor always exists so the controller's Menu button can open it
            Box {
                if (showControllerHints) {
                    ButtonHint(
                        letter = "",
                        label = tr("menu"),
                        icon = Icons.Default.Menu,
                        onClick = { onMenuOpenChange(true) }
                    )
                }
                MainMenu(
                    expanded = isMenuOpen,
                    onDismiss = { onMenuOpenChange(false) },
                    onAddGame = onAddGame,
                    onPurgeRam = onPurgeRam,
                    onFixAccess = onFixAccess,
                    onOpenSuperBase = onOpenSuperBase
                )
            }
        }
    }
}

@Composable
private fun RoundToggle(
    icon: ImageVector,
    active: Boolean,
    description: String,
    tag: String,
    onClick: () -> Unit
) {
    // Icon only: no circle / background (0% opacity). On = blue, off = dim gray.
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .qClickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) QboostBlueGlow else TextGray.copy(alpha = 0.65f),
            modifier = Modifier.size(22.dp)
        )
    }
}

/** "Y Search" / "≡ Menu" style hint. They are touch buttons. */
@Composable
private fun ButtonHint(letter: String, label: String, icon: ImageVector?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .qClickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color(0xFF10141C),
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Text(text = letter, color = Color(0xFF10141C), fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ============================================================================================
//  Helpers
// ============================================================================================

@Composable
private fun PerformanceMode.label(): String = when (this) {
    PerformanceMode.PERFORMANCE -> tr("mode_performance")
    PerformanceMode.BALANCED -> tr("mode_balanced")
    PerformanceMode.BATTERY -> tr("mode_battery")
}

/**
 * The white outline of the focused game tile: a faint white ring with a bright white "comet" that
 * keeps travelling around it, like the loading outline in GameHub.
 */
@Composable
private fun Modifier.selectionOutline(
    enabled: Boolean,
    cornerRadius: Dp = 20.dp,
    strokeWidth: Dp = 3.dp
): Modifier {
    if (!enabled) return this
    val transition = rememberInfiniteTransition(label = "selection_outline")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "selection_outline_progress"
    )
    return this.drawWithContent {
        drawContent()
        val stroke = strokeWidth.toPx()
        val half = stroke / 2f
        val radius = (cornerRadius.toPx() - half).coerceAtLeast(0f)
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = half,
                    top = half,
                    right = size.width - half,
                    bottom = size.height - half,
                    cornerRadius = CornerRadius(radius, radius)
                )
            )
        }
        val measure = PathMeasure()
        measure.setPath(path, false)
        val length = measure.length
        if (length > 0f) {
            // faint static ring
            drawPath(path = path, color = Color.White.copy(alpha = 0.30f), style = Stroke(width = stroke))
            // bright head with a fading tail
            val head = progress.value * length
            val slices = 14
            val slice = (length * 0.42f) / slices
            for (i in 0 until slices) {
                val end = head - i * slice
                val alpha = (1f - i / slices.toFloat()) * 0.95f
                drawOutlineSegment(measure, end - slice, end, length, Color.White.copy(alpha = alpha), stroke)
            }
        }
    }
}

private fun DrawScope.drawOutlineSegment(
    measure: PathMeasure,
    start: Float,
    end: Float,
    length: Float,
    color: Color,
    strokeWidth: Float
) {
    fun wrap(v: Float): Float {
        var r = v % length
        if (r < 0f) r += length
        return r
    }
    val s = wrap(start)
    val e = wrap(end)
    val segment = Path()
    if (s <= e) {
        measure.getSegment(s, e, segment, true)
    } else {
        measure.getSegment(s, length, segment, true)
        measure.getSegment(0f, e, segment, true)
    }
    drawPath(path = segment, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}
