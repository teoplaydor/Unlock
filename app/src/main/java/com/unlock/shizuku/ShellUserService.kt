package com.unlock.shizuku

import android.os.Process
import java.io.BufferedReader

/**
 * Runs inside a process spawned by the Shizuku server, owned by the shell user
 * (uid 2000) — or root if Shizuku itself was started as root. Anything this class
 * execs therefore runs with that user's privileges, which is enough for
 * `pm`, `am`, `cmd`, `settings`, `dumpsys`, `appops`, etc.
 *
 * The returned string always ends with [EXIT_MARKER] + the process exit code, so the
 * caller can distinguish real success from a tool that printed "Failure"/"Failed"
 * with a non-zero exit. [ShizukuManager.parseResult] strips and interprets it.
 *
 * This class must have a no-arg constructor and is referenced by [ComponentName]
 * in [ShizukuManager]; do not rename without updating the ProGuard keep rule.
 */
class ShellUserService : IUserService.Stub() {

    override fun destroy() {
        exit()
    }

    override fun exit() {
        Process.killProcess(Process.myPid())
    }

    override fun execute(command: Array<String>): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val code = process.waitFor()
            val body = buildString {
                append(stdout)
                if (stderr.isNotBlank()) {
                    if (isNotEmpty() && !endsWith("\n")) append('\n')
                    append(stderr)
                }
            }.trim()
            body + EXIT_MARKER + code
        } catch (t: Throwable) {
            "ERROR: ${t.javaClass.simpleName}: ${t.message}" + EXIT_MARKER + (-1)
        }
    }

    companion object {
        /** Sentinel separating command output from the trailing exit code. */
        const val EXIT_MARKER = "__EXIT__="
    }
}
