package io.github.luoyuxiaoxiao.easyreader.ui.bookshelf

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfBook
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfEntry
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfGrouping
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfSeries
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfSortMode
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.SeriesGroupingRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        onSeriesClick = viewModel::openSeries,
        onCloseSeries = viewModel::closeSeries,
        onAssignSeries = viewModel::assignSelectedToSeries,
        onRemoveFromSeries = viewModel::removeSelectedFromSeries,
        onAddRule = viewModel::addCustomRule,
        onSetBuiltInRuleEnabled = viewModel::setBuiltInRuleEnabled,
        onSetCustomRuleEnabled = viewModel::setCustomRuleEnabled,
        onDeleteCustomRule = viewModel::deleteCustomRule,
        onSetSortMode = viewModel::setSortMode,
        onSetSortAscending = viewModel::setSortAscending,
        onClearSelection = viewModel::clearSelection,
        onDeleteSelectedBooks = viewModel::deleteSelectedBooks,
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
    onSeriesClick: (String) -> Unit,
    onCloseSeries: () -> Unit,
    onAssignSeries: (String) -> Unit,
    onRemoveFromSeries: () -> Unit,
    onAddRule: (SeriesGroupingRule) -> Unit,
    onSetBuiltInRuleEnabled: (String, Boolean) -> Unit,
    onSetCustomRuleEnabled: (String, Boolean) -> Unit,
    onDeleteCustomRule: (String) -> Unit,
    onSetSortMode: (BookshelfSortMode) -> Unit,
    onSetSortAscending: (Boolean) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelectedBooks: () -> Unit,
    onRequestShortcuts: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = onImport,
    )
    val openedSeries = state.entries
        .filterIsInstance<BookshelfEntry.Series>()
        .firstOrNull { it.series.id == state.openedSeriesId }
    var showSeriesDialog by remember { mutableStateOf(false) }
    var seriesName by remember { mutableStateOf("") }
    var showRuleDialog by remember { mutableStateOf(false) }
    var showOrganizeMenu by remember { mutableStateOf(false) }
    var showDeleteBooksDialog by remember { mutableStateOf(false) }
    var ruleToDelete by remember { mutableStateOf<SeriesGroupingRule?>(null) }
    var ruleName by remember { mutableStateOf("") }
    var rulePattern by remember { mutableStateOf("") }
    var simpleSeriesName by remember { mutableStateOf("") }

    BackHandler(
        enabled = showDeleteBooksDialog ||
            ruleToDelete != null ||
            showRuleDialog ||
            showSeriesDialog ||
            showOrganizeMenu ||
            state.isSelecting ||
            openedSeries != null,
    ) {
        when {
            showDeleteBooksDialog -> showDeleteBooksDialog = false
            ruleToDelete != null -> ruleToDelete = null
            showRuleDialog -> showRuleDialog = false
            showSeriesDialog -> showSeriesDialog = false
            showOrganizeMenu -> showOrganizeMenu = false
            state.isSelecting -> onClearSelection()
            openedSeries != null -> onCloseSeries()
        }
    }

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
                    Text(
                        when {
                            state.isSelecting -> "已选择 ${state.selectedBookIds.size} 本"
                            openedSeries != null -> openedSeries.series.title
                            else -> "EasyReader"
                        }
                    )
                },
                navigationIcon = {
                    if (openedSeries != null && !state.isSelecting) {
                        TextButton(onClick = onCloseSeries) { Text("返回") }
                    }
                },
                actions = {
                    if (state.isSelecting) {
                        TextButton(onClick = { showSeriesDialog = true }) {
                            Text("加入系列")
                        }
                        TextButton(onClick = onRemoveFromSeries) {
                            Text("移出系列")
                        }
                        TextButton(onClick = onRequestShortcuts) {
                            Text("快捷方式")
                        }
                        TextButton(onClick = { showDeleteBooksDialog = true }) {
                            Text("删除")
                        }
                        TextButton(onClick = onClearSelection) {
                            Text("取消")
                        }
                    } else if (openedSeries == null) {
                        Box {
                            TextButton(onClick = { showOrganizeMenu = true }) {
                                Text("整理")
                            }
                            DropdownMenu(
                                expanded = showOrganizeMenu,
                                onDismissRequest = { showOrganizeMenu = false },
                            ) {
                                Text(
                                    text = "排序方式",
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                BookshelfSortMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(sortModeLabel(mode) + if (state.sortMode == mode) " (当前)" else "")
                                        },
                                        onClick = {
                                            onSetSortMode(mode)
                                            showOrganizeMenu = false
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(if (state.sortAscending) "切换为降序" else "切换为升序") },
                                    onClick = {
                                        onSetSortAscending(!state.sortAscending)
                                        showOrganizeMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("自动归组规则") },
                                    onClick = {
                                        showRuleDialog = true
                                        showOrganizeMenu = false
                                    },
                                )
                            }
                        }
                        Button(
                            onClick = {
                                // 不同 DocumentsProvider 对 .epub 的 MIME 识别不一致，放宽选择器后由导入解析继续兜底。
                                documentPicker.launch(arrayOf("application/epub+zip", "application/octet-stream", "*/*"))
                            },
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
            when {
                state.entries.isEmpty() -> {
                    Text(
                        text = if (state.isImporting) "正在导入" else "导入 EPUB 后开始阅读",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                openedSeries != null -> {
                    SeriesBooksGrid(
                        books = openedSeries.series.books,
                        selectedBookIds = state.selectedBookIds,
                        selecting = state.isSelecting,
                        onBookClick = onBookClick,
                        onBookLongClick = onBookLongClick,
                    )
                }

                else -> {
                    BookshelfGrid(
                        entries = state.entries,
                        selectedBookIds = state.selectedBookIds,
                        selecting = state.isSelecting,
                        onBookClick = onBookClick,
                        onBookLongClick = onBookLongClick,
                        onSeriesClick = onSeriesClick,
                    )
                }
            }

            if (state.isImporting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (showSeriesDialog) {
        AlertDialog(
            onDismissRequest = { showSeriesDialog = false },
            title = { Text("加入系列") },
            text = {
                TextField(
                    value = seriesName,
                    onValueChange = { seriesName = it },
                    label = { Text("系列名") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAssignSeries(seriesName)
                        showSeriesDialog = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSeriesDialog = false }) { Text("取消") }
            },
        )
    }

    if (showDeleteBooksDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteBooksDialog = false },
            title = { Text("删除图书") },
            text = { Text("将从 EasyReader 删除已选 ${state.selectedBookIds.size} 本书。手机下载目录中的原始 EPUB 不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSelectedBooks()
                        showDeleteBooksDialog = false
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBooksDialog = false }) { Text("取消") }
            },
        )
    }

    if (showRuleDialog) {
        AlertDialog(
            onDismissRequest = { showRuleDialog = false },
            title = { Text("系列归组规则") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text("内置规则", fontWeight = FontWeight.Medium)
                    BookshelfGrouping.builtInRules.forEach { rule ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = rule.id !in state.disabledBuiltInRuleIds,
                                onCheckedChange = { checked -> onSetBuiltInRuleEnabled(rule.id, checked) },
                            )
                            Text(rule.name)
                        }
                    }
                    Text("自定义规则", fontWeight = FontWeight.Medium)
                    if (state.customRules.isEmpty()) {
                        Text("暂无自定义规则", style = MaterialTheme.typography.bodySmall)
                    }
                    state.customRules.forEach { rule ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = rule.enabled,
                                onCheckedChange = { checked -> onSetCustomRuleEnabled(rule.id, checked) },
                            )
                            Text(
                                text = rule.name,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { ruleToDelete = rule }) {
                                Text("删除")
                            }
                        }
                    }
                    Text("简单规则", fontWeight = FontWeight.Medium)
                    TextField(
                        value = simpleSeriesName,
                        onValueChange = { simpleSeriesName = it },
                        label = { Text("大系列名") },
                        singleLine = true,
                    )
                    Text(
                        text = "用于 [S1_01]、[S5_02_01] 这类前缀，自动归为同一个大系列。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SimpleRulePreview(simpleSeriesName, state.allBookshelfBooks())
                    Text("高级正则", fontWeight = FontWeight.Medium)
                    TextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        label = { Text("名称") },
                        singleLine = true,
                    )
                    TextField(
                        value = rulePattern,
                        onValueChange = { rulePattern = it },
                        label = { Text("正则") },
                    )
                    Text(
                        text = "需要命名捕获组：(?<series>...)，可选 (?<index>...)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    RulePreview(rulePattern, state.allBookshelfBooks())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val simpleName = simpleSeriesName.trim()
                        val rule = if (simpleName.isNotEmpty()) {
                            SeriesGroupingRule.magicPrefix(
                                id = "custom-${System.currentTimeMillis()}",
                                name = ruleName.ifBlank { "$simpleName [S编号]" },
                                seriesName = simpleName,
                                priority = state.customRules.size,
                            )
                        } else {
                            SeriesGroupingRule(
                                id = "custom-${System.currentTimeMillis()}",
                                name = ruleName.ifBlank { "自定义规则" },
                                pattern = rulePattern,
                                enabled = true,
                                priority = state.customRules.size,
                                builtIn = false,
                            )
                        }
                        onAddRule(rule)
                        showRuleDialog = false
                        ruleName = ""
                        rulePattern = ""
                        simpleSeriesName = ""
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRuleDialog = false }) { Text("关闭") }
            },
        )
    }

    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("删除规则") },
            text = { Text("删除自定义规则：${rule.name}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCustomRule(rule.id)
                        ruleToDelete = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun BookshelfGrid(
    entries: List<BookshelfEntry>,
    selectedBookIds: Set<String>,
    selecting: Boolean,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { entryKey(it) }) { entry ->
            when (entry) {
                is BookshelfEntry.Series -> SeriesStackItem(
                    series = entry.series,
                    onClick = { onSeriesClick(entry.series.id) },
                )

                is BookshelfEntry.SingleBook -> BookGridItem(
                    book = entry.book,
                    progress = entry.progress,
                    selected = entry.book.id in selectedBookIds,
                    selecting = selecting,
                    onClick = { onBookClick(entry.book.id) },
                    onLongClick = { onBookLongClick(entry.book.id) },
                )
            }
        }
    }
}

