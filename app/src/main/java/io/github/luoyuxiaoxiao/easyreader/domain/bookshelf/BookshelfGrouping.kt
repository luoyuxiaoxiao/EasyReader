package io.github.luoyuxiaoxiao.easyreader.domain.bookshelf

object BookshelfGrouping {
    val builtInRules: List<SeriesGroupingRule> = listOf(
        SeriesGroupingRule(
            id = "builtin-vol",
            name = "英文卷号",
            pattern = """(?<series>.+?)\s+(?:Vol\.?|Volume)\s*\.?(?<index>\d+(?:\.\d+)?)""",
            enabled = true,
            priority = 100,
            builtIn = true,
        ),
        SeriesGroupingRule(
            id = "builtin-cn",
            name = "中文卷号",
            pattern = """(?<series>.+?)\s*第\s*(?<index>\d+)\s*[卷册]""",
            enabled = true,
            priority = 110,
            builtIn = true,
        ),
        SeriesGroupingRule(
            id = "builtin-bracket",
            name = "括号卷号",
            pattern = """(?<series>.+?)\s*[\[(（](?<index>\d+)[\])）]""",
            enabled = true,
            priority = 120,
            builtIn = true,
        ),
        SeriesGroupingRule(
            id = "builtin-number",
            name = "数字后缀",
            pattern = """(?<series>.+?)\s+(?<index>\d{1,3})""",
            enabled = true,
            priority = 130,
            builtIn = true,
        ),
    )

    fun entries(
        books: List<BookshelfBook>,
        customRules: List<SeriesGroupingRule>,
        disabledBuiltInRuleIds: Set<String> = emptySet(),
        sortMode: BookshelfSortMode = BookshelfSortMode.Recent,
        sortAscending: Boolean = false,
    ): List<BookshelfEntry> {
        val rules = customRules
            .filter { it.enabled }
            .sortedBy { it.priority } +
            builtInRules.filter { it.enabled && it.id !in disabledBuiltInRuleIds }.sortedBy { it.priority }

        val grouped = books.groupBy { book ->
            // 分组优先级是书柜行为的核心：手动整理必须压过元数据和标题正则。
            book.manualSeries.cleanSeries()
                ?: book.metadataSeries.cleanSeries()
                ?: rules.firstNotNullOfOrNull { rule -> matchSeries(rule, book.title)?.series }
                ?: SINGLE_PREFIX + book.id
        }

        return grouped.values
            .map { group ->
                val first = group.first()
                val key = first.manualSeries.cleanSeries()
                    ?: first.metadataSeries.cleanSeries()
                    ?: groupKeyFromRules(group, rules)
                if (group.size >= 2 && key != null) {
                    val sortedBooks = sortSeriesBooks(group, rules)
                    BookshelfEntry.Series(
                        BookshelfSeries(
                            id = key,
                            title = key,
                            books = sortedBooks,
                            progress = group.map { normalizeProgress(it.totalProgression) }.average(),
                            sortKey = sortedBooks.firstOrNull()?.title,
                        )
                    )
                } else {
                    BookshelfEntry.SingleBook(first, normalizeProgress(first.totalProgression))
                }
            }
            .let { entries -> sortEntries(entries, sortMode, sortAscending) }
    }

    fun normalizeProgress(value: Double?): Double {
        val clamped = (value ?: 0.0).coerceIn(0.0, 1.0)
        return if (clamped >= 0.99) 1.0 else clamped
    }

    private fun groupKeyFromRules(group: List<BookshelfBook>, rules: List<SeriesGroupingRule>): String? =
        group.firstNotNullOfOrNull { book ->
            rules.firstNotNullOfOrNull { rule -> matchSeries(rule, book.title)?.series }
        }

    private fun sortSeriesBooks(books: List<BookshelfBook>, rules: List<SeriesGroupingRule>): List<BookshelfBook> =
        books.sortedWith { left, right ->
            val leftKey = seriesSortKey(left, rules)
            val rightKey = seriesSortKey(right, rules)
            val indexCompare = leftKey.index.compareTo(rightKey.index)
            if (indexCompare != 0) {
                indexCompare
            } else if (leftKey.sortText != null || rightKey.sortText != null) {
                NaturalSort.compare(leftKey.sortText.orEmpty(), rightKey.sortText.orEmpty())
            } else {
                NaturalSort.compare(left.title, right.title)
            }
        }

