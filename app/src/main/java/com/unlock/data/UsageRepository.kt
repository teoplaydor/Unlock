package com.unlock.data

import android.app.usage.UsageStatsManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UsageEntry(
    val lastUsed: Long,
    val totalForegroundMs: Long,
    val launchCount: Int,
)

/** Aggregated foreground usage per package. Requires Usage Access. */
class UsageRepository(private val context: Context) {

    suspend fun usageSince(days: Int = 30): Map<String, UsageEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - days.toLong() * 24L * 60L * 60L * 1000L
            val agg = usm.queryAndAggregateUsageStats(start, end)
            agg.mapValues { (_, s) ->
                UsageEntry(
                    lastUsed = s.lastTimeUsed,
                    totalForegroundMs = s.totalTimeInForeground,
                    launchCount = 0, // UsageStats exposes no public launch-count
                )
            }
        }.getOrDefault(emptyMap())
    }

    /** Best-effort "recently active" list (used by the Running screen without Shizuku). */
    suspend fun recentlyActive(windowMinutes: Int = 60): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - windowMinutes.toLong() * 60L * 1000L
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
                ?.filter { it.totalTimeInForeground > 0 || it.lastTimeUsed >= start }
                ?.sortedByDescending { it.lastTimeUsed }
                ?.map { it.packageName }
                ?.distinct()
                .orEmpty()
        }.getOrDefault(emptyList())
    }
}
