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
    fun explicitTapIsTheOnlyPathThatShowsTopChrome() {
        val fromScroll = ReaderUiState().afterScrollStarted().afterScrollFinished()
        val fromVisibleThenScroll = ReaderUiState(globalChromeVisible = true)
            .afterScrollStarted()
            .afterScrollFinished()
        val fromChapterFeedback = ReaderUiState().afterTransientBottomChrome(message = null)
        val fromEdgeMessage = ReaderUiState().afterTransientBottomChrome(message = "已经到达边界")
        val fromTap = ReaderUiState().afterExplicitChromeTap()

        assertFalse(fromScroll.topChromeVisible)
        assertFalse(fromVisibleThenScroll.topChromeVisible)
        assertFalse(fromChapterFeedback.topChromeVisible)
        assertFalse(fromEdgeMessage.topChromeVisible)
        assertTrue(fromTap.topChromeVisible)
    }

    @Test
    fun locatorUpdatesRefreshOnlyTransientBottomChrome() {
        val transientBottom = ReaderUiState(scrollProgressVisible = true)
        val explicitChrome = ReaderUiState(globalChromeVisible = true)
        val idleHidden = ReaderUiState()

        assertTrue(transientBottom.shouldRefreshScrollProgressHideOnLocatorChanged)
        assertFalse(explicitChrome.shouldRefreshScrollProgressHideOnLocatorChanged)
        assertFalse(idleHidden.shouldRefreshScrollProgressHideOnLocatorChanged)
    }

    @Test
    fun webViewScrollUpdatesOnlyBottomProgress() {
        val state = ReaderUiState(globalChromeVisible = true, totalProgressText = "74.47%")
            .afterReaderContentScrolled(totalProgression = 0.815, chapterProgression = 0.62)

        assertFalse(state.topChromeVisible)
        assertTrue(state.bottomChromeVisible)
        assertEquals("81.50%", state.totalProgressText)
        assertEquals("62.00%", state.chapterProgressText)
    }

    @Test
    fun chapterOpenImmediatelyRefreshesBottomProgressAtChapterStart() {
        val state = ReaderUiState(globalChromeVisible = true, totalProgressText = "74.47%")
            .afterReaderChapterOpened(totalProgression = 10.0 / 35.0, chapterProgression = 0.0)

        assertFalse(state.topChromeVisible)
        assertTrue(state.bottomChromeVisible)
        assertEquals("28.57%", state.totalProgressText)
        assertEquals("0.00%", state.chapterProgressText)
    }

    @Test
    fun locatorProgressDoesNotOverwriteLiveScrollProgress() {
        val state = ReaderUiState()
            .afterReaderContentScrolled(totalProgression = 1.0, chapterProgression = 1.0)
            .afterLocatorProgressChanged(totalProgression = 0.9725, chapterProgression = 0.9725)

        assertEquals("100.00%", state.totalProgressText)
        assertEquals("100.00%", state.chapterProgressText)
    }

    @Test
    fun locatorProgressDoesNotUpdateIdleChromeProgress() {
        val state = ReaderUiState(globalChromeVisible = true)
            .afterLocatorProgressChanged(totalProgression = 0.9725, chapterProgression = 0.9725)

        assertEquals("0.00%", state.totalProgressText)
        assertEquals("0.00%", state.chapterProgressText)
    }

    @Test
    fun fontOverlayShowsCurrentSizeLabel() {
        val state = ReaderUiState(fontSizeOverlayText = "22")

        assertTrue(state.fontSizeOverlayVisible)
        assertEquals("22", state.fontSizeOverlayText)
    }
}
