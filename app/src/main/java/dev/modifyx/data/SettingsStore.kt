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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class SortMode(val label: String) {
    NAME("Name"),
    SIZE("Size"),
    INSTALLED("Installed"),
    UPDATED("Updated"),
}

enum class AppFilter(val label: String) {
    ALL("All"),
    SYSTEM("System"),
    USER("User"),
    BLOAT("Bloat"),
    REMOVED("Removed"),
    DISABLED("Disabled"),
    UNUSED("Unused"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val showCatalogOnly: Boolean = false,
    val allowRisky: Boolean = false,
    val allowProtected: Boolean = false,
    val confirmDestructive: Boolean = true,
    val autoApplyEnabled: Boolean = false,
    val autoApplyProfileId: String? = null,
    val sortMode: SortMode = SortMode.NAME,
    val filter: AppFilter = AppFilter.ALL,
    val themeId: String = ThemeId.DYNAMIC,
    val customThemes: List<CustomTheme> = emptyList(),
    val amoled: Boolean = false,
    val autoUpdateCheck: Boolean = true,
    val lastUpdateCheck: Long = 0L,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "modifyx_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val allowRisky = booleanPreferencesKey("allow_risky")
        val allowProtected = booleanPreferencesKey("allow_protected")
        val confirmDestructive = booleanPreferencesKey("confirm_destructive")
        val autoApplyEnabled = booleanPreferencesKey("auto_apply_enabled")
        val autoApplyProfile = stringPreferencesKey("auto_apply_profile")
        val sortMode = stringPreferencesKey("sort_mode")
        val filter = stringPreferencesKey("filter")
        val showCatalogOnly = booleanPreferencesKey("show_catalog_only")
        val themeId = stringPreferencesKey("theme_id")
        val customThemes = stringPreferencesKey("custom_themes")
        val amoled = booleanPreferencesKey("amoled")
        val autoUpdateCheck = booleanPreferencesKey("auto_update_check")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            AppSettings(
                themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.theme] } ?: ThemeMode.SYSTEM,
                dynamicColor = prefs[Keys.dynamicColor] ?: true,
                showCatalogOnly = prefs[Keys.showCatalogOnly] ?: false,
                allowRisky = prefs[Keys.allowRisky] ?: false,
                allowProtected = prefs[Keys.allowProtected] ?: false,
                confirmDestructive = prefs[Keys.confirmDestructive] ?: true,
                autoApplyEnabled = prefs[Keys.autoApplyEnabled] ?: false,
                autoApplyProfileId = prefs[Keys.autoApplyProfile],
                sortMode = SortMode.entries.firstOrNull { it.name == prefs[Keys.sortMode] } ?: SortMode.NAME,
                filter = AppFilter.entries.firstOrNull { it.name == prefs[Keys.filter] } ?: AppFilter.ALL,
                themeId = prefs[Keys.themeId] ?: ThemeId.DYNAMIC,
                customThemes = CustomTheme.fromJson(prefs[Keys.customThemes]),
                amoled = prefs[Keys.amoled] ?: false,
                autoUpdateCheck = prefs[Keys.autoUpdateCheck] ?: true,
                lastUpdateCheck = prefs[Keys.lastUpdateCheck] ?: 0L,
            )
        }

    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.dynamicColor] = enabled }
    suspend fun setCatalogOnly(enabled: Boolean) = edit { it[Keys.showCatalogOnly] = enabled }
    suspend fun setAllowRisky(enabled: Boolean) = edit { it[Keys.allowRisky] = enabled }
    suspend fun setAllowProtected(enabled: Boolean) = edit { it[Keys.allowProtected] = enabled }
    suspend fun setConfirmDestructive(enabled: Boolean) = edit { it[Keys.confirmDestructive] = enabled }
    suspend fun setSortMode(mode: SortMode) = edit { it[Keys.sortMode] = mode.name }
    suspend fun setFilter(filter: AppFilter) = edit { it[Keys.filter] = filter.name }
    suspend fun setAutoApply(enabled: Boolean) = edit { it[Keys.autoApplyEnabled] = enabled }
    suspend fun setThemeId(id: String) = edit { it[Keys.themeId] = id }
    suspend fun setAmoled(enabled: Boolean) = edit { it[Keys.amoled] = enabled }
    suspend fun setAutoUpdateCheck(enabled: Boolean) = edit { it[Keys.autoUpdateCheck] = enabled }
    suspend fun setLastUpdateCheck(timestamp: Long) = edit { it[Keys.lastUpdateCheck] = timestamp }

    suspend fun setAutoApplyProfile(id: String?) = edit { prefs ->
        if (id == null) prefs.remove(Keys.autoApplyProfile) else prefs[Keys.autoApplyProfile] = id
    }

    /** Adds or replaces a custom theme and makes it the active one. */
    suspend fun saveCustomTheme(theme: CustomTheme) = edit { prefs ->
        val existing = CustomTheme.fromJson(prefs[Keys.customThemes])
        val updated = existing.filterNot { it.id == theme.id } + theme
        prefs[Keys.customThemes] = CustomTheme.toJson(updated)
        prefs[Keys.themeId] = ThemeId.custom(theme.id)
    }

    suspend fun deleteCustomTheme(id: String) = edit { prefs ->
        val existing = CustomTheme.fromJson(prefs[Keys.customThemes])
        prefs[Keys.customThemes] = CustomTheme.toJson(existing.filterNot { it.id == id })
        if (prefs[Keys.themeId] == ThemeId.custom(id)) prefs[Keys.themeId] = ThemeId.MATERIAL
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }
}
