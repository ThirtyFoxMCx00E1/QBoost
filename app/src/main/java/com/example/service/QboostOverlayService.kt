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
import com.example.hardware.RamBooster
import com.example.hardware.TelemetryFormat
import com.example.i18n.I18n
import com.example.model.PerformanceStats
import com.example.settings.AppSettings
import com.example.settings.SettingsStore
import org.json.JSONArray
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
    private var gamesPage: ScrollView? = null
    private var gamesList: LinearLayout? = null
    private var tabTools: TextView? = null
    private var tabGames: TextView? = null
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
    private var upscalerTile: TextView? = null
    private var frameGenTile: TextView? = null
    private var hapticsTile: TextView? = null
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // ---- toggles ----
    private var isHapticsOn = true

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

    /** Rebuilds the panel when language, handle opacity, panel opacity or the dock changes in Settings. */
    private val rebuildRunnable = Runnable { rebuildOverlay() }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsStore.KEY_LANGUAGE ||
            key == SettingsStore.KEY_HANDLE_OPACITY ||
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
            if (handleY == 0) handleY = dp(150)
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
                x = 0
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

            selectTab(0)
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

        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        handle.setOnTouchListener { _, event ->
            val params = overlayParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - initialTouchY
                    if (abs(dy) > dp(8)) isDragging = true
                    if (isDragging) {
                        val screenH = resources.displayMetrics.heightPixels
                        params.y = (initialY + dy.toInt()).coerceIn(0, screenH - dp(86))
                        handleY = params.y
                        overlayRoot?.let { windowManager?.updateViewLayout(it, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - initialTouchX
                    // a tap, or a swipe to the right, opens the panel
                    if (!isDragging && (abs(dx) < dp(16) || dx > dp(20))) {
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
        val icon: View
        if (installedPackage != null) {
            val image = ImageView(this)
            try {
                image.setImageDrawable(packageManager.getApplicationIcon(installedPackage))
            } catch (_: Exception) {
            }
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            icon = image
        } else {
            // Not installed: a plain gradient tile with the first letter (the web version still works)
            val letter = label(app.title.take(1).uppercase(Locale.getDefault()), 18f, Color.WHITE, bold = true)
            letter.gravity = Gravity.CENTER
            letter.background = gradient(Color.parseColor("#FF2A3552"), Color.parseColor("#FF1B2438"), radiusDp = 13)
            icon = letter
        }
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

        // ---- tabs: Gaming tools | Games | x ----
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.gravity = Gravity.CENTER_VERTICAL
        tabs.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))
        val tools = tabLabel(s("p_tools")) { selectTab(0) }
        val games = tabLabel(s("p_games")) { selectTab(1) }
        tabTools = tools
        tabGames = games
        val close = label("×", 22f, C_GRAY)
        close.gravity = Gravity.CENTER
        close.setPadding(dp(12), 0, dp(14), 0)
        close.setOnClickListener {
            UiSounds.play()
            setPanelExpanded(false)
        }
        tabs.addView(tools)
        tabs.addView(games)
        tabs.addView(close)
        card.addView(tabs)

        // ---- pages ----
        val pages = FrameLayout(this)
        pages.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        val toolsScroll = buildToolsPage()
        val gamesScroll = buildGamesPage()
        toolsPage = toolsScroll
        gamesPage = gamesScroll
        pages.addView(toolsScroll)
        pages.addView(gamesScroll)
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

    private fun selectTab(index: Int) {
        val toolsSelected = index == 0
        styleTab(tabTools, toolsSelected)
        styleTab(tabGames, !toolsSelected)
        toolsPage?.visibility = if (toolsSelected) View.VISIBLE else View.GONE
        gamesPage?.visibility = if (toolsSelected) View.GONE else View.VISIBLE
        if (!toolsSelected) refreshGamesPage()
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
        val upscaler = toolTile(s("upscaler"), active = ScalerService.upscalerOn) {
            toggleScaler(toggleUpscaler = true, toggleFrameGen = false)
        }
        val frameGen = toolTile(s("frame_gen"), active = ScalerService.frameGenOn) {
            toggleScaler(toggleUpscaler = false, toggleFrameGen = true)
        }
        val haptics = toolTile(s("tool_haptics"), active = isHapticsOn) { view ->
            isHapticsOn = !isHapticsOn
            styleTile(view as TextView, isHapticsOn)
            vibratePulse()
        }
        satTile = sat
        upscalerTile = upscaler
        frameGenTile = frameGen
        hapticsTile = haptics
        column.addView(toolRow(sat, upscaler, frameGen, haptics))
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

        column.addView(buildSaturationCard())

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

    private fun buildGamesPage(): ScrollView {
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(12), dp(4), dp(12), dp(12))
        gamesList = list
        scroll.addView(list)
        return scroll
    }

    /** Installed games from the Qboost library, read from the saved list. */
    private fun readSavedGames(): List<Pair<String, String>> {
        val json = getSharedPreferences(SettingsStore.prefsName(), Context.MODE_PRIVATE)
            .getString("saved_games_json", null) ?: return emptyList()
        val result = ArrayList<Pair<String, String>>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pkg = obj.optString("packageName", "")
                val name = obj.optString("name", pkg)
                if (pkg.isNotBlank() && packageManager.getLaunchIntentForPackage(pkg) != null) {
                    result.add(Pair(name, pkg))
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun refreshGamesPage() {
        val list = gamesList ?: return
        list.removeAllViews()
        val games = readSavedGames()
        if (games.isEmpty()) {
            list.addView(label(s("no_games"), 12f, C_GRAY))
            return
        }
        games.forEach { (name, pkg) ->
            val row = TextView(this)
            row.text = name
            row.setTextColor(if (pkg == activeGamePackage) Color.WHITE else C_TEXT)
            row.textSize = 13f
            row.typeface = Typeface.DEFAULT_BOLD
            row.maxLines = 1
            row.ellipsize = TextUtils.TruncateAt.END
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(14), 0, dp(14), 0)
            if (pkg == activeGamePackage) {
                row.background = gradient(C_BLUE, C_BLUE_DARK, radiusDp = 14)
            } else {
                row.background = gradient(Color.parseColor("#CC2A3552"), Color.parseColor("#CC1B2438"), radiusDp = 14)
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
            lp.bottomMargin = dp(6)
            row.layoutParams = lp
            row.setOnClickListener {
                UiSounds.play()
                if (launchPackage(pkg)) setPanelExpanded(false)
            }
            list.addView(row)
        }
    }

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
            params.x = 0
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

    // ---------------- Upscaler / Frame gen ----------------

    private fun toggleScaler(toggleUpscaler: Boolean, toggleFrameGen: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Toast.makeText(this, s("scaler_need_android14"), Toast.LENGTH_LONG).show()
            return
        }
        val upscaler = if (toggleUpscaler) !ScalerService.upscalerOn else ScalerService.upscalerOn
        val frameGen = if (toggleFrameGen) !ScalerService.frameGenOn else ScalerService.frameGenOn
        val running = ScalerService.instance
        if (running != null) {
            running.applyConfig(upscaler, frameGen)
        } else if (upscaler || frameGen) {
            // Android asks "start capturing?" first, on a see-through screen
            val intent = Intent(this, ScalerPermissionActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(ScalerService.EXTRA_UPSCALER, upscaler)
            intent.putExtra(ScalerService.EXTRA_FRAME_GEN, frameGen)
            try {
                startActivity(intent)
                setPanelExpanded(false)
            } catch (_: Exception) {
            }
        }
        updateScalerTiles()
    }

    private fun updateScalerTiles() {
        upscalerTile?.let { styleTile(it, ScalerService.upscalerOn) }
        frameGenTile?.let { styleTile(it, ScalerService.frameGenOn) }
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
        gamesPage = null
        gamesList = null
        tabTools = null
        tabGames = null
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
        upscalerTile = null
        frameGenTile = null
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
            deviceTelemetry.sample()
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
            val gaugeValue = if (fpsAvailable) s.fps else s.refreshRate
            gauge?.valueText = "$gaugeValue"
            // The ROM does not always expose the game's FPS; then the real display rate is shown, labelled Hz
            gauge?.labelText = if (fpsAvailable) "FPS" else "Hz"
            gauge?.fraction = gaugeValue.toFloat() / maxOf(s.refreshRate, 60).toFloat()

            cpuValueText?.text = TelemetryFormat.load(s.cpuUsagePercent, s.cpuLoadEstimated)
            cpuBar?.progress = s.cpuUsagePercent.coerceAtLeast(0)
            memValueText?.text = "${s.ramUsedPercent}%"
            memBar?.progress = s.ramUsedPercent

            timeText?.text = timeFormat.format(Date())
            batteryText?.text = "${s.batteryPercent}%"
            statsLine?.text = "GPU ${TelemetryFormat.load(s.gpuUsagePercent)} - " +
                "${s.tempSource} ${TelemetryFormat.temp(s.cpuTempC)} - " +
                "RAM ${TelemetryFormat.gb(s.ramUsedMb)}/${TelemetryFormat.gb(s.ramTotalMb)} GB"

            // ---- Floating System Monitor ----
            if (isMonitorHudOpen) {
                monitorHudValues["fps"]?.text =
                    if (s.fps >= 0) "${s.fps}" else "-- (${s.refreshRate}Hz panel)"
                monitorHudValues["cpu"]?.text = TelemetryFormat.load(s.cpuUsagePercent, s.cpuLoadEstimated) +
                    (if (s.cpuFreqMhz > 0) "  ${TelemetryFormat.ghz(s.cpuFreqMhz)}" else "")
                monitorHudValues["gpu"]?.text = TelemetryFormat.load(s.gpuUsagePercent) +
                    (if (s.gpuFreqMhz > 0) "  ${s.gpuFreqMhz}MHz" else "")
                monitorHudValues["ram"]?.text = TelemetryFormat.ram(s)
                monitorHudValues["mem"]?.text = TelemetryFormat.swap(s)
                monitorHudValues["temp"]?.text = "${s.tempSource} ${TelemetryFormat.temp(s.cpuTempC)}"
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
                orientation = LinearLayout.VERTICAL
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#E6101828"))
                    cornerRadius = 12 * density
                }
                background = bg
                setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            }

            // Header: title + close
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, (3 * density).toInt())
            }
            val title = TextView(this).apply {
                text = "QBOOST MONITOR"
                setTextColor(Color.parseColor("#00E5FF"))
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val closeView = TextView(this).apply {
                text = "  ×"
                setTextColor(Color.parseColor("#FF5252"))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setOnClickListener {
                    UiSounds.play()
                    hideSystemMonitorHud()
                }
            }
            header.addView(title)
            header.addView(closeView)
            root.addView(header)

            monitorHudValues.clear()
            fun addRow(key: String, label: String, labelColor: Int) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, (1 * density).toInt(), 0, (1 * density).toInt())
                }
                val labelView = TextView(this).apply {
                    text = label
                    setTextColor(labelColor)
                    textSize = 10f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val valueView = TextView(this).apply {
                    text = "--"
                    setTextColor(Color.WHITE)
                    textSize = 10.5f
                    typeface = Typeface.MONOSPACE
                }
                row.addView(labelView)
                row.addView(valueView)
                root.addView(row)
                monitorHudValues[key] = valueView
            }
            addRow("fps", "FPS", Color.parseColor("#00E676"))
            addRow("cpu", "CPU", Color.parseColor("#00E5FF"))
            addRow("gpu", "GPU", Color.parseColor("#2F80FF"))
            addRow("ram", "RAM", Color.parseColor("#5C8DFF"))
            addRow("mem", "MEM", Color.parseColor("#FFAB00"))
            addRow("temp", "TEMP", Color.parseColor("#FFA726"))

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
        detachOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
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
