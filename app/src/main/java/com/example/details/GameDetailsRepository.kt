package com.example.details

import android.content.Context
import android.text.Html
import com.example.R
import com.example.model.GameItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Loads the data of the "View details" screen. Sources, best first for each field:
 *
 *  1. Firebase Realtime Database (your own curated data: trailer, gallery, developer, languages ...)
 *     read through its REST address from res/raw/firebase_config.json. This is the ONLY source for the
 *     trailer video — see [PlayStoreClient.fetch] for why the Play page's promo video is intentionally
 *     not read anymore.
 *  2. the public Google Play page of the game (rating, description, genre, developer, age rating,
 *     screenshots ...). Google has no official API for this, so it is read from the page and can stop
 *     working if Google changes the page. Rating is ONLY ever taken from Google Play.
 *  3. the phone itself when the game is installed (engine, minimum Android, app size, 64/32-bit)
 *
 * Anything that cannot be found stays empty and the screen shows "--".
 * Blocking calls: run on a background thread.
 */
class GameDetailsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val cache = appContext.getSharedPreferences("qboost_details", Context.MODE_PRIVATE)

    /** Instant version (no network): title + what the phone knows about the installed game. */
    fun quick(game: GameItem): GameDetails {
        val base = GameDetails(title = game.name)
        val device = DeviceReader.read(appContext, game.packageName)
        return if (device != null) device.withFallback(base) else base
    }

    fun load(game: GameItem, language: String): GameDetails {
        val key = keyFor(game)
        val base = GameDetails(title = game.name)
        val device = DeviceReader.read(appContext, game.packageName)

        val cached = readCache(key)
        val fresh = cached != null && System.currentTimeMillis() - cached.first < CACHE_TTL_MS
        val network: GameDetails? = if (fresh) {
            cached?.second
        } else {
            val result = fetchNetwork(game, key, language)
            if (result != null && result.cacheable) writeCache(key, result.details)
            result?.details ?: cached?.second
        }

        return (network ?: GameDetails())
            .withFallback(device ?: GameDetails())
            .withFallback(base)
    }

    private class NetworkResult(val details: GameDetails, val cacheable: Boolean)

    private fun fetchNetwork(game: GameItem, key: String, language: String): NetworkResult? {
        val firebase = try {
            FirebaseClient.fetch(appContext, key)
        } catch (_: Exception) {
            null
        }
        val hasPackage = game.packageName.isNotBlank()
        val play = if (hasPackage) {
            try {
                PlayStoreClient.fetch(game.packageName, language)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        if (firebase == null && play == null) return null
        val merged = (firebase ?: GameDetails()).withFallback(play ?: GameDetails())

        // The rating shown is always the Google Play one, never a made-up or curated number
        val details = merged.copy(
            rating = play?.rating ?: -1f,
            ratingCount = play?.ratingCount ?: -1L,
            fromPlay = play != null,
            fromFirebase = firebase != null
        )
        // Do not keep a half result (for example offline while Play could not be reached) for 12 hours
        return NetworkResult(details, cacheable = play != null || !hasPackage)
    }

    private fun readCache(key: String): Pair<Long, GameDetails>? {
        val text = cache.getString("d_$key", null) ?: return null
        return try {
            val o = JSONObject(text)
            Pair(o.optLong("t", 0L), GameDetails.fromJson(o.getJSONObject("d")))
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(key: String, details: GameDetails) {
        try {
            val o = JSONObject()
            o.put("t", System.currentTimeMillis())
            o.put("d", details.toJson())
            cache.edit().putString("d_$key", o.toString()).apply()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val CACHE_TTL_MS = 12L * 60L * 60L * 1000L

        /** Key of this game in Firebase: games/<key>. Library games use their id, custom ones their package. */
        fun keyFor(game: GameItem): String {
            val looksGenerated = game.id.length >= 30 && game.id.contains('-')
            val raw = if (looksGenerated && game.packageName.isNotBlank()) {
                "pkg_" + game.packageName
            } else {
                game.id
            }
            return raw.replace(Regex("[.\\$#\\[\\]/]"), "_")
        }
    }
}

// ================================================================================================
//  HTTP
// ================================================================================================

// The desktop version of the Google Play page is the one with the structured data the reader looks for
private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"

private fun httpGet(url: String, acceptLanguage: String? = null, maxBytes: Int = 4_000_000): String? {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", BROWSER_USER_AGENT)
            if (acceptLanguage != null) setRequestProperty("Accept-Language", acceptLanguage)
        }
        if (connection.responseCode != 200) return null
        connection.inputStream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16384)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) break
                out.write(buffer, 0, read)
            }
            out.toString("UTF-8")
        }
    } catch (_: Exception) {
        null
    } finally {
        try {
            connection?.disconnect()
        } catch (_: Exception) {
        }
    }
}

