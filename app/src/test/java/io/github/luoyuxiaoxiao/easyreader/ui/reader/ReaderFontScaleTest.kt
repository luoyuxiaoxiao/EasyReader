package io.github.luoyuxiaoxiao.easyreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderFontScaleTest {
    @Test
    fun mapsScaleToReadableFontSizeLabel() {
        assertEquals("20", ReaderFontScale.labelFor(fontScale = 1.0f))
        assertEquals("22", ReaderFontScale.labelFor(fontScale = 1.1f))
    }

    @Test
    fun clampsPinchScaleToReadableRange() {
        assertEquals(0.7f, ReaderFontScale.adjust(currentScale = 0.8f, gestureScaleFactor = 0.1f), 0.001f)
        assertEquals(1.6f, ReaderFontScale.adjust(currentScale = 1.5f, gestureScaleFactor = 2.0f), 0.001f)
    }
}
