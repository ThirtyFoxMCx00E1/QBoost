package com.example.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.i18n.I18n
import com.example.settings.SettingsStore

/**
 * Invisible screen that shows Android's "start capturing?" question for the Upscaler / Frame gen
 * and hands the answer to [ScalerService]. It closes itself right away.
 */
class ScalerPermissionActivity : ComponentActivity() {

    private var upscaler = false
    private var frameGen = false
    private var language = "en"

    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, ScalerService::class.java)
            intent.action = ScalerService.ACTION_START
            intent.putExtra(ScalerService.EXTRA_RESULT_CODE, result.resultCode)
            intent.putExtra(ScalerService.EXTRA_RESULT_DATA, data)
            intent.putExtra(ScalerService.EXTRA_UPSCALER, upscaler)
            intent.putExtra(ScalerService.EXTRA_FRAME_GEN, frameGen)
            ContextCompat.startForegroundService(this, intent)
        } else {
            Toast.makeText(this, I18n.t(language, "scaler_denied"), Toast.LENGTH_LONG).show()
            ScalerService.onStateChanged?.invoke()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        I18n.init(this)
        language = SettingsStore.load(this).language
        upscaler = intent.getBooleanExtra(ScalerService.EXTRA_UPSCALER, false)
        frameGen = intent.getBooleanExtra(ScalerService.EXTRA_FRAME_GEN, false)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Toast.makeText(this, I18n.t(language, "scaler_need_android14"), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Toast.makeText(this, I18n.t(language, "scaler_pick_app"), Toast.LENGTH_LONG).show()
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(captureIntent(manager))
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun captureIntent(manager: MediaProjectionManager): Intent =
        manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())
}
