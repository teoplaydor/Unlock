package com.unlock.shizuku

import android.os.Process
import java.io.BufferedReader

/**
 * Runs inside a process spawned by the Shizuku server, owned by the shell user
 * (uid 2000) — or root if Shizuku itself was started as root. Anything this class
 * execs therefore runs with that user's privileges, which is enough for
 * `pm`, `am`, `cmd`, `settings`, `dumpsys`, `appops`, etc.
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
            buildString {
                append(stdout)
                if (stderr.isNotBlank()) {
                    if (isNotEmpty() && !endsWith("\n")) append('\n')
                    append(stderr)
                }
                if (code != 0 && isBlank()) append("exit code ").append(code)
            }.trim()
        } catch (t: Throwable) {
            "ERROR: ${t.javaClass.simpleName}: ${t.message}"
        }
    }
}