// ================================================================================================
//  Firebase Realtime Database (REST, no SDK needed)
// ================================================================================================

private object FirebaseClient {

    fun databaseUrl(context: Context): String {
        return try {
            val text = context.resources.openRawResource(R.raw.firebase_config).bufferedReader().use { it.readText() }
            JSONObject(text).optString("databaseUrl").trim().trimEnd('/')
        } catch (_: Exception) {
            ""
        }
    }

    fun fetch(context: Context, key: String): GameDetails? {
        val base = databaseUrl(context)
        if (base.isBlank()) return null
        val text = httpGet("$base/games/${URLEncoder.encode(key, "UTF-8")}.json") ?: return null
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == "null") return null
        val o = JSONObject(trimmed)
        val languages = when (val value = o.opt("languages")) {
            is String -> value
            else -> GameDetails.stringList(value).joinToString(", ")
        }
        return GameDetails(
            title = o.optString("title"),
            summary = o.optString("summary"),
            description = o.optString("description"),
            genres = GameDetails.stringList(o.opt("genres")),
            developer = o.optString("developer"),
            releaseDate = o.optString("releaseDate"),
            ageRating = o.optString("ageRating"),
            languages = languages,
            trailerUrl = o.optString("trailer"),
            gallery = GameDetails.stringList(o.opt("gallery")),
            engine = o.optString("engine"),
            minAndroid = o.optString("minAndroid"),
            fromFirebase = true
        )
    }
}

// ================================================================================================
//  Google Play (public store page)
// ================================================================================================

private object PlayStoreClient {

    private val LD_JSON = Regex(
        "<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>",
        RegexOption.DOT_MATCHES_ALL
    )
    private val IMAGE = Regex("https://play-lh\\.googleusercontent\\.com/([A-Za-z0-9_\\-]{20,})=w(\\d{3,4})-h(\\d{3,4})[A-Za-z0-9\\-]*")
    private val DATE_LOOSE = Regex("([A-Z][a-z]{2,8} \\d{1,2}, \\d{4})")
    private val DATE_PUBLISHED_JSON = Regex("\"datePublished\":\\s*\"(\\d{4}-\\d{2}-\\d{2})")
    private val ANDROID_VERSION = Regex("(\\d+(?:\\.\\d+)?)")
    private val IN_LANGUAGE_JSON = Regex("\"inLanguage\":\\s*\"([A-Za-z]{2}(?:-[A-Za-z]{2})?)\"")
    private val LANGUAGE_NAMES = mapOf(
        "en" to "English", "es" to "Spanish", "ja" to "Japanese", "fr" to "French", "ar" to "Arabic",
        "ko" to "Korean", "zh" to "Chinese", "pt" to "Portuguese", "de" to "German", "ru" to "Russian",
        "hi" to "Hindi", "id" to "Indonesian", "tr" to "Turkish"
    )
    private val ABOUT_HEADINGS = listOf("About this game", "About this app")
    private val ABOUT_END_MARKERS = listOf("Data safety", "Ratings and reviews", "What's new", "App support", "Similar apps")

    private fun playLanguage(code: String): String = when (code) {
        "zh" -> "zh-CN"
        "pt" -> "pt-BR"
        else -> code
    }

