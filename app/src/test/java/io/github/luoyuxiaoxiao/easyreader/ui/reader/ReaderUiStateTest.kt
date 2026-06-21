package io.github.luoyuxiaoxiao.easyreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderUiStateTest {
    @Test
    fun readerChromeStartsHidden() {
        val state = ReaderUiState()

        assertFalse(state.topChromeVisible)
        assertFalse(state.bottomChromeVisible)
        assertFalse(state.fontSizeOverlayVisible)
    }

    @Test
    fun globalChromeShowsTopAndBottomWhenIdle() {
        val state = ReaderUiState(globalChromeVisible = true)

        assertTrue(state.topChromeVisible)
        assertTrue(state.bottomChromeVisible)
    }

    @Test
    fun scrollingHidesTopChromeButKeepsBottomProgressVisible() {
        val state = ReaderUiState(globalChromeVisible = true, scrollInProgress = true)

        assertFalse(state.topChromeVisible)
        assertTrue(state.bottomChromeVisible)
    }

    @Test
    fun transientScrollProgressShowsOnlyBottomChrome() {
        val state = ReaderUiState(scrollProgressVisible = true)

        assertFalse(state.topChromeVisible)
        assertTrue(state.bottomChromeVisible)
    }

    @Test
    fun fontOverlayShowsCurrentSizeLabel() {
        val state = ReaderUiState(fontSizeOverlayText = "22")

        assertTrue(state.fontSizeOverlayVisible)
        assertEquals("22", state.fontSizeOverlayText)
    }
}
