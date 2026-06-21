package io.github.luoyuxiaoxiao.easyreader.ui.reader

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luoyuxiaoxiao.easyreader.EasyReaderApp
import io.github.luoyuxiaoxiao.easyreader.domain.book.Book
import io.github.luoyuxiaoxiao.easyreader.domain.book.Chapter
import io.github.luoyuxiaoxiao.easyreader.domain.importer.Sha256Hasher
import io.github.luoyuxiaoxiao.easyreader.fixtures.MinimalEpubFixture
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment

@RunWith(AndroidJUnit4::class)
class ReaderActivityRecreationTest {
    private val app = ApplicationProvider.getApplicationContext<EasyReaderApp>()

    @Test
    fun recreatesReaderActivityWithOpenEpub() {
        val bookId = runBlocking { seedMinimalBook() }
        val intent = ReaderActivity.createIntent(app, bookId)

        ActivityScenario.launch<ReaderActivity>(intent).use { scenario ->
            assertTrue(waitForNavigatorAttached(scenario))
            // 横竖屏切换会走 Activity 重建；这里验证 Readium Navigator 不会从系统 Fragment 状态错误恢复。
            scenario.recreate()
        }
    }

    private fun waitForNavigatorAttached(scenario: ActivityScenario<ReaderActivity>): Boolean {
        repeat(20) {
            var attached = false
            scenario.onActivity { activity ->
                attached = activity.supportFragmentManager.fragments.any { it is EpubNavigatorFragment }
            }
            if (attached) return true
            Thread.sleep(250)
        }
        return false
    }

    private suspend fun seedMinimalBook(): String {
        val epubFile = File(app.filesDir, "reader-recreation-test/book.epub")
        MinimalEpubFixture.writeTo(epubFile)
        val sha256 = Sha256Hasher.hash(epubFile.inputStream())
        val now = System.currentTimeMillis()
        app.appContainer.bookRepository.saveImportedBook(
            book = Book(
                id = sha256,
                title = "Minimal EPUB",
                author = "EasyReader",
                filePath = epubFile.absolutePath,
                sha256 = sha256,
                coverPath = null,
                createdAt = now,
                updatedAt = now,
                lastOpenedAt = null,
            ),
            chapters = listOf(
                Chapter(
                    id = "chapter-1",
                    bookId = sha256,
                    index = 0,
                    href = "chapter-1.xhtml",
                    title = "Chapter 1",
                    linear = true,
                ),
                Chapter(
                    id = "chapter-2",
                    bookId = sha256,
                    index = 1,
                    href = "chapter-2.xhtml",
                    title = "Chapter 2",
                    linear = true,
                ),
            ),
        )
        return sha256
    }
}
