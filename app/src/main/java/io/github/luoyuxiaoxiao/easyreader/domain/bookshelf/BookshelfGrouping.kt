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
                    BookshelfEntry.Series(
                        BookshelfSeries(
                            id = key,
                            title = key,
                            books = sortSeriesBooks(group, rules),
                            progress = group.map { normalizeProgress(it.totalProgression) }.average(),
                        )
                    )
                } else {
                    BookshelfEntry.SingleBook(first, normalizeProgress(first.totalProgression))
                }
            }
            .sortedWith(compareBy({ entrySortTime(it) }, { entryTitle(it) }))
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
        books.sortedWith(
            compareBy<BookshelfBook> {
                it.manualSeriesIndex
                    ?: it.metadataSeriesIndex
                    ?: rules.firstNotNullOfOrNull { rule -> matchSeries(rule, it.title)?.index }
                    ?: Double.MAX_VALUE
            }.thenBy { it.title }
        )

    private fun matchSeries(rule: SeriesGroupingRule, title: String): RuleMatch? =
        runCatching {
            val match = Regex(rule.pattern).find(title) ?: return null
            val groups = match.groups
            val series = groups["series"]?.value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val index = groups["index"]?.value?.toDoubleOrNull()
            RuleMatch(series, index)
        }.getOrNull()

    private fun String?.cleanSeries(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }

    private fun entrySortTime(entry: BookshelfEntry): Long =
        when (entry) {
            is BookshelfEntry.Series -> entry.series.books.maxOf { it.lastOpenedAt ?: it.updatedAt }
            is BookshelfEntry.SingleBook -> entry.book.lastOpenedAt ?: entry.book.updatedAt
        } * -1

    private fun entryTitle(entry: BookshelfEntry): String =
        when (entry) {
            is BookshelfEntry.Series -> entry.series.title
            is BookshelfEntry.SingleBook -> entry.book.title
        }

    private data class RuleMatch(val series: String, val index: Double?)

    private const val SINGLE_PREFIX = "single:"
}
