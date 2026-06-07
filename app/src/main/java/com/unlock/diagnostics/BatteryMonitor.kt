package com.unlock.diagnostics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** Reads battery + power telemetry from BatteryManager and the sticky battery intent. */
class BatteryMonitor(private val context: Context) {

    private val bm: BatteryManager
        get() = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    fun snapshot(): BatterySnapshot {
        val sticky: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) level * 100 / scale
        else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        return BatterySnapshot(
            percent = percent,
            status = statusText(sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1),
            health = healthText(sticky?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1),
            plugged = pluggedText(sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1),
            technology = sticky?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            voltageMv = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            temperatureC = (sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            currentNowMicroA = safeIntProp(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            currentAvgMicroA = safeIntProp(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),
            chargeCounterMicroAh = safeIntProp(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            energyCounterNwh = runCatching { bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) }
                .getOrDefault(Long.MIN_VALUE),
        )
    }

    private fun safeIntProp(prop: Int): Int =
        runCatching { bm.getIntProperty(prop) }.getOrDefault(Int.MIN_VALUE)

    private fun statusText(v: Int) = when (v) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        else -> "Unknown"
    }

    private fun healthText(v: Int) = when (v) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        else -> "Unknown"
    }

    private fun pluggedText(v: Int) = when (v) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        0 -> "On battery"
        else -> "Unknown"
    }
}