@Composable
private fun SeriesBooksGrid(
    books: List<BookshelfBook>,
    selectedBookIds: Set<String>,
    selecting: Boolean,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(books, key = { it.id }) { book ->
            BookGridItem(
                book = book,
                progress = BookshelfGrouping.normalizeProgress(book.totalProgression),
                selected = book.id in selectedBookIds,
                selecting = selecting,
                onClick = { onBookClick(book.id) },
                onLongClick = { onBookLongClick(book.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridItem(
    book: BookshelfBook,
    progress: Double,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        BookCoverBox(
            book = book,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f),
        )
        LinearProgressIndicator(
            progress = { progress.toFloat() },
            color = BookshelfProgressGreen,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .padding(top = 4.dp),
        )
        Text(
            text = book.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (selecting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(checked = selected, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun SeriesStackItem(series: BookshelfSeries, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f),
        ) {
            series.books.take(4).reversed().forEachIndexed { index, book ->
                BookCoverBox(
                    book = book,
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(0.68f)
                        .align(Alignment.Center)
                        .offset(x = (index * 5).dp, y = (index * 2).dp),
                )
            }
            Text(
                text = "${series.books.size} 本",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        LinearProgressIndicator(
            progress = { series.progress.toFloat() },
            color = BookshelfProgressGreen,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
        Text(
            text = series.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun BookCoverBox(book: BookshelfBook, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = book.coverPath) {
        value = withContext(Dispatchers.IO) {
            book.coverPath?.let { path -> BitmapFactory.decodeFile(path) }
        }
    }
    Box(
        modifier = modifier
            .widthIn(min = 64.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = book.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = book.title.take(12),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
private fun SimpleRulePreview(seriesName: String, books: List<BookshelfBook>) {
    val preview = remember(seriesName, books) {
        val trimmed = seriesName.trim()
        if (trimmed.isEmpty()) {
            ""
        } else {
            val rule = SeriesGroupingRule.magicPrefix("preview", "预览", trimmed, 0)
            BookshelfGrouping.entries(
                books = books,
                customRules = listOf(rule),
                disabledBuiltInRuleIds = BookshelfGrouping.builtInRules.map { it.id }.toSet(),
            )
                .filterIsInstance<BookshelfEntry.Series>()
                .joinToString { "${it.series.title} (${it.series.books.size})" }
        }
    }
    Text(
        text = if (preview.isBlank()) "暂无可折叠系列" else preview,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun RulePreview(pattern: String, books: List<BookshelfBook>) {
    val preview = remember(pattern, books) {
        runCatching {
            val rule = SeriesGroupingRule("preview", "预览", pattern, true, 0, false)
            BookshelfGrouping.entries(
                books = books,
                customRules = listOf(rule),
                disabledBuiltInRuleIds = BookshelfGrouping.builtInRules.map { it.id }.toSet(),
            )
                .filterIsInstance<BookshelfEntry.Series>()
                .joinToString { "${it.series.title} (${it.series.books.size})" }
        }.getOrDefault("")
    }
    Text(
        text = if (preview.isBlank()) "暂无可折叠系列" else preview,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun entryKey(entry: BookshelfEntry): String =
    when (entry) {
        is BookshelfEntry.Series -> "series:${entry.series.id}"
        is BookshelfEntry.SingleBook -> "book:${entry.book.id}"
    }

private fun sortModeLabel(mode: BookshelfSortMode): String =
    when (mode) {
        BookshelfSortMode.Recent -> "最近阅读"
        BookshelfSortMode.Added -> "添加日期"
        BookshelfSortMode.Title -> "标题"
        BookshelfSortMode.Series -> "系列顺序"
    }

private val BookshelfProgressGreen = Color(0xFF18A558)
