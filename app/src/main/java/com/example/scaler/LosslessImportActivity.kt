package com.example.scaler

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.i18n.I18n
import com.example.settings.SettingsStore
import kotlin.concurrent.thread

/**
 * See-through screen that opens the system file picker for Lossless.dll, imports it on a background
 * thread, tells the user how it went and closes. Started from the Scaler card in the in-game panel.
 */
class LosslessImportActivity : ComponentActivity() {

    private var language = "en"

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        Toast.makeText(this, I18n.t(language, "lsfg_importing"), Toast.LENGTH_SHORT).show()
        val app = applicationContext
        thread(name = "qboost-lossless-import") {
            val result = Lossless.import(app, uri)
            runOnUiThread {
                val key = when (result) {
                    Lossless.ImportResult.UPDATED -> "lsfg_imported"
                    Lossless.ImportResult.UNCHANGED -> "lsfg_import_unchanged"
                    Lossless.ImportResult.INVALID -> "lsfg_import_invalid"
                    Lossless.ImportResult.NOT_SUPPORTED -> "lsfg_unsupported_abi"
                    Lossless.ImportResult.FAILED -> "lsfg_import_failed"
                }
                Toast.makeText(app, I18n.t(language, key), Toast.LENGTH_LONG).show()
                ScalerSupport.onLosslessChanged?.invoke()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        I18n.init(this)
        language = SettingsStore.load(this).language
        if (savedInstanceState == null) {
            try {
                picker.launch(arrayOf("application/x-msdownload", "application/x-dosexec", "application/octet-stream", "*/*"))
            } catch (_: Exception) {
                Toast.makeText(this, I18n.t(language, "lsfg_import_failed"), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
