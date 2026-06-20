package io.github.luoyuxiaoxiao.easyreader.ui.bookshelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.luoyuxiaoxiao.easyreader.domain.book.Book

@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    BookshelfContent(
        state = state,
        onImport = viewModel::importUris,
        onBookClick = { bookId ->
            viewModel.openBook(bookId)
            if (!state.isSelecting) onOpenBook(bookId)
        },
        onBookLongClick = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onRequestShortcuts = viewModel::requestShortcutsForSelection,
        onMessageShown = viewModel::clearMessage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookshelfContent(
    state: BookshelfUiState,
    onImport: (List<android.net.Uri>) -> Unit,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRequestShortcuts: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = onImport,
    )

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isSelecting) "已选择 ${state.selectedBookIds.size} 本" else "EasyReader")
                },
                actions = {
                    if (state.isSelecting) {
                        TextButton(onClick = onRequestShortcuts) {
                            Text("快捷方式")
                        }
                        TextButton(onClick = onClearSelection) {
                            Text("取消")
                        }
                    } else {
                        Button(
                            onClick = { documentPicker.launch(arrayOf("application/epub+zip")) },
                            enabled = !state.isImporting,
                        ) {
                            Text("导入")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            if (state.books.isEmpty()) {
                Text(
                    text = if (state.isImporting) "正在导入" else "导入 EPUB 后开始阅读",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.books, key = { it.id }) { book ->
                        BookshelfRow(
                            book = book,
                            selected = book.id in state.selectedBookIds,
                            selecting = state.isSelecting,
                            onClick = { onBookClick(book.id) },
                            onLongClick = { onBookLongClick(book.id) },
                        )
                    }
                }
            }

            if (state.isImporting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfRow(
    book: Book,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        headlineContent = { Text(book.title) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.author ?: "未知作者")
                book.lastOpenedAt?.let { Text("最近阅读") }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = null)
                    Spacer(Modifier.width(4.dp))
                }
            }
        },
    )
}
