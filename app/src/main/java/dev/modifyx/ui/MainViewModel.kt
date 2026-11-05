package dev.modifyx.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.modifyx.container
import dev.modifyx.core.BootProfileReceiver
import dev.modifyx.core.PrivilegeManager
import dev.modifyx.core.ShizukuInstaller
import dev.modifyx.core.UpdateChecker
import dev.modifyx.data.AppEntry
import dev.modifyx.data.AppFilter
import dev.modifyx.data.AppSettings
import dev.modifyx.data.AppSnapshot
import dev.modifyx.data.ComponentRow
import dev.modifyx.data.CustomTheme
import dev.modifyx.data.UsageAccess
import dev.modifyx.data.StandbyBucket
import dev.modifyx.data.BackupDocument
import dev.modifyx.data.BackupManager
import dev.modifyx.data.BloatCatalog
import dev.modifyx.data.DebloatAction
import dev.modifyx.data.PackageRepository
import dev.modifyx.data.Profile
import dev.modifyx.data.RiskLevel
import dev.modifyx.data.SnapshotFile
import dev.modifyx.data.SortMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class Busy(val title: String, val done: Int, val total: Int, val current: String)

data class ConsoleState(
    val running: Boolean = false,
    val command: String = "",
    val output: String = "",
    val exitCode: Int? = null,
    val durationMs: Long = 0L,
)

private const val UNUSED_WINDOW_MS = 60L * 24 * 60 * 60 * 1000

data class UpdateState(
    val currentVersion: String = "",
    val checking: Boolean = false,
    val available: UpdateChecker.UpdateInfo? = null,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val message: String? = null,
)

