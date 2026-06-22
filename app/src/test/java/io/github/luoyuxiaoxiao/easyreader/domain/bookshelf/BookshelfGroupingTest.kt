package io.github.luoyuxiaoxiao.easyreader.domain.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfGroupingTest {
    @Test
    fun naturalSortOrdersMagicIndexPrefixes() {
        val sorted = listOf("[S6_24.12.10]后记", "[S1_02]第二卷", "[S1_01]第一卷", "[S5_02_01]外传")
            .sortedWith(NaturalSort.comparator())

        assertEquals(listOf("[S1_01]第一卷", "[S1_02]第二卷", "[S5_02_01]外传", "[S6_24.12.10]后记"), sorted)
    }

    @Test
    fun titleSortUsesNaturalOrderForSingleBooks() {
        val entries = BookshelfGrouping.entries(
            books = listOf(
                book(id = "10", title = "Book 10", updatedAt = 300L),
                book(id = "2", title = "Book 2", updatedAt = 200L),
                book(id = "1", title = "Book 1", updatedAt = 100L),
            ),
            customRules = emptyList(),
            disabledBuiltInRuleIds = BookshelfGrouping.builtInRules.map { it.id }.toSet(),
            sortMode = BookshelfSortMode.Title,
            sortAscending = true,
        )

        assertEquals(listOf("Book 1", "Book 2", "Book 10"), entries.map { (it as BookshelfEntry.SingleBook).book.title })
    }

    @Test
    fun recentSortCanBeDescending() {
        val entries = BookshelfGrouping.entries(
            books = listOf(
                book(id = "old", title = "Old", updatedAt = 100L),
                book(id = "new", title = "New", updatedAt = 300L),
            ),
            customRules = emptyList(),
            sortMode = BookshelfSortMode.Recent,
            sortAscending = false,
        )

        assertEquals(listOf("New", "Old"), entries.map { (it as BookshelfEntry.SingleBook).book.title })
    }

    @Test
    fun manualSeriesOverridesMetadataAndRegex() {
        val books = listOf(
            book(id = "1", title = "Fate Vol.01", metadataSeries = "Fate", manualSeries = "手动 Fate"),
            book(id = "2", title = "Fate Vol.02", metadataSeries = "Fate", manualSeries = "手动 Fate"),
        )

        val entries = BookshelfGrouping.entries(books, customRules = emptyList())

        val series = entries.single() as BookshelfEntry.Series
        assertEquals("手动 Fate", series.series.title)
        assertEquals(listOf("1", "2"), series.series.books.map { it.id })
    }

    @Test
    fun customRegexWinsOverBuiltInRegexButLosesToMetadata() {
        val custom = SeriesGroupingRule(
            id = "custom-fate",
            name = "Custom Fate",
            pattern = """(?<series>Fate stay night).+?(?<index>\d+)""",
            enabled = true,
            priority = 0,
            builtIn = false,
        )
        val books = listOf(
            book(id = "1", title = "Fate stay night [01]", metadataSeries = null),
            book(id = "2", title = "Fate stay night [02]", metadataSeries = null),
            book(id = "3", title = "UBW Vol.01", metadataSeries = "Fate UBW"),
            book(id = "4", title = "UBW Vol.02", metadataSeries = "Fate UBW"),
        )

        val entries = BookshelfGrouping.entries(books, customRules = listOf(custom))

        val titles = entries.filterIsInstance<BookshelfEntry.Series>().map { it.series.title }.sorted()
        assertEquals(listOf("Fate UBW", "Fate stay night"), titles)
    }

    @Test
    fun singleRegexCandidateRemainsSingleBook() {
        val entries = BookshelfGrouping.entries(
            books = listOf(book(id = "1", title = "孤本 Vol.01")),
            customRules = emptyList(),
        )

        assertTrue(entries.single() is BookshelfEntry.SingleBook)
    }

    @Test
    fun seriesProgressIsAverageOfClampedBookProgress() {
        val books = listOf(
            book(id = "1", title = "A Vol.01", totalProgression = 1.2),
            book(id = "2", title = "A Vol.02", totalProgression = 0.5),
            book(id = "3", title = "A Vol.03", totalProgression = null),
        )

        val series = BookshelfGrouping.entries(books, emptyList()).single() as BookshelfEntry.Series

        assertEquals(0.5, series.series.progress, 0.0001)
    }

    @Test
    fun progressAtNinetyNinePercentIsCompleted() {
        val progress = BookshelfGrouping.normalizeProgress(0.99)

        assertEquals(1.0, progress, 0.0001)
    }

    @Test
    fun invalidRuleReportsValidationError() {
        val result = SeriesGroupingRule.validate("""(?<index>\d+)""")

        assertTrue(result is RuleValidationResult.Invalid)
    }

    private fun book(
        id: String,
        title: String,
        metadataSeries: String? = null,
        manualSeries: String? = null,
        createdAt: Long = 0L,
        updatedAt: Long = 0L,
        totalProgression: Double? = null,
    ) = BookshelfBook(
        id = id,
        title = title,
        author = null,
        coverPath = null,
        metadataSeries = metadataSeries,
        metadataSeriesIndex = null,
        manualSeries = manualSeries,
        manualSeriesIndex = null,
        createdAt = createdAt,
        lastOpenedAt = null,
        updatedAt = updatedAt,
        totalProgression = totalProgression,
    )
}
