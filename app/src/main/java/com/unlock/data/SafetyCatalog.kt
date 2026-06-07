package com.unlock.data

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Two independent safety layers, distilled from the Universal Android Debloater lists and
 * the field reports gathered during research:
 *
 *  - a HARD, non-bypassable NEVER-REMOVE block-list (removing these bootloops the phone or
 *    destroys a vault / payments), and
 *  - a curated safe-to-debloat list (mostly Samsung/OEM extras).
 *
 * The active launcher, active IME and Shizuku's own shell host are protected dynamically.
 */
object SafetyCatalog {

    /** Exact package ids that must never be uninstalled or disabled. */
    private val neverRemoveExact = setOf(
        // AOSP core
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.shell",                // Shizuku's host — removing it kills our own access
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.permissioncontroller",
        "com.android.networkstack",
        "com.android.networkstack.tethering",
        "com.android.cellbroadcastreceiver",
        "com.android.cellbroadcastservice",
        "com.android.bluetooth",
        "com.android.wifi.resources",
        "com.android.incallui",
        // Google framework (GMS handled as CAUTION below)
        "com.google.android.gsf",
        "com.google.android.sdksandbox",
        // Samsung One UI vital
        "com.samsung.android.incallui",
        "com.samsung.android.provider.stickerprovider", // crashes Samsung Camera -> factory reset
        "com.samsung.knox.securefolder",
        "com.samsung.android.samsungpass",
        "com.samsung.android.spay",
        "com.samsung.android.honeyboard",               // usually the active Samsung IME
    )

    /** Prefix blocks (whole families). */
    private val neverRemovePrefixes = listOf(
        "com.android.providers.",
        "com.android.internal.",
        "com.android.cellbroadcast",
        "com.android.ims",
        "com.google.android.ims",
        "com.samsung.android.app.telephonyui",
    )

    /** Allowed but warned. GMS only matters when Play is present, so warn rather than block. */
    private val cautionExact = setOf(
        "com.google.android.gms",
        "com.android.vending",
    )

    /** Curated safe-to-disable / debloat (reversible-first). stickerprovider intentionally excluded. */
    private val debloatSafe = setOf(
        "com.samsung.android.bixby.agent",
        "com.samsung.android.bixby.wakeup",
        "com.samsung.android.bixbyvision.framework",
        "com.samsung.android.app.spage",
        "com.samsung.android.game.gos",
        "com.enhance.gameservice",
        "com.samsung.android.game.gametools",
        "com.samsung.android.game.gamehome",
        "com.sec.android.app.samsungapps",
        "com.samsung.android.scloud",
        "com.samsung.android.voc",
        "com.samsung.android.aremoji",
        "com.samsung.android.aremojieditor",
        "com.samsung.android.app.tvplus",
        "com.samsung.android.oneconnect",
        "com.samsung.android.themestore",
        "com.samsung.android.themecenter",
        "com.samsung.android.forest",
        "com.samsung.android.app.routines",
        "com.samsung.android.kidsinstaller",
        "com.facebook.katana",
        "com.facebook.system",
        "com.facebook.appmanager",
        "com.facebook.services",
        "com.microsoft.skydrive",
        "com.microsoft.office.officehubrow",
        "com.netflix.partner.activation",
    )

    fun classify(pkg: String, dynamicProtected: Set<String>): SafetyTier = when {
        pkg in dynamicProtected -> SafetyTier.PROTECTED
        pkg in neverRemoveExact -> SafetyTier.PROTECTED
        neverRemovePrefixes.any { pkg.startsWith(it) } -> SafetyTier.PROTECTED
        pkg in cautionExact -> SafetyTier.CAUTION
        pkg in debloatSafe -> SafetyTier.DEBLOAT_SAFE
        else -> SafetyTier.NORMAL
    }

    /** Packages we must protect because they are a launcher / the active IME / Shizuku's host. */
    fun dynamicProtected(context: Context): Set<String> {
        val out = mutableSetOf("com.android.shell", context.packageName, "com.sec.android.app.launcher")
        val pm = context.packageManager
        // Protect EVERY installed launcher — resolveActivity can return the system resolver
        // (pkg "android") rather than the real home app, leaving the actual launcher unprotected.
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(home, 0).forEach { ri ->
                ri.activityInfo?.packageName
                    ?.takeIf { it != "android" && !it.startsWith("com.android.internal") }
                    ?.let(out::add)
            }
        }
        // Active IME
        runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let(out::add)
        }
        return out
    }
}
