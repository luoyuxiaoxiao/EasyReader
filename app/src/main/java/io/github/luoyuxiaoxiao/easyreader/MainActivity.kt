package io.github.luoyuxiaoxiao.easyreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.luoyuxiaoxiao.easyreader.data.settings.ThemeSettings
import io.github.luoyuxiaoxiao.easyreader.shortcut.ShortcutContract
import io.github.luoyuxiaoxiao.easyreader.ui.bookshelf.BookshelfScreen
import io.github.luoyuxiaoxiao.easyreader.ui.bookshelf.BookshelfViewModel
import io.github.luoyuxiaoxiao.easyreader.ui.reader.ReaderActivity
import io.github.luoyuxiaoxiao.easyreader.ui.theme.EasyReaderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShortcutContract.bookIdFromUri(intent.data)?.let { bookId ->
            startActivity(ReaderActivity.createIntent(this, bookId))
            finish()
            return
        }

        val appContainer = (application as EasyReaderApp).appContainer
        setContent {
            val scope = rememberCoroutineScope()
            val themeSettings by appContainer.themeSettingsStore.settings.collectAsState(initial = ThemeSettings())
            val viewModel: BookshelfViewModel = viewModel(
                factory = BookshelfViewModel.factory(
                    bookRepository = appContainer.bookRepository,
                    epubImportService = appContainer.epubImportService,
                    shortcutInstaller = appContainer.shortcutInstaller,
                    seriesGroupingRuleStore = appContainer.seriesGroupingRuleStore,
                    bookshelfSettingsStore = appContainer.bookshelfSettingsStore,
                )
            )
            EasyReaderTheme(mode = themeSettings.mode) {
                BookshelfScreen(
                    viewModel = viewModel,
                    themeMode = themeSettings.mode,
                    onThemeModeSelected = { mode ->
                        scope.launch { appContainer.themeSettingsStore.setMode(mode) }
                    },
                    onOpenBook = { bookId -> startActivity(ReaderActivity.createIntent(this, bookId)) },
                )
            }
        }
    }
}
