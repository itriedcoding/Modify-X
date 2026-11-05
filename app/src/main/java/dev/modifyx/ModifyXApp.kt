package dev.modifyx

import android.app.Application
import android.content.Context
import dev.modifyx.data.CleanupEngine
import dev.modifyx.data.HistoryStore
import dev.modifyx.data.SettingsStore
import dev.modifyx.data.TreeScanner

class AppContainer(context: Context) {
    private val app = context.applicationContext
    val settings = SettingsStore(app)
    val history = HistoryStore(app)
    val scanner = TreeScanner(app.contentResolver)
    val cleaner = CleanupEngine(app.contentResolver)
}

class ModifyXApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer
    get() = (applicationContext as ModifyXApp).container
