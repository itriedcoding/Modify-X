package dev.modifyx.core

import android.content.Context
import dev.modifyx.core.adb.AdbClient
import dev.modifyx.core.adb.AdbKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class PrivilegeMode { NONE, ROOT, ADB, LOCAL }

data class PrivilegeState(
    val mode: PrivilegeMode = PrivilegeMode.NONE,
    val rootAvailable: Boolean = false,
    val adbConnected: Boolean = false,
    val adbPort: Int = 5555,
    val adbError: String? = null,
    val adbKeyFingerprint: String = "",
    val suBinary: String? = null,
    val detecting: Boolean = false,
    val checked: Boolean = false,
    val lastError: String? = null,
) {
    val canModify: Boolean get() = mode == PrivilegeMode.ROOT || mode == PrivilegeMode.ADB
    val summary: String
        get() = when (mode) {
            PrivilegeMode.ROOT -> "Root shell active"
            PrivilegeMode.ADB -> "ADB bridge active"
            PrivilegeMode.LOCAL -> "Unprivileged"
            PrivilegeMode.NONE -> "Detecting…"
        }
}

/**
 * Owns privilege detection and hands out the shell every subsystem uses.
 *
 * Two backends are ours: `su -c` on rooted devices, and a built-in ADB client that talks
 * to the phone's own adbd as the `shell` user. Both are probed for real; when neither is
 * available the app says so and stays read-only.
 */
object PrivilegeManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(PrivilegeState())
    val state: StateFlow<PrivilegeState> = _state.asStateFlow()

    private var rootShell: RootShell = RootShell("su")
    private val localShell = LocalShell()

    private var keys: AdbKeys? = null
    private var adbClient: AdbClient? = null
    private var appContext: Context? = null
    private var initialized = false

    fun init(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        initialized = true
        val adbKeys = AdbKeys(context.applicationContext)
        keys = adbKeys
        _state.value = _state.value.copy(adbKeyFingerprint = runCatching { adbKeys.fingerprint() }.getOrDefault(""))
        scope.launch { refresh() }
    }

    /** Shell that should be used for privileged work right now. */
    fun shell(): Shell {
        val client = adbClient
        return when (_state.value.mode) {
            PrivilegeMode.ROOT -> rootShell
            PrivilegeMode.ADB -> if (client != null) AdbShell(client) else localShell
            PrivilegeMode.LOCAL, PrivilegeMode.NONE -> localShell
        }
    }

    fun canModify(): Boolean = _state.value.canModify

    fun setAdbPort(port: Int) {
        val clean = port.coerceIn(1, 65_535)
        if (clean == _state.value.adbPort) return
        adbClient?.close()
        adbClient = null
        _state.value = _state.value.copy(
            adbPort = clean,
            adbConnected = false,
            mode = if (_state.value.rootAvailable) PrivilegeMode.ROOT else PrivilegeMode.LOCAL,
        )
    }

    /** Connects to adbd and reports the reason when it fails. */
    suspend fun connectAdb(port: Int = _state.value.adbPort): String? = mutex.withLock {
        val error = connectInternal(port.coerceIn(1, 65_535))
        _state.value = _state.value.copy(
            adbPort = port.coerceIn(1, 65_535),
            adbConnected = error == null,
            adbError = error,
            mode = when {
                _state.value.rootAvailable -> PrivilegeMode.ROOT
                error == null -> PrivilegeMode.ADB
                else -> PrivilegeMode.LOCAL
            },
            checked = true,
            detecting = false,
        )
        error
    }

    fun disconnectAdb() {
        adbClient?.close()
        adbClient = null
        _state.value = _state.value.copy(
            adbConnected = false,
            adbError = null,
            mode = if (_state.value.rootAvailable) PrivilegeMode.ROOT else PrivilegeMode.LOCAL,
        )
    }

    suspend fun refresh() = mutex.withLock {
        _state.value = _state.value.copy(detecting = true, lastError = null)

        var rootOk = false
        var suPath: String? = null
        var rootError: String? = null
        for (candidate in SU_CANDIDATES) {
            val probe = withContext(Dispatchers.IO) { RootShell(candidate).exec("id -u", timeoutMs = 6_000) }
            if (probe.success && probe.stdout.trim() == "0") {
                rootOk = true
                suPath = candidate
                rootShell = RootShell(candidate)
                break
            }
            if (probe.exitCode != 127 && probe.stderr.isBlank()) rootError = probe.output.take(200)
        }

        val adbError = if (rootOk) null else connectInternal(_state.value.adbPort)

        _state.value = _state.value.copy(
            mode = when {
                rootOk -> PrivilegeMode.ROOT
                adbError == null -> PrivilegeMode.ADB
                else -> PrivilegeMode.LOCAL
            },
            rootAvailable = rootOk,
            adbConnected = adbError == null,
            adbError = adbError,
            adbKeyFingerprint = keys?.let { runCatching { it.fingerprint() }.getOrDefault("") }
                ?: _state.value.adbKeyFingerprint,
            suBinary = suPath,
            detecting = false,
            checked = true,
            lastError = if (rootOk || adbError == null) null else rootError ?: adbError,
        )
    }

    private fun connectInternal(port: Int): String? {
        val context = appContext ?: return "No application context."
        val adbKeys = keys ?: AdbKeys(context).also { keys = it }
        adbClient?.close()
        val client = AdbClient(port = port, keys = adbKeys)
        val error = client.connect()
        if (error == null) {
            adbClient = client
            return null
        }
        adbClient = null
        return error
    }

    private val SU_CANDIDATES = listOf("su", "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su")
}
