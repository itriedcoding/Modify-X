package dev.modifyx.core

import dev.modifyx.core.adb.AdbClient

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
 * Commands executed by the phone's own adbd as the `shell` user, through this app's
 * built-in ADB client. This is the no-root path and needs no third-party app.
 */
class AdbShell(private val client: AdbClient) : Shell {

    override val kind: ShellKind = ShellKind.ADB
    override val label: String = "ADB bridge"

    override suspend fun exec(command: String, timeoutMs: Long): ShellResult =
        client.shell(ProcessRunner.wrap(command), timeoutMs)
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
