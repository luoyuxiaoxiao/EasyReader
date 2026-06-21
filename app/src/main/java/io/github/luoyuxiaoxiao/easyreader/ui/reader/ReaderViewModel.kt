package io.github.luoyuxiaoxiao.easyreader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.luoyuxiaoxiao.easyreader.core.result.EasyReaderResult
import io.github.luoyuxiaoxiao.easyreader.data.local.BookRepository
import io.github.luoyuxiaoxiao.easyreader.data.settings.ReaderSettings
import io.github.luoyuxiaoxiao.easyreader.data.settings.ReaderSettingsStore
import io.github.luoyuxiaoxiao.easyreader.data.settings.toEpubPreferences
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val title: String = "EasyReader",
    val globalChromeVisible: Boolean = false,
    val scrollInProgress: Boolean = false,
    val scrollProgressVisible: Boolean = false,
    val totalProgressText: String = "0.00%",
    val chapterProgressText: String = "0.00%",
    val fontSizeOverlayText: String? = null,
    val edgeMessage: String? = null,
    val errorMessage: String? = null,
) {
    val topChromeVisible: Boolean
        get() = globalChromeVisible && !scrollInProgress

    val bottomChromeVisible: Boolean
        get() = globalChromeVisible || scrollProgressVisible || edgeMessage != null

    val fontSizeOverlayVisible: Boolean
        get() = fontSizeOverlayText != null
}

data class ReaderFontScaleChange(
    val preferences: org.readium.r2.navigator.epub.EpubPreferences,
    val label: String,
)

class ReaderViewModel(
    private val bookRepository: BookRepository,
    private val readerSettingsStore: ReaderSettingsStore,
    private val readerSession: EpubReaderSession,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _sessionState = MutableStateFlow<EpubReaderSessionState?>(null)
    val sessionState: StateFlow<EpubReaderSessionState?> = _sessionState.asStateFlow()

    private var bookId: String? = null
    private var lastProgress: ReadingProgress? = null
    private var progressSaveJob: Job? = null
    private var chromeAutoHideJob: Job? = null
    private var fontOverlayHideJob: Job? = null
    private var settingsSaveJob: Job? = null
    private var saveNextLocatorImmediately = false
    private var readerSettings: ReaderSettings = ReaderSettings()

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
            val settings = readerSettingsStore.settings.first()
            readerSettings = settings
            when (val result = withContext(Dispatchers.IO) { readerSession.open(book, progress, settings) }) {
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
        if (saveNextLocatorImmediately) {
            saveNextLocatorImmediately = false
            saveProgressNow()
        } else {
            scheduleProgressSave()
        }
    }

    fun toggleChrome() {
        chromeAutoHideJob?.cancel()
        _uiState.update {
            it.copy(
                globalChromeVisible = !it.globalChromeVisible,
                scrollInProgress = false,
                scrollProgressVisible = false,
            )
        }
    }

    fun onScrollGestureStarted() {
        chromeAutoHideJob?.cancel()
        _uiState.update { it.copy(scrollInProgress = true, scrollProgressVisible = true) }
    }

    fun onScrollGestureFinished() {
        _uiState.update { state ->
            state.copy(
                scrollInProgress = false,
                scrollProgressVisible = state.globalChromeVisible,
            )
        }
        if (!_uiState.value.globalChromeVisible) {
            chromeAutoHideJob = viewModelScope.launch {
                delay(SCROLL_PROGRESS_HIDE_DELAY_MS)
                _uiState.update { it.copy(scrollProgressVisible = false) }
            }
        }
    }

    fun showChromeBriefly(message: String? = null) {
        _uiState.update { it.copy(globalChromeVisible = true, edgeMessage = message) }
        viewModelScope.launch {
            delay(1200)
            _uiState.update { state -> if (state.edgeMessage == message) state.copy(edgeMessage = null) else state }
        }
    }

    fun adjustFontScale(gestureScaleFactor: Float): ReaderFontScaleChange {
        fontOverlayHideJob?.cancel()
        settingsSaveJob?.cancel()
        val updatedScale = ReaderFontScale.adjust(readerSettings.fontScale, gestureScaleFactor)
        readerSettings = readerSettings.copy(fontScale = updatedScale)
        val label = ReaderFontScale.labelFor(updatedScale)
        _uiState.update { it.copy(fontSizeOverlayText = label) }
        return ReaderFontScaleChange(
            preferences = readerSettings.toEpubPreferences(),
            label = label,
        )
    }

    fun onFontScaleGestureFinished() {
        val settingsToSave = readerSettings
        settingsSaveJob?.cancel()
        settingsSaveJob = viewModelScope.launch(Dispatchers.IO) {
            readerSettingsStore.update(settingsToSave)
        }
        fontOverlayHideJob?.cancel()
        fontOverlayHideJob = viewModelScope.launch {
            delay(FONT_OVERLAY_HIDE_DELAY_MS)
            _uiState.update { it.copy(fontSizeOverlayText = null) }
        }
    }

    fun saveNextLocatorNow() {
        saveNextLocatorImmediately = true
    }

    fun startLocatorFor(session: EpubReaderSessionState) =
        ReaderStartLocator.select(lastProgress?.locatorJson, session.initialLocator)

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
        private const val SCROLL_PROGRESS_HIDE_DELAY_MS = 700L
        private const val FONT_OVERLAY_HIDE_DELAY_MS = 800L

        fun factory(
            bookRepository: BookRepository,
            readerSettingsStore: ReaderSettingsStore,
            readerSession: EpubReaderSession,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReaderViewModel(bookRepository, readerSettingsStore, readerSession) as T
            }
    }
}
