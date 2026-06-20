package io.github.luoyuxiaoxiao.easyreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY COALESCE(lastOpenedAt, updatedAt) DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun findById(bookId: String): BookEntity?

    @Query("SELECT * FROM books WHERE sha256 = :sha256")
    suspend fun findBySha256(sha256: String): BookEntity?

    @Upsert
    suspend fun upsert(book: BookEntity)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY `index` ASC")
    suspend fun findByBookId(bookId: String): List<ChapterEntity>

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Transaction
    suspend fun replaceChapters(bookId: String, chapters: List<ChapterEntity>) {
        // 章节目录来自 EPUB 解析结果，整本书替换，避免旧章节残留。
        deleteByBookId(bookId)
        if (chapters.isNotEmpty()) {
            insertAll(chapters)
        }
    }
}
