package io.github.luoyuxiaoxiao.easyreader.shortcut

import android.net.Uri

object ShortcutContract {
    private const val SCHEME = "easyreader"
    private const val HOST_BOOK = "book"

    fun bookUri(bookId: String): Uri =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST_BOOK)
            .appendPath(bookId)
            .build()

    fun bookIdFromUri(uri: Uri?): String? {
        // 桌面快捷方式只接受 easyreader://book/{bookId}，避免误处理其他深链。
        if (uri == null) return null
        if (uri.scheme != SCHEME || uri.host != HOST_BOOK) return null
        return uri.pathSegments.singleOrNull()
    }

    fun shortcutId(bookId: String): String = "book-$bookId"
}
