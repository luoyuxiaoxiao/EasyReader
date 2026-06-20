package io.github.luoyuxiaoxiao.easyreader.shortcut

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShortcutContractTest {
    @Test
    fun buildsStableBookDeepLink() {
        assertEquals(
            Uri.parse("easyreader://book/book-123"),
            ShortcutContract.bookUri("book-123")
        )
    }

    @Test
    fun parsesBookDeepLink() {
        assertEquals("book-123", ShortcutContract.bookIdFromUri(Uri.parse("easyreader://book/book-123")))
    }

    @Test
    fun rejectsUnknownDeepLink() {
        assertNull(ShortcutContract.bookIdFromUri(Uri.parse("easyreader://settings/book-123")))
    }
}
