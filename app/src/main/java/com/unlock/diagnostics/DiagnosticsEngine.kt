package com.unlock.diagnostics

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.unlock.core.Strings
import com.unlock.core.batteryHealthText
import com.unlock.diagnostics.SlowdownFinding.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Collects every signal and turns it into localized findings + a single health score. */
class DiagnosticsEngine(private val context: Context) {

    private val battery = BatteryMonitor(context)
    private val readers = SystemReaders(context)

    suspend fun run(strings: Strings): DiagnosticsReport = withContext(Dispatchers.IO) {
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

        val findings = analyze(strings, bat, thermals, cores, mem, storage, gov, thermalStatus)
        val score = (100 - findings.sumOf { it.severity.penalty }).coerceIn(0, 100)
        DiagnosticsReport(bat, thermals, cores, mem, storage, gov, findings, score, thermalStatus, headroom)
    }

    private fun analyze(
        s: Strings,
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
            thermalStatus >= 4 -> out += SlowdownFinding(s.fThermalCritT, s.fThermalCritD, Severity.CRITICAL)
            thermalStatus in 2..3 -> out += SlowdownFinding(s.fThermalThrottT, s.fThermalThrottD, Severity.WARN)
        }

        thermals.maxByOrNull { it.tempC }?.let { hot ->
            when {
                hot.tempC >= 55 -> out += SlowdownFinding(
                    String.format(s.fSevereHeatT, hot.tempC.toInt()),
                    String.format(s.fSevereHeatD, hot.type),
                    Severity.CRITICAL,
                )
                hot.tempC >= 45 -> out += SlowdownFinding(
                    String.format(s.fRunWarmT, hot.tempC.toInt()),
                    String.format(s.fRunWarmD, hot.type),
                    Severity.WARN,
                )
            }
        }

        val onlineCores = cores.filter { it.online && it.hwMaxMhz > 0 }
        if (onlineCores.isNotEmpty()) {
            val ratio = onlineCores.map { it.throttleRatio }.average()
            if (ratio < 0.9) out += SlowdownFinding(
                String.format(s.fCpuCapT, (ratio * 100).toInt()),
                s.fCpuCapD,
                Severity.WARN,
            )
        }
        if (gov != null && gov in setOf("powersave", "conservative")) {
            out += SlowdownFinding(String.format(s.fGovT, gov), s.fGovD, Severity.INFO)
        }

        bat?.let { b ->
            if (b.healthCode in 3..7) out += SlowdownFinding(
                String.format(s.fBatHealthT, batteryHealthText(b.healthCode, s)),
                s.fBatHealthD,
                Severity.WARN,
            )
            if (b.temperatureC >= 43) out += SlowdownFinding(
                String.format(s.fBatHotT, b.temperatureC.toInt()),
                s.fBatHotD,
                Severity.WARN,
            )
        }

        storage?.let { st ->
            if (st.usedPercent >= 92) out += SlowdownFinding(
                String.format(s.fStorageT, st.usedPercent), s.fStorageD, Severity.WARN,
            )
        }

        mem?.let { m ->
            if (m.lowMemory || m.usedPercent >= 90) out += SlowdownFinding(
                String.format(s.fRamT, m.usedPercent), s.fRamD, Severity.WARN,
            )
        }

        if (out.isEmpty()) out += SlowdownFinding(s.fOkT, s.fOkD, Severity.OK)
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
