package com.unlock.data

import android.os.Build
import com.unlock.shizuku.ShellResult
import com.unlock.shizuku.ShizukuManager

/** Detects the device/OEM so tweaks can be gated to where they actually apply. */
object DeviceInfo {
    private val manufacturer: String = Build.MANUFACTURER?.lowercase().orEmpty()
    private val brand: String = Build.BRAND?.lowercase().orEmpty()

    val oem: String = when {
        manufacturer.contains("samsung") -> "samsung"
        listOf("xiaomi", "redmi", "poco").any { manufacturer.contains(it) || brand.contains(it) } -> "xiaomi"
        listOf("oppo", "realme").any { manufacturer.contains(it) || brand.contains(it) } -> "oppo"
        manufacturer.contains("oneplus") -> "oneplus"
        manufacturer.contains("vivo") -> "vivo"
        manufacturer.contains("google") -> "pixel"
        listOf("huawei", "honor").any { manufacturer.contains(it) } -> "huawei"
        else -> "aosp"
    }

    /** A tweak applies if it targets "all" or names this OEM (oem field may be slash/comma separated). */
    fun applies(tweakOem: String): Boolean {
        if (tweakOem.isBlank() || tweakOem.equals("all", true)) return true
        return tweakOem.split('/', ',', ' ', '|')
            .map { it.trim().lowercase() }
            .any { it == oem || it == "all" || it == "aosp" && oem == "pixel" }
    }

    val label: String get() = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
}

/** Applies / reverts / reads tweaks through Shizuku. Commands run via `sh -c` so compound lines work. */
class TweaksRepository {

    suspend fun isOn(t: Tweak): Boolean? {
        val read = t.readCmd ?: return null
        if (!ShizukuManager.isReady) return null
        val r = ShizukuManager.exec("sh", "-c", read)
        if (!r.success) return null
        val out = r.output.trim().substringAfterLast(':').trim() // tolerate "Foo: value"
        val on = t.onValue?.trim() ?: return null
        return out == on || out.endsWith(on) || out.contains(on)
    }

    suspend fun apply(t: Tweak): ShellResult = ShizukuManager.exec("sh", "-c", t.applyCmd)

    suspend fun undo(t: Tweak): ShellResult =
        t.undoCmd?.let { ShizukuManager.exec("sh", "-c", it) } ?: ShellResult(false, "", "no undo")

    suspend fun setEnabled(t: Tweak, enabled: Boolean): ShellResult = if (enabled) apply(t) else undo(t)

    /** Reads the current numeric value of a SLIDER tweak. */
    suspend fun readValue(t: Tweak): Float? {
        val read = t.readCmd ?: return null
        if (!ShizukuManager.isReady) return null
        val r = ShizukuManager.exec("sh", "-c", read)
        if (!r.success) return null
        return r.output.trim().substringAfterLast(':').trim().toFloatOrNull()
    }

    /** Applies a SLIDER value (substitutes {v} in applyCmd). */
    suspend fun setValue(t: Tweak, value: Float): ShellResult {
        val cmd = t.applyCmd.replace("{v}", formatValue(value, t.step))
        return ShizukuManager.exec("sh", "-c", cmd)
    }

    /** Runs an arbitrary compound command (used by profiles). */
    suspend fun run(cmd: String): ShellResult = ShizukuManager.exec("sh", "-c", cmd)

    private fun formatValue(v: Float, step: Float): String =
        if (step >= 1f) v.toInt().toString()
        else (Math.round(v * 100f) / 100f).toString().trimEnd('0').trimEnd('.')
}
