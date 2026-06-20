package io.github.luoyuxiaoxiao.easyreader.domain.book

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingProgressFormatterTest {
    @Test
    fun formatsNullAsZeroPercent() {
        assertEquals("0.00%", ReadingProgressFormatter.percent(null))
    }

    @Test
    fun clampsValuesOutsideProgressionRange() {
        assertEquals("0.00%", ReadingProgressFormatter.percent(-0.2))
        assertEquals("100.00%", ReadingProgressFormatter.percent(1.2))
    }

    @Test
    fun formatsProgressionWithTwoDecimals() {
        assertEquals("24.96%", ReadingProgressFormatter.percent(0.24956))
        assertEquals("0.40%", ReadingProgressFormatter.percent(0.004))
    }
}
