package io.github.luoyuxiaoxiao.easyreader.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books", indices = [Index(value = ["sha256"], unique = true)])
data class BookEntity(
    @PrimaryKey val id: String,
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

data class BookshelfBookProjection(
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
    val totalProgression: Double?,
)

@Entity(
    tableName = "chapters",
    primaryKeys = ["bookId", "index"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("bookId")],
)
data class ChapterEntity(
    val id: String,
    val bookId: String,
    val index: Int,
    val href: String,
    val title: String,
    val linear: Boolean,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("bookId")],
)
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val locatorJson: String,
    val readingOrderIndex: Int,
    val totalProgression: Double?,
    val chapterProgression: Double?,
    val updatedAt: Long,
)

@Entity(
    tableName = "shortcuts",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("bookId")],
)
data class ShortcutEntity(
    @PrimaryKey val bookId: String,
    val shortcutId: String,
    val createdAt: Long,
    val lastRequestedAt: Long,
)
