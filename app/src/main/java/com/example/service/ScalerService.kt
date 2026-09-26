package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.i18n.I18n
import com.example.settings.SettingsStore

/**
 * Upscaler + Frame gen (experimental).
 *
 * It asks Android to capture the game (Android 14+, "share a single app"), runs the captured frames
 * through [ScalerRenderer] and shows the result in a full-screen overlay that lets every touch
 * through to the game. The overlay stays invisible until the first processed frame is ready, hides
 * whenever the game is not on screen, and everything is torn down if anything goes wrong.
 */
class ScalerService : Service() {

    companion object {
        const val ACTION_START = "com.example.service.SCALER_START"
        const val ACTION_STOP = "com.example.service.SCALER_STOP"
        const val EXTRA_RESULT_CODE = "com.example.service.SCALER_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.example.service.SCALER_RESULT_DATA"
        const val EXTRA_UPSCALER = "com.example.service.SCALER_UPSCALER"
        const val EXTRA_FRAME_GEN = "com.example.service.SCALER_FRAME_GEN"
        private const val CHANNEL_ID = "qboost_scaler_channel"
        private const val NOTIFICATION_ID = 2026

        @Volatile
        var instance: ScalerService? = null

        @Volatile
        var upscalerOn = false

        @Volatile
        var frameGenOn = false

        /** Called (on any thread) whenever the Upscaler / Frame gen state changes. */
        var onStateChanged: (() -> Unit)? = null

        /** Called when the scaler overlay window was added, so the panel can move itself back on top. */
        var onOverlayAdded: (() -> Unit)? = null

        val isRunning: Boolean
            get() = instance != null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var renderer: ScalerRenderer? = null
    private var overlayRoot: FrameLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var firstFrameShown = false
    private var contentVisible = true
    private var stopping = false
    private var language = "en"

    private val watchdog = Runnable {
        if (!firstFrameShown) {
            toast("scaler_failed")
            stopScaler()
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsStore.KEY_SCALER_SHARPNESS) {
            renderer?.sharpness = SettingsStore.load(this).scalerSharpness
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopScaler()
                return START_NOT_STICKY
            }
            ACTION_START -> startScaler(intent)
            else -> if (instance == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun toast(key: String) {
        Toast.makeText(this, I18n.t(language, key), Toast.LENGTH_LONG).show()
    }

    private fun startScaler(intent: Intent) {
        I18n.init(this)
        language = SettingsStore.load(this).language

        // Android needs the foreground notification before it hands out the capture
        startForegroundNotification()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            toast("scaler_need_android14")
            stopScaler()
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (data == null || resultCode != Activity.RESULT_OK) {
            toast("scaler_denied")
            stopScaler()
            return
        }

        upscalerOn = intent.getBooleanExtra(EXTRA_UPSCALER, false)
        frameGenOn = intent.getBooleanExtra(EXTRA_FRAME_GEN, false)

        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            // getMediaProjection can return null (for example when the permission result is no longer valid)
            val mp = manager.getMediaProjection(resultCode, data)
            if (mp == null) {
                toast("scaler_denied")
                stopScaler()
                return
            }
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    mainHandler.post { stopScaler() }
                }

                // Android 14+: the game left the screen (or came back)
                override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                    mainHandler.post { setContentVisible(isVisible) }
                }
            }, mainHandler)
            projection = mp
            instance = this
            getSharedPreferences(SettingsStore.prefsName(), Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefsListener)
            createOverlay()
            onStateChanged?.invoke()
        } catch (e: Throwable) {
            toast("scaler_failed")
            stopScaler()
        }
    }

    private fun createOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val bounds = wm.maximumWindowMetrics.bounds
        val width = bounds.width()
        val height = bounds.height()
        val dpi = resources.displayMetrics.densityDpi

        val surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                onOverlaySurface(holder.surface, width, height, dpi)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                mainHandler.post { stopScaler() }
            }
        })

        val root = FrameLayout(this)
        root.addView(
            surfaceView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not touchable: every touch goes straight through to the game
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.alpha = 0f // invisible until the first processed frame is ready
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        wm.addView(root, params)
        overlayRoot = root
        overlayParams = params
        onOverlayAdded?.invoke()
    }

    private fun onOverlaySurface(surface: Surface, width: Int, height: Int, dpi: Int) {
        if (renderer != null || stopping) return
        val r = ScalerRenderer(surface, width, height, width, height, object : ScalerRenderer.Listener {
            override fun onFirstFrame() {
                mainHandler.post {
                    firstFrameShown = true
                    mainHandler.removeCallbacks(watchdog)
                    updateOverlayAlpha()
                }
            }

            override fun onError(message: String) {
                mainHandler.post {
                    toast("scaler_failed")
                    stopScaler()
                }
            }
        })
        r.upscalerOn = upscalerOn
        r.frameGenOn = frameGenOn
        r.sharpness = SettingsStore.load(this).scalerSharpness
        renderer = r

        val input = r.start()
        if (input == null) {
            toast("scaler_failed")
            stopScaler()
            return
        }
        try {
            virtualDisplay = projection?.createVirtualDisplay(
                "QboostScaler",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                input,
                null,
                mainHandler
            )
        } catch (e: Throwable) {
            toast("scaler_failed")
            stopScaler()
            return
        }
        mainHandler.postDelayed(watchdog, 4000L)
    }

    private fun setContentVisible(visible: Boolean) {
        contentVisible = visible
        updateOverlayAlpha()
    }

    /** Shown only after the first processed frame, and only while the game itself is on screen. */
    private fun updateOverlayAlpha() {
        val root = overlayRoot ?: return
        val params = overlayParams ?: return
        params.alpha = if (firstFrameShown && contentVisible) 1f else 0f
        try {
            windowManager?.updateViewLayout(root, params)
        } catch (_: Exception) {
        }
    }

    /** Called from the panel when the user toggles Upscaler or Frame gen while the scaler is running. */
    fun applyConfig(upscaler: Boolean, frameGen: Boolean) {
        upscalerOn = upscaler
        frameGenOn = frameGen
        renderer?.upscalerOn = upscaler
        renderer?.frameGenOn = frameGen
        if (!upscaler && !frameGen) {
            stopScaler()
        } else {
            onStateChanged?.invoke()
        }
    }

    fun stopScaler() {
        if (stopping) return
        stopping = true
        mainHandler.removeCallbacks(watchdog)
        try {
            getSharedPreferences(SettingsStore.prefsName(), Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {
        }
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        try {
            projection?.stop()
        } catch (_: Exception) {
        }
        projection = null
        renderer?.stop()
        renderer = null
        overlayRoot?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayRoot = null
        instance = null
        upscalerOn = false
        frameGenOn = false
        onStateChanged?.invoke()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!stopping) stopScaler()
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Qboost Upscaler / Frame gen", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, ScalerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Qboost Upscaler / Frame gen")
            .setContentText("Running - tap Stop to end")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
