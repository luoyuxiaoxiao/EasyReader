package io.github.luoyuxiaoxiao.easyreader.reader.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterSwipeDetectorTest {
    private val detector = ChapterSwipeDetector(screenWidthPx = 1080f, density = 3f)

    @Test
    fun leftSwipeSwitchesToNextChapter() {
        val event = detector.evaluate(startXPx = 540f, dxPx = -300f, dyPx = 80f, velocityXPxPerSecond = -1200f)
        assertEquals(ChapterSwipeDecision.NextChapter, event)
    }

    @Test
    fun rightSwipeSwitchesToPreviousChapter() {
        val event = detector.evaluate(startXPx = 540f, dxPx = 300f, dyPx = 80f, velocityXPxPerSecond = 1200f)
        assertEquals(ChapterSwipeDecision.PreviousChapter, event)
    }

    @Test
    fun nearHorizontalSwipeSwitchesChapterWithoutDiagonalMovement() {
        val next = detector.evaluate(startXPx = 540f, dxPx = -230f, dyPx = 8f, velocityXPxPerSecond = -900f)
        val previous = detector.evaluate(startXPx = 540f, dxPx = 230f, dyPx = 8f, velocityXPxPerSecond = 900f)

        assertEquals(ChapterSwipeDecision.NextChapter, next)
        assertEquals(ChapterSwipeDecision.PreviousChapter, previous)
    }

    @Test
    fun oldMediumHorizontalSwipeNoLongerSwitchesChapter() {
        val event = detector.evaluate(startXPx = 540f, dxPx = -170f, dyPx = 8f, velocityXPxPerSecond = -900f)

        assertEquals(ChapterSwipeDecision.KeepReading, event)
    }

    @Test
    fun longerHorizontalSwipeStillSwitchesChapter() {
        val next = detector.evaluate(startXPx = 540f, dxPx = -230f, dyPx = 20f, velocityXPxPerSecond = -1000f)
        val previous = detector.evaluate(startXPx = 540f, dxPx = 230f, dyPx = 20f, velocityXPxPerSecond = 1000f)

        assertEquals(ChapterSwipeDecision.NextChapter, next)
        assertEquals(ChapterSwipeDecision.PreviousChapter, previous)
    }

    @Test
    fun verticalScrollNeverSwitchesChapter() {
        val event = detector.evaluate(startXPx = 540f, dxPx = 160f, dyPx = 420f, velocityXPxPerSecond = 900f)
        assertEquals(ChapterSwipeDecision.KeepReading, event)
    }

    @Test
    fun shortSlowHorizontalMovementDoesNotSwitchChapter() {
        val event = detector.evaluate(startXPx = 540f, dxPx = -120f, dyPx = 20f, velocityXPxPerSecond = -240f)
        assertEquals(ChapterSwipeDecision.KeepReading, event)
    }

    @Test
    fun diagonalMovementMustBeStronglyHorizontal() {
        val event = detector.evaluate(startXPx = 540f, dxPx = -220f, dyPx = 120f, velocityXPxPerSecond = -1400f)
        assertEquals(ChapterSwipeDecision.KeepReading, event)
    }

    @Test
    fun edgeSwipeStaysAvailableForSystemBackGesture() {
        val leftEdge = detector.evaluate(startXPx = 40f, dxPx = 360f, dyPx = 30f, velocityXPxPerSecond = 1800f)
        val rightEdge = detector.evaluate(startXPx = 1040f, dxPx = -360f, dyPx = 30f, velocityXPxPerSecond = -1800f)

        assertEquals(ChapterSwipeDecision.KeepReading, leftEdge)
        assertEquals(ChapterSwipeDecision.KeepReading, rightEdge)
    }

    @Test
    fun fastFlingStillRequiresStrongHorizontalDirection() {
        val event = detector.evaluate(startXPx = 540f, dxPx = -180f, dyPx = 100f, velocityXPxPerSecond = -3200f)
        assertEquals(ChapterSwipeDecision.KeepReading, event)
    }
}
