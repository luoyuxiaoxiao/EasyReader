package io.github.luoyuxiaoxiao.easyreader.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcuts WHERE bookId = :bookId")
    suspend fun find(bookId: String): ShortcutEntity?

    @Upsert
    suspend fun upsert(shortcut: ShortcutEntity)
}
