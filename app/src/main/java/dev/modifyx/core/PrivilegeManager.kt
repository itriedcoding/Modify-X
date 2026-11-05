package dev.modifyx.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

enum class PrivilegeMode { NONE, ROOT, SHIZUKU, LOCAL }

data class PrivilegeState(
    val mode: PrivilegeMode = PrivilegeMode.NONE,
    val rootAvailable: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val shizukuVersion: Int = -1,
    val suBinary: String? = null,
    val detecting: Boolean = false,
    val checked: Boolean = false,
    val lastError: String? = null,
) {
    val canModify: Boolean get() = mode == PrivilegeMode.ROOT || mode == PrivilegeMode.SHIZUKU
    val summary: String
        get() = when (mode) {
            PrivilegeMode.ROOT -> "Root shell active"
            PrivilegeMode.SHIZUKU -> "Shizuku shell active"
            PrivilegeMode.LOCAL -> "Unprivileged"
            PrivilegeMode.NONE -> "Detecting…"
        }
}

/**
 * Owns privilege detection and hands out the shell every subsystem uses.
 * Detection performs real probes: `su -c id -u` and a live Shizuku binder check.
 */
object PrivilegeManager {

    const val SHIZUKU_REQUEST_CODE = 4213

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(PrivilegeState())
    val state: StateFlow<PrivilegeState> = _state.asStateFlow()

    private var rootShell: RootShell = RootShell("su")
    private var shizukuShell: ShizukuShell? = null
    private val localShell = LocalShell()

    private var appContext: Context? = null
    private var initialized = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            _state.value = _state.value.copy(shizukuGranted = grantResult == 0)
            scope.launch { refresh() }
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        initialized = true
        shizukuShell = ShizukuShell(context.applicationContext)
        ShizukuBridge.addPermissionListener(permissionListener)
        ShizukuBridge.addBinderListener { scope.launch { refresh() } }
        ShizukuBridge.addBinderDeadListener {
            _state.value = _state.value.copy(shizukuRunning = false, shizukuGranted = false, shizukuInstalled = true)
            scope.launch { refresh() }
        }
        scope.launch { refresh() }
    }

    /** Shell that should be used for privileged work right now. */
    fun shell(): Shell = when (_state.value.mode) {
        PrivilegeMode.ROOT -> rootShell
        PrivilegeMode.SHIZUKU -> shizukuShell ?: localShell
        PrivilegeMode.LOCAL, PrivilegeMode.NONE -> localShell
    }

    fun canModify(): Boolean = _state.value.canModify

    fun requestShizukuPermission(): Boolean {
        val started = ShizukuBridge.requestPermission(SHIZUKU_REQUEST_CODE)
        if (!started) {
            scope.launch { refresh() }
        }
        return started
    }

    suspend fun refresh() = mutex.withLock {
        _state.value = _state.value.copy(detecting = true, lastError = null)

        var rootOk = false
        var suPath: String? = null
        var rootError: String? = null
        for (candidate in SU_CANDIDATES) {
            val probe = RootShell(candidate).exec("id -u", timeoutMs = 6_000)
            if (probe.success && probe.stdout.trim() == "0") {
                rootOk = true
                suPath = candidate
                rootShell = RootShell(candidate)
                break
            }
            if (probe.exitCode != 127 && probe.stderr.isBlank()) {
                rootError = probe.output.take(200)
            }
        }

        val context = appContext
        val shizukuInstalled = context?.let { ctx ->
            runCatching { ctx.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) != null }.getOrDefault(false)
        } ?: false
        val shizukuRunning = ShizukuBridge.isBinderAlive()
        val shizukuGranted = shizukuRunning && ShizukuBridge.isGranted()

        val mode = when {
            rootOk -> PrivilegeMode.ROOT
            shizukuGranted -> PrivilegeMode.SHIZUKU
            else -> PrivilegeMode.LOCAL
        }

        _state.value = PrivilegeState(
            mode = mode,
            rootAvailable = rootOk,
            shizukuInstalled = shizukuInstalled || shizukuRunning,
            shizukuRunning = shizukuRunning,
            shizukuGranted = shizukuGranted,
            shizukuVersion = ShizukuBridge.version(),
            suBinary = suPath,
            detecting = false,
            checked = true,
            lastError = if (rootOk || shizukuGranted) null else rootError,
        )
    }

    private val SU_CANDIDATES = listOf("su", "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su")
}
