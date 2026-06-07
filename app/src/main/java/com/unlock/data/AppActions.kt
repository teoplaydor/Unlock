package com.unlock.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.unlock.shizuku.ShellResult
import com.unlock.shizuku.ShizukuManager

/**
 * All package-altering actions. The privileged ones route through [ShizukuManager]
 * and run as the shell user (uid 2000) — no root. Each maps to the exact command a
 * power user would type over ADB, so behaviour is predictable and reversible where noted.
 */
class AppActions(private val context: Context) {

    /** Remove for the current user only. Reversible with [reinstallExisting]; full data kept with -k. */
    suspend fun uninstallUser0(pkg: String, keepData: Boolean = false): ShellResult =
        if (keepData) ShizukuManager.exec("pm", "uninstall", "-k", "--user", "0", pkg)
        else ShizukuManager.exec("pm", "uninstall", "--user", "0", pkg)

    /** Restore an app previously removed with `--user 0` (it stays on the system image). */
    suspend fun reinstallExisting(pkg: String): ShellResult =
        ShizukuManager.exec("cmd", "package", "install-existing", pkg)

    /** Fully reversible freeze: the app disappears and cannot run until re-enabled. */
    suspend fun disable(pkg: String): ShellResult =
        ShizukuManager.exec("pm", "disable-user", "--user", "0", pkg)

    suspend fun enable(pkg: String): ShellResult =
        ShizukuManager.exec("pm", "enable", pkg)

    /** Kill the app and all its processes right now. */
    suspend fun forceStop(pkg: String): ShellResult =
        ShizukuManager.exec("am", "force-stop", pkg)

    /**
     * "Sleep": stop it now, pin it to the RESTRICTED standby bucket, and revoke its ability to
     * run freely in the background. This is the closest no-root equivalent of Samsung's
     * "deep sleep". Note RESTRICTED only throttles and self-reverses on user interaction, so the
     * force-stop is the one-shot part — re-apply if the app wakes again.
     */
    suspend fun sleep(pkg: String): ShellResult {
        val stop = forceStop(pkg)
        ShizukuManager.exec("am", "set-standby-bucket", pkg, "restricted")
        ShizukuManager.exec("cmd", "appops", "set", pkg, "RUN_ANY_IN_BACKGROUND", "ignore")
        return stop
    }

    /** Greys the icon out so it can't be launched until unsuspended (reversible, survives nothing destructive). */
    suspend fun suspend(pkg: String): ShellResult =
        ShizukuManager.exec("pm", "suspend", "--user", "0", pkg)

    suspend fun unsuspend(pkg: String): ShellResult =
        ShizukuManager.exec("pm", "unsuspend", "--user", "0", pkg)

    suspend fun setStandbyBucket(pkg: String, bucket: String): ShellResult =
        ShizukuManager.exec("am", "set-standby-bucket", pkg, bucket)

    suspend fun getStandbyBucket(pkg: String): Int =
        ShizukuManager.exec("am", "get-standby-bucket", pkg)
            .output.trim().toIntOrNull() ?: -1

    /** Disable a single autostart receiver component without touching the rest of the app. */
    suspend fun disableComponent(pkg: String, component: String): ShellResult =
        ShizukuManager.exec("pm", "disable", "$pkg/$component")

    suspend fun enableComponent(pkg: String, component: String): ShellResult =
        ShizukuManager.exec("pm", "enable", "$pkg/$component")

    suspend fun clearData(pkg: String): ShellResult =
        ShizukuManager.exec("pm", "clear", pkg)

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
