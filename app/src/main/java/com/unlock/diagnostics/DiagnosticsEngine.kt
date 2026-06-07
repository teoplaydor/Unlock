package com.unlock.diagnostics

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.unlock.diagnostics.SlowdownFinding.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Collects every signal and turns it into findings + a single health score. */
class DiagnosticsEngine(private val context: Context) {

    private val battery = BatteryMonitor(context)
    private val readers = SystemReaders(context)

    suspend fun run(): DiagnosticsReport = withContext(Dispatchers.IO) {
        val bat = runCatching { battery.snapshot() }.getOrNull()
        val thermals = readers.thermalZones()
        val cores = readers.cpuCores()
        val mem = runCatching { readers.memory() }.getOrNull()
        val storage = runCatching { readers.storage() }.getOrNull()
        val gov = readers.governor()

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null)
            runCatching { pm.currentThermalStatus }.getOrDefault(-1) else -1
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pm != null)
            runCatching { pm.getThermalHeadroom(0) }.getOrDefault(Float.NaN) else Float.NaN

        val findings = analyze(bat, thermals, cores, mem, storage, gov, thermalStatus)
        val score = (100 - findings.sumOf { it.severity.penalty }).coerceIn(0, 100)
        DiagnosticsReport(bat, thermals, cores, mem, storage, gov, findings, score, thermalStatus, headroom)
    }

    private fun analyze(
        bat: BatterySnapshot?,
        thermals: List<ThermalZone>,
        cores: List<CpuCore>,
        mem: MemorySnapshot?,
        storage: StorageSnapshot?,
        gov: String?,
        thermalStatus: Int,
    ): List<SlowdownFinding> {
        val out = mutableListOf<SlowdownFinding>()

        when {
            thermalStatus >= 4 -> out += SlowdownFinding(
                "Thermal status: critical",
                "Android's thermal service reports a critical state — the system is aggressively throttling.",
                Severity.CRITICAL,
            )
            thermalStatus in 2..3 -> out += SlowdownFinding(
                "Thermal status: throttling",
                "Android reports a moderate/severe thermal state, so performance is being held back.",
                Severity.WARN,
            )
        }

        thermals.maxByOrNull { it.tempC }?.let { hot ->
            when {
                hot.tempC >= 55 -> out += SlowdownFinding(
                    "Severe heat (${hot.tempC.toInt()}°C)",
                    "${hot.type} is very hot — the SoC is almost certainly throttling clocks to cool down.",
                    Severity.CRITICAL,
                )
                hot.tempC >= 45 -> out += SlowdownFinding(
                    "Running warm (${hot.tempC.toInt()}°C)",
                    "Sustained load is heating ${hot.type}; expect some thermal throttling.",
                    Severity.WARN,
                )
            }
        }

        val onlineCores = cores.filter { it.online && it.hwMaxMhz > 0 }
        if (onlineCores.isNotEmpty()) {
            val ratio = onlineCores.map { it.throttleRatio }.average()
            if (ratio < 0.9) out += SlowdownFinding(
                "CPU capped at ${(ratio * 100).toInt()}% of max",
                "Cores are pinned below their hardware ceiling — thermal/battery throttling or a power-save governor.",
                Severity.WARN,
            )
        }
        if (gov != null && gov in setOf("powersave", "conservative")) {
            out += SlowdownFinding(
                "Power-save CPU governor ($gov)",
                "The scheduler is favouring efficiency over speed, which can feel sluggish.",
                Severity.INFO,
            )
        }

        bat?.let { b ->
            if (b.health != "Good" && b.health != "Unknown") out += SlowdownFinding(
                "Battery health: ${b.health}",
                "A degraded battery can't hold voltage under load, so the SoC down-clocks to avoid brownouts.",
                Severity.WARN,
            )
            if (b.temperatureC >= 43) out += SlowdownFinding(
                "Battery hot (${b.temperatureC.toInt()}°C)",
                "High battery temperature triggers protective throttling and faster wear.",
                Severity.WARN,
            )
        }

        storage?.let { s ->
            if (s.usedPercent >= 92) out += SlowdownFinding(
                "Storage ${s.usedPercent}% full",
                "Near-full flash slows writes and starves ART/dex optimisation — a classic cause of lag.",
                Severity.WARN,
            )
        }

        mem?.let { m ->
            if (m.lowMemory || m.usedPercent >= 90) out += SlowdownFinding(
                "RAM pressure (${m.usedPercent}% used)",
                "The low-memory killer is recycling apps, so switching back reloads them from scratch.",
                Severity.WARN,
            )
        }

        if (out.isEmpty()) out += SlowdownFinding(
            "No obvious bottleneck",
            "Thermals, clocks, storage and memory all look healthy right now.",
            Severity.OK,
        )
        return out.sortedByDescending { it.severity.penalty }
    }

    private val Severity.penalty: Int
        get() = when (this) {
            Severity.OK -> 0
            Severity.INFO -> 5
            Severity.WARN -> 18
            Severity.CRITICAL -> 35
        }
}
