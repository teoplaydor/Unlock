package com.unlock.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the package database. Everything here works with NO special privilege
 * (beyond QUERY_ALL_PACKAGES, which is a normal manifest permission for a debloater).
 * Sizes need Usage Access; per-app usage is merged in by [UsageRepository].
 */
class AppRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.Default) {
        val bootPkgs = bootReceiverPackages()
        val dynamicProtected = SafetyCatalog.dynamicProtected(context)
        @Suppress("DEPRECATION")
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        packages.mapNotNull { pkg ->
            val ai = pkg.applicationInfo ?: return@mapNotNull null
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            AppInfo(
                packageName = pkg.packageName,
                label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pkg.packageName),
                isSystem = isSystem,
                isUpdatedSystemApp = isUpdatedSystem,
                isEnabled = ai.enabled,
                isLaunchable = pm.getLaunchIntentForPackage(pkg.packageName) != null,
                versionName = pkg.versionName,
                versionCode = pkg.longVersionCodeCompat(),
                targetSdk = ai.targetSdkVersion,
                minSdk = ai.minSdkVersionCompat(),
                installerPackage = installerOf(pkg.packageName),
                firstInstallTime = pkg.firstInstallTime,
                lastUpdateTime = pkg.lastUpdateTime,
                uid = ai.uid,
                hasBootReceiver = pkg.packageName in bootPkgs,
                safetyTier = SafetyCatalog.classify(pkg.packageName, dynamicProtected),
            )
        }.sortedBy { it.label.lowercase() }
    }

    /** Packages that register a BOOT_COMPLETED / QUICKBOOT receiver = candidate autostarters. */
    fun bootReceiverPackages(): Set<String> = bootEntries().map { it.packageName }.toSet()

    fun bootEntries(): List<BootEntry> {
        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
        val dynamicProtected = SafetyCatalog.dynamicProtected(context)
        val enabledPkgs = enabledPackages()
        val out = mutableListOf<BootEntry>()
        for (action in actions) {
            @Suppress("DEPRECATION")
            val resolved = pm.queryBroadcastReceivers(Intent(action), PackageManager.GET_DISABLED_COMPONENTS)
            for (ri in resolved) {
                val act = ri.activityInfo ?: continue
                // A disabled app can't autostart, so don't list it here.
                if (act.packageName !in enabledPkgs) continue
                val tier = SafetyCatalog.classify(act.packageName, dynamicProtected)
                out += BootEntry(
                    packageName = act.packageName,
                    label = runCatching { act.applicationInfo?.let { pm.getApplicationLabel(it).toString() } }
                        .getOrNull() ?: act.packageName,
                    receiverClass = act.name,
                    action = action,
                    isEnabledComponent = act.isEnabled,
                    isProtected = tier == SafetyTier.PROTECTED,
                    safetyTier = tier,
                )
            }
        }
        return out.distinctBy { it.packageName + it.receiverClass + it.action }
            .sortedBy { it.label.lowercase() }
    }

    /** Packages whose application is currently enabled (a disabled app can't autostart). */
    private fun enabledPackages(): Set<String> = runCatching {
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(0).filter { it.enabled }.map { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    /** App / data / cache bytes. Needs Usage Access; returns Triple(-1,-1,-1) otherwise. */
    suspend fun sizesOf(app: AppInfo): Triple<Long, Long, Long> = withContext(Dispatchers.IO) {
        runCatching {
            val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val uuid = StorageManager.UUID_DEFAULT
            val user = Process.myUserHandle()
            val stats = ssm.queryStatsForPackage(uuid, app.packageName, user)
            Triple(stats.appBytes, stats.dataBytes, stats.cacheBytes)
        }.getOrDefault(Triple(-1L, -1L, -1L))
    }

    @Suppress("DEPRECATION")
    private fun installerOf(pkg: String): String? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(pkg).installingPackageName
        } else {
            pm.getInstallerPackageName(pkg)
        }
    }.getOrNull()
}

@Suppress("DEPRECATION")
private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) longVersionCode
    else versionCode.toLong()

private fun ApplicationInfo.minSdkVersionCompat(): Int =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) minSdkVersion else 0
