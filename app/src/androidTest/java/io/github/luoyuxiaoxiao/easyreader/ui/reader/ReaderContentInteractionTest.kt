package io.github.luoyuxiaoxiao.easyreader.ui.reader

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luoyuxiaoxiao.easyreader.EasyReaderApp
import io.github.luoyuxiaoxiao.easyreader.domain.book.Book
import io.github.luoyuxiaoxiao.easyreader.domain.book.Chapter
import io.github.luoyuxiaoxiao.easyreader.domain.importer.Sha256Hasher
import io.github.luoyuxiaoxiao.easyreader.fixtures.MinimalEpubFixture
import io.github.luoyuxiaoxiao.easyreader.fixtures.MinimalEpubOptions
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderContentInteractionTest {
    private val app = ApplicationProvider.getApplicationContext<EasyReaderApp>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun tappingInlineImageOpensPreviewWebView() {
        val body = """
            <h1>Image Chapter</h1>
            <p>before image</p>
            <img id="target-img" src="images/cover.png" style="display:block;width:220px;height:220px;" />
            <p>after image</p>
        """.trimIndent()
        val bookId = runBlocking {
            seedBook(
                name = "reader-image-preview-test",
                options = MinimalEpubOptions(
                    title = "Image Preview Test",
                    includeCover = true,
                    chapter1Body = body,
                ),
            )
        }

        ActivityScenario.launch<ReaderActivity>(ReaderActivity.createIntent(app, bookId)).use { scenario ->
            val rect = waitForJson(
                scenario,
                """
                    (function() {
                      var img = document.getElementById('target-img');
                      if (!img || !img.complete) return null;
                      var r = img.getBoundingClientRect();
                      return JSON.stringify({ x: r.left + r.width / 2, y: r.top + r.height / 2 });
                    })();
                """.trimIndent(),
            )
            assertNotNull(rect)
            val point = requireNotNull(rect)
            val screenPoint = webViewPointOnScreen(scenario, point.getDouble("x").toFloat(), point.getDouble("y").toFloat())
            tap(screenPoint.first, screenPoint.second)

            assertTrue(
                "点击图片后应出现第二个 WebView 作为图片预览层",
                waitForWebViewCount(scenario, minCount = 2),
            )
        }
    }

    @Test
    fun bottomSentinelRemainsVisibleAfterScrollingToChapterEnd() {
        val paragraphs = (1..80).joinToString(separator = "\n") { index ->
            "<p>paragraph $index EasyReader bottom visibility fixture.</p>"
        }
        val body = """
            <h1>Long Chapter</h1>
            $paragraphs
            <p id="bottom-sentinel" style="margin:0;padding:0;">BOTTOM_SENTINEL_VISIBLE</p>
        """.trimIndent()
        val bookId = runBlocking {
            seedBook(
                name = "reader-bottom-sentinel-test",
                options = MinimalEpubOptions(
                    title = "Bottom Sentinel Test",
                    chapter1Body = body,
                ),
            )
        }

        ActivityScenario.launch<ReaderActivity>(ReaderActivity.createIntent(app, bookId)).use { scenario ->
            assertTrue(waitForWebViewCount(scenario, minCount = 1))
            evaluateJavascript(
                scenario,
                """
                    (function() {
                      window.scrollTo(0, Math.max(document.body.scrollHeight, document.documentElement.scrollHeight));
                      return true;
                    })();
                """.trimIndent(),
            )
            SystemClock.sleep(500)

            val visibility = waitForJson(
                scenario,
                """
                    (function() {
                      var el = document.getElementById('bottom-sentinel');
                      if (!el) return null;
                      var r = el.getBoundingClientRect();
                      return JSON.stringify({
                        top: r.top,
                        bottom: r.bottom,
                        viewportHeight: window.innerHeight,
                        text: el.textContent
                      });
                    })();
                """.trimIndent(),
            )

            assertNotNull(visibility)
            val result = requireNotNull(visibility)
            assertTrue(result.getString("text").contains("BOTTOM_SENTINEL_VISIBLE"))
            assertTrue("底部 sentinel 顶部应进入 viewport", result.getDouble("top") >= 0.0)
            assertTrue(
                "底部 sentinel 不应被导航栏或容器裁掉",
                result.getDouble("bottom") <= result.getDouble("viewportHeight"),
            )
        }
    }

    private suspend fun seedBook(name: String, options: MinimalEpubOptions): String {
        val epubFile = File(app.filesDir, "$name/book.epub")
        MinimalEpubFixture.writeTo(epubFile, options)
        val sha256 = Sha256Hasher.hash(epubFile.inputStream())
        val now = System.currentTimeMillis()
        app.appContainer.bookRepository.saveImportedBook(
            book = Book(
                id = sha256,
                title = options.title,
                author = options.author,
                filePath = epubFile.absolutePath,
                sha256 = sha256,
                coverPath = null,
                metadataSeries = null,
                metadataSeriesIndex = null,
                manualSeries = null,
                manualSeriesIndex = null,
                createdAt = now,
                updatedAt = now,
                lastOpenedAt = null,
            ),
            chapters = listOf(
                Chapter(
                    id = "$sha256-chapter-1",
                    bookId = sha256,
                    index = 0,
                    href = "chapter-1.xhtml",
                    title = "Chapter 1",
                    linear = true,
                ),
                Chapter(
                    id = "$sha256-chapter-2",
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

    private fun waitForJson(
        scenario: ActivityScenario<ReaderActivity>,
        script: String,
        attempts: Int = 30,
    ): JSONObject? {
        repeat(attempts) {
            val raw = evaluateJavascript(scenario, script)
            val parsed = raw?.let(::parseJavascriptJsonObject)
            if (parsed != null) return parsed
            SystemClock.sleep(250)
        }
        return null
    }

    private fun evaluateJavascript(
        scenario: ActivityScenario<ReaderActivity>,
        script: String,
    ): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        scenario.onActivity { activity ->
            val webView = activity.window.decorView.findWebViews().firstOrNull()
            if (webView == null) {
                latch.countDown()
                return@onActivity
            }
            webView.evaluateJavascript(script) { value ->
                result = value
                latch.countDown()
            }
        }
        latch.await(3, TimeUnit.SECONDS)
        return result
    }

    private fun parseJavascriptJsonObject(raw: String): JSONObject? {
        if (raw == "null") return null
        val value = JSONTokener(raw).nextValue()
        val json = value as? String ?: return null
        if (json == "null") return null
        return JSONObject(json)
    }

    private fun webViewPointOnScreen(
        scenario: ActivityScenario<ReaderActivity>,
        cssX: Float,
        cssY: Float,
    ): Pair<Float, Float> {
        var point: Pair<Float, Float>? = null
        scenario.onActivity { activity ->
            val webView = requireNotNull(activity.window.decorView.findWebViews().firstOrNull())
            val location = IntArray(2)
            webView.getLocationOnScreen(location)
            @Suppress("DEPRECATION")
            val scale = webView.scale.takeIf { it > 0f } ?: 1f
            point = (location[0] + cssX * scale) to (location[1] + cssY * scale)
        }
        return requireNotNull(point)
    }

    private fun waitForWebViewCount(
        scenario: ActivityScenario<ReaderActivity>,
        minCount: Int,
        attempts: Int = 30,
    ): Boolean {
        repeat(attempts) {
            var count = 0
            scenario.onActivity { activity ->
                count = activity.window.decorView.findWebViews().size
            }
            if (count >= minCount) return true
            SystemClock.sleep(250)
        }
        return false
    }

    private fun tap(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, x, y, 0))
        instrumentation.waitForIdleSync()
    }

    private fun View.findWebViews(): List<WebView> {
        val webViews = mutableListOf<WebView>()
        fun visit(view: View) {
            if (view is WebView && view.isShown) {
                webViews += view
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    visit(view.getChildAt(index))
                }
            }
        }
        visit(this)
        return webViews
    }
}
