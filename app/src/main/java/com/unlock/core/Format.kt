package com.unlock.core

import java.util.Locale
import java.util.concurrent.TimeUnit

object Format {

    fun bytes(b: Long): String {
        if (b < 0) return "—"
        if (b < 1024) return "$b B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = b.toDouble() / 1024
        var i = 0
        while (value >= 1024 && i < units.lastIndex) {
            value /= 1024; i++
        }
        return String.format(Locale.US, "%.1f %s", value, units[i])
    }

    fun durationMs(ms: Long): String {
        if (ms <= 0) return "0m"
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    fun timeAgo(epochMs: Long): String {
        if (epochMs <= 0) return "never"
        val diff = System.currentTimeMillis() - epochMs
        if (diff < 0) return "just now"
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
        return when {
            days > 30 -> "${days / 30}mo ago"
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            mins > 0 -> "${mins}m ago"
            else -> "just now"
        }
    }
}
