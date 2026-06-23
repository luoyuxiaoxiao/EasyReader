package io.github.luoyuxiaoxiao.easyreader.ui.reader

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.luoyuxiaoxiao.easyreader.EasyReaderApp
import io.github.luoyuxiaoxiao.easyreader.data.settings.ThemeSettings
import io.github.luoyuxiaoxiao.easyreader.data.settings.resolveDarkTheme
import io.github.luoyuxiaoxiao.easyreader.reader.readium.EpubReaderSession
import io.github.luoyuxiaoxiao.easyreader.ui.theme.EasyReaderTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.readium.r2.navigator.HyperlinkNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.AbsoluteUrl

class ReaderActivity : FragmentActivity() {
    private lateinit var viewModel: ReaderViewModel
    private var navigator: EpubNavigatorFragment? = null
    private var locatorJob: Job? = null
    private var fragmentContainerId: Int = View.NO_ID
    private var scrollWebView: WebView? = null
    private var currentReadingOrderIndex: Int = 0
    private val footnoteHtml = mutableStateOf<String?>(null)
    private val imagePreviewUrl = mutableStateOf<String?>(null)
    @Volatile
    private var readerContentTapConsumed = false
    private val imageTapBridge = ImageTapBridge()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Readium 的 EpubNavigatorFragment 必须通过 navigatorFactory 创建，不能走系统 Fragment 状态恢复。
        // 旋转屏幕后重新打开 session 并 attach navigator，避免 FragmentManager 用空构造函数恢复导致崩溃。
        super.onCreate(null)
        // ReaderActivity 交给 Readium 和 Compose 各自处理系统栏避让，避免 Activity 默认 fitSystemWindows
        // 再额外把正文整体下推，形成状态栏下方的空白阅读区域。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        if (bookId == null) {
            finish()
            return
        }

        val appContainer = (application as EasyReaderApp).appContainer
        viewModel = ViewModelProvider(
            this,
            ReaderViewModel.factory(
                bookRepository = appContainer.bookRepository,
                readerSettingsStore = appContainer.readerSettingsStore,
                readerSession = EpubReaderSession(appContainer.readiumServices),
            )
        )[ReaderViewModel::class.java]

        val root = ReaderGestureLayout(this)
        fragmentContainerId = View.generateViewId()
        root.addView(
            FragmentContainerView(this).apply { id = fragmentContainerId },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        )
        root.addView(
            ComposeView(this).apply {
                setContent {
                    val themeSettings = appContainer.themeSettingsStore.settings.collectAsState(initial = ThemeSettings()).value
                    val state = viewModel.uiState.collectAsState().value
                    SideEffect {
                        root.topChromeControlsVisible = state.topChromeVisible
                    }
                    EasyReaderTheme(mode = themeSettings.mode) {
                        ReaderChrome(
                            state = state,
                            onBack = { finish() },
                            footnoteHtml = footnoteHtml.value,
                            onDismissFootnote = { footnoteHtml.value = null },
                            imagePreviewUrl = imagePreviewUrl.value,
                            onDismissImagePreview = { imagePreviewUrl.value = null },
                        )
                    }
                }
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        )
        root.onChromeTap = viewModel::toggleChrome
        root.onVerticalScrollStarted = {
            viewModel.sessionState.value?.let { session ->
                rebindWebViewScrollUpdates(session)
                updateReaderContentScrollProgress(
                    nonScrollableProgression = ReaderScrollProgress.syntheticNonScrollableProgression(
                        readingOrderIndex = currentReadingOrderIndex,
                        readingOrderCount = session.chapterWeights.size,
                    ),
                )
            }
            viewModel.onScrollGestureStarted()
        }
        root.onVerticalScrollFinished = {
            viewModel.onScrollGestureFinished()
            viewModel.sessionState.value?.let { session ->
                updateReaderContentScrollProgress(
                    nonScrollableProgression = ReaderScrollProgress.syntheticNonScrollableProgression(
                        readingOrderIndex = currentReadingOrderIndex,
                        readingOrderCount = session.chapterWeights.size,
                    ),
                )
            }
        }
        root.onFontScaleChanged = { scaleFactor ->
            val change = viewModel.adjustFontScale(scaleFactor)
            navigator?.submitPreferences(change.preferences)
        }
        root.onFontScaleFinished = viewModel::onFontScaleGestureFinished
        root.onNextChapter = { goToRelativeChapter(1) }
        root.onPreviousChapter = { goToRelativeChapter(-1) }
        root.onReaderContentTapConsumed = ::consumeReaderContentTap
        setContentView(root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sessionState.collectLatest { session ->
                    if (session != null && navigator == null) {
                        attachNavigator(session)
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appContainer.themeSettingsStore.settings.collectLatest { settings ->
                    val preferences = viewModel.applyResolvedTheme(
                        settings.mode.resolveDarkTheme(currentSystemDarkTheme()),
                    )
                    navigator?.submitPreferences(preferences)
                }
            }
        }
        lifecycleScope.launch {
            val themeSettings = appContainer.themeSettingsStore.settings.first()
            viewModel.applyResolvedTheme(themeSettings.mode.resolveDarkTheme(currentSystemDarkTheme()))
            viewModel.load(bookId)
        }
    }

    override fun onStop() {
        viewModel.saveProgressNow()
        super.onStop()
    }

    private fun attachNavigator(session: io.github.luoyuxiaoxiao.easyreader.reader.readium.EpubReaderSessionState) {
        supportFragmentManager.fragmentFactory = session.navigatorFactory.createFragmentFactory(
            initialLocator = viewModel.startLocatorFor(session),
            initialPreferences = session.initialPreferences,
            listener = readerNavigatorListener(),
        )
        supportFragmentManager.commitNow {
            replace(fragmentContainerId, EpubNavigatorFragment::class.java, null)
        }
        navigator = supportFragmentManager.findFragmentById(fragmentContainerId) as? EpubNavigatorFragment
        collectLocatorUpdates(session)
        bindWebViewScrollUpdates(session)
    }

    private fun collectLocatorUpdates(session: io.github.luoyuxiaoxiao.easyreader.reader.readium.EpubReaderSessionState) {
        val epubNavigator = navigator ?: return
        locatorJob?.cancel()
        locatorJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                epubNavigator.currentLocator.collect { locator ->
                    val href = locator.href.toString()
                    val readingOrderIndex = session.publication.readingOrder.indexOfFirst {
                        it.href.toString() == href
                    }.takeIf { it >= 0 } ?: session.initialReadingOrderIndex
                    currentReadingOrderIndex = readingOrderIndex
                    viewModel.onLocatorChanged(
                        locatorJson = locator.toJSON().toString(),
                        readingOrderIndex = readingOrderIndex,
                        totalProgression = locator.locations.totalProgression,
                        chapterProgression = locator.locations.progression,
                    )
                }
            }
        }
    }

    private fun goToRelativeChapter(delta: Int) {
        val session = viewModel.sessionState.value ?: return
        val epubNavigator = navigator ?: return
        val current = session.publication.readingOrder.indexOfFirst {
            it.href.toString() == epubNavigator.currentLocator.value.href.toString()
        }.takeIf { it >= 0 } ?: session.initialReadingOrderIndex
        val target = current + delta
        val link = session.publication.readingOrder.getOrNull(target)
        if (link == null) {
            viewModel.showChromeBriefly("已经到达边界")
        } else {
            if (epubNavigator.go(link, animated = true)) {
                currentReadingOrderIndex = target
                viewModel.saveNextLocatorNow()
                viewModel.onReaderChapterOpened(
                    readingOrderIndex = target,
                    chapterWeights = session.chapterWeights,
                )
                rebindWebViewScrollUpdates(session, sampleAfterBind = true)
            } else {
                viewModel.showChromeBriefly("已经到达边界")
            }
        }
    }

    private fun bindWebViewScrollUpdates(
        session: io.github.luoyuxiaoxiao.easyreader.reader.readium.EpubReaderSessionState,
        attempt: Int = 0,
        force: Boolean = false,
        sampleAfterBind: Boolean = false,
    ) {
        val existing = scrollWebView
        if (existing != null && !force) {
            installImageTapBridge(existing)
            return
        }

        val webView = window.decorView.findBestVisibleWebView()
        if (webView == null) {
            if (attempt < WEB_VIEW_BIND_MAX_ATTEMPTS) {
                window.decorView.postDelayed(
                    { bindWebViewScrollUpdates(session, attempt + 1, sampleAfterBind = sampleAfterBind) },
                    WEB_VIEW_BIND_RETRY_MS,
                )
            }
            return
        }

        if (existing !== webView) {
            existing?.setOnScrollChangeListener(null)
        }
        scrollWebView = webView
        webView.setOnScrollChangeListener { view, _, scrollY, _, oldScrollY ->
            if (scrollY == oldScrollY) return@setOnScrollChangeListener
            updateReaderContentScrollProgress(webView = webView, viewport = view)
        }
        installImageTapBridge(webView)
        if (sampleAfterBind) {
            // 切章后 WebView 不一定立即发出滚动事件，绑定成功时补一次首尾页测量。
            updateReaderContentScrollProgress(
                webView = webView,
                viewport = webView,
                nonScrollableProgression = ReaderScrollProgress.syntheticNonScrollableProgression(
                    readingOrderIndex = currentReadingOrderIndex,
                    readingOrderCount = session.chapterWeights.size,
                ),
            )
        }
    }

    private fun rebindWebViewScrollUpdates(
        session: io.github.luoyuxiaoxiao.easyreader.reader.readium.EpubReaderSessionState,
        sampleAfterBind: Boolean = false,
    ) {
        val oldWebView = scrollWebView
        oldWebView?.setOnScrollChangeListener(null)
        scrollWebView = null
        window.decorView.post { bindWebViewScrollUpdates(session, force = true, sampleAfterBind = sampleAfterBind) }
        window.decorView.postDelayed(
            { bindWebViewScrollUpdates(session, force = true, sampleAfterBind = sampleAfterBind) },
            250L,
        )
        window.decorView.postDelayed(
            { bindWebViewScrollUpdates(session, force = true, sampleAfterBind = sampleAfterBind) },
            700L,
        )
    }

    private fun updateReaderContentScrollProgress(
        webView: WebView? = scrollWebView ?: window.decorView.findBestVisibleWebView(),
        viewport: View? = webView,
        nonScrollableProgression: Double? = 1.0,
    ) {
        val currentWebView = webView ?: return
        val session = viewModel.sessionState.value ?: return
        val contentHeightPx = ReaderScrollProgress.webViewContentHeightPx(currentWebView.contentHeight)
        viewModel.onReaderContentScrolled(
            readingOrderIndex = currentReadingOrderIndex,
            chapterWeights = session.chapterWeights,
            scrollY = currentWebView.scrollY,
            viewportHeightPx = viewport?.height ?: currentWebView.height,
            contentHeightPx = contentHeightPx,
            nonScrollableProgression = nonScrollableProgression,
        )
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun readerNavigatorListener(): EpubNavigatorFragment.Listener =
        object : EpubNavigatorFragment.Listener {
            override fun shouldFollowInternalLink(
                link: Link,
                context: HyperlinkNavigator.LinkContext?,
            ): Boolean {
                markReaderContentTapConsumed()
                if (context is HyperlinkNavigator.FootnoteContext) {
                    showFootnote(context.noteContent)
                    return false
                }
                return true
            }

            override fun onExternalLinkActivated(url: AbsoluteUrl) {
                // 外部链接由 Readium 回调明确消费，避免同一次点击继续触发阅读器 chrome。
                markReaderContentTapConsumed()
            }
        }

    private fun showFootnote(html: String) {
        markReaderContentTapConsumed()
        footnoteHtml.value = html
    }

    private fun installImageTapBridge(webView: WebView) {
        webView.addJavascriptInterface(imageTapBridge, IMAGE_TAP_BRIDGE_NAME)
        injectImageTapScript(webView)
        webView.postDelayed({ injectImageTapScript(webView) }, 250L)
    }

    private fun injectImageTapScript(webView: WebView) {
        webView.evaluateJavascript(IMAGE_TAP_SCRIPT, null)
    }

    private fun markReaderContentTapConsumed() {
        readerContentTapConsumed = true
    }

    private fun consumeReaderContentTap(): Boolean {
        val consumed = readerContentTapConsumed
        readerContentTapConsumed = false
        return consumed
    }

    private fun currentSystemDarkTheme(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private inner class ImageTapBridge {
        @JavascriptInterface
        fun open(src: String) {
            // JS bridge 可能不在主线程回调；先标记内容点击已消费，再切回主线程打开预览层。
            markReaderContentTapConsumed()
            runOnUiThread {
                imagePreviewUrl.value = src
            }
        }
    }

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"
        private const val WEB_VIEW_BIND_MAX_ATTEMPTS = 20
        private const val WEB_VIEW_BIND_RETRY_MS = 100L
        private const val IMAGE_TAP_BRIDGE_NAME = "EasyReaderImageBridge"
        private val IMAGE_TAP_SCRIPT = """
            (function() {
              if (window.__easyReaderImageTapInstalled) return;
              window.__easyReaderImageTapInstalled = true;
              document.addEventListener('click', function(event) {
                var node = event.target;
                while (node && node.tagName && node.tagName.toLowerCase() !== 'img') {
                  node = node.parentElement;
                }
                if (!node || !node.tagName || node.tagName.toLowerCase() !== 'img') return;
                var src = node.currentSrc || node.src || node.getAttribute('src');
                if (!src) return;
                event.preventDefault();
                event.stopPropagation();
                window.$IMAGE_TAP_BRIDGE_NAME.open(src);
              }, true);
            })();
        """.trimIndent()

        fun createIntent(context: Context, bookId: String): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }
}

private fun View.findBestVisibleWebView(): WebView? {
    var best: WebView? = null
    var bestArea = 0
    fun visit(view: View) {
        if (view is WebView) {
            val rect = Rect()
            if (view.isShown && view.getGlobalVisibleRect(rect)) {
                val area = rect.width() * rect.height()
                if (area >= bestArea) {
                    best = view
                    bestArea = area
                }
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                visit(view.getChildAt(index))
            }
        }
    }
    visit(this)
    return best
}
