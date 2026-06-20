package io.github.luoyuxiaoxiao.easyreader.ui.bookshelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.luoyuxiaoxiao.easyreader.data.local.BookRepository
import io.github.luoyuxiaoxiao.easyreader.domain.book.Book
import io.github.luoyuxiaoxiao.easyreader.domain.importer.EpubImportService
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
        val selectedCount = uiState.value.selectedBookIds.size
        if (selectedCount == 0) {
            showMessage("请选择书籍")
        } else {
            showMessage("桌面快捷方式将在后续任务接入")
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
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BookshelfViewModel(bookRepository, epubImportService) as T
            }
    }
}
