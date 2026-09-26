package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.display.SaturationStore
import com.example.model.GameItem
import com.example.model.InstalledAppItem
import com.example.model.OverlayConfig
import com.example.model.PerformanceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Set once the v6.0 library games (with cover art) have been added to the saved list. */
private const val KEY_LIBRARY_SEEDED = "library_v6_seeded"

/** Set once the v8.6 package fix (DYSMANTLE, Little Nightmares) was applied. */
private const val KEY_LIBRARY_V86 = "library_v86_applied"

/** Set once the v8.2 library changes (PC games replaced by mobile games) were applied. */
private const val KEY_LIBRARY_V82 = "library_v82_applied"

/** Set once the v7.0 library changes (Little Nightmares 2 instead of FF7EC, GTA SA, Sky, Roblox) were applied. */
private const val KEY_LIBRARY_V7 = "library_v7_applied"

class GameRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("qboost_settings", Context.MODE_PRIVATE)

    /** Games that have cover art in the Qboost library (res/drawable-nodpi/lib_*). */
    private val libraryGames = listOf(
        GameItem(
            id = "minecraft",
            name = "Minecraft",
            packageName = "com.mojang.minecraftpe",
            initials = "MC",
            iconColor = 0xFF43A047,
            performanceMode = PerformanceMode.PERFORMANCE,
            targetFps = 120,
            saturation = 1.2f
        ),
        GameItem(
            id = "genshin",
            name = "Genshin Impact",
            packageName = "com.miHoYo.GenshinImpact",
            initials = "GI",
            iconColor = 0xFF1E88E5,
            performanceMode = PerformanceMode.PERFORMANCE,
            targetFps = 60,
            saturation = 1.25f
        ),
        GameItem(
            id = "pubgm",
            name = "PUBG Mobile",
            packageName = "com.tencent.ig",
            initials = "PUBG",
            iconColor = 0xFFF9A825,
            performanceMode = PerformanceMode.PERFORMANCE,
            targetFps = 90,
            saturation = 1.3f
        ),
        GameItem(
            id = "coc",
            name = "Clash of Clans",
            packageName = "com.supercell.clashofclans",
            initials = "CoC",
            iconColor = 0xFFEF6C00,
            performanceMode = PerformanceMode.BALANCED,
            targetFps = 60,
            saturation = 1.2f
        )
    )

    /** Replaces FF7EC, which no longer exists. Auto-links to the installed app by name. */
    private val littleNightmares = GameItem(
        id = "little_nightmares",
        name = "Little Nightmares",
        packageName = "com.playdigious.littlenightmare",
        initials = "LN",
        iconColor = 0xFF37474F,
        performanceMode = PerformanceMode.PERFORMANCE,
        targetFps = 60,
        saturation = 1.15f
    )

    /** v8.2: mobile games that replace the old PC-only tiles. */
    private val libraryGamesV82 = listOf(
        GameItem(
            id = "blood_strike",
            name = "Blood Strike",
            packageName = "com.netease.newspike",
            initials = "BS",
            iconColor = 0xFF00ACC1,
            performanceMode = PerformanceMode.PERFORMANCE,
            targetFps = 90,
            saturation = 1.25f
        ),
        GameItem(
            id = "asphalt8",
            name = "Asphalt 8: Airborne",
            packageName = "com.gameloft.android.ANMP.GloftA8HM",
            initials = "A8",
            iconColor = 0xFFF57C00,
            performanceMode = PerformanceMode.PERFORMANCE,
            targetFps = 60,
            saturation = 1.25f
        ),
        GameItem(
            id = "getting_over_it",
            name = "Getting Over It",
            packageName = "com.noodlecake.gettingoverit",
            initials = "GOI",
            iconColor = 0xFF8D6E63,
            performanceMode = PerformanceMode.BALANCED,
            targetFps = 60,
            saturation = 1.1f
        ),
        GameItem(
            id = "human_fall_flat",
            name = "Human: Fall Flat",
            packageName = "com.NoBrakesGames.HumanFallFlat",
            initials = "HFF",
            iconColor = 0xFF29B6F6,
            performanceMode = PerformanceMode.BALANCED,
            targetFps = 60,
            saturation = 1.2f
        ),
        GameItem(
            id = "oceanhorn",
            name = "Oceanhorn",
            packageName = "com.FDGEntertainment.oceanhorn.gp",
            initials = "OH",
            iconColor = 0xFF26A69A,
            performanceMode = PerformanceMode.BALANCED,
            targetFps = 60,
            saturation = 1.2f
        ),
        GameItem(
            id = "dysmantle",
            name = "DYSMANTLE",
            packageName = "com.the10tons.dysmantle",
            initials = "DYS",
            iconColor = 0xFF7E57C2,
            performanceMode = PerformanceMode.PERFORMANCE,
            targetFps = 60,
            saturation = 1.2f
        )
    )

    /** Added in v7.0 (these have cover art too). */
    private val libraryGamesV7 = listOf(
        GameItem(
            id = "gta_sa",
            name = "GTA: San Andreas",
            packageName = "com.rockstargames.gtasa",
            initials = "GTA",
            iconColor = 0xFFF9A825,
            performanceMode = PerformanceMode.PERFORMANCE,
            targetFps = 60,
            saturation = 1.2f
        ),
        GameItem(
            id = "sky",
            name = "Sky",
            packageName = "com.tgc.sky.android",
            initials = "SKY",
            iconColor = 0xFF26A69A,
            performanceMode = PerformanceMode.BALANCED,
            targetFps = 60,
            saturation = 1.15f
        ),
        GameItem(
            id = "roblox",
            name = "Roblox",
            packageName = "com.roblox.client",
            initials = "RBX",
            iconColor = 0xFF546E7A,
            performanceMode = PerformanceMode.BALANCED,
            targetFps = 60,
            saturation = 1.1f
        )
    )

    private val initialGames = listOf(
        GameItem(
            id = "codm",
            name = "Call of Duty",
            packageName = "com.activision.callofduty.shooter",
            initials = "COD",
            iconColor = 0xFFD32F2F,
            performanceMode = PerformanceMode.PERFORMANCE,
            targetFps = 120,
            saturation = 1.3f
        ),
        GameItem(
            id = "dmc",
            name = "Devil May Cry",
            packageName = "com.nebulajoy.act.dmcp.sea",
            initials = "DMC",
            iconColor = 0xFFC2185B,
            performanceMode = PerformanceMode.PERFORMANCE,
            targetFps = 120,
            saturation = 1.2f
        ),
        GameItem(
            id = "dolphin",
            name = "Dolphin",
            packageName = "org.dolphinemu.dolphinemu",
            initials = "DOL",
            iconColor = 0xFF0288D1,
            performanceMode = PerformanceMode.BALANCED,
            targetFps = 60,
            saturation = 1.15f
        ),
        GameItem(
            id = "worms4",
            name = "Worms 4",
            packageName = "com.worms4.app",
            initials = "W4",
            iconColor = 0xFF689F38,
            performanceMode = PerformanceMode.BALANCED,
            targetFps = 60,
            saturation = 1.25f
        ),
        littleNightmares,
        GameItem(
            id = "wuthering",
            name = "Wuthering Waves",
            packageName = "com.kurogame.wutheringwaves.global",
            initials = "WW",
            iconColor = 0xFF7B1FA2,
            performanceMode = PerformanceMode.PERFORMANCE,
            targetFps = 120,
            saturation = 1.4f
        )
    ) + libraryGames + libraryGamesV7 + libraryGamesV82

    private val _games = MutableStateFlow<List<GameItem>>(loadGamesFromPrefs())
    val games: StateFlow<List<GameItem>> = _games.asStateFlow()

    private val _selectedGame = MutableStateFlow<GameItem>(
        _games.value.firstOrNull { it.isInstalled } ?: _games.value.firstOrNull() ?: initialGames.first()
    )
    val selectedGame: StateFlow<GameItem> = _selectedGame.asStateFlow()

    private val _overlayConfig = MutableStateFlow(
        OverlayConfig(
            isFpsVisible = prefs.getBoolean("overlay_fps", true),
            isCpuTempVisible = prefs.getBoolean("overlay_cpu", true),
            isRamVisible = prefs.getBoolean("overlay_ram", true),
            isGpuVisible = prefs.getBoolean("overlay_gpu", true),
            isPingVisible = prefs.getBoolean("overlay_ping", true),
            posX = prefs.getFloat("overlay_x", 16f),
            posY = prefs.getFloat("overlay_y", 48f),
            scale = prefs.getFloat("overlay_scale", 1.0f),
            alpha = prefs.getFloat("overlay_alpha", 0.92f)
        )
    )
    val overlayConfig: StateFlow<OverlayConfig> = _overlayConfig.asStateFlow()

    fun isPackageInstalled(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun saveGamesToPrefs(list: List<GameItem>) {
        try {
            val array = JSONArray()
            for (g in list) {
                val obj = JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("packageName", g.packageName)
                    put("initials", g.initials)
                    put("iconColor", g.iconColor)
                    put("performanceMode", g.performanceMode.name)
                    put("saturation", g.saturation.toDouble())
                    put("touchSensitivity", g.touchSensitivity)
                    put("blockNotifications", g.blockNotifications)
                    put("blockCalls", g.blockCalls)
                    put("targetFps", g.targetFps)
                    put("touchBooster", g.touchBooster)
                    put("gameModeActive", g.gameModeActive)
                    put("isFavorite", g.isFavorite)
                }
                array.put(obj)
            }
            prefs.edit().putString("saved_games_json", array.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun loadGamesFromPrefs(): List<GameItem> {
        val jsonStr = prefs.getString("saved_games_json", null)
        if (jsonStr.isNullOrEmpty()) {
            prefs.edit()
                .putBoolean(KEY_LIBRARY_SEEDED, true)
                .putBoolean(KEY_LIBRARY_V7, true)
                .putBoolean(KEY_LIBRARY_V82, true)
                .putBoolean(KEY_LIBRARY_V86, true)
                .apply()
            return initialGames.map { withStoredSaturation(it.copy(isInstalled = isPackageInstalled(it.packageName))) }
        }
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<GameItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pkg = obj.optString("packageName", "")
                val modeStr = obj.optString("performanceMode", PerformanceMode.PERFORMANCE.name)
                val mode = try { PerformanceMode.valueOf(modeStr) } catch (_: Exception) { PerformanceMode.PERFORMANCE }
                list.add(
                    withStoredSaturation(
                    GameItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        packageName = pkg,
                        initials = obj.optString("initials", obj.getString("name").take(2).uppercase()),
                        iconColor = obj.optLong("iconColor", 0xFF2F80FF),
                        isInstalled = isPackageInstalled(pkg),
                        performanceMode = mode,
                        saturation = obj.optDouble("saturation", 1.25).toFloat(),
                        touchSensitivity = obj.optInt("touchSensitivity", 8),
                        blockNotifications = obj.optBoolean("blockNotifications", true),
                        blockCalls = obj.optBoolean("blockCalls", true),
                        targetFps = obj.optInt("targetFps", 120),
                        touchBooster = obj.optBoolean("touchBooster", true),
                        gameModeActive = obj.optBoolean("gameModeActive", true),
                        isFavorite = obj.optBoolean("isFavorite", false)
                    )
                    )
                )
            }
            applyLibraryV86(
                applyLibraryV82(
                    applyLibraryV7(
                        seedLibraryGames(
                            list.ifEmpty { initialGames.map { withStoredSaturation(it.copy(isInstalled = isPackageInstalled(it.packageName))) } }
                        )
                    )
                )
            )
        } catch (_: Exception) {
            initialGames.map { withStoredSaturation(it.copy(isInstalled = isPackageInstalled(it.packageName))) }
        }
    }

    /**
     * v6.0: makes sure every game that has Qboost library art also has a tile.
     * Runs once, so games you remove later do not come back.
     */
    private fun seedLibraryGames(current: List<GameItem>): List<GameItem> {
        if (prefs.getBoolean(KEY_LIBRARY_SEEDED, false)) return current
        val merged = current.toMutableList()
        for (seed in libraryGames) {
            val exists = merged.any { g ->
                g.id == seed.id ||
                    (seed.packageName.isNotBlank() && g.packageName == seed.packageName) ||
                    g.name.equals(seed.name, ignoreCase = true)
            }
            if (!exists) {
                merged.add(withStoredSaturation(seed.copy(isInstalled = isPackageInstalled(seed.packageName))))
            }
        }
        prefs.edit().putBoolean(KEY_LIBRARY_SEEDED, true).apply()
        if (merged.size != current.size) saveGamesToPrefs(merged)
        return merged
    }

    /**
     * v7.0 (runs once): FF7EC no longer exists, so it is swapped for Little Nightmares 2 in the same
     * spot, and GTA San Andreas, Sky and Roblox get their tiles.
     */
    private fun applyLibraryV7(current: List<GameItem>): List<GameItem> {
        if (prefs.getBoolean(KEY_LIBRARY_V7, false)) return current
        val merged = current.toMutableList()

        val hasLittleNightmares = merged.any {
            it.id == littleNightmares.id || it.name.equals(littleNightmares.name, ignoreCase = true)
        }
        val ffIndex = merged.indexOfFirst {
            it.id == "ff7ec" ||
                it.name.equals("FF7EC", ignoreCase = true) ||
                it.packageName.contains("ff7ec", ignoreCase = true)
        }
        if (ffIndex >= 0) {
            if (hasLittleNightmares) {
                merged.removeAt(ffIndex)
            } else {
                merged[ffIndex] = withStoredSaturation(littleNightmares)
            }
        } else if (!hasLittleNightmares) {
            merged.add(withStoredSaturation(littleNightmares))
        }

        for (seed in libraryGamesV7) {
            val exists = merged.any { g ->
                g.id == seed.id ||
                    (seed.packageName.isNotBlank() && g.packageName == seed.packageName) ||
                    g.name.equals(seed.name, ignoreCase = true)
            }
            if (!exists) {
                merged.add(withStoredSaturation(seed.copy(isInstalled = isPackageInstalled(seed.packageName))))
            }
        }

        prefs.edit().putBoolean(KEY_LIBRARY_V7, true).apply()
        if (merged != current) saveGamesToPrefs(merged)
        return merged
    }

    /**
     * v8.2 (runs once): PC-only games are not real apps on a phone, so their tiles are replaced by
     * mobile games (same spot in the library). A PC tile is only replaced while it is not linked to an app.
     */
    private fun applyLibraryV82(current: List<GameItem>): List<GameItem> {
        if (prefs.getBoolean(KEY_LIBRARY_V82, false)) return current
        val merged = current.toMutableList()

        // (old PC tile id, old PC tile name, mobile game that takes its place)
        val replacements = listOf(
            Triple("valorant", "Valorant", libraryGamesV82[0]),
            Triple("dota2", "Dota 2", libraryGamesV82[1]),
            Triple("undertale", "Undertale", libraryGamesV82[2]),
            Triple("ratchet_clank", "Ratchet & Clank", libraryGamesV82[3]),
            Triple("wow", "World of Warcraft", libraryGamesV82[4]),
            Triple("marvel_heroes_omega", "Marvel Heroes Omega", libraryGamesV82[5]),
            Triple("little_nightmares_2", "Little Nightmares 2", littleNightmares)
        )
        for ((pcId, pcName, mobile) in replacements) {
            val pcIndex = merged.indexOfFirst {
                it.packageName.isBlank() && (it.id == pcId || it.name.equals(pcName, ignoreCase = true))
            }
            val exists = merged.any { g ->
                g.id == mobile.id ||
                    (mobile.packageName.isNotBlank() && g.packageName == mobile.packageName) ||
                    g.name.equals(mobile.name, ignoreCase = true)
            }
            val tile = withStoredSaturation(mobile.copy(isInstalled = isPackageInstalled(mobile.packageName)))
            if (pcIndex >= 0) {
                if (exists) {
                    merged.removeAt(pcIndex)
                } else {
                    merged[pcIndex] = tile
                }
            } else if (!exists) {
                merged.add(tile)
            }
        }

        prefs.edit().putBoolean(KEY_LIBRARY_V82, true).apply()
        if (merged != current) saveGamesToPrefs(merged)
        return merged
    }

    /**
     * v8.6 (runs once): DYSMANTLE and Little Nightmares now have their Google Play package, so tiles that
     * were still "not linked" get it (and become "installed" if the game is on the phone).
     */
    private fun applyLibraryV86(current: List<GameItem>): List<GameItem> {
        if (prefs.getBoolean(KEY_LIBRARY_V86, false)) return current
        val packages = mapOf(
            "dysmantle" to "com.the10tons.dysmantle",
            "little_nightmares" to "com.playdigious.littlenightmare"
        )
        val updated = current.map { g ->
            val pkg = packages[g.id]
            if (pkg != null && g.packageName.isBlank()) {
                g.copy(packageName = pkg, isInstalled = isPackageInstalled(pkg))
            } else {
                g
            }
        }
        prefs.edit().putBoolean(KEY_LIBRARY_V86, true).apply()
        if (updated != current) saveGamesToPrefs(updated)
        return updated
    }

    private fun normalizeName(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    /** Package of an installed app whose name matches [gameName] (exact, or one name starts with the other). */
    private fun findInstalledByName(gameName: String, apps: List<Pair<String, String>>): String? {
        val wanted = normalizeName(gameName)
        if (wanted.length < 4) return null
        for ((label, pkg) in apps) {
            val candidate = normalizeName(label)
            if (candidate.isEmpty()) continue
            if (candidate == wanted) return pkg
            val shorter = if (candidate.length < wanted.length) candidate else wanted
            val longer = if (candidate.length < wanted.length) wanted else candidate
            if (shorter.length >= 8 && longer.startsWith(shorter)) return pkg
        }
        return null
    }

    /**
     * Keeps every tile's "installed" state up to date and links tiles that have no app yet
     * (for example DYSMANTLE, Little Nightmares) to the installed app with the same name.
     */
    fun refreshInstallStates() {
        try {
            val pm = context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val apps = pm.queryIntentActivities(launcher, 0)
                .map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.second != context.packageName }

            var changed = false
            val updated = _games.value.map { game ->
                var g = game
                if (g.packageName.isBlank() || !isPackageInstalled(g.packageName)) {
                    val linked = findInstalledByName(g.name, apps)
                    if (linked != null && linked != g.packageName) g = g.copy(packageName = linked)
                }
                val installed = isPackageInstalled(g.packageName)
                if (installed != g.isInstalled) g = g.copy(isInstalled = installed)
                if (g != game) changed = true
                g
            }
            if (changed) {
                _games.value = updated
                val selected = _selectedGame.value
                _selectedGame.value = updated.firstOrNull { it.id == selected.id } ?: selected
                saveGamesToPrefs(updated)
            }
        } catch (_: Exception) {
        }
    }

    /** Key used to remember a game's saturation (package name, or id for custom entries). */
    fun saturationKey(game: GameItem): String = game.packageName.ifBlank { game.id }

    /** The in-game HUD can change a game's saturation while Qboost is in the background; pick that up. */
    private fun withStoredSaturation(game: GameItem): GameItem {
        val stored = SaturationStore.get(context, saturationKey(game)) ?: return game
        return if (stored == game.saturation) game else game.copy(saturation = stored)
    }

    fun syncSaturationFromStore() {
        _games.value = _games.value.map { withStoredSaturation(it) }
        _selectedGame.value = withStoredSaturation(_selectedGame.value)
    }

    fun selectGame(game: GameItem) {
        _selectedGame.value = game
    }

    fun addGame(name: String, packageName: String = "") {
        if (packageName.isNotBlank()) {
            val existing = _games.value.firstOrNull { it.packageName == packageName }
            if (existing != null) {
                _selectedGame.value = existing
                return
            }
        }
        val installed = isPackageInstalled(packageName)
        val newGame = GameItem(
            id = UUID.randomUUID().toString(),
            name = name,
            packageName = packageName,
            initials = name.take(2).uppercase(),
            iconColor = 0xFF2F80FF,
            isInstalled = installed,
            performanceMode = PerformanceMode.PERFORMANCE
        )
        val updated = _games.value + newGame
        _games.value = updated
        _selectedGame.value = newGame
        saveGamesToPrefs(updated)
    }

    fun removeGame(id: String) {
        val updated = _games.value.filter { it.id != id }
        _games.value = updated
        if (_selectedGame.value.id == id && updated.isNotEmpty()) {
            _selectedGame.value = updated.first()
        }
        saveGamesToPrefs(updated)
    }

    fun updateGame(updated: GameItem) {
        SaturationStore.set(context, saturationKey(updated), updated.saturation)
        val list = _games.value.map { if (it.id == updated.id) updated else it }
        _games.value = list
        if (_selectedGame.value.id == updated.id) {
            _selectedGame.value = updated
        }
        saveGamesToPrefs(list)
    }

    fun updateOverlayConfig(newConfig: OverlayConfig) {
        _overlayConfig.value = newConfig
        prefs.edit()
            .putBoolean("overlay_fps", newConfig.isFpsVisible)
            .putBoolean("overlay_cpu", newConfig.isCpuTempVisible)
            .putBoolean("overlay_ram", newConfig.isRamVisible)
            .putBoolean("overlay_gpu", newConfig.isGpuVisible)
            .putBoolean("overlay_ping", newConfig.isPingVisible)
            .putFloat("overlay_x", newConfig.posX)
            .putFloat("overlay_y", newConfig.posY)
            .putFloat("overlay_scale", newConfig.scale)
            .putFloat("overlay_alpha", newConfig.alpha)
            .apply()
    }

    fun getInstalledApps(): List<InstalledAppItem> {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val currentGames = _games.value

            resolveInfos.mapNotNull { resolveInfo ->
                val appName = resolveInfo.loadLabel(pm).toString()
                val pkgName = resolveInfo.activityInfo.packageName
                if (pkgName == context.packageName) return@mapNotNull null

                val appInfo = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(pkgName, PackageManager.ApplicationInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(pkgName, 0)
                    }
                } catch (_: Exception) {
                    null
                }

                val isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appInfo?.category == ApplicationInfo.CATEGORY_GAME
                } else {
                    @Suppress("DEPRECATION")
                    val legacyGameFlag = ApplicationInfo.FLAG_IS_GAME
                    @Suppress("DEPRECATION")
                    ((appInfo?.flags ?: 0) and legacyGameFlag) != 0
                } || appName.contains("game", ignoreCase = true) ||
                   appName.contains("play", ignoreCase = true) ||
                   pkgName.contains("game", ignoreCase = true) ||
                   pkgName.contains("unity", ignoreCase = true) ||
                   pkgName.contains("unreal", ignoreCase = true)

                val isAlreadyAdded = currentGames.any { it.packageName == pkgName }

                InstalledAppItem(
                    name = appName,
                    packageName = pkgName,
                    isGame = isGame,
                    isAlreadyAdded = isAlreadyAdded
                )
            }.distinctBy { it.packageName }
             .sortedWith(compareByDescending<InstalledAppItem> { it.isGame }.thenBy { it.name })
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isMusicMuted(): Boolean = prefs.getBoolean("music_muted", false)
    fun setMusicMuted(muted: Boolean) = prefs.edit().putBoolean("music_muted", muted).apply()

    fun isVibrationEnabled(): Boolean = prefs.getBoolean("vibrate_enabled", true)
    fun setVibrationEnabled(enabled: Boolean) = prefs.edit().putBoolean("vibrate_enabled", enabled).apply()

    fun isTurboFanActive(): Boolean = prefs.getBoolean("turbo_fan", true)
    fun setTurboFanActive(active: Boolean) = prefs.edit().putBoolean("turbo_fan", active).apply()
}
