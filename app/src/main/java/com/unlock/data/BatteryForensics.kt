package com.unlock.data

import android.content.Context
import android.content.pm.PackageManager
import com.unlock.shizuku.ShizukuManager

/** One uid's estimated battery use (model estimate from power_profile, not measured). */
data class DrainEntry(
    val uid: Int,
    val label: String,
    val packageName: String?,
    val mAh: Double,
)

/**
 * Per-app battery blame. The accurate, no-root source is `dumpsys batterystats --checkin`
 * parsed defensively (its format drifts across OEMs/versions). Requires Shizuku.
 */
class BatteryForensicsRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    suspend fun topDrain(limit: Int = 15): List<DrainEntry> {
        if (!ShizukuManager.isReady) return emptyList()
        val res = ShizukuManager.exec("dumpsys", "batterystats", "--checkin")
        if (!res.success || res.output.isBlank()) return emptyList()

        // Power-use-item ("pwi") rows: <ver>,<uid>,<agg>,pwi,<component>,<mAh>,...
        // The "uid" component is the per-uid total.
        val totals = HashMap<Int, Double>()
        for (line in res.output.lineSequence()) {
            val f = line.split(',')
            if (f.size > 5 && f[3] == "pwi" && f[4] == "uid") {
                val uid = f[1].toIntOrNull() ?: continue
                val mah = f[5].toDoubleOrNull() ?: continue
                totals[uid] = (totals[uid] ?: 0.0) + mah
            }
        }

        return totals.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { (uid, mah) ->
                val pkg = runCatching { pm.getPackagesForUid(uid)?.firstOrNull() }.getOrNull()
                val label = pkg?.let {
                    runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrNull()
                } ?: wellKnownUid(uid) ?: "uid $uid"
                DrainEntry(uid, label, pkg, mah)
            }
    }

    private fun wellKnownUid(uid: Int): String? = when (uid) {
        0 -> "root"
        1000 -> "Android system"
        1001 -> "Telephony / radio"
        1010 -> "Wi-Fi"
        1013 -> "Media server"
        1036 -> "logd"
        2000 -> "shell"
        else -> null
    }
}