    fun fetch(packageName: String, language: String): GameDetails? {
        val hl = playLanguage(language)
        val html = httpGet(
            "https://play.google.com/store/apps/details?id=$packageName&hl=$hl&gl=US",
            acceptLanguage = hl
        ) ?: return null

        var title = ""
        var description = ""
        var rating = -1f
        var ratingCount = -1L
        var developer = ""
        var genre = ""
        var ageRating = ""
        val gallery = ArrayList<String>()

        val ld = LD_JSON.find(html)?.groupValues?.get(1)?.trim()
        if (!ld.isNullOrEmpty()) {
            try {
                val obj = if (ld.startsWith("[")) JSONArray(ld).getJSONObject(0) else JSONObject(ld)
                title = obj.optString("name")
                description = htmlToText(obj.optString("description"))
                val aggregate = obj.optJSONObject("aggregateRating")
                if (aggregate != null) {
                    rating = aggregate.optString("ratingValue").toFloatOrNull() ?: -1f
                    ratingCount = aggregate.optString("ratingCount").toDoubleOrNull()?.toLong() ?: -1L
                }
                val author = obj.opt("author")
                developer = when (author) {
                    is JSONObject -> author.optString("name")
                    is String -> author
                    else -> ""
                }
                genre = categoryLabel(obj.optString("applicationCategory"))
                ageRating = ageLabel(obj.optString("contentRating"))
                gallery.addAll(GameDetails.stringList(obj.opt("screenshot")))
            } catch (_: Exception) {
            }
        }

        // Play's promo video is always hosted on YouTube, and trailers are no longer sourced from
        // YouTube at all (see GameDetailsRepository doc comment) — so it is intentionally not read here.
        // Set a direct video link (Cloudinary or otherwise) in the Firebase `trailer` field instead.

        // Screenshots (only if the page data did not list them)
        if (gallery.isEmpty()) {
            val seen = LinkedHashSet<String>()
            for (match in IMAGE.findAll(html)) {
                val width = match.groupValues[2].toIntOrNull() ?: 0
                val height = match.groupValues[3].toIntOrNull() ?: 0
                if (width >= 300 && height >= 150) seen.add(match.groupValues[1])
                if (seen.size >= 8) break
            }
            seen.forEach { gallery.add("https://play-lh.googleusercontent.com/$it=w960") }
        }

        // The ld+json "description" above is Play's short SEO tagline, not the full "About this game"
        // text a person sees on the page. Pull the real long description if we can find it, and only
        // fall back to the short one if that fails.
        val longDescription = extractAboutSection(html)
        if (longDescription.length > description.length) description = longDescription

        val released = extractReleaseDate(html)
        val minAndroid = extractMinAndroid(html)
        val languages = extractLanguages(html)

        if (title.isBlank() && rating < 0f && description.isBlank()) return null
        return GameDetails(
            title = title,
            description = description,
            rating = rating,
            ratingCount = ratingCount,
            genres = if (genre.isNotBlank()) listOf(genre) else emptyList(),
            developer = developer,
            releaseDate = released,
            ageRating = ageRating,
            languages = languages,
            gallery = gallery,
            minAndroid = minAndroid,
            fromPlay = true
        )
    }

    private fun htmlToText(value: String): String =
        if (value.isBlank()) "" else Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    /**
     * The visible "About this game" text: everything between that heading and the next section of the
     * page, stripped of markup. This is what actually shows on the store page; Play's structured data
     * (used above for [description]) only carries the short tagline, not this.
     */
    private fun extractAboutSection(html: String): String {
        val headingIndex = ABOUT_HEADINGS.firstNotNullOfOrNull { heading ->
            val i = html.indexOf(heading)
            if (i >= 0) Pair(i, heading) else null
        } ?: return ""
        val (start, heading) = headingIndex
        val contentStart = start + heading.length

        var end = html.length
        for (marker in ABOUT_END_MARKERS) {
            val i = html.indexOf(marker, contentStart)
            if (i in contentStart until end) end = i
        }
        end = minOf(end, contentStart + 12000)

        val raw = html.substring(contentStart, end)
        val text = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        return if (text.length > 6000) text.take(6000).trim() else text
    }