data class ImportPreview(
    val document: BackupDocument,
    val restorable: Int,
    val reRemovable: Int,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.container
    val repository: PackageRepository = container.repository

    val settings = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val privileges = PrivilegeManager.state
    val history = container.history.records

    var snapshot by mutableStateOf(AppSnapshot())
        private set
    var loading by mutableStateOf(true)
        private set
    var busy by mutableStateOf<Busy?>(null)
        private set
    var notice by mutableStateOf<String?>(null)

    var query by mutableStateOf("")
        private set
    var selection by mutableStateOf<Set<String>>(emptySet())
        private set

    var details by mutableStateOf<dev.modifyx.data.AppDetails?>(null)
        private set
    var detailsLoading by mutableStateOf(false)
        private set

    var profiles by mutableStateOf<List<Profile>>(emptyList())
        private set
    var snapshots by mutableStateOf<List<SnapshotFile>>(emptyList())
        private set
    var importPreview by mutableStateOf<ImportPreview?>(null)
        private set

    var console by mutableStateOf(ConsoleState())
        private set

    var shizukuInstallBusy by mutableStateOf(false)
        private set
    var shizukuMessage by mutableStateOf<String?>(null)
        private set

    var components by mutableStateOf<List<ComponentRow>>(emptyList())
        private set
    var componentsLoading by mutableStateOf(false)
        private set
    var componentBusy by mutableStateOf<String?>(null)
        private set

    var standby by mutableStateOf<StandbyBucket?>(null)
        private set

    var update by mutableStateOf(UpdateState())
        private set
    var usageAccessGranted by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            container.history.ensureLoaded()
            container.profiles.ensureLoaded()
            refreshProfiles()
            refreshSnapshots()
        }
        refresh()
        refreshUsageAccess()
        viewModelScope.launch {
            val stored = container.settings.settings.first()
            val stale = System.currentTimeMillis() - stored.lastUpdateCheck > 12 * 60 * 60 * 1000L
            if (stored.autoUpdateCheck && stale) checkForUpdates()
        }
    }

    // --- loading ---

    fun refresh() {
        viewModelScope.launch {
            loading = true
            PrivilegeManager.refresh()
            snapshot = container.repository.loadSnapshot()
            refreshProfiles()
            loading = false
        }
    }

    fun detectPrivileges() {
        viewModelScope.launch { PrivilegeManager.refresh() }
    }

    fun requestShizukuPermission() {
        if (!PrivilegeManager.requestShizukuPermission()) {
            notice = "Shizuku is not running. Start the Shizuku service, then re-detect."
        }
    }

    // --- Shizuku setup ---

    fun installShizuku() {
        if (shizukuInstallBusy) return
        viewModelScope.launch {
            shizukuInstallBusy = true
            val result = ShizukuInstaller.install(getApplication())
            shizukuInstallBusy = false
            shizukuMessage = result.message
            PrivilegeManager.refresh()
        }
    }

    fun openShizuku() {
        if (!ShizukuInstaller.open(getApplication())) {
            shizukuMessage = "Shizuku is not installed yet, so there is nothing to open."
        }
    }

    fun dismissShizukuMessage() {
        shizukuMessage = null
    }

    private suspend fun reloadAfterChange() {
        snapshot = container.repository.loadSnapshot()
        refreshProfiles()
    }

    // --- list filtering ---

    fun updateQuery(value: String) {
        query = value
    }

    fun visibleApps(settings: AppSettings): List<AppEntry> {
        val needle = query.trim().lowercase()
        var sequence = snapshot.apps.asSequence()
        sequence = when (settings.filter) {
            AppFilter.ALL -> sequence
            AppFilter.SYSTEM -> sequence.filter { it.isSystem && it.installedForUser }
            AppFilter.USER -> sequence.filter { it.isUserApp && it.installedForUser }
            AppFilter.BLOAT -> sequence.filter { BloatCatalog.find(it.packageName) != null }
            AppFilter.REMOVED -> sequence.filter { it.removedForUser }
            AppFilter.DISABLED -> sequence.filter { it.disabled }
            AppFilter.UNUSED -> if (!usageAccessGranted) {
                emptySequence()
            } else {
                val cutoff = System.currentTimeMillis() - UNUSED_WINDOW_MS
                sequence.filter { it.installedForUser && (it.lastUsedAt == 0L || it.lastUsedAt < cutoff) }
            }
        }
        if (settings.showCatalogOnly) {
            sequence = sequence.filter { BloatCatalog.find(it.packageName) != null }
        }
        if (needle.isNotEmpty()) {
            sequence = sequence.filter {
                it.label.lowercase().contains(needle) || it.packageName.lowercase().contains(needle)
            }
        }
        val sorted = when (settings.sortMode) {
            SortMode.NAME -> sequence.sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
            SortMode.SIZE -> sequence.sortedByDescending { it.apkBytes }
            SortMode.INSTALLED -> sequence.sortedByDescending { it.firstInstallTime }
            SortMode.UPDATED -> sequence.sortedByDescending { it.lastUpdateTime }
        }
        return sorted.toList()
    }

    // --- selection ---

    fun toggleSelection(pkg: String) {
        selection = if (pkg in selection) selection - pkg else selection + pkg
    }

    fun clearSelection() {
        selection = emptySet()
    }

    fun selectAll(apps: List<AppEntry>) {
        selection = apps.map { it.packageName }.toSet()
    }

    // --- actions ---

    /** Why an action would be refused for this package, or null when it is allowed. */
    fun blockReason(app: AppEntry, settings: AppSettings): String? {
        if (BloatCatalog.isProtected(app.packageName) && !settings.allowProtected) {
            return "Core Android package. Enable the protected-package override in Settings to touch it."
        }
        val risk = BloatCatalog.find(app.packageName)?.risk
        if (risk == RiskLevel.RISKY && !settings.allowRisky) {
            return "The catalog marks this as risky. Enable risky actions in Settings to continue."
        }
        return null
    }

    fun applicableTargets(action: DebloatAction, apps: List<AppEntry>, settings: AppSettings): List<AppEntry> =
        apps.filter { app ->
            blockReason(app, settings) == null && when (action) {
                DebloatAction.UNINSTALL_FOR_USER -> app.installedForUser
                DebloatAction.RESTORE_FOR_USER -> app.removedForUser
                DebloatAction.DISABLE -> app.installedForUser && app.enabled
                DebloatAction.ENABLE -> app.installedForUser && !app.enabled
                DebloatAction.CLEAR_DATA, DebloatAction.FORCE_STOP -> app.installedForUser
                DebloatAction.UNINSTALL_FULL -> app.isUserApp && app.installedForUser
            }
        }

    fun runSingle(action: DebloatAction, app: AppEntry) {
        viewModelScope.launch {
            val result = container.runner.run(action, app)
            notice = "${app.label}: ${result.message}"
            reloadAfterChange()
        }
    }

    fun runBatch(action: DebloatAction, apps: List<AppEntry>) {
        if (apps.isEmpty()) {
            notice = "Nothing to do for ${action.title.lowercase()}."
            return
        }
        viewModelScope.launch {
            busy = Busy(action.title, 0, apps.size, apps.first().label)
            val summary = container.runner.runBatch(action, apps) { done, total, app ->
                busy = Busy(action.title, done, total, app.label)
            }
            busy = null
            selection = emptySet()
            val failed = summary.failures
            notice = if (failed == 0) {
                "${summary.successes} package${if (summary.successes == 1) "" else "s"} ${action.verbPast.lowercase()}."
            } else {
                "${summary.successes} succeeded, $failed failed. See History for details."
            }
            reloadAfterChange()
        }
    }

    fun undo(record: dev.modifyx.data.HistoryRecord) {
        viewModelScope.launch {
            val result = container.runner.undo(record)
            notice = result?.message ?: "This action cannot be undone."
            reloadAfterChange()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.history.clear()
            notice = "History cleared."
        }
    }

    fun consumeNotice() {
        notice = null
    }

    // --- details ---

    fun openDetails(pkg: String) {
        viewModelScope.launch {
            detailsLoading = true
            details = container.repository.loadDetails(pkg)
            detailsLoading = false
        }
    }

    fun closeDetails() {
        details = null
        components = emptyList()
        standby = null
    }

    // --- component, permission and background controls ---

    fun loadComponents(pkg: String) {
        viewModelScope.launch {
            componentsLoading = true
            components = container.controls.components(pkg)
            componentsLoading = false
        }
    }

    fun toggleComponent(pkg: String, component: String, enable: Boolean) {
        if (componentBusy != null) return
        viewModelScope.launch {
            componentBusy = component
            val result = container.controls.setComponent(pkg, component, enable)
            componentBusy = null
            notice = result.message
            components = container.controls.components(pkg)
            snapshot = container.repository.loadSnapshot()
        }
    }

    fun setPermission(pkg: String, permission: String, grant: Boolean) {
        viewModelScope.launch {
            val result = container.controls.setPermission(pkg, permission, grant)
            notice = result.message
            details = container.repository.loadDetails(pkg)
            snapshot = container.repository.loadSnapshot()
        }
    }

    fun loadStandby(pkg: String) {
        viewModelScope.launch { standby = container.controls.standbyBucket(pkg) }
    }

    fun setStandby(pkg: String, bucket: StandbyBucket) {
        viewModelScope.launch {
            val result = container.controls.setStandbyBucket(pkg, bucket)
            notice = result.message
            standby = container.controls.standbyBucket(pkg)
        }
    }

    fun entryFor(pkg: String): AppEntry? = snapshot.apps.firstOrNull { it.packageName == pkg }

    // --- console ---

    fun runCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            console = console.copy(running = true, command = trimmed)
            val result = PrivilegeManager.shell().exec(trimmed, 90_000)
            console = ConsoleState(
                running = false,
                command = trimmed,
                output = result.output.ifBlank { "(no output)" },
                exitCode = result.exitCode,
                durationMs = result.durationMs,
            )
        }
    }

    // --- profiles ---

    fun refreshProfiles() {
        val installed = snapshot.apps.map { it.packageName }.toSet()
        profiles = container.profiles.allProfiles(installed)
    }

    fun saveProfile(name: String, packages: Collection<String>) {
        val clean = packages.filter { it.isNotBlank() }.distinct()
        if (name.isBlank() || clean.isEmpty()) {
            notice = "A profile needs a name and at least one package."
            return
        }
        viewModelScope.launch {
            container.profiles.create(name.trim(), clean)
            refreshProfiles()
            notice = "Saved profile \"${name.trim()}\" with ${clean.size} packages."
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            container.profiles.delete(profile.id)
            refreshProfiles()
            notice = "Deleted \"${profile.name}\"."
        }
    }

    fun applyProfile(profile: Profile) {
        val targets = container.runner.plan(profile, snapshot.apps.associateBy { it.packageName })
        val settingsNow = settings.value
        val allowed = applicableTargets(DebloatAction.UNINSTALL_FOR_USER, targets, settingsNow)
        runBatch(DebloatAction.UNINSTALL_FOR_USER, allowed)
    }

    fun previewProfile(profile: Profile): List<AppEntry> =
        container.runner.plan(profile, snapshot.apps.associateBy { it.packageName })

    fun setAutoApply(enabled: Boolean) {
        viewModelScope.launch { container.settings.setAutoApply(enabled) }
    }

    fun setAutoApplyProfile(id: String?) {
        viewModelScope.launch { container.settings.setAutoApplyProfile(id) }
    }

    // --- usage stats, updates and APK export ---

    fun refreshUsageAccess() {
        usageAccessGranted = UsageAccess.hasAccess(getApplication())
    }

    fun openUsageAccessSettings() {
        UsageAccess.openSettings(getApplication())
    }

    fun exportApk(pkg: String) {
        viewModelScope.launch {
            val result = container.exporter.export(pkg)
            notice = result.message
        }
    }

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

    /** Queues the automation run now instead of waiting for the next reboot. */
    fun runAutomationNow() {
        BootProfileReceiver.enqueue(getApplication(), delaySeconds = 0L)
        notice = "Automation queued. Results appear in History."
    }

    // --- settings ---

    fun setTheme(mode: dev.modifyx.data.ThemeMode) = viewModelScope.launch { container.settings.setTheme(mode) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { container.settings.setDynamicColor(value) }
    fun setFilter(filter: AppFilter) = viewModelScope.launch { container.settings.setFilter(filter) }
    fun setSort(mode: SortMode) = viewModelScope.launch { container.settings.setSortMode(mode) }
    fun setAllowRisky(value: Boolean) = viewModelScope.launch { container.settings.setAllowRisky(value) }
    fun setAllowProtected(value: Boolean) = viewModelScope.launch { container.settings.setAllowProtected(value) }
    fun setConfirmDestructive(value: Boolean) = viewModelScope.launch { container.settings.setConfirmDestructive(value) }
    fun setCatalogOnly(value: Boolean) = viewModelScope.launch { container.settings.setCatalogOnly(value) }
    fun setThemeId(id: String) = viewModelScope.launch { container.settings.setThemeId(id) }
    fun setAmoled(value: Boolean) = viewModelScope.launch { container.settings.setAmoled(value) }
    fun setAutoUpdateCheck(value: Boolean) = viewModelScope.launch { container.settings.setAutoUpdateCheck(value) }

    fun saveCustomTheme(name: String, seed: Long) {
        viewModelScope.launch {
            val theme: CustomTheme = CustomTheme.create(name, seed)
            container.settings.saveCustomTheme(theme)
            notice = "Theme \"${theme.name}\" saved and applied."
        }
    }

    fun deleteCustomTheme(id: String) = viewModelScope.launch { container.settings.deleteCustomTheme(id) }

    // --- backups ---

    fun refreshSnapshots() {
        snapshots = container.backups.listLocal()
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val document = BackupManager.buildDocument(snapshot.apps)
            val payload = BackupManager.toJson(document)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(payload.toByteArray())
                    } ?: error("Could not open the selected file")
                }.isSuccess
            }
            notice = if (ok) {
                "Exported snapshot: ${document.entries.size} packages, ${document.removedCount} removed."
            } else {
                "Export failed. Check storage permissions for the chosen location."
            }
            refreshSnapshots()
        }
    }

    fun saveLocalSnapshot() {
        viewModelScope.launch {
            val document = BackupManager.buildDocument(snapshot.apps)
            withContext(Dispatchers.IO) { container.backups.saveLocal(document) }
            refreshSnapshots()
            notice = "Snapshot saved on the device."
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }.getOrNull()
            }
            if (text == null) {
                notice = "Could not read that file."
                return@launch
            }
            val document = runCatching { BackupManager.parse(text) }.getOrElse {
                notice = "Not a valid Modify-X backup: ${it.message}"
                return@launch
            }
            val restorable = BackupManager.restoreCandidates(document, snapshot.apps)
            val current = snapshot.apps.associateBy { it.packageName }
            val reRemovable = document.entries.count { it.isRemoved && current[it.packageName]?.installedForUser == true }
            importPreview = ImportPreview(document, restorable.size, reRemovable)
        }
    }

    fun cancelImport() {
        importPreview = null
    }

    fun applyImportedRemovals() {
        val preview = importPreview ?: return
        val current = snapshot.apps.associateBy { it.packageName }
        val targets = preview.document.entries
            .filter { it.isRemoved }
            .mapNotNull { entry -> current[entry.packageName]?.takeIf { it.installedForUser } }
        importPreview = null
        runBatch(DebloatAction.UNINSTALL_FOR_USER, applicableTargets(DebloatAction.UNINSTALL_FOR_USER, targets, settings.value))
    }

    fun restoreImportedPackages() {
        val preview = importPreview ?: return
        val installedNow = snapshot.apps.filter { it.installedForUser }.map { it.packageName }.toSet()
        val targets = preview.document.entries
            .filter { it.isRemoved && it.packageName !in installedNow }
            .map { entry ->
                AppEntry(
                    packageName = entry.packageName,
                    label = entry.label,
                    isSystem = entry.system,
                    isUpdatedSystem = false,
                    enabled = false,
                    installedForUser = false,
                    suspended = false,
                    versionName = null,
                    versionCode = 0,
                    targetSdk = 0,
                    minSdk = 0,
                    firstInstallTime = 0L,
                    lastUpdateTime = 0L,
                    apkBytes = 0L,
                    uid = -1,
                    launchable = false,
                    installer = null,
                )
            }
        importPreview = null
        runBatch(DebloatAction.RESTORE_FOR_USER, targets)
    }

    fun restoreSnapshot(file: SnapshotFile) {
        viewModelScope.launch {
            val document = runCatching { withContext(Dispatchers.IO) { container.backups.readLocal(file.file) } }.getOrNull()
            if (document == null) {
                notice = "That snapshot could not be read."
                return@launch
            }
            val current = snapshot.apps.associateBy { it.packageName }
            val restorable = BackupManager.restoreCandidates(document, snapshot.apps)
            val reRemovable = document.entries.count { it.isRemoved && current[it.packageName]?.installedForUser == true }
            importPreview = ImportPreview(document, restorable.size, reRemovable)
        }
    }

    fun deleteSnapshot(file: SnapshotFile) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.backups.deleteLocal(file.file) }
            refreshSnapshots()
        }
    }
}
