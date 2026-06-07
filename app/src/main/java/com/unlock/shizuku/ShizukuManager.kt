package com.unlock.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.unlock.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

/** Result of a single shell command executed through the privileged user-service. */
data class ShellResult(val success: Boolean, val output: String, val error: String? = null) {
    val text: String get() = output.ifBlank { error.orEmpty() }
}

/**
 * Owns the connection to Shizuku and the privileged [ShellUserService]. This is the
 * single gateway for every action that needs more than an unprivileged app can do
 * (uninstalling system apps, force-stopping, disabling, reading dumpsys, etc.).
 *
 * Shizuku itself is started by the user over ADB / wireless debugging (no root), or
 * over root if available. We never require root.
 */
object ShizukuManager {

    private const val TAG = "ShizukuManager"
    private const val PERMISSION_REQUEST_CODE = 4317

    enum class State {
        /** Shizuku app/binder not present — user must install + start it. */
        NOT_RUNNING,
        /** Binder alive but the user hasn't granted us the Shizuku permission yet. */
        NEEDS_PERMISSION,
        /** Fully usable. */
        READY,
    }

    private val _state = MutableStateFlow(State.NOT_RUNNING)
    val state: StateFlow<State> = _state.asStateFlow()

    val isReady: Boolean get() = _state.value == State.READY

    @Volatile
    private var userService: IUserService? = null
    private var pendingBind: CompletableDeferred<IUserService?>? = null
    private val bindMutex = Mutex()

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, ShellUserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = binder?.takeIf { it.pingBinder() }?.let { IUserService.Stub.asInterface(it) }
            userService = svc
            pendingBind?.complete(svc)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
        }
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { code, _ ->
            if (code == PERMISSION_REQUEST_CODE) refreshState()
        }
    private val binderReceived = Shizuku.OnBinderReceivedListener { refreshState() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        userService = null
        refreshState()
    }

    /** Register listeners once, from Application.onCreate. */
    fun init() {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        }.onFailure { Log.w(TAG, "init failed", it) }
        refreshState()
    }

    fun refreshState() {
        _state.value = computeState()
    }

    private fun computeState(): State = try {
        when {
            !Shizuku.pingBinder() -> State.NOT_RUNNING
            Shizuku.isPreV11() -> State.NOT_RUNNING // pre-v11 lacks the API we use
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> State.READY
            else -> State.NEEDS_PERMISSION
        }
    } catch (t: Throwable) {
        State.NOT_RUNNING
    }

    /** The uid Shizuku is running as (2000 = shell, 0 = root); null if unknown. */
    fun shizukuUid(): Int? = runCatching { Shizuku.getUid() }.getOrNull()

    fun isRoot(): Boolean = shizukuUid() == 0

    fun requestPermission() {
        runCatching {
            if (!Shizuku.shouldShowRequestPermissionRationale()) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        }.onFailure { Log.w(TAG, "requestPermission failed", it) }
    }

    private suspend fun service(): IUserService? {
        userService?.let { if (it.asBinder().pingBinder()) return it }
        if (!isReady) return null
        return bindMutex.withLock {
            userService?.let { if (it.asBinder().pingBinder()) return it }
            val deferred = CompletableDeferred<IUserService?>()
            pendingBind = deferred
            try {
                Shizuku.bindUserService(userServiceArgs, connection)
                deferred.await()
            } catch (t: Throwable) {
                Log.w(TAG, "bindUserService failed", t)
                null
            } finally {
                pendingBind = null
            }
        }
    }

    private val failureRegex = Regex("^(Failure|Failed|\\[Error\\]|Error:|Exception)", RegexOption.IGNORE_CASE)

    /** Execute an argv command through the privileged service. */
    suspend fun exec(vararg command: String): ShellResult {
        val svc = service() ?: return ShellResult(false, "", "Shizuku not ready")
        return try {
            parseResult(svc.execute(command))
        } catch (t: Throwable) {
            ShellResult(false, "", t.message)
        }
    }

    /** Strip the trailing exit-code marker and decide success from the exit code + output. */
    private fun parseResult(raw: String): ShellResult {
        val marker = ShellUserService.EXIT_MARKER
        val idx = raw.lastIndexOf(marker)
        val out: String
        val code: Int?
        if (idx >= 0) {
            out = raw.substring(0, idx).trim()
            code = raw.substring(idx + marker.length).trim().toIntOrNull()
        } else {
            out = raw.trim()
            code = null
        }
        val firstLine = out.lineSequence().firstOrNull()?.trim().orEmpty()
        val failed = out.startsWith("ERROR:") ||
            (code != null && code != 0) ||
            failureRegex.containsMatchIn(firstLine)
        return ShellResult(
            success = !failed,
            output = out,
            error = if (failed) out.ifBlank { "exit ${code ?: "?"}" } else null,
        )
    }

    /** Convenience for `cmd`/`pm`/`am`/`settings`/`dumpsys` etc. expressed as one line. */
    suspend fun shell(line: String): ShellResult =
        exec(*line.trim().split(Regex("\\s+")).toTypedArray())
}
