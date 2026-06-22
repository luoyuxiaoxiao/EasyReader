package io.github.luoyuxiaoxiao.easyreader.domain.book

data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val filePath: String,
    val sha256: String,
    val coverPath: String?,
    val metadataSeries: String?,
    val metadataSeriesIndex: Double?,
    val manualSeries: String?,
    val manualSeriesIndex: Double?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
)

data class BookshelfBookSnapshot(
    val book: Book,
    val totalProgression: Double?,
)

data class Chapter(
    val id: String,
    val bookId: String,
    val index: Int,
    val href: String,
    val title: String,
    val linear: Boolean,
)

data class ReadingProgress(
    val bookId: String,
    val locatorJson: String,
    val readingOrderIndex: Int,
    val totalProgression: Double?,
    val chapterProgression: Double?,
    val updatedAt: Long,
)

data class ReaderProgressPercentages(
    val total: String,
    val chapter: String,
)
