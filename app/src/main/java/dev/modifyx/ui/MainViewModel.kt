package dev.modifyx.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.modifyx.container
import dev.modifyx.core.UpdateChecker
import dev.modifyx.data.AppSettings
import dev.modifyx.data.AutoScanPolicy
import dev.modifyx.data.CleanupReport
import dev.modifyx.data.CustomTheme
import dev.modifyx.data.DeleteTarget
import dev.modifyx.data.DuplicateGroup
import dev.modifyx.data.EmptyFolder
import dev.modifyx.data.ScanResult
import dev.modifyx.data.ScanRoot
import dev.modifyx.data.ScannedFile
import dev.modifyx.data.ThemeMode
import dev.modifyx.data.TreeScanner
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScanProgress(
    val phase: TreeScanner.Phase,
    val files: Int,
    val bytes: Long,
    val current: String,
)

private const val AUTO_UPDATE_INTERVAL_MS = 12L * 60 * 60 * 1000
private const val AUTO_SCAN_GUARD_MS = 60_000L

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.container
    val resolver = application.contentResolver

    val settings = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val history = container.history.records

    var roots by mutableStateOf<List<ScanRoot>>(emptyList())
        private set
    var scan by mutableStateOf<ScanResult?>(null)
        private set
    var scanning by mutableStateOf(false)
        private set
    var progress by mutableStateOf<ScanProgress?>(null)
        private set
    var busy by mutableStateOf<Busy?>(null)
        private set
    var notice by mutableStateOf<String?>(null)

    var junkSelection by mutableStateOf<Set<String>>(emptySet())
        private set
    var duplicateSelection by mutableStateOf<Set<String>>(emptySet())
        private set
    var largeSelection by mutableStateOf<Set<String>>(emptySet())
        private set
    var folderSelection by mutableStateOf<Set<String>>(emptySet())
        private set

    var update by mutableStateOf(UpdateState())
        private set

    private val cancelRequested = AtomicBoolean(false)
    private var lastAutoScanAttempt = 0L

    init {
        viewModelScope.launch {
            container.history.ensureLoaded()
            val stored = container.settings.settings.first()
            refreshRoots(stored.scanRoots)
            val stale = System.currentTimeMillis() - stored.lastUpdateCheck > AUTO_UPDATE_INTERVAL_MS
            if (stored.autoUpdateCheck && stale) checkForUpdates()
        }
    }

    // --- folders ---

    fun addRoot(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val persisted = runCatching { resolver.takePersistableUriPermission(uri, flags) }.isSuccess
        viewModelScope.launch {
            container.settings.addScanRoot(uri.toString())
            val current = container.settings.settings.first()
            refreshRoots(current.scanRoots)
            notice = if (persisted) {
                "Folder added. Its files are readable and deletable while this grant lasts."
            } else {
                "Folder added, but Android did not persist the permission. Re-add it if a scan fails."
            }
            if (current.onboardingDone && current.autoScan) startScan(auto = true)
        }
    }

    fun removeRoot(root: ScanRoot) {
        runCatching {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                Uri.parse(root.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModelScope.launch {
            container.settings.removeScanRoot(root.uri)
            refreshRoots(container.settings.settings.first().scanRoots)
            notice = "${root.label} is no longer scanned."
        }
    }

    fun refreshRoots() = viewModelScope.launch {
        refreshRoots(container.settings.settings.first().scanRoots)
    }

    private fun refreshRoots(stored: List<String>) {
        roots = stored.mapNotNull { raw ->
            runCatching { container.scanner.describe(Uri.parse(raw)) }.getOrNull()
        }
    }

    // --- scanning ---

    fun startScan(auto: Boolean = false) {
        if (scanning) return
        val current = settings.value
        val granted = roots
        if (granted.isEmpty()) {
            notice = "Add at least one folder before scanning."
            return
        }
        cancelRequested.set(false)
        scanning = true
        progress = ScanProgress(TreeScanner.Phase.WALKING, 0, 0L, "Opening ${granted.size} folder(s)")
        viewModelScope.launch {
            val result = runCatching {
                container.scanner.scan(
                    roots = granted,
                    options = TreeScanner.Options(
                        includeHidden = current.includeHidden,
                        minDuplicateBytes = current.minDuplicateBytes,
                        enabledRuleIds = current.enabledRuleIds,
                    ),
                    onProgress = { update ->
                        viewModelScope.launch {
                            progress = ScanProgress(update.phase, update.files, update.bytes, update.current)
                        }
                    },
                    isCancelled = { cancelRequested.get() },
                )
            }.getOrElse { error ->
                notice = "Scan failed: ${error.message ?: "unknown error"}"
                null
            }
            scanning = false
            progress = null
            if (result != null) {
                scan = result
                junkSelection = result.junk.filter { it.safe }.map { it.file.uri }.toSet()
                duplicateSelection = result.duplicates.flatMap { it.copies }.map { it.uri }.toSet()
                largeSelection = emptySet()
                folderSelection = emptySet()
                container.settings.setLastScan(result.scannedAt, result.bytesScanned)
                notice = when {
                    result.cancelled -> "Scan cancelled. Partial results are shown."
                    auto -> "Auto-scan: ${result.files.size} files, ${formatBytes(result.reclaimable)} reclaimable."
                    else -> "Scanned ${result.files.size} files (${formatBytes(result.bytesScanned)}) in ${formatDuration(result.durationMs)}."
                }
            }
        }
    }

    /**
     * Automatic scan: runs when the app opens or returns to the foreground, provided a folder
     * is granted and the last result is older than the configured interval. No background
     * service is involved, so nothing scans while the app is closed.
     */
    fun autoScanIfDue() {
        if (scanning) return
        val current = settings.value
        if (!current.onboardingDone || roots.isEmpty()) return
        val last = scan?.scannedAt?.takeIf { it > 0L } ?: current.lastScanAt
        if (!AutoScanPolicy.isDue(last, System.currentTimeMillis(), current.autoScanHours, current.autoScan)) return
        val now = SystemClock.elapsedRealtime()
        if (lastAutoScanAttempt != 0L && now - lastAutoScanAttempt < AUTO_SCAN_GUARD_MS) return
        lastAutoScanAttempt = now
        startScan(auto = true)
    }

    fun cancelScan() {
        if (scanning) {
            cancelRequested.set(true)
            notice = "Stopping the scan after the current folder…"
        }
    }

    // --- selections ---

    fun toggleJunk(uri: String) {
        junkSelection = if (uri in junkSelection) junkSelection - uri else junkSelection + uri
    }

    fun selectJunk(safeOnly: Boolean) {
        val findings = scan?.junk ?: return
        junkSelection = findings.filter { !safeOnly || it.safe }.map { it.file.uri }.toSet()
    }

    fun clearJunkSelection() {
        junkSelection = emptySet()
    }

    fun toggleDuplicate(uri: String) {
        duplicateSelection = if (uri in duplicateSelection) duplicateSelection - uri else duplicateSelection + uri
    }

    fun selectDuplicateCopies() {
        duplicateSelection = scan?.duplicates?.flatMap { it.copies }?.map { it.uri }?.toSet() ?: emptySet()
    }

    fun clearDuplicateSelection() {
        duplicateSelection = emptySet()
    }

    fun toggleLarge(uri: String) {
        largeSelection = if (uri in largeSelection) largeSelection - uri else largeSelection + uri
    }

    fun selectLarge(threshold: Long) {
        largeSelection = scan?.largeFiles(threshold)?.map { it.uri }?.toSet() ?: emptySet()
    }

    fun clearLargeSelection() {
        largeSelection = emptySet()
    }

    fun toggleFolder(uri: String) {
        folderSelection = if (uri in folderSelection) folderSelection - uri else folderSelection + uri
    }

    fun selectAllFolders() {
        folderSelection = scan?.emptyFolders?.map { it.uri }?.toSet() ?: emptySet()
    }

    fun clearFolderSelection() {
        folderSelection = emptySet()
    }

    fun junkBytesSelected(): Long {
        val selected = junkSelection
        return scan?.junk?.filter { it.file.uri in selected }?.sumOf { it.file.size } ?: 0L
    }

    fun duplicateBytesSelected(): Long {
        val selected = duplicateSelection
        return scan?.duplicates.orEmpty()
            .flatMap { it.files }
            .filter { it.uri in selected }
            .distinctBy { it.uri }
            .sumOf { it.size }
    }

    fun largeBytesSelected(): Long {
        val selected = largeSelection
        return scan?.files?.filter { it.uri in selected }?.sumOf { it.size } ?: 0L
    }

    fun selectedJunkTargets(): List<DeleteTarget> {
        val selected = junkSelection
        return scan?.junk.orEmpty().filter { it.file.uri in selected }.map { it.file.toTarget() }
    }

    fun selectedDuplicateTargets(): List<DeleteTarget> {
        val selected = duplicateSelection
        return scan?.duplicates.orEmpty()
            .flatMap { it.files }
            .filter { it.uri in selected }
            .distinctBy { it.uri }
            .map { it.toTarget() }
    }

    fun selectedLargeTargets(): List<DeleteTarget> {
        val selected = largeSelection
        return scan?.files.orEmpty().filter { it.uri in selected }.map { it.toTarget() }
    }

    fun selectedFolderTargets(): List<DeleteTarget> {
        val selected = folderSelection
        return scan?.emptyFolders.orEmpty()
            .filter { it.uri in selected }
            .map { DeleteTarget(it.uri, it.name, 0L, directory = true) }
    }

    // --- cleanup ---

    fun cleanJunk() = clean(selectedJunkTargets(), "Junk files")
    fun cleanDuplicates() = clean(selectedDuplicateTargets(), "Duplicate files")
    fun cleanLargeFiles() = clean(selectedLargeTargets(), "Large files")
    fun cleanEmptyFolders() = clean(selectedFolderTargets(), "Empty folders")

    private fun clean(targets: List<DeleteTarget>, label: String) {
        if (targets.isEmpty()) {
            notice = "Nothing is selected."
            return
        }
        viewModelScope.launch {
            busy = Busy(label, 0, targets.size, targets.first().name)
            val report: CleanupReport = runCatching {
                container.cleaner.delete(targets, label) { done, total, name ->
                    busy = Busy(label, done, total, name)
                }
            }.getOrElse { error ->
                busy = null
                notice = "Cleanup failed: ${error.message ?: "unknown error"}"
                return@launch
            }
            busy = null
            container.history.append(report)
            prune(report)
            notice = when {
                report.deleted == 0 -> "Nothing was deleted. ${report.failed} target(s) were refused by Android."
                report.failed > 0 -> "${report.deleted} deleted, ${report.failed} refused. ${formatBytes(report.bytesFreed)} freed."
                else -> "${report.deleted} deleted · ${formatBytes(report.bytesFreed)} freed."
            }
        }
    }

    /** Removes confirmed deletions from the current results so the UI cannot keep offering them. */
    private fun prune(report: CleanupReport) {
        val gone = report.outcomes.filter { it.success }.map { it.uri }.toSet()
        if (gone.isEmpty()) return
        val current = scan ?: return
        scan = current.copy(
            files = current.files.filterNot { it.uri in gone },
            junk = current.junk.filterNot { it.file.uri in gone },
            duplicates = current.duplicates.mapNotNull { group ->
                val left = group.files.filterNot { it.uri in gone }
                if (left.size < 2) null else group.copy(files = left)
            },
            emptyFolders = current.emptyFolders.filterNot { it.uri in gone },
        )
        junkSelection = junkSelection - gone
        duplicateSelection = duplicateSelection - gone
        largeSelection = largeSelection - gone
        folderSelection = folderSelection - gone
    }

    private fun ScannedFile.toTarget() = DeleteTarget(uri = uri, name = name, size = size)

    fun consumeNotice() {
        notice = null
    }

    // --- onboarding ---

    fun finishOnboarding(scanNow: Boolean) {
        viewModelScope.launch { container.settings.setOnboardingDone(true) }
        if (scanNow) startScan()
    }

    fun replayOnboarding() = viewModelScope.launch { container.settings.setOnboardingDone(false) }

    // --- updates ---

    fun currentVersionName(): String = runCatching {
        getApplication<Application>().packageManager
            .getPackageInfo(getApplication<Application>().packageName, 0).versionName
    }.getOrNull() ?: ""

    fun checkForUpdates(notify: Boolean = false) {
        if (update.checking || update.downloading) return
        viewModelScope.launch {
            val current = currentVersionName()
            update = update.copy(currentVersion = current, checking = true, message = null)
            val result = runCatching { UpdateChecker.latest() }
            val info = result.getOrNull()
            update = when {
                result.isFailure -> UpdateState(
                    currentVersion = current,
                    message = "Update check failed: ${result.exceptionOrNull()?.message ?: "network error"}",
                )
                info == null -> UpdateState(currentVersion = current, message = "The release page could not be read.")
                UpdateChecker.isNewer(info.version, current) -> UpdateState(
                    currentVersion = current,
                    available = info,
                    message = "Version ${info.version} is available.",
                )
                else -> UpdateState(currentVersion = current, message = "Modify-X $current is the newest release.")
            }
            container.settings.setLastUpdateCheck(System.currentTimeMillis())
            if (notify) notice = update.message
        }
    }

    fun downloadAndInstallUpdate() {
        val info = update.available ?: return
        if (update.downloading) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            update = update.copy(downloading = true, progress = 0, message = null)
            val file = runCatching {
                UpdateChecker.download(app, info) { percent -> update = update.copy(progress = percent) }
            }.getOrElse { error ->
                update = update.copy(downloading = false, message = "Download failed: ${error.message}")
                return@launch
            }
            val problem = UpdateChecker.verify(app, file, currentVersionName())
            if (problem != null) {
                update = update.copy(downloading = false, message = "Update rejected: $problem")
                return@launch
            }
            val result = UpdateChecker.install(app, file)
            update = update.copy(
                downloading = false,
                available = if (result.installed) null else info,
                message = result.message,
            )
            notice = result.message
        }
    }

    // --- settings ---

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { container.settings.setTheme(mode) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { container.settings.setDynamicColor(value) }
    fun setAmoled(value: Boolean) = viewModelScope.launch { container.settings.setAmoled(value) }
    fun setThemeId(id: String) = viewModelScope.launch { container.settings.setThemeId(id) }
    fun setIncludeHidden(value: Boolean) = viewModelScope.launch { container.settings.setIncludeHidden(value) }
    fun setMinDuplicateMb(value: Long) = viewModelScope.launch { container.settings.setMinDuplicateMb(value) }
    fun setLargeFileMb(value: Long) = viewModelScope.launch { container.settings.setLargeFileMb(value) }
    fun setConfirmDeletes(value: Boolean) = viewModelScope.launch { container.settings.setConfirmDeletes(value) }
    fun setAutoScan(value: Boolean) = viewModelScope.launch { container.settings.setAutoScan(value) }
    fun setAutoScanHours(value: Long) = viewModelScope.launch { container.settings.setAutoScanHours(value) }
    fun setAutoUpdateCheck(value: Boolean) = viewModelScope.launch { container.settings.setAutoUpdateCheck(value) }
    fun setRuleEnabled(id: String, value: Boolean) = viewModelScope.launch { container.settings.setRuleEnabled(id, value) }

    fun saveCustomTheme(name: String, seed: Long) {
        viewModelScope.launch {
            val theme: CustomTheme = CustomTheme.create(name, seed)
            container.settings.saveCustomTheme(theme)
            notice = "Theme \"${theme.name}\" saved and applied."
        }
    }

    fun deleteCustomTheme(id: String) = viewModelScope.launch { container.settings.deleteCustomTheme(id) }

    fun clearHistory() = viewModelScope.launch {
        container.history.clear()
        notice = "Cleanup history cleared."
    }

    fun duplicateGroupsFor(category: dev.modifyx.data.FileCategory?): List<DuplicateGroup> {
        val groups = scan?.duplicates.orEmpty()
        return if (category == null) groups else groups.filter { it.category == category }
    }

    fun emptyFolders(): List<EmptyFolder> = scan?.emptyFolders.orEmpty()
}
