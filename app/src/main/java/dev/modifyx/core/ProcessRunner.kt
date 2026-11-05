package dev.modifyx.core

import android.os.SystemClock
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs real shell commands in a child process and returns its actual output and exit code.
 *
 * Every command is wrapped so that stdout and stderr are interleaved in order and a
 * sentinel line carries the exit status. This avoids the classic "process exited but the
 * stream was only half read" bug for commands that print a lot (`pm list packages -u`).
 */
object ProcessRunner {

    private const val EXIT_MARKER = "__MX_EXIT__:"

    fun wrap(command: String): String = buildString {
        // Some su implementations start with a PATH that lacks /system/bin.
        append("export PATH=/system/bin:/system/xbin:/vendor/bin:/sbin:\$PATH; ")
        append("( ")
        append(command)
        append(" ) 2>&1; echo \"")
        append(EXIT_MARKER)
        append("$?\"")
    }

    suspend fun exec(
        kind: ShellKind,
        command: String,
        timeoutMs: Long,
        start: () -> Process,
    ): ShellResult = withContext(Dispatchers.IO) {
        val began = SystemClock.elapsedRealtime()
        fun fail(message: String) = ShellResult(
            command = command,
            stdout = "",
            stderr = message,
            exitCode = 127,
            durationMs = SystemClock.elapsedRealtime() - began,
        )

        val process: Process = try {
            start()
        } catch (t: Throwable) {
            return@withContext fail(t.message ?: "${kind.name.lowercase()} shell unavailable")
        }

        val buffer = StringBuilder()
        val reader = thread(isDaemon = true, name = "modifyx-shell-read") {
            try {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(buffer) { buffer.append(line).append('\n') }
                }
            } catch (_: Throwable) {
                // Stream closed by destroyForcibly: the exit code below tells the story.
            }
        }

        val finished = try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            false
        }
        if (!finished) process.destroyForcibly()
        reader.join(1_000)

        val raw = synchronized(buffer) { buffer.toString() }
        val markerIndex = raw.lastIndexOf(EXIT_MARKER)
        val exitCode = when {
            markerIndex >= 0 -> raw.substring(markerIndex + EXIT_MARKER.length).trim().toIntOrNull() ?: -1
            !finished -> ShellResult.EXIT_TIMEOUT
            else -> runCatching { process.exitValue() }.getOrDefault(-1)
        }
        val stdout = if (markerIndex >= 0) raw.substring(0, markerIndex) else raw
        val duration = SystemClock.elapsedRealtime() - began

        if (!finished) {
            ShellResult(command, stdout.trimEnd(), "Timed out after ${timeoutMs / 1000}s", ShellResult.EXIT_TIMEOUT, duration)
        } else {
            ShellResult(command, stdout.trimEnd(), "", exitCode, duration)
        }
    }
}
