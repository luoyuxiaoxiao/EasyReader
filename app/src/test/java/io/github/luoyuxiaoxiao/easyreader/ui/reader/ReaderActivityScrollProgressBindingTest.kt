package io.github.luoyuxiaoxiao.easyreader.ui.reader

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderActivityScrollProgressBindingTest {
    @Test
    fun webViewScrollListenerUsesChapterAwareNonScrollableProgression() {
        val source = readerActivitySource().readText()
        val listenerStart = source.indexOf("webView.setOnScrollChangeListener")
        assertTrue("ReaderActivity should bind a WebView scroll listener", listenerStart >= 0)

        val listenerEnd = source.indexOf("installImageTapBridge(webView)", listenerStart)
        assertTrue("Scroll listener block should be found before image bridge install", listenerEnd > listenerStart)

        val listenerBlock = source.substring(listenerStart, listenerEnd)
        assertTrue(
            "WebView scroll listener must use the same non-scrollable chapter policy as other scroll samples",
            listenerBlock.contains("nonScrollableProgression = ReaderScrollProgress.syntheticNonScrollableProgression("),
        )
    }

    private fun readerActivitySource(): File =
        listOf(
            File("src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderActivity.kt"),
            File("app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderActivity.kt"),
        ).first { it.isFile }
}