    private fun seriesSortKey(book: BookshelfBook, rules: List<SeriesGroupingRule>): SeriesBookSortKey {
        val directIndex = book.manualSeriesIndex ?: book.metadataSeriesIndex
        if (directIndex != null) return SeriesBookSortKey(index = directIndex, sortText = null)
        val ruleMatch = rules.firstNotNullOfOrNull { rule -> matchSeries(rule, book.title) }
        return SeriesBookSortKey(
            index = ruleMatch?.index ?: Double.MAX_VALUE,
            sortText = ruleMatch?.sortText,
        )
    }

    private fun matchSeries(rule: SeriesGroupingRule, title: String): RuleMatch? =
        when (rule.kind) {
            SeriesGroupingRuleKind.MagicPrefix -> matchMagicPrefix(rule, title)
            SeriesGroupingRuleKind.Regex -> matchRegex(rule, title)
        }

    private fun matchRegex(rule: SeriesGroupingRule, title: String): RuleMatch? =
        runCatching {
            val match = Regex(rule.pattern).find(title) ?: return null
            val groups = match.groups
            val series = groups["series"]?.value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val index = groups["index"]?.value?.toDoubleOrNull()
            RuleMatch(series, index)
        }.getOrNull()

    private fun matchMagicPrefix(rule: SeriesGroupingRule, title: String): RuleMatch? {
        val series = rule.seriesOverride.cleanSeries() ?: return null
        // [S...] 前缀只用于自动归大系列和排序，小系列仍由手动整理覆盖。
        val prefix = Regex("""^\[S([\d_.]+)\]""").find(title)?.groupValues?.getOrNull(1) ?: return null
        return RuleMatch(series = series, sortText = prefix)
    }

    private fun sortEntries(
        entries: List<BookshelfEntry>,
        sortMode: BookshelfSortMode,
        ascending: Boolean,
    ): List<BookshelfEntry> {
        // 顶层书架顺序由用户设置控制；系列内部顺序始终保持卷号/自然顺序，避免阅读路径被最近打开时间打乱。
        val comparator = when (sortMode) {
            BookshelfSortMode.Recent -> compareBy<BookshelfEntry> { entryRecentAt(it) }
            BookshelfSortMode.Added -> compareBy { entryCreatedAt(it) }
            BookshelfSortMode.Title -> Comparator { left, right -> NaturalSort.compare(entryTitle(left), entryTitle(right)) }
            BookshelfSortMode.Series -> Comparator { left, right -> NaturalSort.compare(entrySeriesSortText(left), entrySeriesSortText(right)) }
        }
        val sorted = entries.sortedWith(comparator.thenBy { entryTitle(it) })
        return if (ascending) sorted else sorted.reversed()
    }

    private fun String?.cleanSeries(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }

    private fun entryRecentAt(entry: BookshelfEntry): Long =
        when (entry) {
            is BookshelfEntry.Series -> entry.series.books.maxOf { it.lastOpenedAt ?: it.updatedAt }
            is BookshelfEntry.SingleBook -> entry.book.lastOpenedAt ?: entry.book.updatedAt
        }

    private fun entryCreatedAt(entry: BookshelfEntry): Long =
        when (entry) {
            is BookshelfEntry.Series -> entry.series.books.minOf { it.createdAt }
            is BookshelfEntry.SingleBook -> entry.book.createdAt
        }

    private fun entrySeriesSortText(entry: BookshelfEntry): String =
        when (entry) {
            is BookshelfEntry.Series -> entry.series.sortKey ?: entry.series.title
            is BookshelfEntry.SingleBook -> entry.book.title
        }

    private fun entryTitle(entry: BookshelfEntry): String =
        when (entry) {
            is BookshelfEntry.Series -> entry.series.title
            is BookshelfEntry.SingleBook -> entry.book.title
        }

    private data class RuleMatch(val series: String, val index: Double? = null, val sortText: String? = null)

    private data class SeriesBookSortKey(val index: Double, val sortText: String?)

    private const val SINGLE_PREFIX = "single:"
}
