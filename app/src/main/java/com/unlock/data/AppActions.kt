package com.unlock.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.unlock.shizuku.ShellResult
import com.unlock.shizuku.ShizukuManager

/**
 * All package-altering actions. The privileged ones route through [ShizukuManager] and run as
 * the shell user (uid 2000) — no root. Each maps to the exact command a power user would type
 * over ADB, and every successful mutation is written to [ActionLog] with an inverse command
 * where one exists, so the user can undo it.
 */
class AppActions(private val context: Context) {

    private suspend fun recorded(
        pkg: String,
        action: String,
        undo: List<String>?,
        block: suspend () -> ShellResult,
    ): ShellResult {
        val r = block()
        if (r.success) ActionLog.record(pkg, action, undo)
        return r
    }

    /** Remove for the current user only. Reversible with install-existing; keeps data with -k. */
    suspend fun uninstallUser0(pkg: String, keepData: Boolean = true): ShellResult =
        recorded(pkg, "Uninstall (user 0)", listOf("cmd", "package", "install-existing", pkg)) {
            if (keepData) ShizukuManager.exec("pm", "uninstall", "-k", "--user", "0", pkg)
            else ShizukuManager.exec("pm", "uninstall", "--user", "0", pkg)
        }

    /** Restore an app previously removed with `--user 0` (it stays on the system image). */
    suspend fun reinstallExisting(pkg: String): ShellResult =
        recorded(pkg, "Restore", null) {
            ShizukuManager.exec("cmd", "package", "install-existing", pkg)
        }

    /** Fully reversible freeze: the app disappears and cannot run until re-enabled. */
    suspend fun disable(pkg: String): ShellResult =
        recorded(pkg, "Disable", listOf("pm", "enable", pkg)) {
            ShizukuManager.exec("pm", "disable-user", "--user", "0", pkg)
        }

    suspend fun enable(pkg: String): ShellResult =
        recorded(pkg, "Enable", null) { ShizukuManager.exec("pm", "enable", pkg) }

    /** Kill the app and all its processes right now (transient, no undo). */
    suspend fun forceStop(pkg: String): ShellResult =
        recorded(pkg, "Force-stop", null) { ShizukuManager.exec("am", "force-stop", pkg) }

    /**
     * "Sleep": stop it now, pin it to RESTRICTED standby, and revoke free background running.
     * Closest no-root equivalent of Samsung "deep sleep". RESTRICTED self-reverses on use, so the
     * force-stop is the one-shot part. Undo lifts the background restriction.
     */
    suspend fun sleep(pkg: String): ShellResult =
        recorded(
            pkg,
            "Sleep",
            // Faithful inverse: return the appop to system-managed (default) AND lift the bucket.
            listOf("sh", "-c", "cmd appops set $pkg RUN_ANY_IN_BACKGROUND default; am set-standby-bucket $pkg active"),
        ) {
            forceStopRaw(pkg)
            ShizukuManager.exec("am", "set-standby-bucket", pkg, "restricted")
            // Last expression = the appop mutation the undo reverses; recorded() logs iff it succeeds.
            ShizukuManager.exec("cmd", "appops", "set", pkg, "RUN_ANY_IN_BACKGROUND", "ignore")
        }

    private suspend fun forceStopRaw(pkg: String): ShellResult =
        ShizukuManager.exec("am", "force-stop", pkg)

    /** Greys the icon out so it can't be launched until unsuspended (reversible). */
    suspend fun suspend(pkg: String): ShellResult =
        recorded(pkg, "Suspend", listOf("pm", "unsuspend", "--user", "0", pkg)) {
            ShizukuManager.exec("pm", "suspend", "--user", "0", pkg)
        }

    suspend fun unsuspend(pkg: String): ShellResult =
        recorded(pkg, "Unsuspend", null) { ShizukuManager.exec("pm", "unsuspend", "--user", "0", pkg) }

    suspend fun setStandbyBucket(pkg: String, bucket: String): ShellResult =
        ShizukuManager.exec("am", "set-standby-bucket", pkg, bucket)

    suspend fun getStandbyBucket(pkg: String): Int =
        ShizukuManager.exec("am", "get-standby-bucket", pkg).output.trim().toIntOrNull() ?: -1

    /** Disable a single autostart receiver component without touching the rest of the app. */
    suspend fun disableComponent(pkg: String, component: String): ShellResult =
        recorded(pkg, "Disable autostart", listOf("pm", "enable", "$pkg/$component")) {
            ShizukuManager.exec("pm", "disable", "$pkg/$component")
        }

