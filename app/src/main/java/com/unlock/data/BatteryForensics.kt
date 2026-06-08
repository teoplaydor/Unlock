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

data class BatteryHealth(
    val sohPercent: Int,            // direct % (Samsung) or computed from capacity; -1 if unknown
    val cycleCount: Int,
    val chargeFullUah: Int,        // -1 if not from sysfs
    val chargeFullDesignUah: Int, // -1 if not from sysfs
    val source: String,
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

    /** Reads true State of Health via Shizuku: sysfs design capacity, else Samsung dumpsys ASOC/BSOH. */
    suspend fun batteryHealth(): BatteryHealth? {
        if (!ShizukuManager.isReady) return null
        val full = catInt("/sys/class/power_supply/battery/charge_full")
            ?: catInt("/sys/class/power_supply/bms/charge_full")
        val design = catInt("/sys/class/power_supply/battery/charge_full_design")
            ?: catInt("/sys/class/power_supply/bms/charge_full_design")
        val cycles = catInt("/sys/class/power_supply/battery/cycle_count")
            ?: catInt("/sys/class/power_supply/bms/cycle_count") ?: -1
        if (full != null && design != null && design > 0) {
            return BatteryHealth((full * 100 / design).coerceIn(0, 100), cycles, full, design, "sysfs")
        }
        // Samsung One UI exposes ASOC/BSOH in dumpsys battery.
        val dump = ShizukuManager.exec("dumpsys", "battery").output
        val soh = Regex("(?i)(?:mBatteryAsoc|BSOH|mSavedBatteryAsoc)\\D*(\\d{1,3})")
            .find(dump)?.groupValues?.get(1)?.toIntOrNull()
        val cyc = Regex("(?i)cycle\\s*count\\D*(\\d+)").find(dump)?.groupValues?.get(1)?.toIntOrNull() ?: cycles
        if (soh != null && soh in 1..100) return BatteryHealth(soh, cyc, -1, -1, "samsung")
        if (cyc >= 0) return BatteryHealth(-1, cyc, -1, -1, "dumpsys")
        return null
    }

    private suspend fun catInt(path: String): Int? {
        val r = ShizukuManager.exec("cat", path)
        if (!r.success) return null
        return r.output.trim().toIntOrNull()
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
