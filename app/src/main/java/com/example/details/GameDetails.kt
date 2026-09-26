package com.example.details

import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the "View details" screen shows for one game.
 * A blank string / empty list / -1 means "not known", and the screen shows "--" instead of inventing a value.
 */
data class GameDetails(
    val title: String = "",
    val summary: String = "",
    val description: String = "",
    /** Google Play star rating (0-5), or -1 when it could not be read from Google Play. */
    val rating: Float = -1f,
    val ratingCount: Long = -1L,
    val genres: List<String> = emptyList(),
    val developer: String = "",
    val releaseDate: String = "",
    val ageRating: String = "",
    val languages: String = "",
    /** Direct video link (mp4 / m3u8 / webm — for example a Cloudinary-hosted trailer). No longer fetched from or played through YouTube. */
    val trailerUrl: String = "",
    val gallery: List<String> = emptyList(),
    val engine: String = "",
    /** Minimum Android version, for example "8.0". */
    val minAndroid: String = "",
    val appSize: String = "",
    val architecture: String = "",
    val fromPlay: Boolean = false,
    val fromFirebase: Boolean = false
) {

    /** Values of this object win; whatever is missing is taken from [other]. */
    fun withFallback(other: GameDetails): GameDetails = GameDetails(
        title = first(title, other.title),
        summary = first(summary, other.summary),
        description = first(description, other.description),
        rating = if (rating >= 0f) rating else other.rating,
        ratingCount = if (ratingCount >= 0L) ratingCount else other.ratingCount,
        genres = if (genres.isNotEmpty()) genres else other.genres,
        developer = first(developer, other.developer),
        releaseDate = first(releaseDate, other.releaseDate),
        ageRating = first(ageRating, other.ageRating),
        languages = first(languages, other.languages),
        trailerUrl = first(trailerUrl, other.trailerUrl),
        gallery = if (gallery.isNotEmpty()) gallery else other.gallery,
        engine = first(engine, other.engine),
        minAndroid = first(minAndroid, other.minAndroid),
        appSize = first(appSize, other.appSize),
        architecture = first(architecture, other.architecture),
        fromPlay = fromPlay || other.fromPlay,
        fromFirebase = fromFirebase || other.fromFirebase
    )

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("title", title)
        o.put("summary", summary)
        o.put("description", description)
        o.put("rating", rating.toDouble())
        o.put("ratingCount", ratingCount)
        o.put("genres", JSONArray(genres))
        o.put("developer", developer)
        o.put("releaseDate", releaseDate)
        o.put("ageRating", ageRating)
        o.put("languages", languages)
        o.put("trailerUrl", trailerUrl)
        o.put("gallery", JSONArray(gallery))
        o.put("engine", engine)
        o.put("minAndroid", minAndroid)
        o.put("appSize", appSize)
        o.put("architecture", architecture)
        o.put("fromPlay", fromPlay)
        o.put("fromFirebase", fromFirebase)
        return o
    }

    companion object {
        private fun first(a: String, b: String): String = if (a.isNotBlank()) a else b

        fun fromJson(o: JSONObject): GameDetails = GameDetails(
            title = o.optString("title"),
            summary = o.optString("summary"),
            description = o.optString("description"),
            rating = o.optDouble("rating", -1.0).toFloat(),
            ratingCount = o.optLong("ratingCount", -1L),
            genres = stringList(o.opt("genres")),
            developer = o.optString("developer"),
            releaseDate = o.optString("releaseDate"),
            ageRating = o.optString("ageRating"),
            languages = o.optString("languages"),
            trailerUrl = o.optString("trailerUrl"),
            gallery = stringList(o.opt("gallery")),
            engine = o.optString("engine"),
            minAndroid = o.optString("minAndroid"),
            appSize = o.optString("appSize"),
            architecture = o.optString("architecture"),
            fromPlay = o.optBoolean("fromPlay", false),
            fromFirebase = o.optBoolean("fromFirebase", false)
        )

        /**
         * A list of strings from JSON: an array, an object whose values are the strings
         * (Firebase turns arrays into {"0": ..., "1": ...}), or one single string.
         */
        fun stringList(value: Any?): List<String> {
            val result = ArrayList<String>()
            when (value) {
                is JSONArray -> for (i in 0 until value.length()) {
                    val item = value.optString(i)
                    if (item.isNotBlank()) result.add(item)
                }
                is JSONObject -> {
                    val keys = value.keys().asSequence().toList()
                        .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
                    for (key in keys) {
                        val item = value.optString(key)
                        if (item.isNotBlank()) result.add(item)
                    }
                }
                is String -> if (value.isNotBlank()) result.add(value)
            }
            return result
        }
    }
}
