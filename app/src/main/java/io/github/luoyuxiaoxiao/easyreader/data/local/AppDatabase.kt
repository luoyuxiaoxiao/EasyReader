package io.github.luoyuxiaoxiao.easyreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        ReadingProgressEntity::class,
        ShortcutEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun shortcutDao(): ShortcutDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN metadataSeries TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN metadataSeriesIndex REAL")
                db.execSQL("ALTER TABLE books ADD COLUMN manualSeries TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN manualSeriesIndex REAL")
            }
        }
    }
}
