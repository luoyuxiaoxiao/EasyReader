package io.github.luoyuxiaoxiao.easyreader.ui.bookshelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.luoyuxiaoxiao.easyreader.data.local.BookRepository
import io.github.luoyuxiaoxiao.easyreader.data.settings.BookshelfSettingsStore
import io.github.luoyuxiaoxiao.easyreader.data.settings.SeriesGroupingRuleStore
import io.github.luoyuxiaoxiao.easyreader.domain.book.Book
import io.github.luoyuxiaoxiao.easyreader.domain.book.BookshelfBookSnapshot
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfBook
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfEntry
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfGrouping
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfSortMode
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.RuleValidationResult
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.SeriesGroupingRule
import io.github.luoyuxiaoxiao.easyreader.domain.importer.EpubImportService
import io.github.luoyuxiaoxiao.easyreader.shortcut.ShortcutInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BookshelfUiState(
    val books: List<Book> = emptyList(),
    val entries: List<BookshelfEntry> = emptyList(),
    val booksById: Map<String, Book> = emptyMap(),
    val selectedBookIds: Set<String> = emptySet(),
    val openedSeriesId: String? = null,
    val customRules: List<SeriesGroupingRule> = emptyList(),
    val disabledBuiltInRuleIds: Set<String> = emptySet(),
    val sortMode: BookshelfSortMode = BookshelfSortMode.Recent,
    val sortAscending: Boolean = false,
    val isImporting: Boolean = false,
    val message: String? = null,
) {
    val isSelecting: Boolean = selectedBookIds.isNotEmpty()
}

