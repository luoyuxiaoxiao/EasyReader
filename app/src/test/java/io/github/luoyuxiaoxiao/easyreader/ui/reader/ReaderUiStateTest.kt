package io.github.luoyuxiaoxiao.easyreader.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Test

class ReaderUiStateTest {
    @Test
    fun readerChromeStartsHidden() {
        assertFalse(ReaderUiState().chromeVisible)
    }
}
