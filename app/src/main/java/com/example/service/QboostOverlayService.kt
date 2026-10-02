package com.example.service

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.TextUtils
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.UiSounds
import com.example.display.SaturationEngine
import com.example.display.SaturationStore
import com.example.hardware.DeviceTelemetry
import com.example.hardware.HardwareProbe
import com.example.hardware.HardwareProfile
import com.example.hardware.RamBooster
import com.example.hardware.ScalerMode
import com.example.hardware.ScalerTuning
import com.example.hardware.TelemetryFormat
import com.example.i18n.I18n
import com.example.model.PerformanceStats
import com.example.settings.AppSettings
import com.example.settings.SettingsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The in-game Qboost panel (v8.0).
 *
 * A thin edge handle (invisible by default, see Settings > In-game panel) opens a panel with a
 * "Gaming tools" page and a "Games" page, and a dock of quick-launch apps next to it. Everything
 * is drawn with gradients and plain text: no emoji.
 */
class QboostOverlayService : Service() {

    companion object {
        const val ACTION_START_OVERLAY = "com.example.service.START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.example.service.STOP_OVERLAY"
        const val ACTION_QUICK_BOOST = "com.example.service.QUICK_BOOST"
        const val EXTRA_GAME_NAME = "com.example.service.EXTRA_GAME_NAME"
        const val EXTRA_GAME_PACKAGE = "com.example.service.EXTRA_GAME_PACKAGE"
        const val EXTRA_APPLY_SATURATION = "com.example.service.EXTRA_APPLY_SATURATION"
        private const val TELEMETRY_INTERVAL_MS = 1000L
        private const val NOTIFICATION_CHANNEL_ID = "qboost_running_device_channel"
        private const val NOTIFICATION_ID = 2025

        private const val PANEL_WIDTH_DP = 390
        private const val PANEL_HEIGHT_DP = 338
        private const val DOCK_WIDTH_DP = 64

        private val C_BLUE = Color.parseColor("#2F80FF")
        private val C_BLUE_DARK = Color.parseColor("#1746B8")
        private val C_CYAN = Color.parseColor("#00E5FF")
        private val C_TEXT = Color.parseColor("#F5F7FA")
        private val C_GRAY = Color.parseColor("#9EA3B0")
        private val C_GREEN = Color.parseColor("#00E676")
        private val C_AMBER = Color.parseColor("#FFB74D")
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var telemetryRunnable: Runnable? = null

    // ---- overlay window ----
    private var windowManager: WindowManager? = null
    private var overlayRoot: FrameLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var handleView: FrameLayout? = null
    private var panelContainer: LinearLayout? = null
    private var dockCard: LinearLayout? = null
    private var mainCard: LinearLayout? = null
    private var isOverlayAttached = false

    @Volatile
    private var isPanelOpen = false
    private var handleX = 0
    private var handleY = 0
    private var expandedY = 0

    // ---- game ----
    private var activeGameName: String = "Minecraft"
    private var activeGamePackage: String = "com.mojang.minecraftpe"

    // ---- settings / language ----
    private var settings = AppSettings()
    private var language = "en"

    // ---- panel widgets ----
    private var toolsPage: ScrollView? = null
    private var tabTools: TextView? = null
    private var gauge: GaugeView? = null
    private var cpuValueText: TextView? = null
    private var cpuBar: ProgressBar? = null
    private var memValueText: TextView? = null
    private var memBar: ProgressBar? = null
    private var timeText: TextView? = null
    private var gameNameText: TextView? = null
    private var batteryText: TextView? = null
    private var statsLine: TextView? = null
    private var boostStatusText: TextView? = null
    private val modeButtons = arrayOfNulls<TextView>(3)
    private var currentPerformanceMode = 2
    private var monitorTile: LinearLayout? = null
    private var satTile: TextView? = null
    // Scaler (the merged Upscaler + Frame gen)
    private var scalerTile: TextView? = null
    private var scalerCard: LinearLayout? = null
    private var scalerStartButton: TextView? = null
    private var scalerStatusText: TextView? = null
    private var scalerHwText: TextView? = null
    private val scalerModeChips = arrayOfNulls<TextView>(3)
    private val scalerQualityChips = arrayOfNulls<TextView>(4)
    private var hapticsTile: TextView? = null
    private var rotationLockTile: TextView? = null
    private var dndTile: TextView? = null
    private var brightnessLockTile: TextView? = null
    private var crosshairTile: TextView? = null
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // ---- toggles ----
    private var isHapticsOn = true
    private var isRotationLockOn = false
    private var isDndOn = false
    private var isBrightnessLockOn = false
    private var crosshairWindow: View? = null

    // ---- floating apps (multitasking windows over the game) ----
    private var floatingApps: FloatingAppWindows? = null

    // ---- real screen saturation (0.0 - 2.0, 1.0 = normal) ----
    private var currentSaturation = SaturationEngine.NEUTRAL
    private var pendingSaturation = SaturationEngine.NEUTRAL
    private var satCard: LinearLayout? = null
    private var satSeekBar: SeekBar? = null
    private var satValueText: TextView? = null
    private var satStatusText: TextView? = null
    private var satFailureToastShown = false

    // ---- system monitor HUD (FPS / CPU / GPU / RAM / MEM / TEMP over the game) ----
    private var monitorHudRoot: LinearLayout? = null
    private val monitorHudValues = HashMap<String, TextView>()

    @Volatile
    private var isMonitorHudOpen = false

    // ---- background thread for telemetry, RAM purge and saturation ----
    private var workThread: HandlerThread? = null
    private var workHandler: Handler? = null
    private val deviceTelemetry by lazy { DeviceTelemetry(this) }

    // Real vsync-driven frame timing on this device, used only when neither the sysfs "measured_fps"
    // node nor a root-based per-app reading is available. It is a genuine measurement (not invented),
    // just device-wide rather than scoped to the foreground game - so it is shown with a "~" prefix.
    private var isTrackingFrames = false
    private var frameCount = 0
    private var lastFrameStamp = 0L

    @Volatile
    private var deviceFrameRate = -1

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameCount++
            val elapsed = frameTimeNanos - lastFrameStamp
            if (elapsed >= 1_000_000_000L) {
                deviceFrameRate = ((frameCount * 1_000_000_000.0) / elapsed).roundToInt().coerceIn(1, 240)
                frameCount = 0
                lastFrameStamp = frameTimeNanos
            }
            if (isTrackingFrames) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    private fun startFrameTracking() {
        if (isTrackingFrames) return
        isTrackingFrames = true
        frameCount = 0
        lastFrameStamp = System.nanoTime()
        deviceFrameRate = -1
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopFrameTracking() {
        isTrackingFrames = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    /** Rebuilds the panel when language, handle opacity, panel opacity or the dock changes in Settings. */
    private val rebuildRunnable = Runnable { rebuildOverlay() }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsStore.KEY_LANGUAGE ||
            key == SettingsStore.KEY_HANDLE_OPACITY ||
            key == SettingsStore.KEY_HANDLE_OUTSIDE_EDGE ||
            key == SettingsStore.KEY_PANEL_OPACITY ||
            key == SettingsStore.KEY_SHOW_DOCK
        ) {
            // wait a moment so dragging a Settings slider does not rebuild the panel on every tick
            mainHandler.removeCallbacks(rebuildRunnable)
            mainHandler.postDelayed(rebuildRunnable, 350L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        UiSounds.init(this)
        I18n.init(this)
        settings = SettingsStore.load(this)
        language = settings.language
        val thread = HandlerThread("qboost-work").also { it.start() }
        workThread = thread
        workHandler = Handler(thread.looper)
        getSharedPreferences(SettingsStore.prefsName(), Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
        ScalerService.onStateChanged = { mainHandler.post { updateScalerTiles() } }
        ScalerService.onOverlayAdded = { mainHandler.post { bringOverlaysToFront() } }
        startForegroundServiceNotification()
        startFrameTracking()
    }

    /** Flipping between landscape and reverse landscape (or rotating): rebuild for the new screen size. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mainHandler.removeCallbacks(rebuildRunnable)
        mainHandler.postDelayed(rebuildRunnable, 300L)
        mainHandler.postDelayed({ floatingApps?.clampAll() }, 350L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val gameNameExtra = intent?.getStringExtra(EXTRA_GAME_NAME)
        val gamePkgExtra = intent?.getStringExtra(EXTRA_GAME_PACKAGE)
        if (!gameNameExtra.isNullOrBlank()) {
            activeGameName = gameNameExtra
        }
        if (!gamePkgExtra.isNullOrBlank()) {
            activeGamePackage = gamePkgExtra
        }
        updateGameHeaderUi()

        when (intent?.action) {
            ACTION_STOP_OVERLAY -> {
                stopFloatingOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_QUICK_BOOST -> {
                performQuickBoost()
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                    showGameSpaceSwipeOverlay()
                }
                // Show this game's saved saturation; apply it only when the game is being launched
                syncSaturationFromStore(applyNow = intent?.getBooleanExtra(EXTRA_APPLY_SATURATION, false) == true)
            }
        }
        return START_STICKY
    }

    private fun s(key: String): String = I18n.t(language, key)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun startForegroundServiceNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Qboost Gaming Cockpit",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when Qboost Gaming Cockpit is running on device"
                enableLights(true)
                lightColor = C_BLUE
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val quickBoostIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, QboostOverlayService::class.java).apply { action = ACTION_QUICK_BOOST },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, QboostOverlayService::class.java).apply { action = ACTION_STOP_OVERLAY },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Qboost is running on device")
            .setContentText("Gaming panel active - tap to open Game Space")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_launcher_foreground, "Open Qboost", openAppIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Quick Boost", quickBoostIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop HUD", stopIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateGameHeaderUi() {
        gameNameText?.text = activeGameName.uppercase(Locale.getDefault())
    }

    // ============================================================================================
    //  View helpers (gradients + plain text only, no emoji)
    // ============================================================================================

    private fun gradient(
        vararg colors: Int,
        radiusDp: Int = 12,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR
    ): GradientDrawable {
        val drawable = GradientDrawable(orientation, colors)
        drawable.cornerRadius = dp(radiusDp).toFloat()
        return drawable
    }

    private fun label(value: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
        val view = TextView(this)
        view.text = value
        view.setTextColor(color)
        view.textSize = sizeSp
        view.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return view
    }

    private fun styledBar(): ProgressBar {
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        val background = GradientDrawable()
        background.setColor(Color.parseColor("#33FFFFFF"))
        background.cornerRadius = dp(3).toFloat()
        val fill = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(C_BLUE, C_CYAN))
        fill.cornerRadius = dp(3).toFloat()
        val progress = ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)
        val layers = LayerDrawable(arrayOf(background, progress))
        layers.setId(0, android.R.id.background)
        layers.setId(1, android.R.id.progress)
        bar.progressDrawable = layers
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6))
        lp.topMargin = dp(4)
        bar.layoutParams = lp
        return bar
    }

    private fun styleTile(tile: TextView, active: Boolean) {
        if (active) {
            tile.background = gradient(C_BLUE, C_BLUE_DARK, radiusDp = 12)
            tile.setTextColor(Color.WHITE)
        } else {
            tile.background = gradient(Color.parseColor("#CC2A3552"), Color.parseColor("#CC1B2438"), radiusDp = 12)
            tile.setTextColor(C_TEXT)
        }
    }

    private fun toolTile(labelText: String, active: Boolean = false, onClick: (View) -> Unit): TextView {
        val tile = TextView(this)
        tile.text = labelText
        tile.gravity = Gravity.CENTER
        tile.textSize = 11f
        tile.typeface = Typeface.DEFAULT_BOLD
        tile.maxLines = 2
        tile.ellipsize = TextUtils.TruncateAt.END
        tile.setPadding(dp(4), dp(4), dp(4), dp(4))
        val lp = LinearLayout.LayoutParams(0, dp(46), 1f)
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        tile.layoutParams = lp
        tile.setOnClickListener { view ->
            UiSounds.play()
            onClick(view)
        }
        styleTile(tile, active)
        return tile
    }

    private fun toolRow(vararg tiles: View): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        tiles.forEach { row.addView(it) }
        return row
    }

    private fun featureTile(title: String, desc: String, onClick: () -> Unit): LinearLayout {
        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER_VERTICAL
        tile.background = gradient(Color.parseColor("#CC22304D"), Color.parseColor("#CC18233B"), radiusDp = 14)
        tile.setPadding(dp(12), dp(9), dp(12), dp(9))
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        tile.layoutParams = lp
        tile.addView(label(title, 12f, C_TEXT, bold = true))
        tile.addView(label(desc, 10f, C_GRAY))
        tile.setOnClickListener {
            UiSounds.play()
            onClick()
        }
        return tile
    }

    // ============================================================================================
    //  Overlay window: edge handle + panel (dock + main card)
    // ============================================================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun showGameSpaceSwipeOverlay() {
        if (isOverlayAttached) {
            updateGameHeaderUi()
            return
        }
        try {
            settings = SettingsStore.load(this)
            language = settings.language

            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm
            val screenH = resources.displayMetrics.heightPixels
            val panelH = min(dp(PANEL_HEIGHT_DP), screenH - dp(16))
            val cornerInset = if (settings.handleOutsideEdge) 0 else dp(8)
            handleY = cornerInset
            handleX = cornerInset
            expandedY = ((screenH - panelH) / 2).coerceAtLeast(dp(6))

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = handleX
                y = handleY
            }
            overlayParams = params

            val root = FrameLayout(this)
            // Tapping the game (outside the panel) closes the panel
            root.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE && isPanelOpen) {
                    setPanelExpanded(false)
                    true
                } else {
                    false
                }
            }

            val handle = buildHandle()
            val container = buildPanel(panelH)
            root.addView(container)
            root.addView(handle)

            wm.addView(root, params)
            overlayRoot = root
            handleView = handle
            panelContainer = container
            isOverlayAttached = true
            isPanelOpen = false

            updateSaturationUi()
            updateModeButtons()
            startTelemetryLoop()
        } catch (e: Exception) {
            Toast.makeText(this, "Qboost panel error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** The edge tab. Its opacity comes from Settings (0% = invisible, but still touchable). */
    @SuppressLint("ClickableViewAccessibility")
    private fun buildHandle(): FrameLayout {
        val handle = FrameLayout(this)
        handle.layoutParams = FrameLayout.LayoutParams(dp(30), dp(86))
        val radius = dp(14).toFloat()
        val background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(C_BLUE, C_BLUE_DARK))
        background.cornerRadii = floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
        handle.background = background
        handle.alpha = settings.handleOpacity

        val chevron = ChevronView(this)
        chevron.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        handle.addView(chevron)

        var initialTouchX = 0f
        var initialTouchY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    // A tap, or a swipe to the right, opens the panel. The handle no longer drags.
                    if (abs(dy) < dp(16) && (abs(dx) < dp(16) || dx > dp(20))) {
                        UiSounds.play()
                        setPanelExpanded(true)
                    }
                    true
                }
                else -> false
            }
        }
        return handle
    }

