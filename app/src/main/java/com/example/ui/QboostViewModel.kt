package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.LobbyMusicManager
import com.example.audio.UiSounds
import com.example.data.GameRepository
import com.example.display.SaturationEngine
import com.example.display.SaturationStore
import com.example.details.GameDetails
import com.example.details.GameDetailsRepository
import com.example.hardware.SystemPerformanceMonitor
import com.example.input.GamepadAction
import com.example.input.GamepadMonitor
import com.example.model.GameItem
import com.example.model.InstalledAppItem
import com.example.model.OverlayConfig
import com.example.model.PerformanceMode
import com.example.model.PerformanceStats
import com.example.model.SaturationUiState
import com.example.net.UpdateChecker
import com.example.net.UpdateNotifier
import com.example.service.QboostOverlayService
import com.example.settings.AppSettings
import com.example.settings.SettingsStore
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Result of "Check for updates" in Settings. */
sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    object UpToDate : UpdateUiState()
    data class Available(val latest: String) : UpdateUiState()
    object Failed : UpdateUiState()
}

enum class AppScreen {
    LOBBY,
    IN_GAME,
    SETTINGS,
    DETAILS
}

class QboostViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository(application)
    private val monitor = SystemPerformanceMonitor(application)
    private val musicManager = LobbyMusicManager(application)

    val games: StateFlow<List<GameItem>> = repository.games
    val selectedGame: StateFlow<GameItem> = repository.selectedGame
    val overlayConfig: StateFlow<OverlayConfig> = repository.overlayConfig

    val stats: StateFlow<PerformanceStats> = monitor.stats
    val isMusicMuted: StateFlow<Boolean> = musicManager.isMuted
    val beatIntensity: StateFlow<Float> = musicManager.beatIntensity

    private val _currentScreen = MutableStateFlow(AppScreen.LOBBY)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _isVibrationEnabled = MutableStateFlow(repository.isVibrationEnabled())
    val isVibrationEnabled: StateFlow<Boolean> = _isVibrationEnabled.asStateFlow()

    private val _isTurboFanActive = MutableStateFlow(repository.isTurboFanActive())
    val isTurboFanActive: StateFlow<Boolean> = _isTurboFanActive.asStateFlow()

    private val _isClickSoundEnabled = MutableStateFlow(UiSounds.isEnabled())
    val isClickSoundEnabled: StateFlow<Boolean> = _isClickSoundEnabled.asStateFlow()

    private val _saturationState = MutableStateFlow(SaturationUiState())
    val saturationState: StateFlow<SaturationUiState> = _saturationState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _notInstalledGamePrompt = MutableStateFlow<GameItem?>(null)
    val notInstalledGamePrompt: StateFlow<GameItem?> = _notInstalledGamePrompt.asStateFlow()

    private val _overlayPermissionPromptGame = MutableStateFlow<GameItem?>(null)
    val overlayPermissionPromptGame: StateFlow<GameItem?> = _overlayPermissionPromptGame.asStateFlow()

    private val _showRestrictedSettingsDialog = MutableStateFlow(false)
    val showRestrictedSettingsDialog: StateFlow<Boolean> = _showRestrictedSettingsDialog.asStateFlow()

    private val _hasOverlayPermission = MutableStateFlow(checkOverlayPermission())
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    private val _launchToast = MutableStateFlow<String?>(null)
    val launchToast: StateFlow<String?> = _launchToast.asStateFlow()

    // ---- v8.0: settings, controller, updates ----
    private val _settings = MutableStateFlow(SettingsStore.load(application))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val gamepadMonitor = GamepadMonitor(application)
    val isGamepadConnected: StateFlow<Boolean> = gamepadMonitor.isConnected

    /** LB / RB / Y / Menu hints are only shown when a controller is connected (or when forced in Settings). */
    val showControllerHints: StateFlow<Boolean> = combine(_settings, gamepadMonitor.isConnected) { s, connected ->
        when (s.controllerHints) {
            AppSettings.CONTROLLER_HINTS_ALWAYS -> true
            AppSettings.CONTROLLER_HINTS_NEVER -> false
            else -> connected
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _gamepadActions = MutableSharedFlow<GamepadAction>(extraBufferCapacity = 8)
    val gamepadActions: SharedFlow<GamepadAction> = _gamepadActions.asSharedFlow()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    // ---- v8.6: game details + notifications ----
    private val detailsRepository = GameDetailsRepository(application)
    private var detailsJob: Job? = null

    private val _detailsGame = MutableStateFlow<GameItem?>(null)
    val detailsGame: StateFlow<GameItem?> = _detailsGame.asStateFlow()

    private val _details = MutableStateFlow(GameDetails())
    val details: StateFlow<GameDetails> = _details.asStateFlow()

    private val _detailsLoading = MutableStateFlow(false)
    val detailsLoading: StateFlow<Boolean> = _detailsLoading.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(
        NotificationManagerCompat.from(application).areNotificationsEnabled()
    )
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    init {
        UiSounds.init(application)
        _isClickSoundEnabled.value = UiSounds.isEnabled()

        gamepadMonitor.start()
        musicManager.setVolumeScale(_settings.value.musicVolume)

        // "Update alerts": at most one GitHub check every 12 hours, then a normal notification
        viewModelScope.launch(Dispatchers.IO) {
            try {
                UpdateNotifier.checkAndNotify(application)
            } catch (_: Exception) {
            }
        }

        // Start monitor tracking (real telemetry, paused while Qboost is in the background)
        monitor.start(viewModelScope)

        // Load installed apps
        refreshInstalledApps()
    }

    fun checkOverlayPermission(): Boolean {
        val app = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(app)
        } else {
            true
        }
    }

    fun dismissOverlayPermissionPrompt() {
        _overlayPermissionPromptGame.value = null
    }

    fun openRestrictedSettingsGuide() {
        _showRestrictedSettingsDialog.value = true
    }

    fun dismissRestrictedSettingsGuide() {
        _showRestrictedSettingsDialog.value = false
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            repository.refreshInstallStates()
            _installedApps.value = repository.getInstalledApps()
        }
    }

    fun selectGame(game: GameItem) {
        repository.selectGame(game)
    }

    private var lobbyMusicStarted = false

    /** Called once the launch splash screen finishes — not from [init], so the lobby theme never plays
     *  underneath the splash's own sfx. Guarded so a stray second call (e.g. a config change replaying
     *  the splash) can't restart the loop from the beginning. */
    fun startLobbyMusic() {
        if (lobbyMusicStarted) return
        lobbyMusicStarted = true
        musicManager.startMusic(viewModelScope, initiallyMuted = repository.isMusicMuted())
    }

    fun startGame(game: GameItem) {
        repository.selectGame(game)
        if (_isVibrationEnabled.value) {
            musicManager.triggerHapticBeat(true)
        }

        val app = getApplication<Application>()
        val isInstalled = repository.isPackageInstalled(game.packageName)

        if (isInstalled && game.packageName.isNotBlank()) {
            if (!checkOverlayPermission()) {
                // Prompt user to enable overlay permission so Qboost panel displays over Minecraft / games
                _overlayPermissionPromptGame.value = game
                return
            }
            launchGameDirectly(game)
            return
        }

        // If not installed on real device, show real action prompt to download or test cockpit
        _notInstalledGamePrompt.value = game
    }

    fun launchGameDirectly(game: GameItem) {
        val app = getApplication<Application>()
        clearProcesses()
        val pm = app.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(game.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Hand the game's saturation to the in-game HUD, which applies it while you play
            SaturationStore.set(app, repository.saturationKey(game), game.saturation)

            // Start Qboost floating assistant if overlay permission is granted
            if (checkOverlayPermission()) {
                startSystemOverlayService(game.name, game.packageName, applySaturation = true)
            }

            _launchToast.value = "⚡ QBOOST v9.4: Turbo Boosted! Launching ${game.name}..."
            app.startActivity(launchIntent)
            musicManager.pause()
        }
        _overlayPermissionPromptGame.value = null
    }

    fun dismissNotInstalledPrompt() {
        _notInstalledGamePrompt.value = null
    }

    fun clearLaunchToast() {
        _launchToast.value = null
    }

    fun openPlayStoreForGame(packageName: String) {
        val app = getApplication<Application>()
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(marketIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(webIntent)
        }
        _notInstalledGamePrompt.value = null
    }

    fun launchCockpitScreen(game: GameItem) {
        _notInstalledGamePrompt.value = null
        _currentScreen.value = AppScreen.IN_GAME
        musicManager.pause()
    }

    fun backToLobby() {
        _currentScreen.value = AppScreen.LOBBY
        // Resume lobby music
        musicManager.resume()
        refreshInstalledApps()
    }

    fun addGame(name: String, packageName: String = "") {
        repository.addGame(name, packageName)
        refreshInstalledApps()
    }

    fun removeGame(id: String) {
        repository.removeGame(id)
        refreshInstalledApps()
    }

    fun updateGameSettings(game: GameItem) {
        repository.updateGame(game)
    }

    fun updateOverlayConfig(newConfig: OverlayConfig) {
        repository.updateOverlayConfig(newConfig)
    }

    // ---------------- Settings ----------------
    fun openSettings() {
        _currentScreen.value = AppScreen.SETTINGS
    }

    fun closeSettings() {
        _currentScreen.value = AppScreen.LOBBY
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        SettingsStore.save(getApplication<Application>(), updated)
    }

    fun setLanguage(code: String) = updateSettings { it.copy(language = code) }

    fun setControllerHints(mode: Int) = updateSettings { it.copy(controllerHints = mode) }

    fun setMusicVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        musicManager.setVolumeScale(v)
        updateSettings { it.copy(musicVolume = v) }
    }

    fun setHandleOpacity(opacity: Float) = updateSettings { it.copy(handleOpacity = opacity.coerceIn(0f, 1f)) }

    fun setPanelOpacity(opacity: Float) = updateSettings { it.copy(panelOpacity = opacity.coerceIn(0.3f, 1f)) }

    fun setShowDock(show: Boolean) = updateSettings { it.copy(showDock = show) }

    fun setScalerSharpness(value: Float) = updateSettings { it.copy(scalerSharpness = value.coerceIn(0f, 1f)) }

    fun setUpdateAlerts(enabled: Boolean) = updateSettings { it.copy(updateAlerts = enabled) }

    /** The push-notification switch mirrors the real Android setting; call again after the user comes back from it. */
    fun refreshNotificationState() {
        _notificationsEnabled.value = NotificationManagerCompat.from(getApplication<Application>()).areNotificationsEnabled()
    }

    // ---------------- Game details ----------------
    fun openDetails(game: GameItem) {
        detailsJob?.cancel()
        _detailsGame.value = game
        _details.value = GameDetails(title = game.name)
        _detailsLoading.value = true
        _currentScreen.value = AppScreen.DETAILS
        val language = _settings.value.language
        detailsJob = viewModelScope.launch(Dispatchers.IO) {
            // what the phone already knows (engine, size ...) shows up first, then Firebase + Google Play
            try {
                _details.value = detailsRepository.quick(game)
            } catch (_: Exception) {
            }
            val loaded = try {
                detailsRepository.load(game, language)
            } catch (_: Exception) {
                null
            }
            if (loaded != null) _details.value = loaded
            _detailsLoading.value = false
        }
    }

    fun closeDetails() {
        detailsJob?.cancel()
        _detailsLoading.value = false
        _currentScreen.value = AppScreen.LOBBY
        musicManager.setDucked(false)
    }

    fun checkForUpdates() {
        if (_updateState.value == UpdateUiState.Checking) return
        _updateState.value = UpdateUiState.Checking
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val current = try {
                app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0"
            } catch (_: Exception) {
                "0"
            }
            _updateState.value = when (val result = UpdateChecker.check(current)) {
                is UpdateChecker.Result.Available -> UpdateUiState.Available(result.latest)
                UpdateChecker.Result.UpToDate -> UpdateUiState.UpToDate
                UpdateChecker.Result.Failed -> UpdateUiState.Failed
            }
        }
    }

    /**
     * Controller buttons from MainActivity. Returns true when the press was used by Qboost, so the
     * activity should swallow it.
     */
    fun dispatchGamepad(action: GamepadAction): Boolean {
        return when (_currentScreen.value) {
            AppScreen.LOBBY -> {
                _gamepadActions.tryEmit(action)
                true
            }
            AppScreen.SETTINGS -> {
                if (action == GamepadAction.BACK) {
                    closeSettings()
                    true
                } else {
                    false
                }
            }
            AppScreen.DETAILS -> {
                if (action == GamepadAction.BACK) {
                    closeDetails()
                    true
                } else {
                    false
                }
            }
            AppScreen.IN_GAME -> false
        }
    }

    fun toggleMuteMusic() {
        val muted = musicManager.toggleMute()
        repository.setMusicMuted(muted)
    }

    /** While a trailer is playing on the details screen, duck the lobby theme so it isn't fighting it. */
    fun setTrailerAudioDucked(ducked: Boolean) {
        musicManager.setDucked(ducked)
    }

    fun toggleVibration() {
        val newVal = !_isVibrationEnabled.value
        _isVibrationEnabled.value = newVal
        repository.setVibrationEnabled(newVal)
        if (newVal) {
            musicManager.triggerHapticBeat(true)
        }
    }

    fun toggleClickSound() {
        val newVal = !_isClickSoundEnabled.value
        _isClickSoundEnabled.value = newVal
        UiSounds.setEnabled(getApplication<Application>(), newVal)
        if (newVal) UiSounds.play()
    }

    /**
     * Applies a saturation value to the whole screen right now (needs root, see SaturationEngine).
     * Runs off the main thread because it starts a `su` process.
     */
    fun applySaturation(value: Float) {
        viewModelScope.launch(Dispatchers.Default) {
            val result = SaturationEngine.apply(value)
            _saturationState.value = SaturationUiState(
                attempted = true,
                ok = result.ok,
                appliedValue = value,
                message = result.message
            )
        }
    }

    fun resetSaturation() {
        applySaturation(SaturationEngine.NEUTRAL)
    }

    fun toggleTurboFan() {
        val newVal = !_isTurboFanActive.value
        _isTurboFanActive.value = newVal
        repository.setTurboFanActive(newVal)
        if (_isVibrationEnabled.value) {
            musicManager.triggerHapticBeat(true)
        }
    }

    fun clearProcesses() {
        viewModelScope.launch {
            monitor.clearBackgroundProcesses()
            if (_isVibrationEnabled.value) {
                musicManager.triggerHapticBeat(true)
            }
        }
    }

    fun startSystemOverlayService(gameName: String = "", gamePackage: String = "", applySaturation: Boolean = false) {
        val app = getApplication<Application>()
        try {
            val intent = Intent(app, QboostOverlayService::class.java).apply {
                action = QboostOverlayService.ACTION_START_OVERLAY
                val targetGame = if (gameName.isNotEmpty()) gameName else selectedGame.value.name
                val targetPkg = if (gamePackage.isNotEmpty()) gamePackage else selectedGame.value.packageName
                putExtra(QboostOverlayService.EXTRA_GAME_NAME, targetGame)
                putExtra(QboostOverlayService.EXTRA_GAME_PACKAGE, targetPkg)
                putExtra(QboostOverlayService.EXTRA_APPLY_SATURATION, applySaturation)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(app, intent)
            } else {
                app.startService(intent)
            }
            _hasOverlayPermission.value = checkOverlayPermission()
        } catch (_: Exception) {}
    }

    fun onResume() {
        refreshNotificationState()
        repository.syncSaturationFromStore()
        monitor.resume()
        _hasOverlayPermission.value = checkOverlayPermission()
        if (_currentScreen.value == AppScreen.LOBBY) {
            musicManager.resume()
        }
        refreshInstalledApps()
    }

    fun onPause() {
        musicManager.pause()
        monitor.pause()
    }

    override fun onCleared() {
        super.onCleared()
        monitor.stop()
        gamepadMonitor.stop()
        musicManager.release()
    }
}
