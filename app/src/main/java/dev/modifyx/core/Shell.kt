package dev.modifyx.core

/** Which privilege backend produced a command result. */
enum class ShellKind { ROOT, SHIZUKU, LOCAL }

/**
 * Outcome of one shell command. [stderr] is normally empty because both streams are
 * merged inside the wrapped command; [exitCode] is the real exit status of the command.
 */
data class ShellResult(
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
) {
    val success: Boolean get() = exitCode == 0
    val output: String get() = (stdout + stderr).trim()

    companion object {
        const val EXIT_TIMEOUT = -9
    }
}

interface Shell {
    val kind: ShellKind
    val label: String
    suspend fun exec(command: String, timeoutMs: Long = 60_000L): ShellResult
}
