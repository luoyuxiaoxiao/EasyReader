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
    val lastOpenedAt: Long?,
    val updatedAt: Long,
    val totalProgression: Double?,
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
)

data class SeriesGroupingRule(
    val id: String,
    val name: String,
    val pattern: String,
    val enabled: Boolean,
    val priority: Int,
    val builtIn: Boolean,
) {
    companion object {
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
