package com.unlock.data

import android.content.Context
import com.unlock.shizuku.ShellResult
import com.unlock.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Append-only audit trail of every privileged change, with an inverse command where the
 * action is reversible. Persists to a small TSV in filesDir so undo survives app restarts.
 *
 * Columns are tab-separated; the undo argv is joined by a unit-separator (0x1F) so tokens
 * may contain spaces (e.g. an `sh -c` script). All writes go through [mutex] so the in-memory
 * value and the on-disk file never diverge under concurrent record()/undo().
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
    private val US: Char = 31.toChar()          // unit separator joining undo argv tokens
    private val usStr: String = US.toString()

    private val _records = MutableStateFlow<List<Record>>(emptyList())
    val records: StateFlow<List<Record>> = _records.asStateFlow()

    private val mutex = Mutex()
    private var file: File? = null

    fun init(context: Context) {
        file = File(context.filesDir, "action_log.tsv")
        runCatching {
            val f = file ?: return
            if (!f.exists()) return
            _records.value = f.readLines().mapNotNull(::parseLine).takeLast(MAX)
        }
    }

    suspend fun record(pkg: String, action: String, undo: List<String>?) {
        mutex.withLock {
            val rec = Record(nowMillis(), pkg.clean(), action.clean(), undo)
            val updated = (_records.value + rec).takeLast(MAX)
            _records.value = updated
            persistLocked(updated)
        }
    }

    /** Reverse a recorded action. Returns null if it isn't reversible. Idempotent: consumes the undo. */
    suspend fun undo(record: Record): ShellResult? {
        val undo = record.undo ?: return null
        val r = ShizukuManager.exec(*undo.toTypedArray())
        if (r.success) {
            mutex.withLock {
                val consumed = _records.value.map {
                    if (it.time == record.time && it.pkg == record.pkg && it.action == record.action && it.undo != null) {
                        it.copy(undo = null)
                    } else it
                }
                val appended = (consumed + Record(nowMillis(), record.pkg, "Undo · ${record.action}", null)).takeLast(MAX)
                _records.value = appended
                persistLocked(appended)
            }
        }
        return r
    }

    fun clear() {
        _records.value = emptyList()
        runCatching { file?.writeText("") }
    }

    private suspend fun persistLocked(list: List<Record>) {
        withContext(Dispatchers.IO) {
            runCatching { file?.writeText(list.joinToString("\n", transform = ::serialize)) }
        }
    }

    private fun serialize(r: Record): String =
        "${r.time}\t${r.pkg}\t${r.action}\t${r.undo?.joinToString(usStr) ?: ""}"

    private fun parseLine(line: String): Record? {
        val p = line.split('\t')
        if (p.size < 3) return null
        return Record(
            time = p[0].toLongOrNull() ?: 0L,
            pkg = p[1],
            action = p[2],
            undo = p.getOrNull(3)?.takeIf { it.isNotBlank() }?.split(US),
        )
    }

    private fun String.clean(): String = replace('\t', ' ').replace('\n', ' ').replace(US, ' ')

    private fun nowMillis(): Long = System.currentTimeMillis()
}
