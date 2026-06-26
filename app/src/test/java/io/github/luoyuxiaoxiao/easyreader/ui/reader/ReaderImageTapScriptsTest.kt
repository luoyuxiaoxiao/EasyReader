package io.github.luoyuxiaoxiao.easyreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImageTapScriptsTest {
    @Test
    fun probeScriptReportsImageHitSynchronously() {
        val script = ReaderImageTapScripts.probeScript(clientX = 120f, clientY = 240f)

        assertTrue(script.contains("return window.__easyReaderOpenImageFromNode(node);"))
    }

    @Test
    fun probeScriptResolvesRelativeImageSourceAgainstDocumentBase() {
        val script = ReaderImageTapScripts.probeScript(clientX = 120f, clientY = 240f)

        assertTrue(script.contains("new URL(src, document.baseURI).href"))
    }

    @Test
    fun probeScriptFetchesOriginalImageBytesForPreview() {
        val script = ReaderImageTapScripts.probeScript(clientX = 120f, clientY = 240f)

        assertTrue(script.contains("fetch(src)"))
        assertTrue(script.contains("response.blob()"))
        assertTrue(script.contains("readAsDataURL(blob)"))
        assertFalse(script.contains("canvas.toDataURL"))
    }

    @Test
    fun clickBridgeScriptFetchesOriginalImageBytesForPreview() {
        val script = ReaderImageTapScripts.clickBridgeScript("EasyReaderImageBridge")

        assertTrue(script.contains("fetch(src)"))
        assertTrue(script.contains("readAsDataURL(blob)"))
        assertTrue(script.contains("window.EasyReaderImageBridge.open"))
        assertTrue(script.contains("window.__easyReaderLastImagePreviewSource = value"))
    }

    @Test
    fun consumeProbeResultScriptReturnsAndClearsAsyncPreviewSource() {
        val script = ReaderImageTapScripts.consumeProbeResultScript()

        assertTrue(script.contains("return value;"))
        assertTrue(script.contains("window.__easyReaderLastImagePreviewSource = null"))
    }

    @Test
    fun parseProbeResultReadsJavascriptString() {
        assertEquals(
            "data:image/jpeg;base64,abc/123",
            ReaderImageTapScripts.parseProbeResult("\"data:image/jpeg;base64,abc\\/123\""),
        )
        assertNull(ReaderImageTapScripts.parseProbeResult("null"))
    }

    @Test
    fun parseProbeHitReadsJavascriptBoolean() {
        assertTrue(ReaderImageTapScripts.parseProbeHit("true"))
        assertFalse(ReaderImageTapScripts.parseProbeHit("false"))
        assertFalse(ReaderImageTapScripts.parseProbeHit("null"))
    }
}
