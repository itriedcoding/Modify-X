package dev.modifyx.core

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs inside a process that Shizuku starts with the `shell` UID.
 * Commands executed here have exactly the privileges of an `adb shell` session.
 */
class UserService : Service() {

    private val binder = object : IUserService.Stub() {
        override fun exec(command: String?, timeoutMs: Int): String = runCommand(command.orEmpty(), timeoutMs)
        override fun version(): Int = SERVICE_VERSION
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun runCommand(command: String, timeoutMs: Int): String {
        val process = try {
            ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start()
        } catch (t: Throwable) {
            return JSONObject()
                .put("exit", 127)
                .put("out", t.message ?: "could not start sh")
                .put("timeout", false)
                .toString()
        }

        val buffer = StringBuilder()
        val reader = thread(isDaemon = true) {
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(buffer) { buffer.append(line).append('\n') }
                }
            } catch (_: Throwable) {
                // The process was killed; the exit code below is authoritative.
            }
        }

        val finished = try {
            process.waitFor(timeoutMs.toLong().coerceAtLeast(1_000L), TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            false
        }
        if (!finished) process.destroyForcibly()
        reader.join(500)

        val exit = if (finished) runCatching { process.exitValue() }.getOrDefault(-1) else ShellResult.EXIT_TIMEOUT
        val output = synchronized(buffer) { buffer.toString().trimEnd() }
        return JSONObject()
            .put("exit", exit)
            .put("out", output)
            .put("timeout", !finished)
            .toString()
    }

    companion object {
        const val SERVICE_VERSION = 1
    }
}