    suspend fun enableComponent(pkg: String, component: String): ShellResult =
        recorded(pkg, "Enable autostart", null) { ShizukuManager.exec("pm", "enable", "$pkg/$component") }

    suspend fun clearData(pkg: String): ShellResult =
        recorded(pkg, "Clear data", null) { ShizukuManager.exec("pm", "clear", pkg) }

    /**
     * The RELIABLE no-root way to stop an app autostarting on Samsung/stock: block background
     * running + pin RESTRICTED bucket + force the stopped state. Operates at app level, so it
     * never throws the "Shell cannot change component state" error that per-component disable does.
     * Reversible.
     */
    suspend fun stopAutostart(pkg: String): ShellResult =
        recorded(
            pkg,
            "Stop autostart",
            listOf("sh", "-c", "cmd appops set $pkg RUN_ANY_IN_BACKGROUND default; am set-standby-bucket $pkg active"),
        ) {
            ShizukuManager.exec("am", "set-standby-bucket", pkg, "restricted")
            ShizukuManager.exec("am", "set-stopped-state", pkg, "true")
            // Last expression = the persistent background block; recorded() logs iff it succeeds.
            ShizukuManager.exec("cmd", "appops", "set", pkg, "RUN_ANY_IN_BACKGROUND", "ignore")
        }

    /** Undo stopAutostart: clear stopped-state, restore standby bucket and background appop. */
    suspend fun restoreAutostart(pkg: String): ShellResult =
        recorded(pkg, "Restore autostart", null) {
            ShizukuManager.exec("am", "set-stopped-state", pkg, "false")
            ShizukuManager.exec("am", "set-standby-bucket", pkg, "active")
            ShizukuManager.exec("cmd", "appops", "set", pkg, "RUN_ANY_IN_BACKGROUND", "default")
        }

    /** Undo boostPerformance: re-enable GOS, drop fixed-performance, restore adaptive saver, reset thermal. */
    suspend fun restorePerformance(gosPackages: List<String>): List<ShellResult> {
        val out = mutableListOf<ShellResult>()
        for (g in gosPackages) out += ShizukuManager.exec("pm", "enable", g)
        out += ShizukuManager.exec("cmd", "power", "set-fixed-performance-mode-enabled", "false")
        out += ShizukuManager.exec("cmd", "power", "set-adaptive-power-saver-enabled", "true")
        out += ShizukuManager.exec("cmd", "thermalservice", "reset")
        return out
    }

    /**
     * Best-effort no-root performance levers: kill Samsung's software throttle (GOS), turn off
     * low-power / adaptive saver, pin the framework to fixed-performance, and clear the thermal
     * status apps react to. CANNOT override kernel/HAL hardware throttling (needs root) — honest
     * limits surfaced in the UI. GOS re-enables on reboot, so re-apply.
     */
    suspend fun boostPerformance(gosPackages: List<String>): List<ShellResult> {
        val out = mutableListOf<ShellResult>()
        for (g in gosPackages) out += ShizukuManager.exec("pm", "disable-user", "--user", "0", g)
        out += ShizukuManager.exec("cmd", "power", "set-mode", "0")
        out += ShizukuManager.exec("cmd", "power", "set-adaptive-power-saver-enabled", "false")
        out += ShizukuManager.exec("settings", "put", "global", "low_power", "0")
        out += ShizukuManager.exec("cmd", "power", "set-fixed-performance-mode-enabled", "true")
        out += ShizukuManager.exec("cmd", "thermalservice", "override-status", "0")
        return out
    }

    /** Self-grant the powerful perms we declared, once Shizuku is available. */
    suspend fun grantSelfPrivileges(): List<ShellResult> {
        val self = context.packageName
        return listOf(
            ShizukuManager.exec("appops", "set", self, "GET_USAGE_STATS", "allow"),
            ShizukuManager.exec("pm", "grant", self, "android.permission.WRITE_SECURE_SETTINGS"),
            ShizukuManager.exec("pm", "grant", self, "android.permission.DUMP"),
        )
    }

    /** Unprivileged path: hand the user the system uninstall dialog (user apps only). */
    fun systemUninstallIntent(pkg: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Unprivileged path: open the per-app settings page (manual force-stop / uninstall). */
    fun appDetailsIntent(pkg: String): Intent =
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
