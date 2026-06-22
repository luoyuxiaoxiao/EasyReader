package io.github.luoyuxiaoxiao.easyreader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.SeriesGroupingRule
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.SeriesGroupingRuleSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.seriesGroupingRuleDataStore by preferencesDataStore(name = "series_grouping_rules")

class SeriesGroupingRuleStore(context: Context) {
    private val dataStore = context.applicationContext.seriesGroupingRuleDataStore

    val settings: Flow<SeriesGroupingRuleSettings> =
        dataStore.data.map { preferences ->
            SeriesGroupingRuleSerializer.decode(preferences[SETTINGS_JSON])
        }

    suspend fun updateSettings(settings: SeriesGroupingRuleSettings) {
        dataStore.edit { preferences ->
            preferences[SETTINGS_JSON] = SeriesGroupingRuleSerializer.encode(settings)
        }
    }

    suspend fun updateCustomRules(rules: List<SeriesGroupingRule>) {
        val current = settings.first()
        updateSettings(current.copy(customRules = rules))
    }

    suspend fun setBuiltInRuleEnabled(ruleId: String, enabled: Boolean) {
        val current = settings.first()
        val disabled = if (enabled) {
            current.disabledBuiltInRuleIds - ruleId
        } else {
            current.disabledBuiltInRuleIds + ruleId
        }
        updateSettings(current.copy(disabledBuiltInRuleIds = disabled))
    }

    private companion object {
        val SETTINGS_JSON = stringPreferencesKey("settings_json")
    }
}

object SeriesGroupingRuleSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(settings: SeriesGroupingRuleSettings): String =
        json.encodeToString(settings.toStored())

    fun decode(value: String?): SeriesGroupingRuleSettings =
        if (value.isNullOrBlank()) {
            SeriesGroupingRuleSettings()
        } else {
            runCatching { json.decodeFromString<StoredSeriesGroupingRuleSettings>(value).toDomain() }
                .getOrDefault(SeriesGroupingRuleSettings())
        }
}

@Serializable
private data class StoredSeriesGroupingRuleSettings(
    val customRules: List<StoredSeriesGroupingRule> = emptyList(),
    val disabledBuiltInRuleIds: Set<String> = emptySet(),
)

@Serializable
private data class StoredSeriesGroupingRule(
    val id: String,
    val name: String,
    val pattern: String,
    val enabled: Boolean,
    val priority: Int,
    val builtIn: Boolean,
)

private fun SeriesGroupingRuleSettings.toStored(): StoredSeriesGroupingRuleSettings =
    StoredSeriesGroupingRuleSettings(
        customRules = customRules.map { it.toStored() },
        disabledBuiltInRuleIds = disabledBuiltInRuleIds,
    )

private fun StoredSeriesGroupingRuleSettings.toDomain(): SeriesGroupingRuleSettings =
    SeriesGroupingRuleSettings(
        customRules = customRules.map { it.toDomain() },
        disabledBuiltInRuleIds = disabledBuiltInRuleIds,
    )

private fun SeriesGroupingRule.toStored(): StoredSeriesGroupingRule =
    StoredSeriesGroupingRule(id, name, pattern, enabled, priority, builtIn)

private fun StoredSeriesGroupingRule.toDomain(): SeriesGroupingRule =
    SeriesGroupingRule(id, name, pattern, enabled, priority, builtIn)
