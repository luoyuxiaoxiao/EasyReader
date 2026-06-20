package io.github.luoyuxiaoxiao.easyreader.reader.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterSwipeDetectorTest {
    private val detector = ChapterSwipeDetector(screenWidthPx = 1080f, density = 3f)

    @Test
    fun leftSwipeSwitchesToNextChapter() {
        val event = detector.evaluate(dxPx = -360f, dyPx = 40f, velocityXPxPerSecond = -1200f)
        assertEquals(ChapterSwipeDecision.NextChapter, event)
    }

    @Test
    fun rightSwipeSwitchesToPreviousChapter() {
        val event = detector.evaluate(dxPx = 360f, dyPx = 40f, velocityXPxPerSecond = 1200f)
        assertEquals(ChapterSwipeDecision.PreviousChapter, event)
    }

    @Test
    fun verticalScrollNeverSwitchesChapter() {
        val event = detector.evaluate(dxPx = 160f, dyPx = 420f, velocityXPxPerSecond = 900f)
        assertEquals(ChapterSwipeDecision.KeepReading, event)
    }

    @Test
    fun shortSlowHorizontalMovementDoesNotSwitchChapter() {
        val event = detector.evaluate(dxPx = -120f, dyPx = 20f, velocityXPxPerSecond = -240f)
        assertEquals(ChapterSwipeDecision.KeepReading, event)
    }
}
