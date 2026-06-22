package io.github.luoyuxiaoxiao.easyreader.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun persistsBookGraphAndFindsDuplicateBySha256() = runBlocking {
        val book = BookEntity(
            id = "book-1",
            title = "Minimal EPUB",
            author = "EasyReader",
            filePath = "/books/book-1/book.epub",
            sha256 = "hash-1",
            coverPath = null,
            metadataSeries = null,
            metadataSeriesIndex = null,
            manualSeries = null,
            manualSeriesIndex = null,
            createdAt = 100L,
            updatedAt = 200L,
            lastOpenedAt = null,
        )
        val chapters = listOf(
            ChapterEntity(
                id = "chapter-1",
                bookId = book.id,
                index = 0,
                href = "chapter-1.xhtml",
                title = "Chapter 1",
                linear = true,
            ),
            ChapterEntity(
                id = "chapter-2",
                bookId = book.id,
                index = 1,
                href = "chapter-2.xhtml",
                title = "Chapter 2",
                linear = true,
            ),
        )
        val progress = ReadingProgressEntity(
            bookId = book.id,
            locatorJson = """{"href":"chapter-1.xhtml"}""",
            readingOrderIndex = 0,
            totalProgression = 0.25,
            chapterProgression = 0.5,
            updatedAt = 300L,
        )
        val shortcut = ShortcutEntity(
            bookId = book.id,
            shortcutId = "book-${book.id}",
            createdAt = 400L,
            lastRequestedAt = 500L,
        )

        database.bookDao().upsert(book)
        database.chapterDao().replaceChapters(book.id, chapters)
        database.readingProgressDao().upsert(progress)
        database.shortcutDao().upsert(shortcut)

        assertEquals(listOf(book), database.bookDao().observeBooks().first())
        assertEquals(book, database.bookDao().findById(book.id))
        assertEquals(book, database.bookDao().findBySha256(book.sha256))
        assertEquals(chapters, database.chapterDao().findByBookId(book.id))
        assertEquals(progress, database.readingProgressDao().find(book.id))
        assertEquals(shortcut, database.shortcutDao().find(book.id))
    }

    @Test
    fun observesBookshelfBooksWithManualSeriesAndProgress() = runBlocking {
        val book = BookEntity(
            id = "book-series-1",
            title = "Series Vol.01",
            author = "Author",
            filePath = "/books/book-series-1/book.epub",
            sha256 = "hash-series-1",
            coverPath = "/books/book-series-1/cover.jpg",
            metadataSeries = "Series",
            metadataSeriesIndex = 1.0,
            manualSeries = "Manual Series",
            manualSeriesIndex = 2.0,
            createdAt = 100L,
            updatedAt = 200L,
            lastOpenedAt = null,
        )
        val progress = ReadingProgressEntity(
            bookId = book.id,
            locatorJson = """{"href":"chapter.xhtml"}""",
            readingOrderIndex = 0,
            totalProgression = 0.5,
            chapterProgression = 0.5,
            updatedAt = 300L,
        )

        database.bookDao().upsert(book)
        database.readingProgressDao().upsert(progress)

        val snapshot = database.bookDao().observeBookshelfBooks().first().single()
        assertEquals("Manual Series", snapshot.manualSeries)
        assertEquals(0.5, snapshot.totalProgression!!, 0.0001)
    }

    @Test
    fun deletingBookCascadesBookGraph() = runBlocking {
        val book = BookEntity(
            id = "book-delete",
            title = "Delete Me",
            author = null,
            filePath = "/books/book-delete/book.epub",
            sha256 = "hash-delete",
            coverPath = null,
            metadataSeries = null,
            metadataSeriesIndex = null,
            manualSeries = null,
            manualSeriesIndex = null,
            createdAt = 100L,
            updatedAt = 200L,
            lastOpenedAt = null,
        )
        database.bookDao().upsert(book)
        database.chapterDao().replaceChapters(
            book.id,
            listOf(ChapterEntity("chapter", book.id, 0, "chapter.xhtml", "Chapter", true))
        )
        database.readingProgressDao().upsert(
            ReadingProgressEntity(book.id, "{}", 0, 0.5, 0.5, 300L)
        )
        database.shortcutDao().upsert(
            ShortcutEntity(book.id, "shortcut", 400L, 500L)
        )

        database.bookDao().deleteByIds(listOf(book.id))

        assertEquals(null, database.bookDao().findById(book.id))
        assertEquals(emptyList<ChapterEntity>(), database.chapterDao().findByBookId(book.id))
        assertEquals(null, database.readingProgressDao().find(book.id))
        assertEquals(null, database.shortcutDao().find(book.id))
    }
}
