package io.github.luoyuxiaoxiao.easyreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderScrollProgressTest {
    @Test
    fun chapterProgressionUsesActualWebViewScrollRange() {
        val progression = ReaderScrollProgress.chapterProgression(
            scrollY = 600,
            viewportHeightPx = 1000,
            contentHeightPx = 2200f,
        )

        assertEquals(0.5, progression!!, 0.001)
    }

    @Test
    fun shortChapterIsFullyVisibleWhenItDoesNotScroll() {
        val progression = ReaderScrollProgress.chapterProgression(
            scrollY = 0,
            viewportHeightPx = 1200,
            contentHeightPx = 900f,
        )

        assertEquals(1.0, progression!!, 0.001)
    }

    @Test
    fun syntheticSampleKeepsFirstNonScrollableChapterAtStart() {
        val progression = ReaderScrollProgress.chapterProgression(
            scrollY = 0,
            viewportHeightPx = 1200,
            contentHeightPx = 900f,
            nonScrollableProgression = ReaderScrollProgress.syntheticNonScrollableProgression(
                readingOrderIndex = 0,
                readingOrderCount = 4,
            ),
        )

        assertNull(progression)
    }

    @Test
    fun syntheticSampleKeepsMiddleNonScrollableChapterUnchanged() {
        val progression = ReaderScrollProgress.chapterProgression(
            scrollY = 0,
            viewportHeightPx = 1200,
            contentHeightPx = 900f,
            nonScrollableProgression = ReaderScrollProgress.syntheticNonScrollableProgression(
                readingOrderIndex = 2,
                readingOrderCount = 4,
            ),
        )

        assertNull(progression)
    }

    @Test
    fun syntheticSampleCompletesLastNonScrollableChapter() {
        val progression = ReaderScrollProgress.chapterProgression(
            scrollY = 0,
            viewportHeightPx = 1200,
            contentHeightPx = 900f,
            nonScrollableProgression = ReaderScrollProgress.syntheticNonScrollableProgression(
                readingOrderIndex = 3,
                readingOrderCount = 4,
            ),
        )

        assertEquals(1.0, progression!!, 0.001)
    }

    @Test
    fun chapterProgressionSnapsToEndNearBottom() {
        val progression = ReaderScrollProgress.chapterProgression(
            scrollY = 1199,
            viewportHeightPx = 1000,
            contentHeightPx = 2200f,
        )

        assertEquals(1.0, progression!!, 0.001)
    }

    @Test
    fun webViewContentHeightStaysInScrollCoordinatesWithoutScale() {
        assertEquals(2200f, ReaderScrollProgress.webViewContentHeightPx(contentHeight = 2200), 0.001f)
    }

    @Test
    fun totalProgressionUsesChapterWeights() {
        val progression = ReaderScrollProgress.totalProgression(
            chapterWeights = listOf(10, 20, 5),
            readingOrderIndex = 1,
            chapterProgression = 0.5,
        )

        assertEquals(20.0 / 35.0, progression!!, 0.001)
    }

    @Test
    fun chapterStartTotalProgressionUsesCompletedPreviousChapterWeights() {
        val progression = ReaderScrollProgress.chapterStartTotalProgression(
            chapterWeights = listOf(10, 20, 5),
            readingOrderIndex = 1,
        )

        assertEquals(10.0 / 35.0, progression!!, 0.001)
    }

    @Test
    fun firstChapterStartProgressionStaysAtZeroForCoverDisplay() {
        val totalProgression = ReaderScrollProgress.chapterStartTotalProgression(
            chapterWeights = listOf(10, 20, 5),
            readingOrderIndex = 0,
        )

        assertEquals(0.0, ReaderScrollProgress.CHAPTER_START_PROGRESSION, 0.001)
        assertEquals(0.0, totalProgression!!, 0.001)
    }

    @Test
    fun totalProgressionFallsBackWhenWeightsAreMissing() {
        assertNull(ReaderScrollProgress.totalProgression(emptyList(), 0, 0.5))
    }
}
