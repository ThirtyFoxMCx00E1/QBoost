package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Plays the Qboost lobby theme (res/raw/lobby_theme.m4a), a ~60-minute track, and loops it with a
 * deliberate [LOOP_GAP_MS] of silence between one play-through ending and the next one starting.
 *
 * A single `MediaPlayer` is enough for this: when it finishes, this class waits out the gap on a
 * coroutine, then seeks back to the start and starts it again. (An earlier version of this class used
 * two `MediaPlayer`s and `setNextMediaPlayer` for a gapless loop; that's the opposite of what's wanted
 * now, so it's gone.)
 *
 * The visual pulse is driven by the REAL music: res/raw/lobby_theme_env.bin holds the loudness of the
 * track every 50 ms, and we read it at the player's current playback position.
 */
class LobbyMusicManager(private val context: Context) {

    private companion object {
        /** Length of one loudness sample in lobby_theme_env.bin. */
        const val ENVELOPE_STEP_MS = 50

        /** Volume while a trailer is playing, as a fraction of the normal (un-ducked) volume. */
        const val DUCK_FACTOR = 0.15f

        /** Silence between the lobby theme ending and the next loop starting. */
        const val LOOP_GAP_MS = 8000L
    }

    private var envelope = ByteArray(0)

    private var player: MediaPlayer? = null
    private var started = false

    private var beatJob: Job? = null
    private var loopJob: Job? = null
    private var musicScope: CoroutineScope? = null

    /** 0..1, set from Settings > Music volume. */
    @Volatile
    private var volumeScale = 1.0f

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** True while a game trailer is playing over this music (see [setDucked]). */
    private val _isDucked = MutableStateFlow(false)
    val isDucked: StateFlow<Boolean> = _isDucked.asStateFlow()

    private val _beatIntensity = MutableStateFlow(0f)
    val beatIntensity: StateFlow<Float> = _beatIntensity.asStateFlow()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun startMusic(scope: CoroutineScope, initiallyMuted: Boolean = false) {
        _isMuted.value = initiallyMuted
        musicScope = scope
        loadEnvelope()

        if (!started) {
            val p = newPreparedPlayer()
            if (p != null) {
                player = p
                applyVolumeTo(p)
                wireCompletion(p)
                p.start()
                started = true
                _isPlaying.value = true
            } else {
                _isPlaying.value = false
            }
        } else {
            resume()
        }

        // Pulse follows the loudness of the music at the player's current position
        beatJob?.cancel()
        beatJob = scope.launch {
            while (isActive) {
                var level = 0f
                if (_isPlaying.value && !_isMuted.value && envelope.isNotEmpty()) {
                    val positionMs = try {
                        player?.currentPosition ?: 0
                    } catch (_: Exception) {
                        0
                    }
                    val index = (positionMs / ENVELOPE_STEP_MS).coerceIn(0, envelope.size - 1)
                    level = (envelope[index].toInt() and 0xFF) / 255f
                }
                // 5% steps so the UI only redraws when the pulse visibly changes
                _beatIntensity.value = (level * 20f).roundToInt() / 20f
                delay(80)
            }
        }
    }

    /** A freshly prepared player on the lobby theme, ready to play from the start. */
    private fun newPreparedPlayer(): MediaPlayer? {
        return try {
            val p = MediaPlayer()
            context.resources.openRawResourceFd(R.raw.lobby_theme).use { afd ->
                p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            p.prepare() // local raw resource: fast enough to do synchronously
            p
        } catch (_: Exception) {
            null
        }
    }

    /** Fires when the track plays through to the end: wait out [LOOP_GAP_MS] of silence, then loop. */
    private fun wireCompletion(mediaPlayer: MediaPlayer) {
        mediaPlayer.setOnCompletionListener {
            _isPlaying.value = false
            loopJob?.cancel()
            loopJob = musicScope?.launch {
                delay(LOOP_GAP_MS)
                restartFromBeginning()
            }
        }
    }

    /** Loops the track from the top once the silent gap has passed. */
    private fun restartFromBeginning() {
        val current = player
        if (current == null) {
            val fresh = newPreparedPlayer() ?: return
            player = fresh
            applyVolumeTo(fresh)
            wireCompletion(fresh)
            fresh.start()
            _isPlaying.value = true
            return
        }
        try {
            current.seekTo(0)
            applyVolumeTo(current)
            current.start()
            _isPlaying.value = true
        } catch (_: Exception) {
            // The player is in a bad state: drop it and build a fresh one rather than leaving the
            // lobby theme stopped forever.
            try {
                current.release()
            } catch (_: Exception) {
            }
            val fresh = newPreparedPlayer()
            player = fresh
            if (fresh != null) {
                applyVolumeTo(fresh)
                wireCompletion(fresh)
                fresh.start()
                _isPlaying.value = true
            }
        }
    }

    private fun loadEnvelope() {
        if (envelope.isNotEmpty()) return
        try {
            envelope = context.resources.openRawResource(R.raw.lobby_theme_env).use { it.readBytes() }
        } catch (_: Exception) {
            envelope = ByteArray(0)
        }
    }

    fun toggleMute(): Boolean {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        applyVolume()
        return newMuted
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        applyVolume()
    }

    fun setVolumeScale(scale: Float) {
        volumeScale = scale.coerceIn(0f, 1f)
        applyVolume()
    }

    /**
     * Ducks the lobby theme down to [DUCK_FACTOR] of its normal volume while [ducked] is true — used
     * while a game trailer is playing on the details screen, so its own audio isn't fighting the music.
     * The normal volume (Settings > Music volume) is untouched and comes straight back once un-ducked.
     */
    fun setDucked(ducked: Boolean) {
        if (_isDucked.value == ducked) return
        _isDucked.value = ducked
        applyVolume()
    }

    private fun applyVolume() {
        applyVolumeTo(player)
    }

    /** No-op (safely ignored) while [target] is mid-reset and not yet prepared. */
    private fun applyVolumeTo(target: MediaPlayer?) {
        if (target == null) return
        var vol = if (_isMuted.value) 0.0f else volumeScale
        if (_isDucked.value) vol *= DUCK_FACTOR
        try {
            target.setVolume(vol, vol)
        } catch (_: Exception) {
        }
    }

    fun triggerHapticBeat(enabled: Boolean) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(28, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(28)
            }
        } catch (_: Exception) {
        }
    }

    fun pause() {
        try {
            player?.pause()
            _isPlaying.value = false
        } catch (_: Exception) {
        }
    }

    fun resume() {
        try {
            if (!_isMuted.value) {
                player?.start()
                _isPlaying.value = true
            }
        } catch (_: Exception) {
        }
    }

    fun release() {
        beatJob?.cancel()
        beatJob = null
        loopJob?.cancel()
        loopJob = null
        try {
            player?.setOnCompletionListener(null)
            player?.stop()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        started = false
        _isPlaying.value = false
    }
}
