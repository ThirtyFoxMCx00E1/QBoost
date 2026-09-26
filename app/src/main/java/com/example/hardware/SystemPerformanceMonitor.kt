package com.example.hardware

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.view.Choreographer
import com.example.model.PerformanceStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt

/**
 * Feeds the Game Space screens with REAL device telemetry (see [DeviceTelemetry]).
 * It only runs while the Qboost screen is visible so it does not steal performance from a game.
 */
class SystemPerformanceMonitor(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telemetry = DeviceTelemetry(context)

    private val _stats = MutableStateFlow(
        PerformanceStats(
            cpuCores = Runtime.getRuntime().availableProcessors(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    )
    val stats: StateFlow<PerformanceStats> = _stats.asStateFlow()

    private var hostScope: CoroutineScope? = null
    private var monitorJob: Job? = null

    // Frame rate of Qboost's own window (only used when the display FPS node is not readable).
    private var isTrackingFrames = false
    private var frameCount = 0
    private var lastFrameStamp = 0L

    @Volatile
    private var ownFps = -1

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameCount++
            val elapsed = frameTimeNanos - lastFrameStamp
            if (elapsed >= 1_000_000_000L) {
                ownFps = ((frameCount * 1_000_000_000.0) / elapsed).roundToInt().coerceIn(1, 240)
                frameCount = 0
                lastFrameStamp = frameTimeNanos
            }
            if (isTrackingFrames) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun start(scope: CoroutineScope) {
        hostScope = scope
        resume()
    }

    /** Starts (or restarts) sampling. Call from the main thread. */
    fun resume() {
        val scope = hostScope ?: return
        if (monitorJob?.isActive == true) return

        if (!isTrackingFrames) {
            isTrackingFrames = true
            frameCount = 0
            lastFrameStamp = System.nanoTime()
            ownFps = -1
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        monitorJob = scope.launch(Dispatchers.Default) {
            var tick = 0
            var online = true
            var ping = -1
            while (isActive) {
                val snapshot = telemetry.sample()

                // Network check every ~3.6s to avoid constant traffic
                if (tick % 3 == 0) {
                    val (isOnline, latency) = checkNetworkLatency()
                    online = isOnline
                    ping = latency
                }
                tick++

                val previous = _stats.value
                _stats.value = snapshot.copy(
                    fps = if (snapshot.fpsIsSystemMeasured) snapshot.fps else ownFps,
                    pingMs = ping,
                    isOnline = online,
                    processesCleared = previous.processesCleared,
                    freedRamMb = previous.freedRamMb
                )
                delay(1200)
            }
        }
    }

    /** Stops sampling and frame counting (call when the app goes to the background). */
    fun pause() {
        isTrackingFrames = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        monitorJob?.cancel()
        monitorJob = null
    }

    private suspend fun checkNetworkLatency(): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (!hasInternet) {
            return@withContext Pair(false, -1)
        }

        try {
            val start = System.currentTimeMillis()
            val socket = Socket()
            socket.connect(InetSocketAddress("8.8.8.8", 53), 1500)
            socket.close()
            val elapsed = (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
            Pair(true, elapsed)
        } catch (e: Exception) {
            // Connected, but the DNS probe failed: report "unknown" instead of inventing a number.
            Pair(true, -1)
        }
    }

    /** Real purge: asks Android to stop background apps, then reports the measured freed RAM. */
    suspend fun clearBackgroundProcesses(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val result = RamBooster.purge(context, setOf(context.packageName))
        _stats.value = _stats.value.copy(
            processesCleared = result.requestedApps,
            freedRamMb = result.freedMb
        )
        Pair(result.requestedApps, result.freedMb)
    }

    fun stop() {
        pause()
        hostScope = null
    }
}
