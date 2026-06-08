package com.unlock.data

import com.unlock.shizuku.ShizukuManager

data class MemEntry(val processName: String, val packageName: String, val pssKb: Long)

/**
 * Top RAM consumers, parsed from the "Total PSS by process:" tail of `dumpsys meminfo`
 * (already sorted descending). Handles both modern ("167128 kB: name (pid N)") and legacy
 * ("78,971K: name (pid N)") line formats. Requires Shizuku.
 */
class MemoryRepository {

    private val line = Regex("""^\s*([\d,]+)\s*(?:kB|K):\s+(\S+)\s+\(pid\s+(\d+)""")

    suspend fun topMemory(limit: Int = 15): List<MemEntry> {
        if (!ShizukuManager.isReady) return emptyList()
        val res = ShizukuManager.exec("dumpsys", "meminfo")
        if (!res.success || res.output.isBlank()) return emptyList()

        val out = mutableListOf<MemEntry>()
        var inSection = false
        for (raw in res.output.lineSequence()) {
            if (!inSection) {
                if (raw.contains("Total PSS by process")) inSection = true
                continue
            }
            if (raw.isBlank()) break
            val m = line.find(raw) ?: continue
            val pss = m.groupValues[1].replace(",", "").toLongOrNull() ?: continue
            val name = m.groupValues[2]
            out += MemEntry(name, name.substringBefore(':'), pss)
            if (out.size >= limit) break
        }
        return out
    }
}