    /** Looks for a real date near "Released on" (any HTML in between is ignored), then a JSON-LD fallback. */
    private fun extractReleaseDate(html: String): String {
        val nearLabel = extractNear(html, "Released on", DATE_LOOSE)
        if (!nearLabel.isNullOrBlank()) return nearLabel
        val iso = DATE_PUBLISHED_JSON.find(html)?.groupValues?.get(1) ?: return ""
        return try {
            val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)
            if (parsed != null) java.text.SimpleDateFormat("MMM d, yyyy", Locale.US).format(parsed) else iso
        } catch (_: Exception) {
            iso
        }
    }

    private fun extractMinAndroid(html: String): String {
        val near = extractNear(html, "Requires Android", ANDROID_VERSION)
        return near ?: ""
    }

    /**
     * Best-effort: Play's current page design does not clearly list every supported language for most
     * apps, so this is often not available and stays blank (set it in Firebase if you know it).
     */
    private fun extractLanguages(html: String): String {
        val code = IN_LANGUAGE_JSON.find(html)?.groupValues?.get(1)?.lowercase(Locale.ROOT)?.take(2) ?: return ""
        return LANGUAGE_NAMES[code] ?: ""
    }

    /**
     * Finds [label] in the raw HTML, strips the markup from the text right after it, and runs [pattern]
     * on that plain text. Tag-blind on purpose: Play often puts the value in a sibling element rather
     * than right next to the label in the raw source, so searching the stripped text is far more
     * reliable than trying to match through the HTML itself.
     */
    private fun extractNear(html: String, label: String, pattern: Regex): String? {
        val index = html.indexOf(label)
        if (index < 0) return null
        val window = html.substring(index, minOf(html.length, index + 700))
        val text = Html.fromHtml(window, Html.FROM_HTML_MODE_LEGACY).toString()
        return pattern.find(text)?.groupValues?.get(1)
    }

    /** "GAME_ACTION" -> "Action". */
    private fun categoryLabel(value: String): String {
        val raw = value.removePrefix("GAME_").replace('_', ' ').trim()
        if (raw.isEmpty() || raw.equals("GAME", ignoreCase = true)) return ""
        return raw.lowercase(Locale.ROOT).split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { c -> c.titlecase(Locale.ROOT) }
        }
    }

    /** "Rated for 12+" -> "12+". */
    private fun ageLabel(value: String): String {
        if (value.isBlank()) return ""
        return Regex("\\d+\\+").find(value)?.value ?: value.trim()
    }
}

// ================================================================================================
//  The phone itself (only for installed games)
// ================================================================================================

private object DeviceReader {

    fun read(context: Context, packageName: String): GameDetails? {
        if (packageName.isBlank()) return null
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            val paths = ArrayList<String>()
            paths.add(info.sourceDir)
            info.splitSourceDirs?.let { paths.addAll(it.toList()) }

            var totalBytes = 0L
            val libs = HashSet<String>()
            for (path in paths) {
                val file = File(path)
                totalBytes += file.length()
                try {
                    ZipFile(file).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val name = entries.nextElement().name
                            if (name.startsWith("lib/") && name.endsWith(".so")) libs.add(name)
                        }
                    }
                } catch (_: Exception) {
                }
            }
            val names = libs.map { it.substringAfterLast('/').lowercase(Locale.ROOT) }.toSet()

            val engine = when {
                "libunity.so" in names || "libil2cpp.so" in names -> "Unity"
                names.any { it.startsWith("libue4") || it.startsWith("libue5") || it.startsWith("libunreal") } -> "Unreal Engine"
                names.any { it.startsWith("libgodot") } -> "Godot"
                names.any { it.startsWith("libcocos") } -> "Cocos2d-x"
                "libgdx.so" in names -> "libGDX"
                names.any { it.startsWith("libyoyo") } -> "GameMaker"
                names.any { it.startsWith("libdmengine") } -> "Defold"
                names.isNotEmpty() -> "Custom native engine"
                else -> "Java / Kotlin"
            }
            val architecture = when {
                libs.any { it.startsWith("lib/arm64-v8a/") } -> "64-bit (ARM64)"
                libs.any { it.startsWith("lib/armeabi-v7a/") } -> "32-bit (ARMv7)"
                else -> ""
            }

            GameDetails(
                engine = engine,
                minAndroid = androidName(info.minSdkVersion),
                appSize = formatSize(totalBytes),
                architecture = architecture
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun androidName(api: Int): String = when {
        api <= 0 -> ""
        api <= 20 -> "4.4"
        else -> when (api) {
            21 -> "5.0"
            22 -> "5.1"
            23 -> "6.0"
            24 -> "7.0"
            25 -> "7.1"
            26 -> "8.0"
            27 -> "8.1"
            28 -> "9"
            29 -> "10"
            30 -> "11"
            31 -> "12"
            32 -> "12L"
            33 -> "13"
            34 -> "14"
            35 -> "15"
            36 -> "16"
            else -> "API $api"
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) String.format(Locale.US, "%.1f GB", mb / 1024.0) else String.format(Locale.US, "%.0f MB", mb)
    }
}
