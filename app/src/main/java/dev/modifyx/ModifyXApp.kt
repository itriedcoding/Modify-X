package dev.modifyx

import android.app.Application
import android.content.Context
import dev.modifyx.core.PrivilegeManager
import dev.modifyx.data.ActionEngine
import dev.modifyx.data.ActionRunner
import dev.modifyx.data.ApkExporter
import dev.modifyx.data.AppControls
import dev.modifyx.data.BackupManager
import dev.modifyx.data.HistoryStore
import dev.modifyx.data.PackageRepository
import dev.modifyx.data.ProfileStore
import dev.modifyx.data.SettingsStore

class AppContainer(context: Context) {
    val repository = PackageRepository(context)
    val history = HistoryStore(context)
    val profiles = ProfileStore(context)
    val settings = SettingsStore(context)
    val backups = BackupManager(context)
    val engine = ActionEngine()
    val controls = AppControls(context, repository)
    val exporter = ApkExporter()
    val runner = ActionRunner(engine, history, settings)
}

class ModifyXApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PrivilegeManager.init(this)
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ModifyXApp).container
