package com.unlock.diagnostics

/** Instantaneous battery + power readings (all without root). */
data class BatterySnapshot(
    val percent: Int,
    val status: String,
    val health: String,
    val plugged: String,
    val technology: String?,
    val voltageMv: Int,
    val temperatureC: Float,
    val currentNowMicroA: Int,
    val currentAvgMicroA: Int,
    val chargeCounterMicroAh: Int,
    val energyCounterNwh: Long,
    val chargeFullUah: Int = -1,
    val chargeFullDesignUah: Int = -1,
    val cycleCount: Int = -1,
    val sohPercentDirect: Int = -1, // when a device reports SoH directly (e.g. Samsung ASOC/BSOH)
) {
    /** Negative while discharging on most devices. Watts. */
    val powerWatts: Float
        get() = if (voltageMv > 0 && currentNowMicroA != 0)
            (voltageMv / 1000f) * (currentNowMicroA / 1_000_000f) else 0f

    val dischargingMilliAmps: Int get() = currentNowMicroA / 1000

    /** State of health: direct % if reported, else current full-charge vs design capacity. -1 unknown. */
    val sohPercent: Int
        get() = when {
            sohPercentDirect in 1..100 -> sohPercentDirect
            chargeFullDesignUah > 0 && chargeFullUah > 0 -> chargeFullUah * 100 / chargeFullDesignUah
            else -> -1
        }
}

data class ThermalZone(val type: String, val tempC: Float)

data class CpuCore(
    val index: Int,
    val curMhz: Int,
    val maxMhz: Int,
    val hwMaxMhz: Int,
    val online: Boolean,
) {
    /** <1.0 means this core's ceiling is currently capped below its hardware max (throttled). */
    val throttleRatio: Float get() = if (hwMaxMhz > 0) maxMhz.toFloat() / hwMaxMhz else 1f
}

data class MemorySnapshot(
    val totalBytes: Long,
    val availBytes: Long,
    val thresholdBytes: Long,
    val lowMemory: Boolean,
) {
    val usedPercent: Int get() = if (totalBytes > 0) (((totalBytes - availBytes) * 100) / totalBytes).toInt() else 0
}

data class StorageSnapshot(val totalBytes: Long, val freeBytes: Long) {
    val usedPercent: Int get() = if (totalBytes > 0) (((totalBytes - freeBytes) * 100) / totalBytes).toInt() else 0
}

/** A scored, human-readable reason the phone might feel slow. */
data class SlowdownFinding(
    val title: String,
    val detail: String,
    val severity: Severity,
) {
    enum class Severity { OK, INFO, WARN, CRITICAL }
}

data class DiagnosticsReport(
    val battery: BatterySnapshot?,
    val thermals: List<ThermalZone>,
    val cores: List<CpuCore>,
    val memory: MemorySnapshot?,
    val storage: StorageSnapshot?,
    val governor: String?,
    val findings: List<SlowdownFinding>,
    val healthScore: Int, // 0..100, higher = healthier
    val thermalStatus: Int = -1,       // PowerManager 0..6 (NONE..SHUTDOWN), -1 unknown
    val thermalHeadroom: Float = Float.NaN, // 0..1, closer to 1 = closer to throttling
) {
    val maxTempC: Float? get() = thermals.maxByOrNull { it.tempC }?.tempC
    val avgThrottleRatio: Float
        get() = cores.filter { it.online }.map { it.throttleRatio }.ifEmpty { listOf(1f) }.average().toFloat()
}
