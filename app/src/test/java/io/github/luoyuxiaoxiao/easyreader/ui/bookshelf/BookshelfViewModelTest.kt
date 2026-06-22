package io.github.luoyuxiaoxiao.easyreader.ui.bookshelf

import io.github.luoyuxiaoxiao.easyreader.domain.book.Book
import io.github.luoyuxiaoxiao.easyreader.domain.book.BookshelfBookSnapshot
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfViewModelTest {
    @Test
    fun buildsSeriesEntriesFromRepositorySnapshots() {
        val entries = buildBookshelfEntries(
            snapshots = listOf(
                snapshot("1", "Fate Vol.01", 1.0),
                snapshot("2", "Fate Vol.02", 0.5),
            ),
            customRules = emptyList(),
        )

        val series = entries.single() as BookshelfEntry.Series
        assertEquals("Fate", series.series.title)
        assertEquals(0.75, series.series.progress, 0.0001)
    }

    private fun snapshot(id: String, title: String, progress: Double) =
        BookshelfBookSnapshot(
            book = Book(
                id = id,
                title = title,
                author = null,
                filePath = "/$id.epub",
                sha256 = id,
                coverPath = null,
                metadataSeries = null,
                metadataSeriesIndex = null,
                manualSeries = null,
                manualSeriesIndex = null,
                createdAt = 0L,
                updatedAt = 0L,
                lastOpenedAt = null,
            ),
            totalProgression = progress,
        )
}
