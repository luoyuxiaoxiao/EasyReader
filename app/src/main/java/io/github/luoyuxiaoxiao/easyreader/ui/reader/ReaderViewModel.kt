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

    val shouldRefreshScrollProgressHideOnLocatorChanged: Boolean
        get() = scrollProgressVisible && !globalChromeVisible
}

fun ReaderUiState.afterExplicitChromeTap(): ReaderUiState =
    copy(
        globalChromeVisible = !globalChromeVisible,
        scrollInProgress = false,
        scrollProgressVisible = false,
        edgeMessage = null,
    )

fun ReaderUiState.afterScrollStarted(): ReaderUiState =
    copy(
        // 滚动只允许带出底部进度；顶部 Chrome 必须等下一次明确点击才重新出现。
        globalChromeVisible = false,
        scrollInProgress = true,
        scrollProgressVisible = true,
    )

fun ReaderUiState.afterScrollFinished(): ReaderUiState =
    copy(
        // 抬手后 WebView 可能还在惯性滚动；直到隐藏倒计时结束前都继续保护滚动桥进度。
        scrollInProgress = true,
        scrollProgressVisible = true,
    )

fun ReaderUiState.afterTransientBottomChrome(message: String? = null): ReaderUiState =
    copy(
        scrollInProgress = false,
        scrollProgressVisible = true,
        edgeMessage = message,
    )

fun ReaderUiState.afterReaderContentScrolled(
    totalProgression: Double?,
    chapterProgression: Double?,
): ReaderUiState =
    copy(
        // 真实内容滚动事件覆盖点击状态；滚动期间顶部 Chrome 不自动恢复。
        globalChromeVisible = false,
        scrollInProgress = true,
        scrollProgressVisible = true,
        totalProgressText = totalProgression?.let(ReadingProgressFormatter::percent) ?: totalProgressText,
        chapterProgressText = chapterProgression?.let(ReadingProgressFormatter::percent) ?: chapterProgressText,
    )

fun ReaderUiState.afterReaderChapterOpened(
    totalProgression: Double?,
    chapterProgression: Double,
): ReaderUiState =
    copy(
        // 切章反馈只更新底部进度；顶部 Chrome 仍然只允许由明确点击切换。
        globalChromeVisible = false,
        scrollInProgress = false,
        scrollProgressVisible = true,
        edgeMessage = null,
        totalProgressText = totalProgression?.let(ReadingProgressFormatter::percent) ?: totalProgressText,
        chapterProgressText = ReadingProgressFormatter.percent(chapterProgression),
    )

fun ReaderUiState.afterLocatorProgressChanged(
    totalProgression: Double?,
    chapterProgression: Double?,
): ReaderUiState = this

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
    private var useDarkTheme: Boolean = false

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
            val initialPreferences = settings.toEpubPreferences(useDarkTheme = useDarkTheme)
            when (val result = withContext(Dispatchers.IO) { readerSession.open(book, progress, initialPreferences) }) {
                is EasyReaderResult.Success -> _sessionState.value = result.value
                is EasyReaderResult.Failure -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun applyResolvedTheme(useDarkTheme: Boolean): org.readium.r2.navigator.epub.EpubPreferences {
        this.useDarkTheme = useDarkTheme
        return readerSettings.toEpubPreferences(useDarkTheme = useDarkTheme)
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
        _uiState.update { it.afterLocatorProgressChanged(totalProgression, chapterProgression) }
        if (saveNextLocatorImmediately) {
            saveNextLocatorImmediately = false
            saveProgressNow()
        } else {
            scheduleProgressSave()
        }
        if (_uiState.value.shouldRefreshScrollProgressHideOnLocatorChanged) {
            scheduleScrollProgressHide()
        }
    }

    fun toggleChrome() {
        chromeAutoHideJob?.cancel()
        _uiState.update { it.afterExplicitChromeTap() }
    }

    fun onScrollGestureStarted() {
        chromeAutoHideJob?.cancel()
        _uiState.update { it.afterScrollStarted() }
    }

    fun onScrollGestureFinished() {
        _uiState.update { it.afterScrollFinished() }
        scheduleScrollProgressHide()
    }

    fun onReaderContentScrolled(
        readingOrderIndex: Int,
        chapterWeights: List<Int>,
        scrollY: Int,
        viewportHeightPx: Int,
        contentHeightPx: Float,
        nonScrollableProgression: Double? = 1.0,
    ) {
        val chapterProgression = ReaderScrollProgress.chapterProgression(
            scrollY = scrollY,
            viewportHeightPx = viewportHeightPx,
            contentHeightPx = contentHeightPx,
            nonScrollableProgression = nonScrollableProgression,
        )
        val totalProgression = ReaderScrollProgress.totalProgression(
            chapterWeights = chapterWeights,
            readingOrderIndex = readingOrderIndex,
            chapterProgression = chapterProgression,
        )
        _uiState.update {
            it.afterReaderContentScrolled(
                totalProgression = totalProgression,
                chapterProgression = chapterProgression,
            )
        }
        scheduleScrollProgressHide()
    }

    fun onReaderChapterOpened(
        readingOrderIndex: Int,
        chapterWeights: List<Int>,
    ) {
        val chapterProgression = ReaderScrollProgress.CHAPTER_START_PROGRESSION
        val totalProgression = ReaderScrollProgress.chapterStartTotalProgression(
            chapterWeights = chapterWeights,
            readingOrderIndex = readingOrderIndex,
        )
        _uiState.update {
            it.afterReaderChapterOpened(
                totalProgression = totalProgression,
                chapterProgression = chapterProgression,
            )
        }
        scheduleScrollProgressHide()
    }

    fun showChromeBriefly(message: String? = null) {
        _uiState.update { it.afterTransientBottomChrome(message) }
        scheduleScrollProgressHide()
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
            preferences = readerSettings.toEpubPreferences(useDarkTheme = useDarkTheme),
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

    private fun scheduleScrollProgressHide() {
        chromeAutoHideJob?.cancel()
        chromeAutoHideJob = viewModelScope.launch {
            // 手指离开后 Readium 可能仍在惯性滚动；locator 持续更新会刷新这个倒计时。
            delay(SCROLL_PROGRESS_HIDE_DELAY_MS)
            _uiState.update { it.copy(scrollInProgress = false, scrollProgressVisible = false) }
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
