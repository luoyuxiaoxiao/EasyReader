package io.github.luoyuxiaoxiao.easyreader.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun find(bookId: String): ReadingProgressEntity?

    @Upsert
    suspend fun upsert(progress: ReadingProgressEntity)
}
