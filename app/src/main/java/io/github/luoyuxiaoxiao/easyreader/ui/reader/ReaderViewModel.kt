package io.github.luoyuxiaoxiao.easyreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.luoyuxiaoxiao.easyreader.core.result.EasyReaderResult
import io.github.luoyuxiaoxiao.easyreader.data.local.BookRepository
import io.github.luoyuxiaoxiao.easyreader.domain.book.ReadingProgress
import io.github.luoyuxiaoxiao.easyreader.domain.book.ReadingProgressFormatter
import io.github.luoyuxiaoxiao.easyreader.reader.readium.EpubReaderSession
import io.github.luoyuxiaoxiao.easyreader.reader.readium.EpubReaderSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val title: String = "EasyReader",
    val chromeVisible: Boolean = true,
    val totalProgressText: String = "0.00%",
    val chapterProgressText: String = "0.00%",
    val edgeMessage: String? = null,
    val errorMessage: String? = null,
)

class ReaderViewModel(
    private val bookRepository: BookRepository,
    private val readerSession: EpubReaderSession,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _sessionState = MutableStateFlow<EpubReaderSessionState?>(null)
    val sessionState: StateFlow<EpubReaderSessionState?> = _sessionState.asStateFlow()

    private var bookId: String? = null
    private var lastProgress: ReadingProgress? = null
    private var progressSaveJob: Job? = null

    fun load(bookId: String) {
        if (this.bookId == bookId && _sessionState.value != null) return
        this.bookId = bookId
        viewModelScope.launch {
            val book = withContext(Dispatchers.IO) { bookRepository.findBook(bookId) }
            if (book == null) {
                _uiState.update { it.copy(errorMessage = "未找到书籍") }
                return@launch
            }
            _uiState.update { it.copy(title = book.title, errorMessage = null) }
            val progress = withContext(Dispatchers.IO) { bookRepository.progress(bookId) }
            when (val result = withContext(Dispatchers.IO) { readerSession.open(book, progress) }) {
                is EasyReaderResult.Success -> _sessionState.value = result.value
                is EasyReaderResult.Failure -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun onLocatorChanged(
        locatorJson: String,
        readingOrderIndex: Int,
        totalProgression: Double?,
        chapterProgression: Double?,
    ) {
        val currentBookId = bookId ?: return
        val progress = ReadingProgress(
            bookId = currentBookId,
            locatorJson = locatorJson,
            readingOrderIndex = readingOrderIndex,
            totalProgression = totalProgression,
            chapterProgression = chapterProgression,
            updatedAt = System.currentTimeMillis(),
        )
        lastProgress = progress
        _uiState.update {
            it.copy(
                totalProgressText = ReadingProgressFormatter.percent(totalProgression),
                chapterProgressText = ReadingProgressFormatter.percent(chapterProgression),
            )
        }
        scheduleProgressSave()
    }

    fun toggleChrome() {
        _uiState.update { it.copy(chromeVisible = !it.chromeVisible) }
    }

    fun hideChromeForScroll() {
        _uiState.update { it.copy(chromeVisible = false) }
    }

    fun showChromeBriefly(message: String? = null) {
        _uiState.update { it.copy(chromeVisible = true, edgeMessage = message) }
        viewModelScope.launch {
            delay(1200)
            _uiState.update { state -> if (state.edgeMessage == message) state.copy(edgeMessage = null) else state }
        }
    }

    fun saveProgressNow() {
        progressSaveJob?.cancel()
        val progress = lastProgress ?: return
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.saveProgress(progress.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    private fun scheduleProgressSave() {
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            // Locator 高频变化只更新内存状态，数据库写入节流，避免滚动时因为 I/O 造成掉帧。
            delay(500)
            val progress = lastProgress ?: return@launch
            withContext(Dispatchers.IO) {
                bookRepository.saveProgress(progress.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    override fun onCleared() {
        readerSession.close()
        super.onCleared()
    }

    companion object {
        fun factory(
            bookRepository: BookRepository,
            readerSession: EpubReaderSession,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReaderViewModel(bookRepository, readerSession) as T
            }
    }
}
