package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.R

/**
 * Plays res/raw/click.ogg on UI buttons (Game Space and the in-game HUD).
 * Replace app/src/main/res/raw/click.ogg with your own sound to change it.
 */
object UiSounds {

    private const val PREFS = "qboost_settings"
    private const val KEY_ENABLED = "click_sound_enabled"

    private var soundPool: SoundPool? = null

    @Volatile
    private var clickId = 0

    @Volatile
    private var loaded = false

    @Volatile
    private var enabled = true

    @Synchronized
    fun init(context: Context) {
        val appContext = context.applicationContext
        enabled = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
        if (soundPool != null) return
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attributes)
                .build()
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0 && sampleId == clickId) loaded = true
            }
            soundPool = pool
            clickId = pool.load(appContext, R.raw.click, 1)
        } catch (_: Exception) {
            soundPool = null
            loaded = false
        }
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /** Plays the click if sounds are on. Cheap and safe to call from any UI callback. */
    fun play() {
        if (!enabled || !loaded) return
        try {
            soundPool?.play(clickId, 1f, 1f, 1, 0, 1f)
        } catch (_: Exception) {
        }
    }
}
