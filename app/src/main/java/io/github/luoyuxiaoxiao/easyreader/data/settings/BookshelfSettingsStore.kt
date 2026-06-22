package io.github.luoyuxiaoxiao.easyreader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfSettings
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfSortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.bookshelfSettingsDataStore by preferencesDataStore(name = "bookshelf_settings")

class BookshelfSettingsStore(context: Context) {
    private val dataStore = context.applicationContext.bookshelfSettingsDataStore

    val settings: Flow<BookshelfSettings> = dataStore.data.map { preferences ->
        BookshelfSettings(
            sortMode = preferences[SORT_MODE]?.let { stored ->
                runCatching { BookshelfSortMode.valueOf(stored) }.getOrNull()
            } ?: BookshelfSortMode.Recent,
            sortAscending = preferences[SORT_ASCENDING] ?: false,
        )
    }

    suspend fun setSortMode(mode: BookshelfSortMode) {
        dataStore.edit { preferences -> preferences[SORT_MODE] = mode.name }
    }

    suspend fun setSortAscending(ascending: Boolean) {
        dataStore.edit { preferences -> preferences[SORT_ASCENDING] = ascending }
    }

    private companion object {
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
    }
}
