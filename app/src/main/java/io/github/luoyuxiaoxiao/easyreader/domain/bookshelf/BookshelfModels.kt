package io.github.luoyuxiaoxiao.easyreader.domain.bookshelf

data class BookshelfBook(
    val id: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val metadataSeries: String?,
    val metadataSeriesIndex: Double?,
    val manualSeries: String?,
    val manualSeriesIndex: Double?,
    val createdAt: Long,
    val lastOpenedAt: Long?,
    val updatedAt: Long,
    val totalProgression: Double?,
)

enum class BookshelfSortMode {
    Recent,
    Added,
    Title,
    Series,
}

data class BookshelfSettings(
    val sortMode: BookshelfSortMode = BookshelfSortMode.Recent,
    val sortAscending: Boolean = false,
)

sealed interface BookshelfEntry {
    data class Series(val series: BookshelfSeries) : BookshelfEntry
    data class SingleBook(val book: BookshelfBook, val progress: Double) : BookshelfEntry
}

data class BookshelfSeries(
    val id: String,
    val title: String,
    val books: List<BookshelfBook>,
    val progress: Double,
    val sortKey: String? = null,
)

enum class SeriesGroupingRuleKind {
    Regex,
    MagicPrefix,
}

data class SeriesGroupingRule(
    val id: String,
    val name: String,
    val pattern: String,
    val enabled: Boolean,
    val priority: Int,
    val builtIn: Boolean,
    val kind: SeriesGroupingRuleKind = SeriesGroupingRuleKind.Regex,
    val seriesOverride: String? = null,
) {
    companion object {
        fun magicPrefix(id: String, name: String, seriesName: String, priority: Int): SeriesGroupingRule =
            SeriesGroupingRule(
                id = id,
                name = name,
                pattern = """^\[S[\d_.]+\]""",
                enabled = true,
                priority = priority,
                builtIn = false,
                kind = SeriesGroupingRuleKind.MagicPrefix,
                seriesOverride = seriesName,
            )

        fun validate(rule: SeriesGroupingRule): RuleValidationResult =
            when (rule.kind) {
                SeriesGroupingRuleKind.MagicPrefix ->
                    if (rule.seriesOverride.isNullOrBlank()) {
                        RuleValidationResult.Invalid("大系列名不能为空")
                    } else {
                        RuleValidationResult.Valid
                    }

                SeriesGroupingRuleKind.Regex -> validate(rule.pattern)
            }

        fun validate(pattern: String): RuleValidationResult =
            runCatching {
                Regex(pattern)
                if (pattern.contains("(?<series>")) {
                    RuleValidationResult.Valid
                } else {
                    RuleValidationResult.Invalid("缺少 series 捕获组")
                }
            }.getOrElse { RuleValidationResult.Invalid(it.message ?: "正则表达式无效") }
    }
}

data class SeriesGroupingRuleSettings(
    val customRules: List<SeriesGroupingRule> = emptyList(),
    val disabledBuiltInRuleIds: Set<String> = emptySet(),
)

sealed interface RuleValidationResult {
    data object Valid : RuleValidationResult
    data class Invalid(val message: String) : RuleValidationResult
}
