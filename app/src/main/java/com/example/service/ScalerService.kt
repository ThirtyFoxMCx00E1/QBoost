package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
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
import com.example.hardware.HardwareProbe
import com.example.hardware.ScalerTuning
import com.example.i18n.I18n
import com.example.scaler.GameDownscale
import com.example.scaler.Lossless
import com.example.scaler.LsfgNative
import com.example.scaler.ScalerSupport
import com.example.settings.SettingsStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The Scaler (Android 14+, "share a single app").
 *
 *  - Frame gen is real LSFG, the frame generation of Lossless Scaling: the captured game frames go
 *    through the LSFG engine (from DroidDeck, shaders translated from the user's own Lossless.dll) on
 *    the GPU with Vulkan compute, and the real + generated frames are presented on a full-screen
 *    overlay that lets every touch through. x2 / x3 / x4 like Lossless Scaling.
 *  - Upscale asks Android (root only) to render the game at the Quality size; the capture is taken at
 *    that size and the display hardware scales it to the screen, which costs no GPU time.
 *
 * Nothing is drawn with OpenGL any more and there is no CPU copy: capture buffers are imported
 * straight into Vulkan. Without Lossless.dll (or on a GPU LSFG cannot run on) frame gen is refused
 * with the reason instead of being faked.
 */
class ScalerService : Service() {

    companion object {
        const val ACTION_START = "com.example.service.SCALER_START"
        const val ACTION_STOP = "com.example.service.SCALER_STOP"
        const val EXTRA_RESULT_CODE = "com.example.service.SCALER_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.example.service.SCALER_RESULT_DATA"
        const val EXTRA_UPSCALER = "com.example.service.SCALER_UPSCALER"
        const val EXTRA_FRAME_GEN = "com.example.service.SCALER_FRAME_GEN"
        const val EXTRA_GAME_PACKAGE = "com.example.service.SCALER_GAME_PACKAGE"
        private const val CHANNEL_ID = "qboost_scaler_channel"
        private const val NOTIFICATION_ID = 2026

        @Volatile
        var instance: ScalerService? = null

        @Volatile
        var upscalerOn = false

        @Volatile
        var frameGenOn = false

        /** Live numbers for the panel, refreshed about once a second while running. */
        @Volatile
        var sourceFps = 0f

        @Volatile
        var shownFps = 0f

        /** True when Android really renders the game smaller (root Game Mode downscale). */
        @Volatile
        var downscaleActive = false

        /** True when this session runs the LSFG engine (Lossless.dll loaded). */
        @Volatile
        var lsfgActive = false

        /** Called (on any thread) whenever the Scaler state or its live numbers change. */
        var onStateChanged: (() -> Unit)? = null

        /** Called when the scaler overlay window was added, so the panel can move itself back on top. */
        var onOverlayAdded: (() -> Unit)? = null

        val isRunning: Boolean
            get() = instance != null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var worker: HandlerThread? = null
    private var workHandler: Handler? = null

    private var windowManager: WindowManager? = null
    private var projection: MediaProjection? = null
    private var overlayRoot: FrameLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    // Owned by the worker thread
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var handle = 0L

    @Volatile
    private var firstFrameShown = false
    private var contentVisible = true

    @Volatile
    private var stopping = false
    private var language = "en"
    private var gamePackage = ""
    private var captureScale = 1.0f
    private var flowScale = 0.8f
    private var multiplier = 2
    private var captureWidth = 0
    private var captureHeight = 0
    private var cachePath: String? = null
    private val statsBuffer = FloatArray(4)

    private val watchdog = Runnable {
        if (!firstFrameShown) {
            toast("scaler_failed")
            stopScaler()
        }
    }

    private val statsTick = object : Runnable {
        override fun run() {
            val h = handle
            if (h != 0L && !stopping) {
                LsfgNative.nativeStats(h, statsBuffer)
                sourceFps = statsBuffer[0]
                shownFps = statsBuffer[1]
                onStateChanged?.invoke()
                workHandler?.postDelayed(this, 1000L)
            }
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

    private fun toastText(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
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
        if (!LsfgNative.available) {
            toast("lsfg_unsupported_abi")
            stopScaler()
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data: Intent? = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        if (data == null || resultCode != Activity.RESULT_OK) {
            toast("scaler_denied")
            stopScaler()
            return
        }

        upscalerOn = intent.getBooleanExtra(EXTRA_UPSCALER, false)
        frameGenOn = intent.getBooleanExtra(EXTRA_FRAME_GEN, false)
        gamePackage = intent.getStringExtra(EXTRA_GAME_PACKAGE).orEmpty()

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
        } catch (e: Throwable) {
            toast("scaler_failed")
            stopScaler()
            return
        }

        val t = HandlerThread("qboost-scaler")
        t.start()
        worker = t
        val h = Handler(t.looper)
        workHandler = h
        h.post { prepareOnWorker() }
        onStateChanged?.invoke()
    }

    /** Checks the GPU and Lossless.dll, applies the root downscale, then builds the overlay. */
    private fun prepareOnWorker() {
        val report = ScalerSupport.report(this)
        if (frameGenOn) {
            val problem = when {
                !report.frameGenPossible -> I18n.t(language, "lsfg_gpu_unsupported") + "\n" + report.gpuProblem
                !report.losslessReady -> I18n.t(language, "lsfg_need_dll")
                else -> null
            }
            cachePath = if (problem == null) Lossless.readyCache(this)?.path else null
            if (cachePath == null) {
                val message = problem ?: I18n.t(language, "lsfg_import_failed")
                mainHandler.post {
                    toastText(message)
                    frameGenOn = false
                    if (!upscalerOn) stopScaler()
                }
                if (!upscalerOn) return
            }
        }
        lsfgActive = cachePath != null

        val tier = HardwareProbe.get(this).tier
        val quality = SettingsStore.scalerQuality(this)
        captureScale = if (upscalerOn || lsfgActive) ScalerTuning.scaleFor(quality, tier) else 1.0f
        flowScale = ScalerTuning.flowScaleFor(quality, tier)
        multiplier = SettingsStore.scalerMultiplier(this)

        if (upscalerOn) {
            downscaleActive = captureScale < 0.999f && GameDownscale.apply(gamePackage, captureScale)
            if (!downscaleActive) mainHandler.post { toast("scaler_upscale_needs_root") }
        }
        mainHandler.post { if (!stopping) createOverlay() }
    }

    private fun createOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val bounds = wm.maximumWindowMetrics.bounds
        captureWidth = ScalerTuning.scaledSize(bounds.width(), captureScale)
        captureHeight = ScalerTuning.scaledSize(bounds.height(), captureScale)
        val dpi = resources.displayMetrics.densityDpi
        val refreshHz = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f

        val surfaceView = SurfaceView(this)
        // Buffers at the capture size: the display hardware scales them to the screen for free.
        surfaceView.holder.setFixedSize(captureWidth, captureHeight)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                val surface = holder.surface
                workHandler?.post { startPipeline(surface, dpi, refreshHz) }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                // The pipeline must let go of the window before this returns.
                releasePipelineBlocking()
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
        try {
            wm.addView(root, params)
        } catch (e: Throwable) {
            toast("scaler_failed")
            stopScaler()
            return
        }
        overlayRoot = root
        overlayParams = params
        onOverlayAdded?.invoke()
        mainHandler.postDelayed(watchdog, 6000L)
    }

    /** Worker thread: Vulkan pipeline on the overlay, then the capture into an ImageReader. */
    private fun startPipeline(surface: Surface, dpi: Int, refreshHz: Float) {
        if (stopping || handle != 0L) return
        val h = try {
            LsfgNative.nativeStart(
                surface, cachePath, captureWidth, captureHeight,
                if (lsfgActive && frameGenOn) multiplier else 1, flowScale, refreshHz
            )
        } catch (t: Throwable) {
            0L
        }
        if (h == 0L) {
            val why = runCatching { LsfgNative.nativeLastError() }.getOrDefault("")
            mainHandler.post {
                toastText(I18n.t(language, "scaler_failed") + if (why.isNotBlank()) "\n$why" else "")
                stopScaler()
            }
            return
        }
        handle = h
        try {
            val r = ImageReader.newInstance(
                captureWidth, captureHeight, PixelFormat.RGBA_8888, 3,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
            )
            r.setOnImageAvailableListener({ onCapturedFrame(it) }, workHandler)
            reader = r
            virtualDisplay = projection?.createVirtualDisplay(
                "QboostScaler", captureWidth, captureHeight, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, workHandler
            )
        } catch (e: Throwable) {
            mainHandler.post {
                toast("scaler_failed")
                stopScaler()
            }
            return
        }
        workHandler?.postDelayed(statsTick, 1000L)
    }

    private fun onCapturedFrame(source: ImageReader) {
        val h = handle
        val image = try {
            source.acquireLatestImage()
        } catch (_: Throwable) {
            null
        } ?: return
        try {
            if (h == 0L || stopping) return
            val buffer = image.hardwareBuffer ?: return
            val result = try {
                LsfgNative.nativeFrame(h, buffer)
            } finally {
                buffer.close()
            }
            if (result < 0) {
                val why = runCatching { LsfgNative.nativeLastError() }.getOrDefault("")
                mainHandler.post {
                    toastText(I18n.t(language, "scaler_failed") + if (why.isNotBlank()) "\n$why" else "")
                    stopScaler()
                }
                return
            }
            if (!firstFrameShown) {
                firstFrameShown = true
                mainHandler.post {
                    mainHandler.removeCallbacks(watchdog)
                    updateOverlayAlpha()
                }
            }
        } finally {
            image.close()
        }
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

    /** Called from the panel when the mode changes while the Scaler is running. */
    fun applyConfig(upscaler: Boolean, frameGen: Boolean) {
        if (!upscaler && !frameGen) {
            stopScaler()
            return
        }
        val upscaleChanged = upscaler != upscalerOn
        upscalerOn = upscaler
        frameGenOn = frameGen && lsfgActive
        if (frameGen && !lsfgActive) toast("scaler_framegen_next")
        workHandler?.post {
            val h = handle
            if (h != 0L) LsfgNative.nativeConfigure(h, if (frameGenOn) multiplier else 1, flowScale)
            if (upscaleChanged) {
                if (upscaler) {
                    downscaleActive = captureScale < 0.999f && GameDownscale.apply(gamePackage, captureScale)
                } else if (downscaleActive) {
                    GameDownscale.reset(gamePackage)
                    downscaleActive = false
                }
            }
            onStateChanged?.invoke()
        }
        onStateChanged?.invoke()
    }

    /** x2 / x3 / x4, applied on the next captured frame. */
    fun applyMultiplier(value: Int) {
        multiplier = value.coerceIn(2, 4)
        workHandler?.post {
            val h = handle
            if (h != 0L && frameGenOn) LsfgNative.nativeConfigure(h, multiplier, flowScale)
        }
    }

    /** Worker-thread teardown of capture + Vulkan; waits so the overlay surface can safely go away. */
    private fun releasePipelineBlocking() {
        val h = workHandler ?: return
        if (Looper.myLooper() == h.looper) {
            releasePipeline()
            return
        }
        val done = CountDownLatch(1)
        h.post {
            releasePipeline()
            done.countDown()
        }
        try {
            done.await(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
        }
    }

    private fun releasePipeline() {
        workHandler?.removeCallbacks(statsTick)
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        reader?.setOnImageAvailableListener(null, null)
        val h = handle
        handle = 0L
        if (h != 0L) {
            try {
                LsfgNative.nativeStop(h)
            } catch (_: Throwable) {
            }
        }
        try {
            reader?.close()
        } catch (_: Exception) {
        }
        reader = null
        if (downscaleActive) {
            GameDownscale.reset(gamePackage)
            downscaleActive = false
        }
    }

    fun stopScaler() {
        if (stopping) return
        stopping = true
        mainHandler.removeCallbacks(watchdog)
        releasePipelineBlocking()
        try {
            projection?.stop()
        } catch (_: Exception) {
        }
        projection = null
        overlayRoot?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayRoot = null
        worker?.quitSafely()
        worker = null
        workHandler = null
        instance = null
        upscalerOn = false
        frameGenOn = false
        lsfgActive = false
        sourceFps = 0f
        shownFps = 0f
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
                NotificationChannel(CHANNEL_ID, "Qboost Scaler", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, ScalerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Qboost Scaler")
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
