package dev.modifyx.core

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import rikka.shizuku.Shizuku

/** A real `su -c <command>` shell. */
class RootShell(private val suBinary: String = "su") : Shell {

    override val kind: ShellKind = ShellKind.ROOT
    override val label: String = "Root (su)"

    override suspend fun exec(command: String, timeoutMs: Long): ShellResult =
        ProcessRunner.exec(ShellKind.ROOT, command, timeoutMs) {
            ProcessBuilder(suBinary, "-c", ProcessRunner.wrap(command))
                .redirectErrorStream(true)
                .start()
        }
}

/**
 * A shell running as the `shell` user through Shizuku, no root required.
 *
 * Shizuku starts [UserService] in a process that carries the shell UID; every command is
 * executed there and the exit code plus output are returned over AIDL.
 */
class ShizukuShell(context: Context) : Shell {

    override val kind: ShellKind = ShellKind.SHIZUKU
    override val label: String = "Shizuku (adb shell)"

    private val args = Shizuku.UserServiceArgs(
        ComponentName(context.applicationContext, UserService::class.java),
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(false)
        .version(UserService.SERVICE_VERSION)

    @Volatile
    private var service: IUserService? = null

    @Volatile
    private var binding = false

    @Volatile
    private var ready = CompletableDeferred<IUserService>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val remote = IUserService.Stub.asInterface(binder)
            service = remote
            if (!ready.isCompleted) ready.complete(remote)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            binding = false
            ready = CompletableDeferred()
        }

        override fun onBindingDied(name: ComponentName?) {
            service = null
            binding = false
            ready = CompletableDeferred()
        }
    }

    override suspend fun exec(command: String, timeoutMs: Long): ShellResult {
        val started = SystemClock.elapsedRealtime()
        val remote = ensureService() ?: return ShellResult(
            command = command,
            stdout = "",
            stderr = when {
                !ShizukuBridge.isBinderAlive() -> "Shizuku is not running."
                !ShizukuBridge.isGranted() -> "Shizuku permission has not been granted to Modify-X."
                else -> "The Shizuku shell service did not start."
            },
            exitCode = 127,
            durationMs = SystemClock.elapsedRealtime() - started,
        )

        val raw = try {
            withContext(Dispatchers.IO) { remote.exec(command, timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
        } catch (t: Throwable) {
            return ShellResult(
                command = command,
                stdout = "",
                stderr = t.message ?: "Shizuku shell call failed",
                exitCode = 127,
                durationMs = SystemClock.elapsedRealtime() - started,
            )
        }

        val json = runCatching { JSONObject(raw.orEmpty()) }.getOrNull()
            ?: return ShellResult(command, raw.orEmpty(), "", -1, SystemClock.elapsedRealtime() - started)

        val exit = json.optInt("exit", -1)
        return ShellResult(
            command = command,
            stdout = json.optString("out"),
            stderr = if (json.optBoolean("timeout")) "Timed out after ${timeoutMs / 1000}s" else "",
            exitCode = exit,
            durationMs = SystemClock.elapsedRealtime() - started,
        )
    }

    private suspend fun ensureService(): IUserService? {
        service?.let { return it }
        if (!ShizukuBridge.isBinderAlive() || !ShizukuBridge.isGranted()) return null

        synchronized(this) {
            if (!binding) {
                binding = ShizukuBridge.bindUserService(args, connection)
            }
        }
        if (!binding) return null

        return withTimeoutOrNull(8_000) { ready.await() } ?: service
    }

    fun release() {
        if (binding) {
            ShizukuBridge.unbindUserService(args, connection)
            binding = false
        }
    }
}

/**
 * Runs commands as the app's own UID. Cannot modify packages, but read-only probing
 * (`getprop`, `dumpsys` subsets) still works and keeps the console honest.
 */
class LocalShell : Shell {

    override val kind: ShellKind = ShellKind.LOCAL
    override val label: String = "Unprivileged"

    override suspend fun exec(command: String, timeoutMs: Long): ShellResult =
        ProcessRunner.exec(ShellKind.LOCAL, command, timeoutMs) {
            ProcessBuilder("/system/bin/sh", "-c", ProcessRunner.wrap(command))
                .redirectErrorStream(true)
                .start()
        }
}
