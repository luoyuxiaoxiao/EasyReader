package io.github.luoyuxiaoxiao.easyreader.data.local

import androidx.room.withTransaction
import io.github.luoyuxiaoxiao.easyreader.domain.book.Book
import io.github.luoyuxiaoxiao.easyreader.domain.book.BookshelfBookSnapshot
import io.github.luoyuxiaoxiao.easyreader.domain.book.Chapter
import io.github.luoyuxiaoxiao.easyreader.domain.book.ReadingProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookRepository(
    private val database: AppDatabase,
) {
    private val bookDao = database.bookDao()
    private val chapterDao = database.chapterDao()
    private val readingProgressDao = database.readingProgressDao()
    private val shortcutDao = database.shortcutDao()

    fun observeBooks(): Flow<List<Book>> =
        bookDao.observeBooks().map { books -> books.map { it.toDomain() } }

    fun observeBookshelfBooks(): Flow<List<BookshelfBookSnapshot>> =
        bookDao.observeBookshelfBooks().map { rows ->
            rows.map { row ->
                BookshelfBookSnapshot(
                    book = row.toDomainBook(),
                    totalProgression = row.totalProgression,
                )
            }
        }

    suspend fun findBook(bookId: String): Book? =
        bookDao.findById(bookId)?.toDomain()

    suspend fun findDuplicate(sha256: String): Book? =
        bookDao.findBySha256(sha256)?.toDomain()

    suspend fun saveImportedBook(book: Book, chapters: List<Chapter>) {
        database.withTransaction {
            // 书籍和章节必须同事务落库，避免导入中断后出现有书无目录的半成品。
            bookDao.upsert(book.toEntity())
            chapterDao.replaceChapters(book.id, chapters.map { it.toEntity() })
        }
    }

    suspend fun progress(bookId: String): ReadingProgress? =
        readingProgressDao.find(bookId)?.toDomain()

    suspend fun saveProgress(progress: ReadingProgress) {
        readingProgressDao.upsert(progress.toEntity())
    }

    suspend fun updateManualSeries(bookIds: List<String>, series: String?, seriesIndex: Double?) {
        if (bookIds.isEmpty()) return
        bookDao.updateManualSeries(bookIds, series, seriesIndex, System.currentTimeMillis())
    }

    suspend fun recordShortcut(bookId: String, shortcutId: String, now: Long) {
        shortcutDao.upsert(
            ShortcutEntity(
                bookId = bookId,
                shortcutId = shortcutId,
                createdAt = now,
                lastRequestedAt = now,
            )
        )
    }
}

private fun BookEntity.toDomain(): Book =
    Book(
        id = id,
        title = title,
        author = author,
        filePath = filePath,
        sha256 = sha256,
        coverPath = coverPath,
        metadataSeries = metadataSeries,
        metadataSeriesIndex = metadataSeriesIndex,
        manualSeries = manualSeries,
        manualSeriesIndex = manualSeriesIndex,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
    )

private fun BookshelfBookProjection.toDomainBook(): Book =
    Book(
        id = id,
        title = title,
        author = author,
        filePath = filePath,
        sha256 = sha256,
        coverPath = coverPath,
        metadataSeries = metadataSeries,
        metadataSeriesIndex = metadataSeriesIndex,
        manualSeries = manualSeries,
        manualSeriesIndex = manualSeriesIndex,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
    )

private fun Book.toEntity(): BookEntity =
    BookEntity(
        id = id,
        title = title,
        author = author,
        filePath = filePath,
        sha256 = sha256,
        coverPath = coverPath,
        metadataSeries = metadataSeries,
        metadataSeriesIndex = metadataSeriesIndex,
        manualSeries = manualSeries,
        manualSeriesIndex = manualSeriesIndex,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
    )

private fun Chapter.toEntity(): ChapterEntity =
    ChapterEntity(
        id = id,
        bookId = bookId,
        index = index,
        href = href,
        title = title,
        linear = linear,
    )

private fun ReadingProgressEntity.toDomain(): ReadingProgress =
    ReadingProgress(
        bookId = bookId,
        locatorJson = locatorJson,
        readingOrderIndex = readingOrderIndex,
        totalProgression = totalProgression,
        chapterProgression = chapterProgression,
        updatedAt = updatedAt,
    )

private fun ReadingProgress.toEntity(): ReadingProgressEntity =
    ReadingProgressEntity(
        bookId = bookId,
        locatorJson = locatorJson,
        readingOrderIndex = readingOrderIndex,
        totalProgression = totalProgression,
        chapterProgression = chapterProgression,
        updatedAt = updatedAt,
    )