    private fun buildPanel(panelH: Int): LinearLayout {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.HORIZONTAL
        container.visibility = View.GONE
        container.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, panelH)

        val dock = buildDockCard()
        val main = buildMainCard()
        dockCard = dock
        mainCard = main
        container.addView(dock)
        container.addView(main)
        applyPanelAppearance()
        return container
    }

    /**
     * Apps in a column, like the reference design. Tap = floating window over the game,
     * hold = open the real app (in a window too if the phone supports freeform windows).
     */
    private fun buildDockCard(): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = gradient(
            Color.parseColor("#E61B2439"),
            Color.parseColor("#E610182B"),
            radiusDp = 22,
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        )
        card.setPadding(dp(9), dp(10), dp(9), dp(10))
        val lp = LinearLayout.LayoutParams(dp(DOCK_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT)
        lp.marginEnd = dp(8)
        card.layoutParams = lp
        if (!settings.showDock) {
            card.visibility = View.GONE
        }

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        FloatingApps.all.forEach { app -> column.addView(dockIcon(app)) }
        scroll.addView(column)
        card.addView(scroll)
        return card
    }

    private fun dockIcon(app: FloatingApp): View {
        val installedPackage = app.packages.firstOrNull { packageManager.getLaunchIntentForPackage(it) != null }
        // The bundled icon is always used, so the dock looks the same whether or not the real app is
        // installed (long-press still opens the real app when it is).
        val image = ImageView(this)
        image.setImageResource(app.iconRes)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        val icon: View = image
        icon.clipToOutline = true
        icon.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(13).toFloat())
            }
        }
        val lp = LinearLayout.LayoutParams(dp(46), dp(46))
        lp.bottomMargin = dp(10)
        icon.layoutParams = lp
        icon.setOnClickListener {
            UiSounds.play()
            openFloatingApp(app)
        }
        icon.setOnLongClickListener {
            UiSounds.play()
            if (installedPackage != null && launchPackage(installedPackage, floating = true)) {
                setPanelExpanded(false)
            }
            true
        }
        return icon
    }

    private fun openFloatingApp(app: FloatingApp) {
        val windows = floatingApps ?: FloatingAppWindows(this).also { floatingApps = it }
        windows.open(app)
        setPanelExpanded(false)
    }

    private fun buildMainCard(): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = gradient(
            Color.parseColor("#E61B2439"),
            Color.parseColor("#E60F1729"),
            radiusDp = 22,
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        )
        card.layoutParams = LinearLayout.LayoutParams(dp(PANEL_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT)

        // ---- header: "Gaming tools" title, x ----
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.gravity = Gravity.CENTER_VERTICAL
        tabs.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))
        val tools = tabLabel(s("p_tools")) {}
        tabTools = tools
        styleTab(tools, selected = true)
        val close = label("×", 22f, C_GRAY)
        close.gravity = Gravity.CENTER
        close.setPadding(dp(12), 0, dp(14), 0)
        close.setOnClickListener {
            UiSounds.play()
            setPanelExpanded(false)
        }
        tabs.addView(tools)
        tabs.addView(close)
        card.addView(tabs)

        // ---- page ----
        val pages = FrameLayout(this)
        pages.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        val toolsScroll = buildToolsPage()
        toolsPage = toolsScroll
        pages.addView(toolsScroll)
        card.addView(pages)
        return card
    }

    private fun tabLabel(value: String, onClick: () -> Unit): TextView {
        val tab = TextView(this)
        tab.text = value
        tab.gravity = Gravity.CENTER
        tab.textSize = 13f
        tab.typeface = Typeface.DEFAULT_BOLD
        tab.maxLines = 1
        tab.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        tab.setOnClickListener {
            UiSounds.play()
            onClick()
        }
        return tab
    }

    private fun styleTab(tab: TextView?, selected: Boolean) {
        if (tab == null) return
        tab.setTextColor(if (selected) Color.WHITE else C_GRAY)
        if (selected) {
            val r = dp(16).toFloat()
            val bg = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#662F80FF"), Color.parseColor("#0F2F80FF"))
            )
            bg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            tab.background = bg
        } else {
            tab.background = null
        }
    }

    // ---------------- Gaming tools page ----------------

    private fun buildToolsPage(): ScrollView {
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(dp(12), dp(2), dp(12), dp(12))

        // time  -  game name  -  battery
        val statusRow = LinearLayout(this)
        statusRow.orientation = LinearLayout.HORIZONTAL
        statusRow.gravity = Gravity.CENTER_VERTICAL
        val time = label("--:--", 11f, C_GRAY)
        val name = label(activeGameName.uppercase(Locale.getDefault()), 11f, C_TEXT, bold = true)
        name.gravity = Gravity.CENTER
        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.END
        name.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val battery = label("--%", 11f, C_GRAY)
        timeText = time
        gameNameText = name
        batteryText = battery
        statusRow.addView(time)
        statusRow.addView(name)
        statusRow.addView(battery)
        column.addView(statusRow)

        // CPU bar  -  FPS gauge  -  Memory bar
        val gaugeRow = LinearLayout(this)
        gaugeRow.orientation = LinearLayout.HORIZONTAL
        gaugeRow.gravity = Gravity.CENTER_VERTICAL
        gaugeRow.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(108))
        gaugeRow.addView(statColumn(s("p_cpu"), isCpu = true))
        val gaugeView = GaugeView(this)
        gaugeView.layoutParams = LinearLayout.LayoutParams(dp(104), dp(104))
        gauge = gaugeView
        gaugeRow.addView(gaugeView)
        gaugeRow.addView(statColumn(s("p_memory"), isCpu = false))
        column.addView(gaugeRow)

        val line = label("", 10f, C_GRAY)
        line.gravity = Gravity.CENTER
        line.maxLines = 1
        line.ellipsize = TextUtils.TruncateAt.END
        statsLine = line
        column.addView(line)

        column.addView(buildModeSegments())

        // two big tiles: memory optimization + system monitor
        val featureRow = LinearLayout(this)
        featureRow.orientation = LinearLayout.HORIZONTAL
        featureRow.addView(featureTile(s("tile_mem"), s("tile_mem_desc")) { performQuickBoost() })
        val monitor = featureTile(s("tile_monitor"), s("tile_monitor_desc")) { toggleSystemMonitorHud() }
        monitorTile = monitor
        featureRow.addView(monitor)
        column.addView(featureRow)

        // tools: 4 x 2, nothing else
        val sat = toolTile(s("saturation")) { toggleSaturationCard() }
        val scaler = toolTile(s("scaler"), active = ScalerService.isRunning) { toggleScalerCard() }
        val haptics = toolTile(s("tool_haptics"), active = isHapticsOn) { view ->
            isHapticsOn = !isHapticsOn
            styleTile(view as TextView, isHapticsOn)
            vibratePulse()
        }
        satTile = sat
        scalerTile = scaler
        hapticsTile = haptics
        column.addView(toolRow(sat, scaler, haptics))
        column.addView(
            toolRow(
                toolTile(s("tool_wifi")) { openSystemScreen(Settings.ACTION_WIFI_SETTINGS) },
                toolTile(s("set_display")) { openSystemScreen(Settings.ACTION_DISPLAY_SETTINGS) },
                toolTile(s("tool_qboost")) { openQboost() },
                toolTile(s("tool_stop")) {
                    stopFloatingOverlay()
                    stopSelf()
                }
            )
        )

        // Everything below is new: only tools Qboost didn't already cover in the two rows above
        // (refresh rate is the Hz gauge, "information monitor" is the System monitor tile, "quick
        // boost" is the Qboost tile, and "touch enhancer" is already a per-game setting) so none of
        // those are repeated here.
        val rotationLock = toolTile(s("tool_rotation_lock"), active = isRotationLockOn) { toggleRotationLock() }
        val dnd = toolTile(s("tool_dnd"), active = isDndOn) { toggleDoNotDisturb() }
        val brightnessLock = toolTile(s("tool_brightness_lock"), active = isBrightnessLockOn) { toggleBrightnessLock() }
        val crosshair = toolTile(s("tool_crosshair"), active = crosshairWindow != null) { toggleCrosshair() }
        rotationLockTile = rotationLock
        dndTile = dnd
        brightnessLockTile = brightnessLock
        crosshairTile = crosshair
        column.addView(toolRow(rotationLock, dnd, brightnessLock, crosshair))
        column.addView(
            toolRow(
                toolTile(s("tool_show_taps")) { openSystemScreen(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
                toolTile(s("tool_dns")) { openPrivateDnsSettings() },
                toolTile(s("tool_screenshot")) { showScreenshotInfo() },
                toolTile(s("tool_gyro")) { showGyroCalibrationInfo() }
            )
        )

        column.addView(buildSaturationCard())
        column.addView(buildScalerCard())

        val status = label("", 10f, C_GRAY)
        status.gravity = Gravity.CENTER
        status.setPadding(0, dp(6), 0, 0)
        boostStatusText = status
        column.addView(status)

        scroll.addView(column)
        return scroll
    }

    private fun statColumn(title: String, isCpu: Boolean): LinearLayout {
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_VERTICAL
        column.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        column.setPadding(dp(4), 0, dp(4), 0)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        val name = label(title, 11f, C_CYAN, bold = true)
        val value = label("--%", 12f, Color.WHITE, bold = true)
        value.gravity = Gravity.END
        value.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        header.addView(name)
        header.addView(value)

        val bar = styledBar()
        column.addView(header)
        column.addView(bar)
        if (isCpu) {
            cpuValueText = value
            cpuBar = bar
        } else {
            memValueText = value
            memBar = bar
        }
        return column
    }

    private fun buildModeSegments(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.background = gradient(Color.parseColor("#B3141B2B"), Color.parseColor("#B3141B2B"), radiusDp = 16)
        row.setPadding(dp(3), dp(3), dp(3), dp(3))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(6)
        lp.bottomMargin = dp(4)
        row.layoutParams = lp

        val names = arrayOf(s("seg_battery"), s("mode_balanced"), s("mode_performance"))
        for (i in 0..2) {
            val segment = TextView(this)
            segment.text = names[i]
            segment.gravity = Gravity.CENTER
            segment.textSize = 12f
            segment.typeface = Typeface.DEFAULT_BOLD
            segment.maxLines = 1
            segment.ellipsize = TextUtils.TruncateAt.END
            segment.setPadding(dp(4), dp(9), dp(4), dp(9))
            segment.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            segment.setOnClickListener {
                UiSounds.play()
                setPerformanceMode(i)
            }
            modeButtons[i] = segment
            row.addView(segment)
        }
        return row
    }

    private fun updateModeButtons() {
        for (i in 0..2) {
            val segment = modeButtons[i] ?: continue
            if (i == currentPerformanceMode) {
                segment.background = gradient(C_BLUE, C_BLUE_DARK, radiusDp = 13)
                segment.setTextColor(Color.WHITE)
            } else {
                segment.background = null
                segment.setTextColor(C_GRAY)
            }
        }
    }

    private fun setPerformanceMode(mode: Int) {
        currentPerformanceMode = mode
        updateModeButtons()
        vibratePulse()
        boostStatusText?.text = when (mode) {
            0 -> s("mode_battery")
            1 -> s("mode_balanced")
            else -> s("mode_performance")
        }
    }

    // ---------------- Saturation card (real, whole screen) ----------------

    private fun buildSaturationCard(): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.visibility = View.GONE
        card.background = gradient(Color.parseColor("#CC22304D"), Color.parseColor("#CC18233B"), radiusDp = 14)
        card.setPadding(dp(12), dp(9), dp(12), dp(9))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(6)
        card.layoutParams = lp

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = label(s("saturation"), 12f, C_CYAN, bold = true)
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val value = label(SaturationEngine.percentText(currentSaturation), 12f, Color.WHITE, bold = true)
        satValueText = value
        header.addView(title)
        header.addView(value)
        card.addView(header)

        val seek = SeekBar(this)
        seek.max = 200
        seek.progress = (currentSaturation * 100).roundToInt().coerceIn(0, 200)
        seek.progressTintList = ColorStateList.valueOf(C_BLUE)
        seek.thumbTintList = ColorStateList.valueOf(C_BLUE)
        seek.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                view.parent?.requestDisallowInterceptTouchEvent(true)
            }
            false
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = ((progress / 5) * 5) / 100f // 5% steps
                currentSaturation = v
                satValueText?.text = SaturationEngine.percentText(v)
                scheduleSaturationApply(v, immediate = false)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                UiSounds.play()
                SaturationStore.set(this@QboostOverlayService, storeKey(), currentSaturation)
                scheduleSaturationApply(currentSaturation, immediate = true)
            }
        })
        satSeekBar = seek
        card.addView(seek)

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL
        val reset = toolTile(s("sat_reset")) {
            currentSaturation = SaturationEngine.NEUTRAL
            SaturationStore.set(this, storeKey(), currentSaturation)
            updateSaturationUi()
            scheduleSaturationApply(currentSaturation, immediate = true)
        }
        val copy = toolTile(s("sat_copy")) {
            SaturationEngine.copyAdbCommand(this, currentSaturation)
            Toast.makeText(this, "ADB command copied", Toast.LENGTH_SHORT).show()
        }
        buttons.addView(reset)
        buttons.addView(copy)
        card.addView(buttons)

        val status = label("", 9f, C_GRAY)
        satStatusText = status
        card.addView(status)

        satCard = card
        return card
    }

    private fun toggleSaturationCard() {
        val card = satCard ?: return
        val show = card.visibility != View.VISIBLE
        card.visibility = if (show) View.VISIBLE else View.GONE
        satTile?.let { styleTile(it, show) }
        if (show) {
            satStatusText?.text = "Root or ADB required: Android does not let apps recolor other apps."
        }
    }

    // ---------------- Games page ----------------

    // ---------------- open / close ----------------

    /** Panel and dock backgrounds follow the "Panel opacity" setting. */
    private fun applyPanelAppearance() {
        val alpha = (settings.panelOpacity * 255f).roundToInt().coerceIn(0, 255)
        mainCard?.background?.mutate()?.setAlpha(alpha)
        dockCard?.background?.mutate()?.setAlpha(alpha)
        handleView?.alpha = settings.handleOpacity
    }

    private fun setPanelExpanded(expand: Boolean) {
        val params = overlayParams ?: return
        if (isPanelOpen == expand) return
        isPanelOpen = expand
        if (expand) {
            applyPanelAppearance()
            handleView?.visibility = View.GONE
            panelContainer?.visibility = View.VISIBLE
            params.x = dp(8)
            params.y = expandedY
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            sampleNow()
        } else {
            panelContainer?.visibility = View.GONE
            handleView?.visibility = View.VISIBLE
            params.x = handleX
            params.y = handleY
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
        }
        vibratePulse()
        overlayRoot?.let { windowManager?.updateViewLayout(it, params) }
    }

    /**
     * Opens the real app. With [floating] it asks Android for a smaller window; phones that support
     * freeform windows honour that, other phones simply open the app normally.
     */
    private fun launchPackage(pkg: String, floating: Boolean = false): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (floating) {
                val metrics = resources.displayMetrics
                val left = dp(90)
                val top = dp(24)
                val bounds = Rect(
                    left,
                    top,
                    left + (metrics.widthPixels * 0.5f).toInt(),
                    top + (metrics.heightPixels * 0.7f).toInt()
                )
                startActivity(intent, ActivityOptions.makeBasic().setLaunchBounds(bounds).toBundle())
            } else {
                startActivity(intent)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openSystemScreen(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            setPanelExpanded(false)
        } catch (_: Exception) {
            Toast.makeText(this, "Not available on this phone", Toast.LENGTH_SHORT).show()
        }
    }

    /** Auto-rotate is a per-device setting, not a permission — this needs Settings.canWrite() first. */
    private fun toggleRotationLock() {
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, "Allow Qboost to modify system settings, then try again", Toast.LENGTH_LONG).show()
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
            return
        }
        isRotationLockOn = !isRotationLockOn
        try {
            // Locked = auto-rotate OFF (value 0); un-locked hands rotation back to the accelerometer (1).
            Settings.System.putInt(
                contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (isRotationLockOn) 0 else 1
            )
        } catch (_: Exception) {
        }
        rotationLockTile?.let { styleTile(it, isRotationLockOn) }
    }

    /** Silencing notifications needs the special "Do Not Disturb access" grant, not a manifest permission. */
    private fun toggleDoNotDisturb() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm == null || !nm.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, "Allow Qboost to control Do Not Disturb, then try again", Toast.LENGTH_LONG).show()
            try {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
            return
        }
        isDndOn = !isDndOn
        try {
            nm.setInterruptionFilter(
                if (isDndOn) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
        } catch (_: Exception) {
        }
        dndTile?.let { styleTile(it, isDndOn) }
    }

    /**
     * Locks the screen at its current brightness for as long as the overlay window is up, by setting
     * that brightness directly on Qboost's own overlay window — a floating window's `screenBrightness`
     * overrides the physical backlight system-wide while it's showing, no special permission needed since
     * it's only ever touching a window Qboost itself owns. -1 hands control back to the system.
     */
    private fun toggleBrightnessLock() {
        val params = overlayParams ?: return
        isBrightnessLockOn = !isBrightnessLockOn
        params.screenBrightness = if (isBrightnessLockOn) {
            try {
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                    .coerceIn(0, 255) / 255f
            } catch (_: Exception) {
                -1f
            }
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        overlayRoot?.let { windowManager?.updateViewLayout(it, params) }
        brightnessLockTile?.let { styleTile(it, isBrightnessLockOn) }
    }

    /** A small always-on-top reticle, centered on screen, in its own tiny overlay window. */
    private fun toggleCrosshair() {
        val wm = windowManager ?: return
        val existing = crosshairWindow
        if (existing != null) {
            try {
                wm.removeView(existing)
            } catch (_: Exception) {
            }
            crosshairWindow = null
            crosshairTile?.let { styleTile(it, false) }
            return
        }
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val size = dp(28)
        val crosshairParams = WindowManager.LayoutParams(
            size,
            size,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        val view = View(this)
        view.background = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply {
                color = Color.parseColor("#E6FFFFFF")
                strokeWidth = dp(2).toFloat()
            }
            override fun draw(canvas: android.graphics.Canvas) {
                val w = bounds.width().toFloat()
                val h = bounds.height().toFloat()
                canvas.drawLine(w / 2f, 0f, w / 2f, h, paint)
                canvas.drawLine(0f, h / 2f, w, h / 2f, paint)
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
            @Suppress("DEPRECATION")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
        try {
            wm.addView(view, crosshairParams)
            crosshairWindow = view
        } catch (_: Exception) {
        }
        crosshairTile?.let { styleTile(it, crosshairWindow != null) }
    }

    private fun openPrivateDnsSettings() {
        try {
            startActivity(
                Intent("android.settings.PRIVATE_DNS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            setPanelExpanded(false)
        } catch (_: Exception) {
            // Not every OEM skin ships that exact screen; the network settings page always exists.
            openSystemScreen(Settings.ACTION_WIRELESS_SETTINGS)
        }
    }

    /**
     * A real screenshot needs MediaProjection consent, which Qboost only asks for when the Scaler
     * gen is turned on — reusing that instead of asking a second time for a whole separate feature.
     */
    private fun showScreenshotInfo() {
        val message = if (ScalerService.upscalerOn || ScalerService.frameGenOn) {
            "Screenshot capture from here is coming soon"
        } else {
            "Turn on Scaler first — Screenshot reuses that same screen-capture permission"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /** No Android version exposes a public API for this; it's a device-firmware-level calibration. */
    private fun showGyroCalibrationInfo() {
        Toast.makeText(
            this,
            "Not something an app can do on most phones — check your device's own Settings app for a gyroscope calibration option",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun openQboost() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
            setPanelExpanded(false)
        } catch (_: Exception) {
        }
    }

    // ---------------- Scaler (upscale + frame gen in one) ----------------

    /** A short selectable chip (same look as the tool tiles, just lower). */
    private fun scalerChip(text: String, onClick: () -> Unit): TextView {
        val chip = toolTile(text) { onClick() }
        (chip.layoutParams as LinearLayout.LayoutParams).height = dp(34)
        chip.textSize = 10.5f
        return chip
    }

    private fun scalerChipRow(labels: List<String>, store: Array<TextView?>, onPick: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        labels.forEachIndexed { index, text ->
            val chip = scalerChip(text) { onPick(index) }
            store[index] = chip
            row.addView(chip)
        }
        return row
    }

    private fun buildScalerCard(): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.visibility = View.GONE
        card.background = gradient(Color.parseColor("#CC22304D"), Color.parseColor("#CC18233B"), radiusDp = 14)
        card.setPadding(dp(12), dp(9), dp(12), dp(9))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(6)
        card.layoutParams = lp

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = label(s("scaler"), 12f, C_CYAN, bold = true)
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val state = label("", 11f, C_GRAY, bold = true)
        scalerStatusText = state
        header.addView(title)
        header.addView(state)
        card.addView(header)

        card.addView(
            scalerChipRow(
                listOf(s("scaler_mode_upscale"), s("scaler_mode_framegen"), s("scaler_mode_both")),
                scalerModeChips
            ) { index -> onScalerMode(index) }
        )

        val qualityLabel = label(s("scaler_quality"), 10f, C_GRAY)
        qualityLabel.setPadding(dp(3), dp(6), 0, 0)
        card.addView(qualityLabel)
        card.addView(
            scalerChipRow(
                listOf(s("scaler_q_auto"), s("scaler_q_max"), s("scaler_q_balanced"), s("scaler_q_fast")),
                scalerQualityChips
            ) { index -> onScalerQuality(index) }
        )

        val hardware = label("", 9.5f, C_GRAY)
        hardware.setPadding(dp(3), dp(6), 0, dp(2))
        scalerHwText = hardware
        card.addView(hardware)

        val start = toolTile(s("scaler_start")) { onScalerStartStop() }
        scalerStartButton = start
        card.addView(toolRow(start))

        scalerCard = card
        return card
    }

    private fun toggleScalerCard() {
        val card = scalerCard ?: return
        val show = card.visibility != View.VISIBLE
        card.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            updateScalerChips()
            updateScalerTiles()
            loadHardwareLine()
        }
    }

    private fun onScalerMode(mode: Int) {
        SettingsStore.setScalerMode(this, mode)
        updateScalerChips()
        // Changing the mode while it runs takes effect immediately.
        ScalerService.instance?.applyConfig(ScalerMode.upscale(mode), ScalerMode.frameGen(mode))
        updateScalerTiles()
    }

    private fun onScalerQuality(quality: Int) {
        SettingsStore.setScalerQuality(this, quality)
        updateScalerChips()
        loadHardwareLine()
        if (ScalerService.isRunning) scalerStatusText?.text = s("scaler_quality_next")
    }

    private fun onScalerStartStop() {
        val running = ScalerService.instance
        if (running != null) {
            running.stopScaler()
            updateScalerTiles()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Toast.makeText(this, s("scaler_need_android14"), Toast.LENGTH_LONG).show()
            return
        }
        val mode = SettingsStore.scalerMode(this)
        // Android asks "start capturing?" first, on a see-through screen
        val intent = Intent(this, ScalerPermissionActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(ScalerService.EXTRA_UPSCALER, ScalerMode.upscale(mode))
        intent.putExtra(ScalerService.EXTRA_FRAME_GEN, ScalerMode.frameGen(mode))
        try {
            startActivity(intent)
            setPanelExpanded(false)
        } catch (_: Exception) {
        }
        updateScalerTiles()
    }

    private fun updateScalerChips() {
        val mode = SettingsStore.scalerMode(this)
        for (i in scalerModeChips.indices) scalerModeChips[i]?.let { styleTile(it, i == mode) }
        val quality = SettingsStore.scalerQuality(this)
        for (i in scalerQualityChips.indices) scalerQualityChips[i]?.let { styleTile(it, i == quality) }
    }

    /** Hardware line: read the real GPU / RAM on the background thread, then show what the Scaler will use. */
    private fun loadHardwareLine() {
        val handler = workHandler ?: return
        handler.post {
            val profile = HardwareProbe.get(this)
            mainHandler.post { scalerHwText?.text = describeHardware(profile) }
        }
    }

    private fun describeHardware(profile: HardwareProfile): String {
        val scale = ScalerTuning.scaleFor(SettingsStore.scalerQuality(this), profile.tier)
        return profile.summary() + "\n" + s("scaler_engine") + " · " + s("scaler_capture") + " " +
            (scale * 100).roundToInt() + "%"
    }

    private fun updateScalerTiles() {
        val running = ScalerService.isRunning
        scalerTile?.let { styleTile(it, running) }
        scalerStartButton?.let {
            it.text = if (running) s("scaler_stop") else s("scaler_start")
            styleTile(it, running)
        }
        scalerStatusText?.let {
            it.text = if (running) s("scaler_running") else s("scaler_off")
            it.setTextColor(if (running) C_GREEN else C_GRAY)
        }
    }

    /** Windows added later sit on top, so after the scaler overlay appears everything of ours goes back over it. */
    private fun bringOverlaysToFront() {
        val root = overlayRoot
        val params = overlayParams
        if (root != null && params != null) {
            try {
                windowManager?.removeView(root)
                windowManager?.addView(root, params)
            } catch (_: Exception) {
            }
        }
        floatingApps?.bringAllToFront()
        monitorHudRoot?.let { hud ->
            try {
                val hudParams = hud.layoutParams as? WindowManager.LayoutParams
                windowManager?.removeView(hud)
                if (hudParams != null) windowManager?.addView(hud, hudParams)
            } catch (_: Exception) {
            }
        }
    }

    /** Tears the overlay down and builds it again (used when Settings change the panel). */
    private fun rebuildOverlay() {
        if (!isOverlayAttached) return
        val wasOpen = isPanelOpen
        detachOverlay()
        showGameSpaceSwipeOverlay()
        if (wasOpen) setPanelExpanded(true)
    }

    private fun detachOverlay() {
        overlayRoot?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayRoot = null
        overlayParams = null
        handleView = null
        panelContainer = null
        dockCard = null
        mainCard = null
        toolsPage = null
        tabTools = null
        gauge = null
        cpuValueText = null
        cpuBar = null
        memValueText = null
        memBar = null
        timeText = null
        gameNameText = null
        batteryText = null
        statsLine = null
        boostStatusText = null
        monitorTile = null
        satTile = null
        scalerTile = null
        scalerCard = null
        scalerStartButton = null
        scalerStatusText = null
        scalerHwText = null
        for (i in scalerModeChips.indices) scalerModeChips[i] = null
        for (i in scalerQualityChips.indices) scalerQualityChips[i] = null
        hapticsTile = null
        satCard = null
        satSeekBar = null
        satValueText = null
        satStatusText = null
        for (i in modeButtons.indices) modeButtons[i] = null
        isOverlayAttached = false
        isPanelOpen = false
    }

    // ============================================================================================
    //  Actions
    // ============================================================================================

    /** Real purge: asks Android to stop background apps, then reports the MEASURED freed RAM. */
    private fun performQuickBoost() {
        vibratePulse()
        boostStatusText?.text = s("purge_ram") + "..."
        val handler = workHandler ?: return
        handler.post {
            val result = RamBooster.purge(this, setOf(activeGamePackage))
            mainHandler.post {
                boostStatusText?.text =
                    "Freed ${result.freedMb} MB RAM (measured) - asked Android to stop ${result.requestedApps} background apps"
                Toast.makeText(this, "Qboost: freed ${result.freedMb} MB RAM", Toast.LENGTH_SHORT).show()
                mainHandler.postDelayed({ boostStatusText?.text = "" }, 4500)
            }
        }
    }

    private fun vibratePulse() {
        if (!isHapticsOn) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(35)
                }
            }
        } catch (_: Exception) {
        }
    }

    // ============================================================================================
    //  REAL TELEMETRY
    // ============================================================================================

    private fun startTelemetryLoop() {
        telemetryRunnable?.let { workHandler?.removeCallbacks(it) }
        val handler = workHandler ?: return
        val runnable = object : Runnable {
            override fun run() {
                // Only read the hardware while something on screen is actually showing the numbers
                if (isPanelOpen || isMonitorHudOpen) {
                    readAndPostTelemetry()
                }
                handler.postDelayed(this, TELEMETRY_INTERVAL_MS)
            }
        }
        telemetryRunnable = runnable
        handler.post(runnable)
    }

    /** Take one reading right now (used when the panel or monitor opens). */
    private fun sampleNow() {
        workHandler?.post { readAndPostTelemetry() }
    }

    /** Runs on the background thread. */
    private fun readAndPostTelemetry() {
        val stats = try {
            deviceTelemetry.sample(activeGamePackage, deviceFrameRate)
        } catch (_: Throwable) {
            null
        }
        if (stats != null) {
            mainHandler.post { applySnapshot(stats) }
        }
    }

    /** Runs on the main thread: paints REAL numbers into the panel and the monitor HUD. */
    private fun applySnapshot(s: PerformanceStats) {
        try {
            // ---- Panel ----
            val fpsAvailable = s.fps >= 0
            gauge?.valueText = TelemetryFormat.fps(s)
            gauge?.labelText = "FPS"
            gauge?.fraction = if (fpsAvailable) s.fps.toFloat() / maxOf(s.refreshRate, 60).toFloat() else 0f

            cpuValueText?.text = TelemetryFormat.load(s.cpuUsagePercent)
            cpuBar?.progress = s.cpuUsagePercent.coerceAtLeast(0)
            memValueText?.text = "${s.ramUsedPercent}%"
            memBar?.progress = s.ramUsedPercent

            timeText?.text = timeFormat.format(Date())
            batteryText?.text = "${s.batteryPercent}%"
            statsLine?.text = "GPU ${TelemetryFormat.load(s.gpuUsagePercent)} - " +
                "${s.tempSource} ${TelemetryFormat.temp(s.cpuTempC)} - " +
                "RAM ${TelemetryFormat.gb(s.ramUsedMb)}/${TelemetryFormat.gb(s.ramTotalMb)} GB"

            // ---- Floating System Monitor: clean "LABEL number" values, real device data only ----
            if (isMonitorHudOpen) {
                monitorHudValues["fps"]?.text = TelemetryFormat.fps(s)
                monitorHudValues["gpu"]?.text = TelemetryFormat.load(s.gpuUsagePercent)
                monitorHudValues["cpu"]?.text = TelemetryFormat.load(s.cpuUsagePercent)
                monitorHudValues["ram"]?.text = "${s.ramUsedPercent}%"
                monitorHudValues["mem"]?.text = if (s.swapTotalMb > 0L) "${TelemetryFormat.gb(s.swapUsedMb)}GB" else "off"
                monitorHudValues["temp"]?.text = if (s.cpuTempC > 0f) "${TelemetryFormat.temp(s.cpuTempC)}" else TelemetryFormat.NA
            }
        } catch (_: Exception) {
        }
    }

    // ============================================================================================
    //  SYSTEM MONITOR HUD
    // ============================================================================================

    private fun toggleSystemMonitorHud() {
        if (isMonitorHudOpen) {
            hideSystemMonitorHud()
        } else {
            showSystemMonitorHud()
            // Close the big panel so the monitor sits on top of the game
            setPanelExpanded(false)
        }
        updateMonitorTile()
    }

    private fun updateMonitorTile() {
        val tile = monitorTile ?: return
        tile.background = if (isMonitorHudOpen) {
            gradient(C_BLUE, C_BLUE_DARK, radiusDp = 14)
        } else {
            gradient(Color.parseColor("#CC22304D"), Color.parseColor("#CC18233B"), radiusDp = 14)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showSystemMonitorHud() {
        if (monitorHudRoot != null) return
        try {
            val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            val density = resources.displayMetrics.density

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val hudParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (56 * density).toInt()
                y = (24 * density).toInt()
            }

            val root = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#E60D1826"))
                    cornerRadius = 20 * density
                }
                background = bg
                setPadding((12 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
            }

            val monitorBlueLabel = Color.parseColor("#7FB4FF")
            val monitorBlueValue = Color.parseColor("#4FC3F7")

            monitorHudValues.clear()
            fun addStat(key: String, label: String, isFirst: Boolean = false) {
                val stat = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.marginStart = if (isFirst) 0 else (10 * density).toInt()
                    layoutParams = lp
                }
                val labelView = TextView(this).apply {
                    text = label
                    setTextColor(monitorBlueLabel)
                    textSize = 10f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                val valueView = TextView(this).apply {
                    text = "--"
                    setTextColor(monitorBlueValue)
                    textSize = 11f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    setPadding((3 * density).toInt(), 0, 0, 0)
                }
                stat.addView(labelView)
                stat.addView(valueView)
                root.addView(stat)
                monitorHudValues[key] = valueView
            }
            addStat("fps", "FPS", isFirst = true)
            addStat("gpu", "GPU")
            addStat("cpu", "CPU")
            addStat("ram", "RAM")
            addStat("mem", "MEM")
            addStat("temp", "TEMP")

            // Drag the whole monitor anywhere on the screen
            root.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var touchX = 0f
                private var touchY = 0f

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = hudParams.x
                            initialY = hudParams.y
                            touchX = event.rawX
                            touchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            hudParams.x = initialX + (event.rawX - touchX).toInt()
                            hudParams.y = initialY + (event.rawY - touchY).toInt()
                            wm.updateViewLayout(root, hudParams)
                            return true
                        }
                    }
                    return false
                }
            })

            wm.addView(root, hudParams)
            monitorHudRoot = root
            isMonitorHudOpen = true
            vibratePulse()
            sampleNow()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open System Monitor: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideSystemMonitorHud() {
        val root = monitorHudRoot
        isMonitorHudOpen = false
        monitorHudRoot = null
        monitorHudValues.clear()
        if (root != null) {
            try {
                windowManager?.removeView(root)
            } catch (_: Exception) {
            }
        }
    }

    // ================= REAL SATURATION =================
    private fun storeKey(): String = activeGamePackage

    private val satApplyRunnable = Runnable {
        val target = pendingSaturation
        val result = SaturationEngine.apply(target)
        mainHandler.post { onSaturationResult(target, result) }
    }

    /** Shows the game's saved saturation on the slider; applies it to the screen only if [applyNow]. */
    private fun syncSaturationFromStore(applyNow: Boolean) {
        currentSaturation = SaturationStore.get(this, storeKey()) ?: SaturationEngine.NEUTRAL
        updateSaturationUi()
        if (applyNow && abs(currentSaturation - SaturationEngine.lastApplied) > 0.005f) {
            scheduleSaturationApply(currentSaturation, immediate = true)
        }
    }

    private fun updateSaturationUi() {
        val pct = SaturationEngine.percentText(currentSaturation)
        satSeekBar?.progress = (currentSaturation * 100).roundToInt().coerceIn(0, 200)
        satValueText?.text = pct
    }

    /** Debounced (slider drag) or immediate (release / preset); runs `su` on the background thread. */
    private fun scheduleSaturationApply(value: Float, immediate: Boolean) {
        pendingSaturation = value
        val handler = workHandler ?: return
        handler.removeCallbacks(satApplyRunnable)
        if (immediate) handler.post(satApplyRunnable) else handler.postDelayed(satApplyRunnable, 160L)
    }

    private fun onSaturationResult(target: Float, result: SaturationEngine.Result) {
        if (result.ok) {
            satStatusText?.setTextColor(Color.parseColor("#00E676"))
            satStatusText?.text = result.message
            boostStatusText?.text = "${s("saturation")} ${SaturationEngine.percentText(target)}"
        } else {
            satStatusText?.setTextColor(Color.parseColor("#FFB74D"))
            satStatusText?.text = "${result.message}\n${SaturationEngine.adbCommand(target)}"
            if (!satFailureToastShown && abs(target - SaturationEngine.NEUTRAL) > 0.005f) {
                satFailureToastShown = true
                Toast.makeText(
                    this,
                    "Saturation needs root. Tap 'Copy ADB command' in the panel to run it from a PC.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun stopFloatingOverlay() {
        telemetryRunnable?.let { workHandler?.removeCallbacks(it) }
        hideSystemMonitorHud()
        floatingApps?.closeAll()
        ScalerService.instance?.stopScaler()
        crosshairWindow?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        crosshairWindow = null
        detachOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFrameTracking()
        try {
            getSharedPreferences(SettingsStore.prefsName(), Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {
        }
        mainHandler.removeCallbacks(rebuildRunnable)
        ScalerService.onStateChanged = null
        ScalerService.onOverlayAdded = null
        ScalerService.instance?.stopScaler()
        floatingApps?.closeAll()
        floatingApps = null
        stopFloatingOverlay()

        // Put the screen colors back to normal, then stop the background thread
        val handler = workHandler
        if (handler != null) {
            handler.removeCallbacks(satApplyRunnable)
            if (abs(SaturationEngine.lastApplied - SaturationEngine.NEUTRAL) > 0.005f) {
                handler.post { SaturationEngine.reset() }
            }
        }
        workThread?.quitSafely()
        workThread = null
        workHandler = null
    }
}
