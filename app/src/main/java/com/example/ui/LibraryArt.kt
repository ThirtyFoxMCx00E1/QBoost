package com.example.ui

import androidx.annotation.DrawableRes
import com.example.R
import com.example.model.GameItem

/**
 * Cover art for the Qboost game library (res/drawable-nodpi/lib_*).
 * A game gets its art by name or package, so games you add yourself are matched too.
 */
object LibraryArt {

    @DrawableRes
    fun forGame(game: GameItem): Int? {
        val name = game.name.trim().lowercase()
        val key = "$name ${game.packageName.lowercase()}"
        return when {
            "minecraft" in key -> R.drawable.lib_minecraft
            "wuthering" in key -> R.drawable.lib_wuthering_waves
            "genshin" in key -> R.drawable.lib_genshin_impact
            "pubg" in key || "battlegrounds" in key || "tencent.ig" in key -> R.drawable.lib_pubg_mobile
            "clash of clans" in key || "clashofclans" in key -> R.drawable.lib_clash_of_clans
            "call of duty" in key || "callofduty" in key -> R.drawable.lib_cod_mobile
            "devil may cry" in key -> R.drawable.lib_devil_may_cry
            "dolphin" in key -> R.drawable.lib_dolphin
            "worms" in key -> R.drawable.lib_worms4
            "little nightmares" in key -> R.drawable.lib_little_nightmares
            "blood strike" in key || "newspike" in key -> R.drawable.lib_blood_strike
            "asphalt" in key -> R.drawable.lib_asphalt8
            "dysmantle" in key -> R.drawable.lib_dysmantle
            "getting over it" in key || "gettingoverit" in key -> R.drawable.lib_getting_over_it
            "human: fall flat" in key || "humanfallflat" in key -> R.drawable.lib_human_fall_flat
            "oceanhorn" in key -> R.drawable.lib_oceanhorn
            "san andreas" in key || "gtasa" in key || name.startsWith("gta") -> R.drawable.lib_gta_san_andreas
            "roblox" in key -> R.drawable.lib_roblox
            "tgc.sky" in key || name == "sky" || "children of the light" in key -> R.drawable.lib_sky
            else -> null
        }
    }
}
