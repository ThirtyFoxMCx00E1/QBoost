package com.example.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.example.R
import java.util.Locale

/**
 * Qboost UI translations. The strings live in res/raw/i18n.tsv (one row per string, one column per
 * language), so adding a language or fixing a translation never touches Kotlin code.
 * Anything missing in a language falls back to English.
 */
object I18n {

    data class Language(val code: String, val nativeName: String, val rtl: Boolean = false)

    val languages = listOf(
        Language("en", "English"),
        Language("es", "Español"),
        Language("ja", "日本語"),
        Language("fr", "Français"),
        Language("ar", "العربية", rtl = true),
        Language("ko", "한국어"),
        Language("zh", "中文 (Singapore)"),
        Language("pt", "Português (Brasil)"),
        Language("de", "Deutsch"),
        Language("ru", "Русский"),
        Language("hi", "हिन्दी"),
        Language("id", "Bahasa Indonesia"),
        Language("tr", "Türkçe")
    )

    private var tables: Map<String, Map<String, String>> = emptyMap()

    @Synchronized
    fun init(context: Context) {
        if (tables.isNotEmpty()) return
        try {
            val lines = context.applicationContext.resources
                .openRawResource(R.raw.i18n)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readLines() }
            var codes: List<String> = emptyList()
            val built = HashMap<String, HashMap<String, String>>()
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val cols = line.split('\t')
                if (codes.isEmpty()) {
                    // first real row is the header: key, en, es, ...
                    codes = cols.drop(1)
                    codes.forEach { built[it] = HashMap() }
                    continue
                }
                val key = cols[0]
                for (i in codes.indices) {
                    val value = cols.getOrNull(i + 1)
                    if (!value.isNullOrEmpty()) built[codes[i]]?.put(key, value)
                }
            }
            tables = built
        } catch (_: Exception) {
            tables = emptyMap()
        }
    }

    /** Language to use when the user never picked one: the phone's language if we have it, else English. */
    fun defaultLanguage(): String {
        val code = Locale.getDefault().language
        return if (languages.any { it.code == code }) code else "en"
    }

    fun isRtl(code: String): Boolean = languages.firstOrNull { it.code == code }?.rtl == true

    fun nativeName(code: String): String = languages.firstOrNull { it.code == code }?.nativeName ?: code

    fun t(lang: String, key: String): String =
        tables[lang]?.get(key) ?: tables["en"]?.get(key) ?: key

    /** Same as [t] but fills in `%s` style placeholders. */
    fun tf(lang: String, key: String, vararg args: Any): String {
        val template = t(lang, key)
        return try {
            String.format(template, *args)
        } catch (_: Exception) {
            template
        }
    }
}

/** The language the Compose UI is currently drawn in (provided in MainActivity). */
val LocalLanguage = compositionLocalOf { "en" }

@Composable
fun tr(key: String): String = I18n.t(LocalLanguage.current, key)

@Composable
fun tr(key: String, arg: Any): String = I18n.tf(LocalLanguage.current, key, arg)
