package io.github.luoyuxiaoxiao.easyreader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi

private val Context.readerSettingsDataStore by preferencesDataStore(name = "reader_settings")

data class ReaderSettings(
    val fontScale: Float = 1.0f,
    val publisherStyles: Boolean = true,
    val scroll: Boolean = true,
    val backgroundColor: String = "#FFFFFF",
    val foregroundColor: String = "#1F1F1F",
)

@OptIn(ExperimentalReadiumApi::class)
fun ReaderSettings.toEpubPreferences(): EpubPreferences =
    EpubPreferences(
        fontSize = fontScale.toDouble(),
        publisherStyles = publisherStyles,
        // 滚动模式必须显式交给 Readium，否则 navigator 会沿用分页默认行为。
        scroll = scroll,
    )

class ReaderSettingsStore(context: Context) {
    private val dataStore = context.applicationContext.readerSettingsDataStore

    val settings: Flow<ReaderSettings> =
        dataStore.data.map { preferences ->
            ReaderSettings(
                fontScale = preferences[FONT_SCALE] ?: 1.0f,
                publisherStyles = preferences[PUBLISHER_STYLES] ?: true,
                scroll = preferences[SCROLL] ?: true,
                backgroundColor = preferences[BACKGROUND_COLOR] ?: "#FFFFFF",
                foregroundColor = preferences[FOREGROUND_COLOR] ?: "#1F1F1F",
            )
        }

    suspend fun update(settings: ReaderSettings) {
        dataStore.edit { preferences ->
            preferences[FONT_SCALE] = settings.fontScale
            preferences[PUBLISHER_STYLES] = settings.publisherStyles
            preferences[SCROLL] = settings.scroll
            preferences[BACKGROUND_COLOR] = settings.backgroundColor
            preferences[FOREGROUND_COLOR] = settings.foregroundColor
        }
    }

    private companion object {
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val PUBLISHER_STYLES = booleanPreferencesKey("publisher_styles")
        val SCROLL = booleanPreferencesKey("scroll")
        val BACKGROUND_COLOR = stringPreferencesKey("background_color")
        val FOREGROUND_COLOR = stringPreferencesKey("foreground_color")
    }
}
