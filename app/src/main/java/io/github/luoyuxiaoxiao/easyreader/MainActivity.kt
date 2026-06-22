package io.github.luoyuxiaoxiao.easyreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.luoyuxiaoxiao.easyreader.shortcut.ShortcutContract
import io.github.luoyuxiaoxiao.easyreader.ui.bookshelf.BookshelfScreen
import io.github.luoyuxiaoxiao.easyreader.ui.bookshelf.BookshelfViewModel
import io.github.luoyuxiaoxiao.easyreader.ui.reader.ReaderActivity

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
            val viewModel: BookshelfViewModel = viewModel(
                factory = BookshelfViewModel.factory(
                    bookRepository = appContainer.bookRepository,
                    epubImportService = appContainer.epubImportService,
                    shortcutInstaller = appContainer.shortcutInstaller,
                    seriesGroupingRuleStore = appContainer.seriesGroupingRuleStore,
                    bookshelfSettingsStore = appContainer.bookshelfSettingsStore,
                )
            )
            MaterialTheme {
                BookshelfScreen(
                    viewModel = viewModel,
                    onOpenBook = { bookId -> startActivity(ReaderActivity.createIntent(this, bookId)) },
                )
            }
        }
    }
}
