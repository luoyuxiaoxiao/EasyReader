package io.github.luoyuxiaoxiao.easyreader.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.luoyuxiaoxiao.easyreader.EasyReaderApp
import io.github.luoyuxiaoxiao.easyreader.reader.readium.EpubReaderSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment

class ReaderActivity : FragmentActivity() {
    private lateinit var viewModel: ReaderViewModel
    private var navigator: EpubNavigatorFragment? = null
    private var locatorJob: Job? = null
    private var fragmentContainerId: Int = View.NO_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    val state = viewModel.uiState.collectAsState().value
                    MaterialTheme {
                        ReaderChrome(state = state, onBack = { finish() })
                    }
                }
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        )
        root.setOnClickListener { viewModel.toggleChrome() }
        root.onVerticalScrollStarted = viewModel::hideChromeForScroll
        root.onNextChapter = { goToRelativeChapter(1) }
        root.onPreviousChapter = { goToRelativeChapter(-1) }
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
        viewModel.load(bookId)
    }

    override fun onStop() {
        viewModel.saveProgressNow()
        super.onStop()
    }

    private fun attachNavigator(session: io.github.luoyuxiaoxiao.easyreader.reader.readium.EpubReaderSessionState) {
        supportFragmentManager.fragmentFactory = session.navigatorFactory.createFragmentFactory(
            initialLocator = session.initialLocator,
        )
        supportFragmentManager.commitNow {
            replace(fragmentContainerId, EpubNavigatorFragment::class.java, null)
        }
        navigator = supportFragmentManager.findFragmentById(fragmentContainerId) as? EpubNavigatorFragment
        collectLocatorUpdates(session)
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
                viewModel.saveNextLocatorNow()
                viewModel.showChromeBriefly()
            } else {
                viewModel.showChromeBriefly("已经到达边界")
            }
        }
    }

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"

        fun createIntent(context: Context, bookId: String): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }
}
