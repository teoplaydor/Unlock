package com.unlock.data

import com.unlock.shizuku.ShizukuManager

data class RunningProcess(
    val processName: String,
    val packageName: String,
    val importance: String,
)

/**
 * Lists what is actually running. Modern Android hides this from unprivileged apps,
 * so the accurate path is Shizuku (`dumpsys activity processes`). Without Shizuku we
 * fall back to "recently foregrounded" from UsageStats.
 */
class RunningRepository(private val usageRepository: UsageRepository) {

    suspend fun running(): List<RunningProcess> {
        if (ShizukuManager.isReady) {
            val res = ShizukuManager.exec("dumpsys", "activity", "processes")
            if (res.success && res.output.isNotBlank()) {
                val parsed = parseProcesses(res.output)
                if (parsed.isNotEmpty()) return parsed
            }
        }
        // Fallback: recently active apps (no live process info available).
        return usageRepository.recentlyActive(60).map {
            RunningProcess(processName = it, packageName = it, importance = "recent")
        }
    }

    /** Pull "pid:procName/uXXX" tokens out of dumpsys, tagging by the nearest "Proc #" line. */
    internal fun parseProcesses(dump: String): List<RunningProcess> {
        val token = Regex("""\d+:([A-Za-z0-9_.]+(?::[A-Za-z0-9_.]+)?)/(u\d+a?\d*|root|system|\d+)""")
        val procTag = Regex("""Proc\s+#\s*\d+:\s*(\S+)""")
        val result = LinkedHashMap<String, RunningProcess>()
        var lastTag = ""
        for (line in dump.lineSequence()) {
            procTag.find(line)?.let { lastTag = it.groupValues[1] }
            val m = token.find(line) ?: continue
            val procName = m.groupValues[1]
            val pkg = procName.substringBefore(':')
            if (pkg.contains('.')) {
                result.putIfAbsent(
                    procName,
                    RunningProcess(processName = procName, packageName = pkg, importance = lastTag),
                )
            }
        }
        return result.values.sortedBy { it.packageName }
    }
}
