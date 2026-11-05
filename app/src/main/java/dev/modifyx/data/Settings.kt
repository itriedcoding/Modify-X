package dev.modifyx.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val themeId: String = ThemeId.DYNAMIC,
    val customThemes: List<CustomTheme> = emptyList(),
    val amoled: Boolean = false,
    val onboardingDone: Boolean = false,
    val scanRoots: List<String> = emptyList(),
    val includeHidden: Boolean = false,
    val minDuplicateMb: Long = 0L,
    val largeFileMb: Long = 100L,
    val confirmDeletes: Boolean = true,
    val disabledRules: Set<String> = emptySet(),
    val autoUpdateCheck: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    val lastScanAt: Long = 0L,
    val lastScanBytes: Long = 0L,
) {
    val minDuplicateBytes: Long get() = col(minDuplicateMb) * 1024 * 1024
    val largeFileBytes: Long get() = col(largeFileMb) * 1024 * 1024
    val enabledRuleIds: Set<String> get() = JunkRules.all.map { it.id }.toSet() - disabledRules

    private fun col(value: Long): Long = value.coerceIn(0L, 4096L)
}

private val Context.cleanerDataStore: DataStore<Preferences> by preferencesDataStore(name = "modifyx_cleaner")

class SettingsStore(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val themeId = stringPreferencesKey("theme_id")
        val customThemes = stringPreferencesKey("custom_themes")
        val amoled = booleanPreferencesKey("amoled")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val roots = stringPreferencesKey("scan_roots")
        val includeHidden = booleanPreferencesKey("include_hidden")
        val minDuplicateMb = longPreferencesKey("min_duplicate_mb")
        val largeFileMb = longPreferencesKey("large_file_mb")
        val confirmDeletes = booleanPreferencesKey("confirm_deletes")
        val disabledRules = stringPreferencesKey("disabled_rules")
        val autoUpdateCheck = booleanPreferencesKey("auto_update_check")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val lastScanAt = longPreferencesKey("last_scan_at")
        val lastScanBytes = longPreferencesKey("last_scan_bytes")
    }

    val settings: Flow<AppSettings> = context.cleanerDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            AppSettings(
                themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.theme] } ?: ThemeMode.SYSTEM,
                dynamicColor = prefs[Keys.dynamicColor] ?: true,
                themeId = prefs[Keys.themeId] ?: ThemeId.DYNAMIC,
                customThemes = CustomTheme.fromJson(prefs[Keys.customThemes]),
                amoled = prefs[Keys.amoled] ?: false,
                onboardingDone = prefs[Keys.onboarding] ?: false,
                scanRoots = prefs[Keys.roots].orEmpty().split('\n').filter { it.isNotBlank() },
                includeHidden = prefs[Keys.includeHidden] ?: false,
                minDuplicateMb = prefs[Keys.minDuplicateMb] ?: 0L,
                largeFileMb = prefs[Keys.largeFileMb] ?: 100L,
                confirmDeletes = prefs[Keys.confirmDeletes] ?: true,
                disabledRules = prefs[Keys.disabledRules].orEmpty().split('\n').filter { it.isNotBlank() }.toSet(),
                autoUpdateCheck = prefs[Keys.autoUpdateCheck] ?: true,
                lastUpdateCheck = prefs[Keys.lastUpdateCheck] ?: 0L,
                lastScanAt = prefs[Keys.lastScanAt] ?: 0L,
                lastScanBytes = prefs[Keys.lastScanBytes] ?: 0L,
            )
        }

    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.dynamicColor] = enabled }
    suspend fun setAmoled(enabled: Boolean) = edit { it[Keys.amoled] = enabled }
    suspend fun setThemeId(id: String) = edit { it[Keys.themeId] = id }
    suspend fun setIncludeHidden(enabled: Boolean) = edit { it[Keys.includeHidden] = enabled }
    suspend fun setMinDuplicateMb(mb: Long) = edit { it[Keys.minDuplicateMb] = mb.coerceIn(0L, 1024L) }
    suspend fun setLargeFileMb(mb: Long) = edit { it[Keys.largeFileMb] = mb.coerceIn(1L, 4096L) }
    suspend fun setConfirmDeletes(enabled: Boolean) = edit { it[Keys.confirmDeletes] = enabled }
    suspend fun setAutoUpdateCheck(enabled: Boolean) = edit { it[Keys.autoUpdateCheck] = enabled }
    suspend fun setLastUpdateCheck(timestamp: Long) = edit { it[Keys.lastUpdateCheck] = timestamp }
    suspend fun setOnboardingDone(done: Boolean) = edit { it[Keys.onboarding] = done }

    suspend fun setLastScan(timestamp: Long, bytes: Long) = edit {
        it[Keys.lastScanAt] = timestamp
        it[Keys.lastScanBytes] = bytes
    }

    suspend fun setRuleEnabled(ruleId: String, enabled: Boolean) = edit { prefs ->
        val disabled = prefs[Keys.disabledRules].orEmpty().split('\n').filter { it.isNotBlank() }.toMutableSet()
        if (enabled) disabled.remove(ruleId) else disabled.add(ruleId)
        prefs[Keys.disabledRules] = disabled.joinToString("\n")
    }

    suspend fun addScanRoot(uri: String) = edit { prefs ->
        val roots = prefs[Keys.roots].orEmpty().split('\n').filter { it.isNotBlank() }.toMutableList()
        if (uri !in roots) roots.add(uri)
        prefs[Keys.roots] = roots.joinToString("\n")
    }

    suspend fun removeScanRoot(uri: String) = edit { prefs ->
        val roots = prefs[Keys.roots].orEmpty().split('\n').filter { it.isNotBlank() && it != uri }
        prefs[Keys.roots] = roots.joinToString("\n")
    }

    suspend fun saveCustomTheme(theme: CustomTheme) = edit { prefs ->
        val existing = CustomTheme.fromJson(prefs[Keys.customThemes])
        prefs[Keys.customThemes] = CustomTheme.toJson(existing.filterNot { it.id == theme.id } + theme)
        prefs[Keys.themeId] = ThemeId.custom(theme.id)
    }

    suspend fun deleteCustomTheme(id: String) = edit { prefs ->
        val existing = CustomTheme.fromJson(prefs[Keys.customThemes])
        prefs[Keys.customThemes] = CustomTheme.toJson(existing.filterNot { it.id == id })
        if (prefs[Keys.themeId] == ThemeId.custom(id)) prefs[Keys.themeId] = ThemeId.MATERIAL
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.cleanerDataStore.edit { block(it) }
    }
}
