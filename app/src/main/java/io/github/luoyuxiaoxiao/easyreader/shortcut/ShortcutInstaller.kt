package io.github.luoyuxiaoxiao.easyreader.shortcut

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.luoyuxiaoxiao.easyreader.MainActivity
import io.github.luoyuxiaoxiao.easyreader.data.local.BookRepository
import io.github.luoyuxiaoxiao.easyreader.domain.book.Book
import java.io.File

class ShortcutInstaller(
    private val context: Context,
    private val bookRepository: BookRepository,
) {
    fun isSupported(): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    suspend fun requestPinnedShortcuts(books: List<Book>): Int {
        if (!isSupported()) return 0
        var requested = 0
        for (book in books) {
            // Android 不允许应用静默批量放置桌面图标，因此这里按队列逐个请求 Launcher 确认。
            val shortcutId = ShortcutContract.shortcutId(book.id)
            val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
                .setShortLabel(book.title.take(MAX_SHORT_LABEL_LENGTH).ifBlank { "EasyReader" })
                .setLongLabel(book.title.ifBlank { "EasyReader" })
                .setIcon(book.shortcutIcon())
                .setIntent(
                    Intent(Intent.ACTION_VIEW, ShortcutContract.bookUri(book.id), context, MainActivity::class.java)
                )
                .build()
            if (ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)) {
                requested += 1
                bookRepository.recordShortcut(book.id, shortcutId, System.currentTimeMillis())
            }
        }
        return requested
    }

    private fun Book.shortcutIcon(): IconCompat {
        val cover = coverPath
            ?.let(::File)
            ?.takeIf { it.isFile }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
        return if (cover != null) {
            IconCompat.createWithBitmap(cover)
        } else {
            IconCompat.createWithResource(context, android.R.drawable.sym_def_app_icon)
        }
    }

    private companion object {
        const val MAX_SHORT_LABEL_LENGTH = 20
    }
}