class BookshelfViewModel(
    private val bookRepository: BookRepository,
    private val epubImportService: EpubImportService,
    private val shortcutInstaller: ShortcutInstaller,
    private val seriesGroupingRuleStore: SeriesGroupingRuleStore,
    private val bookshelfSettingsStore: BookshelfSettingsStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookshelfUiState())
    val uiState: StateFlow<BookshelfUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                bookRepository.observeBookshelfBooks(),
                seriesGroupingRuleStore.settings,
                bookshelfSettingsStore.settings,
            ) { snapshots, groupingSettings, bookshelfSettings ->
                Triple(snapshots, groupingSettings, bookshelfSettings)
            }.collect { (snapshots, groupingSettings, bookshelfSettings) ->
                _uiState.update { state ->
                    state.copy(
                        books = snapshots.map { it.book },
                        entries = buildBookshelfEntries(
                            snapshots = snapshots,
                            customRules = groupingSettings.customRules,
                            disabledBuiltInRuleIds = groupingSettings.disabledBuiltInRuleIds,
                            sortMode = bookshelfSettings.sortMode,
                            sortAscending = bookshelfSettings.sortAscending,
                        ),
                        booksById = snapshots.associate { it.book.id to it.book },
                        customRules = groupingSettings.customRules,
                        disabledBuiltInRuleIds = groupingSettings.disabledBuiltInRuleIds,
                        sortMode = bookshelfSettings.sortMode,
                        sortAscending = bookshelfSettings.sortAscending,
                    )
                }
            }
        }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, message = null) }
            val results = withContext(Dispatchers.IO) {
                epubImportService.importUris(uris)
            }
            val imported = results.count { !it.duplicate }
            val duplicates = results.count { it.duplicate }
            _uiState.update {
                it.copy(
                    isImporting = false,
                    message = "导入 $imported 本，跳过重复 $duplicates 本",
                )
            }
        }
    }

    fun openBook(bookId: String) {
        if (uiState.value.isSelecting) {
            toggleSelection(bookId)
        }
    }

    fun toggleSelection(bookId: String) {
        _uiState.update { state ->
            val selected = state.selectedBookIds
            state.copy(
                selectedBookIds = if (bookId in selected) {
                    selected - bookId
                } else {
                    selected + bookId
                }
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedBookIds = emptySet()) }
    }

    fun openSeries(seriesId: String) {
        _uiState.update { it.copy(openedSeriesId = seriesId, selectedBookIds = emptySet()) }
    }

    fun closeSeries() {
        _uiState.update { it.copy(openedSeriesId = null, selectedBookIds = emptySet()) }
    }

    fun assignSelectedToSeries(series: String) {
        val selected = uiState.value.selectedBookIds
        if (selected.isEmpty()) {
            showMessage("请选择书籍")
            return
        }
        val trimmed = series.trim()
        if (trimmed.isEmpty()) {
            showMessage("系列名不能为空")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.updateManualSeries(selected.toList(), trimmed, null)
            _uiState.update { it.copy(selectedBookIds = emptySet(), message = "已加入系列：$trimmed") }
        }
    }

    fun removeSelectedFromSeries() {
        val selected = uiState.value.selectedBookIds
        if (selected.isEmpty()) {
            showMessage("请选择书籍")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.updateManualSeries(selected.toList(), null, null)
            _uiState.update { it.copy(selectedBookIds = emptySet(), message = "已移出手动系列") }
        }
    }

    fun addCustomRule(rule: SeriesGroupingRule) {
        if (SeriesGroupingRule.validate(rule.pattern) !is RuleValidationResult.Valid) {
            showMessage("正则需要包含 series 捕获组")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            seriesGroupingRuleStore.updateCustomRules(uiState.value.customRules + rule)
        }
    }

    fun setBuiltInRuleEnabled(ruleId: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            seriesGroupingRuleStore.setBuiltInRuleEnabled(ruleId, enabled)
        }
    }

    fun setCustomRuleEnabled(ruleId: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = uiState.value.customRules.map { rule ->
                if (rule.id == ruleId) rule.copy(enabled = enabled) else rule
            }
            seriesGroupingRuleStore.updateCustomRules(updated)
        }
    }

    fun setSortMode(mode: BookshelfSortMode) {
        viewModelScope.launch(Dispatchers.IO) {
            bookshelfSettingsStore.setSortMode(mode)
        }
    }

    fun setSortAscending(ascending: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            bookshelfSettingsStore.setSortAscending(ascending)
        }
    }

    fun requestShortcutsForSelection() {
        val state = uiState.value
        val selectedBooks = state.selectedBookIds.mapNotNull { state.booksById[it] }
        if (selectedBooks.isEmpty()) {
            showMessage("请选择书籍")
            return
        }
        if (!shortcutInstaller.isSupported()) {
            showMessage("当前桌面不支持添加快捷方式")
            return
        }
        viewModelScope.launch {
            val requested = withContext(Dispatchers.IO) {
                shortcutInstaller.requestPinnedShortcuts(selectedBooks)
            }
            _uiState.update {
                it.copy(
                    selectedBookIds = emptySet(),
                    message = if (requested > 0) "已发送桌面快捷方式请求" else "当前桌面不支持添加快捷方式",
                )
            }
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        fun factory(
            bookRepository: BookRepository,
            epubImportService: EpubImportService,
            shortcutInstaller: ShortcutInstaller,
            seriesGroupingRuleStore: SeriesGroupingRuleStore,
            bookshelfSettingsStore: BookshelfSettingsStore,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BookshelfViewModel(
                        bookRepository = bookRepository,
                        epubImportService = epubImportService,
                        shortcutInstaller = shortcutInstaller,
                        seriesGroupingRuleStore = seriesGroupingRuleStore,
                        bookshelfSettingsStore = bookshelfSettingsStore,
                    ) as T
            }
    }
}

internal fun buildBookshelfEntries(
    snapshots: List<BookshelfBookSnapshot>,
    customRules: List<SeriesGroupingRule>,
    disabledBuiltInRuleIds: Set<String> = emptySet(),
    sortMode: BookshelfSortMode = BookshelfSortMode.Recent,
    sortAscending: Boolean = false,
): List<BookshelfEntry> =
    // UI 只消费聚合后的书柜条目，标题正则和进度归一化都收敛在领域层。
    BookshelfGrouping.entries(
        books = snapshots.map { snapshot ->
            val book = snapshot.book
            BookshelfBook(
                id = book.id,
                title = book.title,
                author = book.author,
                coverPath = book.coverPath,
                metadataSeries = book.metadataSeries,
                metadataSeriesIndex = book.metadataSeriesIndex,
                manualSeries = book.manualSeries,
                manualSeriesIndex = book.manualSeriesIndex,
                createdAt = book.createdAt,
                lastOpenedAt = book.lastOpenedAt,
                updatedAt = book.updatedAt,
                totalProgression = snapshot.totalProgression,
            )
        },
        customRules = customRules,
        disabledBuiltInRuleIds = disabledBuiltInRuleIds,
        sortMode = sortMode,
        sortAscending = sortAscending,
    )

internal fun BookshelfUiState.allBookshelfBooks(): List<BookshelfBook> =
    entries.flatMap { entry ->
        when (entry) {
            is BookshelfEntry.Series -> entry.series.books
            is BookshelfEntry.SingleBook -> listOf(entry.book)
        }
    }
