package com.unlock.data

/** A single installed package with everything the UI needs to reason about it. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isUpdatedSystemApp: Boolean,
    val isEnabled: Boolean,
    val isLaunchable: Boolean,
    val versionName: String?,
    val versionCode: Long,
    val targetSdk: Int,
    val minSdk: Int,
    val installerPackage: String?,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val uid: Int,
    val hasBootReceiver: Boolean = false,
    // Lazily filled (need Usage Access / Shizuku):
    val appBytes: Long = -1,
    val dataBytes: Long = -1,
    val cacheBytes: Long = -1,
    val lastUsed: Long = 0,
    val totalForegroundMs: Long = 0,
    val launchCount: Int = 0,
    val standbyBucket: Int = -1,
    val safetyTier: SafetyTier = SafetyTier.NORMAL,
) {
    val totalBytes: Long get() = if (appBytes < 0) -1 else appBytes + dataBytes + cacheBytes
    val isRemovableUserApp: Boolean get() = !isSystem
    val isProtected: Boolean get() = safetyTier == SafetyTier.PROTECTED
}

/**
 * Risk classification used to gate destructive actions.
 * PROTECTED = hard block (removing it can bootloop / wipe a vault).
 * CAUTION = allowed but warned (e.g. GMS on a Play device).
 * DEBLOAT_SAFE = on a curated safe-to-remove list.
 */
enum class SafetyTier { PROTECTED, CAUTION, NORMAL, DEBLOAT_SAFE }

/** A boot/autostart entry: which receiver in which package fires on which action. */
data class BootEntry(
    val packageName: String,
    val label: String,
    val receiverClass: String,
    val action: String,
    val isEnabledComponent: Boolean,
    val isProtected: Boolean = false,
)

/** Standby bucket constants mirrored so we don't pull UsageStatsManager into the UI. */
object StandbyBucket {
    const val ACTIVE = 10
    const val WORKING_SET = 20
    const val FREQUENT = 30
    const val RARE = 40
    const val RESTRICTED = 45
    const val NEVER = 50

    fun label(value: Int): String = when (value) {
        ACTIVE -> "Active"
        WORKING_SET -> "Working set"
        FREQUENT -> "Frequent"
        RARE -> "Rare"
        RESTRICTED -> "Restricted"
        NEVER -> "Never"
        else -> "Unknown"
    }
}
