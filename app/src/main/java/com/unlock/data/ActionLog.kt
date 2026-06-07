package com.unlock.data

import android.content.Context
import com.unlock.shizuku.ShellResult
import com.unlock.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Append-only audit trail of every privileged change, with an inverse command where the
 * action is reversible. Persists to a small TSV in filesDir so undo survives app restarts.
 */
object ActionLog {

    data class Record(
        val time: Long,
        val pkg: String,
        val action: String,
        val undo: List<String>?, // argv to reverse it, or null if not reversible
    ) {
        val reversible: Boolean get() = undo != null
    }

    private const val MAX = 300

    private val _records = MutableStateFlow<List<Record>>(emptyList())
    val records: StateFlow<List<Record>> = _records.asStateFlow()

    private var file: File? = null

    fun init(context: Context) {
        file = File(context.filesDir, "action_log.tsv")
        runCatching {
            val f = file ?: return
            if (!f.exists()) return
            _records.value = f.readLines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 3) return@mapNotNull null
                Record(
                    time = p[0].toLongOrNull() ?: 0L,
                    pkg = p[1],
                    action = p[2],
                    undo = p.getOrNull(3)?.takeIf { it.isNotBlank() }?.split(' '),
                )
            }.takeLast(MAX)
        }
    }

    suspend fun record(pkg: String, action: String, undo: List<String>?) {
        val updated = (_records.value + Record(nowMillis(), pkg, action, undo)).takeLast(MAX)
        _records.value = updated
        persist(updated)
    }

    /** Reverse a recorded action. Returns null if it isn't reversible. */
    suspend fun undo(record: Record): ShellResult? {
        val undo = record.undo ?: return null
        val r = ShizukuManager.exec(*undo.toTypedArray())
        if (r.success) record(record.pkg, "Undo · ${record.action}", null)
        return r
    }

    fun clear() {
        _records.value = emptyList()
        runCatching { file?.writeText("") }
    }

    private suspend fun persist(list: List<Record>) = withContext(Dispatchers.IO) {
        runCatching {
            file?.writeText(
                list.joinToString("\n") {
                    "${it.time}\t${it.pkg}\t${it.action}\t${it.undo?.joinToString(" ") ?: ""}"
                },
            )
        }
        Unit
    }

    private fun nowMillis(): Long = System.currentTimeMillis()
}
