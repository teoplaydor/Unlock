package com.unlock.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.storage.StorageManager
import java.io.File

/** Reads CPU / thermal / memory / storage state from sysfs and system services. */
class SystemReaders(private val context: Context) {

    fun thermalZones(): List<ThermalZone> {
        val base = File("/sys/class/thermal")
        val zones = base.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return emptyList()
        return zones.sortedBy { it.name }.mapNotNull { zone ->
            val raw = readFile(File(zone, "temp"))?.trim()?.toLongOrNull() ?: return@mapNotNull null
            val type = readFile(File(zone, "type"))?.trim() ?: zone.name
            val celsius = when {
                raw > 1000 -> raw / 1000f   // millidegree (most common)
                raw in 200..2000 -> raw / 10f
                else -> raw.toFloat()
            }
            if (celsius in -40f..150f) ThermalZone(type, celsius) else null
        }
    }

    fun cpuCores(): List<CpuCore> {
        val count = (File("/sys/devices/system/cpu")
            .listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }?.size)
            ?: Runtime.getRuntime().availableProcessors()
        return (0 until count).map { i ->
            val freqDir = "/sys/devices/system/cpu/cpu$i/cpufreq"
            CpuCore(
                index = i,
                curMhz = readKHz("$freqDir/scaling_cur_freq"),
                maxMhz = readKHz("$freqDir/scaling_max_freq"),
                hwMaxMhz = readKHz("$freqDir/cpuinfo_max_freq"),
                online = readFile(File("/sys/devices/system/cpu/cpu$i/online"))?.trim() != "0",
            )
        }
    }

    fun governor(): String? =
        readFile(File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"))?.trim()

    fun memory(): MemorySnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return MemorySnapshot(
            totalBytes = info.totalMem,
            availBytes = info.availMem,
            thresholdBytes = info.threshold,
            lowMemory = info.lowMemory,
        )
    }

    fun storage(): StorageSnapshot = runCatching {
        val sm = context.getSystemService(Context.STORAGE_STATS_SERVICE)
                as android.app.usage.StorageStatsManager
        val uuid = StorageManager.UUID_DEFAULT
        StorageSnapshot(totalBytes = sm.getTotalBytes(uuid), freeBytes = sm.getFreeBytes(uuid))
    }.getOrElse {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        StorageSnapshot(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
    }

    private fun readKHz(path: String): Int =
        (readFile(File(path))?.trim()?.toIntOrNull() ?: 0) / 1000

    private fun readFile(file: File): String? =
        runCatching { if (file.canRead()) file.readText() else null }.getOrNull()
}
