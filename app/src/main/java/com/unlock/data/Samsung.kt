package com.unlock.data

import android.content.Context

/** Samsung One UI specifics. */
object Samsung {
    const val GOS = "com.samsung.android.game.gos"        // Game Optimizing Service (throttles ~10k apps)
    const val GOS_OLD = "com.enhance.gameservice"

    /** Returns the installed+enabled GOS package if present (so we can offer to disable it). */
    fun activeGosPackage(context: Context): String? {
        for (p in listOf(GOS, GOS_OLD)) {
            val enabled = runCatching {
                context.packageManager.getApplicationInfo(p, 0).enabled
            }.getOrDefault(false)
            if (enabled) return p
        }
        return null
    }
}
