package io.github.luoyuxiaoxiao.easyreader.ui.bookshelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.luoyuxiaoxiao.easyreader.data.local.BookRepository
import io.github.luoyuxiaoxiao.easyreader.domain.book.Book
import io.github.luoyuxiaoxiao.easyreader.domain.importer.EpubImportService
import io.github.luoyuxiaoxiao.easyreader.shortcut.ShortcutInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BookshelfUiState(
    val books: List<Book> = emptyList(),
    val selectedBookIds: Set<String> = emptySet(),
    val isImporting: Boolean = false,
    val message: String? = null,
) {
    val isSelecting: Boolean = selectedBookIds.isNotEmpty()
}

class BookshelfViewModel(
    private val bookRepository: BookRepository,
    private val epubImportService: EpubImportService,
    private val shortcutInstaller: ShortcutInstaller,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookshelfUiState())
    val uiState: StateFlow<BookshelfUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bookRepository.observeBooks().collect { books ->
                _uiState.update { state -> state.copy(books = books) }
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

    fun requestShortcutsForSelection() {
        val state = uiState.value
        val selectedBooks = state.books.filter { it.id in state.selectedBookIds }
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
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BookshelfViewModel(bookRepository, epubImportService, shortcutInstaller) as T
            }
    }
}
