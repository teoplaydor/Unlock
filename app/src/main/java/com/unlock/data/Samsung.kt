package com.unlock.data

import android.content.Context
import android.content.pm.PackageManager

/** Samsung One UI specifics. */
object Samsung {
    const val GOS = "com.samsung.android.game.gos"        // Game Optimizing Service (throttles ~10k apps)
    const val GOS_OLD = "com.enhance.gameservice"

    private val GAME_PACKAGES = listOf(
        GOS,
        GOS_OLD,
        "com.samsung.android.game.gametools",
        "com.samsung.android.game.gamehome",
    )

    /** Installed Game-Booster/GOS packages — disabling all of them neuters the software throttle. */
    fun allGamePackages(context: Context): List<String> =
        GAME_PACKAGES.filter { p ->
            runCatching {
                context.packageManager.getApplicationInfo(p, PackageManager.MATCH_DISABLED_COMPONENTS); true
            }.getOrDefault(false)
        }

    /**
     * Returns the GOS package if it's installed and currently active (so we can offer to disable it).
     * "Active" = component enabled state DEFAULT or ENABLED; once disabled via `pm disable-user`
     * this returns null and we stop offering the action.
     */
    fun activeGosPackage(context: Context): String? {
        val pm = context.packageManager
        for (p in listOf(GOS, GOS_OLD)) {
            @Suppress("DEPRECATION")
            val present = runCatching {
                pm.getApplicationInfo(p, PackageManager.MATCH_DISABLED_COMPONENTS); true
            }.getOrDefault(false)
            if (!present) continue
            val setting = runCatching { pm.getApplicationEnabledSetting(p) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            val active = setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
                setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            if (active) return p
        }
        return null
    }
}
