package io.github.luoyuxiaoxiao.easyreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.luoyuxiaoxiao.easyreader.ui.bookshelf.BookshelfScreen
import io.github.luoyuxiaoxiao.easyreader.ui.bookshelf.BookshelfViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as EasyReaderApp).appContainer
        setContent {
            val viewModel: BookshelfViewModel = viewModel(
                factory = BookshelfViewModel.factory(
                    bookRepository = appContainer.bookRepository,
                    epubImportService = appContainer.epubImportService,
                )
            )
            MaterialTheme {
                BookshelfScreen(
                    viewModel = viewModel,
                    onOpenBook = { bookId -> viewModel.showMessage("阅读页将在后续任务接入：$bookId") },
                )
            }
        }
    }
}
