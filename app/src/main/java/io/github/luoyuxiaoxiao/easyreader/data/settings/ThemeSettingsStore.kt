package io.github.luoyuxiaoxiao.easyreader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeSettingsDataStore by preferencesDataStore(name = "theme_settings")

enum class AppThemeMode {
    System,
    Light,
    Dark,
}

data class ThemeSettings(
    val mode: AppThemeMode = AppThemeMode.System,
)

fun AppThemeMode.resolveDarkTheme(systemDarkTheme: Boolean): Boolean =
    when (this) {
        AppThemeMode.System -> systemDarkTheme
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }

class ThemeSettingsStore(context: Context) {
    private val dataStore = context.applicationContext.themeSettingsDataStore

    val settings: Flow<ThemeSettings> =
        dataStore.data.map { preferences ->
            val stored = preferences[MODE]
            ThemeSettings(
                mode = stored
                    ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                    ?: AppThemeMode.System,
            )
        }

    suspend fun setMode(mode: AppThemeMode) {
        dataStore.edit { preferences ->
            preferences[MODE] = mode.name
        }
    }

    private companion object {
        val MODE = stringPreferencesKey("mode")
    }
}
