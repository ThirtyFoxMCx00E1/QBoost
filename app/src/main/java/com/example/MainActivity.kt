package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.UiSounds
import com.example.i18n.I18n
import com.example.i18n.LocalLanguage
import com.example.input.GamepadAction
import com.example.input.GamepadMonitor
import com.example.net.UpdateChecker
import com.example.ui.GameDetailsScreen
import com.example.ui.SettingsScreen
import com.example.ui.AppScreen
import com.example.ui.components.InGameCockpitScreen
import com.example.ui.QboostViewModel
import com.example.ui.QboostGameSpaceScreen
import com.example.ui.SplashScreen
import com.example.ui.theme.CyberDarkBg
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: QboostViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate(): this is what takes over the OS's own cold-start frame (see
        // Theme.App.Starting in themes.xml) instead of leaving it to draw its default one. No
        // setKeepOnScreenCondition here on purpose — it should dismiss the moment our first frame is up,
        // handing straight off to SplashScreen.kt's own animated splash, not hold on screen itself.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        UiSounds.init(this)
        I18n.init(this)
        enableEdgeToEdge()

        // Hide system status/navigation bars for immersive gaming console experience
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CyberDarkBg
                ) {
                    var showSplash by remember { mutableStateOf(true) }
                    AnimatedContent(
                        targetState = showSplash,
                        transitionSpec = {
                            fadeIn(tween(700)) togetherWith fadeOut(tween(450))
                        },
                        label = "splash_to_app"
                    ) { splashing ->
                        if (splashing) {
                            SplashScreen(
                                onFinished = {
                                    viewModel.startLobbyMusic()
                                    showSplash = false
                                }
                            )
                        } else {
                            QboostApp(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    // ---------------- Game controller (USB / OTG / Bluetooth) ----------------
    private var lastAxisDirection = 0

    private fun isGamepadEvent(source: Int) = GamepadMonitor.isGamepadSource(source)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && isGamepadEvent(event.source)) {
            val action = when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_L1 -> GamepadAction.PREV_TAB
                KeyEvent.KEYCODE_BUTTON_R1 -> GamepadAction.NEXT_TAB
                KeyEvent.KEYCODE_BUTTON_Y -> GamepadAction.SEARCH
                KeyEvent.KEYCODE_BUTTON_START -> GamepadAction.MENU
                KeyEvent.KEYCODE_BUTTON_A -> GamepadAction.LAUNCH
                KeyEvent.KEYCODE_BUTTON_B -> GamepadAction.BACK
                KeyEvent.KEYCODE_DPAD_LEFT -> GamepadAction.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> GamepadAction.RIGHT
                else -> null
            }
            if (action != null && viewModel.dispatchGamepad(action)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Some controllers report the D-pad / left stick as axes instead of keys. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (isGamepadEvent(event.source) && event.action == MotionEvent.ACTION_MOVE) {
            val hat = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val value = if (kotlin.math.abs(hat) > 0.5f) hat else event.getAxisValue(MotionEvent.AXIS_X)
            val direction = when {
                value < -0.7f -> -1
                value > 0.7f -> 1
                else -> 0
            }
            if (direction != lastAxisDirection) {
                lastAxisDirection = direction
                if (direction == -1) viewModel.dispatchGamepad(GamepadAction.LEFT)
                if (direction == 1) viewModel.dispatchGamepad(GamepadAction.RIGHT)
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }
}

@Composable
fun QboostApp(viewModel: QboostViewModel) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val games by viewModel.games.collectAsStateWithLifecycle()
    val selectedGame by viewModel.selectedGame.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val overlayConfig by viewModel.overlayConfig.collectAsStateWithLifecycle()
    val isMusicMuted by viewModel.isMusicMuted.collectAsStateWithLifecycle()
    val isVibrationEnabled by viewModel.isVibrationEnabled.collectAsStateWithLifecycle()
    val isTurboFanActive by viewModel.isTurboFanActive.collectAsStateWithLifecycle()
    val isClickSoundEnabled by viewModel.isClickSoundEnabled.collectAsStateWithLifecycle()
    val saturationState by viewModel.saturationState.collectAsStateWithLifecycle()
    val beatIntensity by viewModel.beatIntensity.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val notInstalledGamePrompt by viewModel.notInstalledGamePrompt.collectAsStateWithLifecycle()
    val overlayPermissionPromptGame by viewModel.overlayPermissionPromptGame.collectAsStateWithLifecycle()
    val showRestrictedSettingsDialog by viewModel.showRestrictedSettingsDialog.collectAsStateWithLifecycle()
    val hasOverlayPermission by viewModel.hasOverlayPermission.collectAsStateWithLifecycle()
    val launchToast by viewModel.launchToast.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val showControllerHints by viewModel.showControllerHints.collectAsStateWithLifecycle()
    val isGamepadConnected by viewModel.isGamepadConnected.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val detailsGame by viewModel.detailsGame.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()
    val detailsLoading by viewModel.detailsLoading.collectAsStateWithLifecycle()

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onResume()
    }

    val appInfoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onResume()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> viewModel.refreshNotificationState() }

    // Settings > Notifications > Push notifications: ask once, then send the user to the system settings
    val openNotificationSettings: () -> Unit = {
        val prefs = context.getSharedPreferences("qboost_settings", Context.MODE_PRIVATE)
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission && !prefs.getBoolean("asked_notification_permission", false)) {
            prefs.edit().putBoolean("asked_notification_permission", true).apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            try {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                }
                appInfoLauncher.launch(intent)
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val layoutDirection = if (I18n.isRtl(settings.language)) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalLanguage provides settings.language,
        LocalLayoutDirection provides layoutDirection
    ) {
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            AppScreen.LOBBY -> {
                QboostGameSpaceScreen(
                    games = games,
                    selectedGame = selectedGame,
                    stats = stats,
                    isMusicMuted = isMusicMuted,
                    isVibrationEnabled = isVibrationEnabled,
                    isTurboFanActive = isTurboFanActive,
                    isClickSoundEnabled = isClickSoundEnabled,
                    saturationState = saturationState,
                    beatIntensity = beatIntensity,
                    installedApps = installedApps,
                    onSelectGame = { viewModel.selectGame(it) },
                    onStartGame = { viewModel.startGame(it) },
                    onAddGame = { name, pkg -> viewModel.addGame(name, pkg) },
                    onToggleMuteMusic = { viewModel.toggleMuteMusic() },
                    onToggleVibration = { viewModel.toggleVibration() },
                    onToggleTurboFan = { viewModel.toggleTurboFan() },
                    onToggleClickSound = { viewModel.toggleClickSound() },
                    onApplySaturation = { viewModel.applySaturation(it) },
                    onSaveGameSettings = { updatedGame -> viewModel.updateGameSettings(updatedGame) },
                    onRemoveGame = { removed -> viewModel.removeGame(removed.id) },
                    onClearProcesses = { viewModel.clearProcesses() },
                    notInstalledGame = notInstalledGamePrompt,
                    launchToast = launchToast,
                    onDismissNotInstalledPrompt = { viewModel.dismissNotInstalledPrompt() },
                    onOpenPlayStore = { pkg -> viewModel.openPlayStoreForGame(pkg) },
                    onLaunchCockpit = { game -> viewModel.launchCockpitScreen(game) },
                    onClearToast = { viewModel.clearLaunchToast() },
                    hasOverlayPermission = hasOverlayPermission,
                    overlayPermissionPromptGame = overlayPermissionPromptGame,
                    showRestrictedSettingsDialog = showRestrictedSettingsDialog,
                    onRequestOverlayPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                overlayPermissionLauncher.launch(intent)
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                overlayPermissionLauncher.launch(intent)
                            }
                        }
                    },
                    onDismissOverlayPrompt = { viewModel.dismissOverlayPermissionPrompt() },
                    onOpenRestrictedSettingsGuide = { viewModel.openRestrictedSettingsGuide() },
                    onDismissRestrictedSettingsGuide = { viewModel.dismissRestrictedSettingsGuide() },
                    onOpenAppInfo = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            appInfoLauncher.launch(intent)
                        } catch (_: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                            appInfoLauncher.launch(intent)
                        }
                    },
                    onLaunchDirectly = { game -> viewModel.launchGameDirectly(game) },
                    onTriggerOverlayService = { viewModel.startSystemOverlayService() },
                    showControllerHints = showControllerHints,
                    isGamepadConnected = isGamepadConnected,
                    gamepadActions = viewModel.gamepadActions,
                    onOpenSettings = { viewModel.openSettings() },
                    onViewDetails = { viewModel.openDetails(it) }
                )
            }
            AppScreen.SETTINGS -> {
                SettingsScreen(
                    settings = settings,
                    isMusicMuted = isMusicMuted,
                    isVibrationEnabled = isVibrationEnabled,
                    isClickSoundEnabled = isClickSoundEnabled,
                    isControllerConnected = isGamepadConnected,
                    notificationsEnabled = notificationsEnabled,
                    updateState = updateState,
                    onBack = { viewModel.closeSettings() },
                    onLanguageChange = { viewModel.setLanguage(it) },
                    onControllerHintsChange = { viewModel.setControllerHints(it) },
                    onToggleMusic = { viewModel.toggleMuteMusic() },
                    onMusicVolumeChange = { viewModel.setMusicVolume(it) },
                    onToggleVibration = { viewModel.toggleVibration() },
                    onToggleClickSound = { viewModel.toggleClickSound() },
                    onOpenNotificationSettings = openNotificationSettings,
                    onUpdateAlertsChange = { viewModel.setUpdateAlerts(it) },
                    onHandleOpacityChange = { viewModel.setHandleOpacity(it) },
                    onPanelOpacityChange = { viewModel.setPanelOpacity(it) },
                    onShowDockChange = { viewModel.setShowDock(it) },
                    onScalerSharpnessChange = { viewModel.setScalerSharpness(it) },
                    onResetSaturation = { viewModel.resetSaturation() },
                    onCheckUpdates = { viewModel.checkForUpdates() },
                    onOpenGithub = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.REPO_URL)))
                        } catch (_: Exception) {
                        }
                    }
                )
            }
            AppScreen.DETAILS -> {
                val shown = detailsGame
                if (shown != null) {
                    // use the tile from the library list so "installed" is always current
                    val current = games.firstOrNull { it.id == shown.id } ?: shown
                    GameDetailsScreen(
                        game = current,
                        details = details,
                        isLoading = detailsLoading,
                        onBack = { viewModel.closeDetails() },
                        onTrailerAudioDuckChange = { ducked -> viewModel.setTrailerAudioDucked(ducked) },
                        onStart = {
                            if (!current.isInstalled && current.packageName.isNotBlank()) {
                                viewModel.openPlayStoreForGame(current.packageName)
                            } else {
                                viewModel.closeDetails()
                                viewModel.startGame(current)
                            }
                        }
                    )
                }
            }
            AppScreen.IN_GAME -> {
                InGameCockpitScreen(
                    game = selectedGame,
                    stats = stats,
                    overlayConfig = overlayConfig,
                    onBackToLobby = { viewModel.backToLobby() },
                    onUpdateGameSettings = { updatedGame -> viewModel.updateGameSettings(updatedGame) },
                    onUpdateOverlayConfig = { newConfig -> viewModel.updateOverlayConfig(newConfig) },
                    onOptimizeProcesses = { viewModel.clearProcesses() }
                )
            }
        }
    }
    }
}

