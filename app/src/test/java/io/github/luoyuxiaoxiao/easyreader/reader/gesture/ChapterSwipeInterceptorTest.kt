package io.github.luoyuxiaoxiao.easyreader.reader.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSwipeInterceptorTest {
    @Test
    fun slowDeliberateHorizontalSwipeArmsAndCommitsNextChapter() {
        var next = 0
        val interceptor = chapterInterceptor(onNext = { next++ })

        interceptor.onTouchEvent(detail(TouchPhase.DOWN, eventTime = 0L))
        val move = interceptor.onTouchEvent(
            detail(
                TouchPhase.MOVE,
                netDx = -230f,
                pathAbsDx = 230f,
                pathAbsDy = 100f,
                maxAbsDx = 230f,
                eventTime = 120L,
            ),
        )
        val up = interceptor.onTouchEvent(
            detail(
                TouchPhase.UP,
                netDx = -230f,
                pathAbsDx = 230f,
                pathAbsDy = 100f,
                maxAbsDx = 230f,
                eventTime = 240L,
            ),
        )

        assertEquals(GestureResult.CONSUMED, move.result)
        assertTrue(move.cancelChild)
        assertEquals(GestureResult.CONSUMED, up.result)
        assertEquals(1, next)
    }

    @Test
    fun fastShortHorizontalSwipeCanCommitAfterMinimumDuration() {
        var previous = 0
        val interceptor = chapterInterceptor(onPrevious = { previous++ })

        interceptor.onTouchEvent(detail(TouchPhase.DOWN, eventTime = 0L))
        interceptor.onTouchEvent(
            detail(
                TouchPhase.MOVE,
                netDx = 160f,
                pathAbsDx = 160f,
                pathAbsDy = 35f,
                maxAbsDx = 160f,
                velocityX = 1900f,
                eventTime = 190L,
            ),
        )
        val up = interceptor.onTouchEvent(
            detail(
                TouchPhase.UP,
                netDx = 160f,
                pathAbsDx = 160f,
                pathAbsDy = 35f,
                maxAbsDx = 160f,
                velocityX = 1900f,
                eventTime = 210L,
            ),
        )

        assertEquals(GestureResult.CONSUMED, up.result)
        assertTrue(up.cancelChild)
        assertEquals(1, previous)
    }

    @Test
    fun verticalDominantSwipeDoesNotSwitchChapter() {
        var next = 0
        val interceptor = chapterInterceptor(onNext = { next++ })

        interceptor.onTouchEvent(detail(TouchPhase.DOWN, eventTime = 0L))
        val up = interceptor.onTouchEvent(
            detail(
                TouchPhase.UP,
                netDx = -120f,
                pathAbsDx = 120f,
                pathAbsDy = 260f,
                maxAbsDx = 120f,
                eventTime = 240L,
            ),
        )

        assertEquals(0, next)
        assertEquals(GestureResult.CONSUMED, up.result)
        assertTrue(up.cancelChild)
    }

    @Test
    fun systemBackEdgeSwipeConsumesHorizontalTailWithoutChapterCommit() {
        var previous = 0
        val interceptor = chapterInterceptor(onPrevious = { previous++ })

        interceptor.onTouchEvent(
            detail(TouchPhase.DOWN, downX = 40f, startedFromSystemBackEdge = true, isChapterAllowed = false),
        )
        val up = interceptor.onTouchEvent(
            detail(
                TouchPhase.UP,
                downX = 40f,
                netDx = 360f,
                pathAbsDx = 360f,
                pathAbsDy = 20f,
                maxAbsDx = 360f,
                startedFromSystemBackEdge = true,
                isChapterAllowed = false,
                eventTime = 240L,
            ),
        )

        assertEquals(GestureResult.CONSUMED, up.result)
        assertTrue(up.cancelChild)
        assertEquals(0, previous)
    }

    @Test
    fun cooldownPreventsSecondChapterCommitButStillConsumesHorizontalTail() {
        var next = 0
        val interceptor = chapterInterceptor(onNext = { next++ })

        interceptor.onTouchEvent(detail(TouchPhase.DOWN, eventTime = 0L))
        interceptor.onTouchEvent(
            detail(TouchPhase.UP, netDx = -260f, pathAbsDx = 260f, pathAbsDy = 20f, maxAbsDx = 260f, eventTime = 240L),
        )
        interceptor.onTouchEvent(detail(TouchPhase.DOWN, eventTime = 300L))
        val second = interceptor.onTouchEvent(
            detail(TouchPhase.UP, netDx = -260f, pathAbsDx = 260f, pathAbsDy = 20f, maxAbsDx = 260f, eventTime = 360L),
        )

        assertEquals(1, next)
        assertEquals(GestureResult.CONSUMED, second.result)
        assertTrue(second.cancelChild)
    }

    @Test
    fun ambiguousHorizontalTailIsConsumedWithoutChapterCommit() {
        var next = 0
        val interceptor = chapterInterceptor(onNext = { next++ })

        interceptor.onTouchEvent(detail(TouchPhase.DOWN, eventTime = 0L))
        val up = interceptor.onTouchEvent(
            detail(
                TouchPhase.UP,
                netDx = -80f,
                pathAbsDx = 90f,
                pathAbsDy = 70f,
                maxAbsDx = 90f,
                eventTime = 240L,
            ),
        )

        assertEquals(0, next)
        assertEquals(GestureResult.CONSUMED, up.result)
        assertTrue(up.cancelChild)
    }

    @Test
    fun foldedVerticalPathConsumesHorizontalTailWithoutChapterCommit() {
        var next = 0
        val interceptor = chapterInterceptor(onNext = { next++ })

        interceptor.onTouchEvent(detail(TouchPhase.DOWN, eventTime = 0L))
        val up = interceptor.onTouchEvent(
            detail(
                TouchPhase.UP,
                netDx = -240f,
                pathAbsDx = 240f,
                pathAbsDy = 120f,
                maxAbsDx = 240f,
                verticalReversed = true,
                eventTime = 260L,
            ),
        )

        assertEquals(0, next)
        assertEquals(GestureResult.CONSUMED, up.result)
        assertTrue(up.cancelChild)
    }

    @Test
    fun postVerticalSuppressConsumesHorizontalTailWithoutChapterCommit() {
        var next = 0
        val interceptor = chapterInterceptor(onNext = { next++ })

        interceptor.onTouchEvent(detail(TouchPhase.DOWN, eventTime = 0L))
        val up = interceptor.onTouchEvent(
            detail(
                TouchPhase.UP,
                netDx = -240f,
                pathAbsDx = 240f,
                pathAbsDy = 80f,
                maxAbsDx = 240f,
                isChapterAllowed = false,
                isPostVerticalScrollSuppressed = true,
                eventTime = 260L,
            ),
        )

        assertEquals(0, next)
        assertEquals(GestureResult.CONSUMED, up.result)
        assertTrue(up.cancelChild)
    }

    private fun chapterInterceptor(
        onNext: () -> Unit = {},
        onPrevious: () -> Unit = {},
    ): ChapterSwipeInterceptor = ChapterSwipeInterceptor(
        density = 3f,
        onNextChapter = onNext,
        onPreviousChapter = onPrevious,
    )

    private fun detail(
        phase: TouchPhase,
        downX: Float = 540f,
        netDx: Float = 0f,
        pathAbsDx: Float = 0f,
        pathAbsDy: Float = 0f,
        maxAbsDx: Float = pathAbsDx,
        velocityX: Float = 0f,
        startedFromSystemBackEdge: Boolean = false,
        isChapterAllowed: Boolean = true,
        isPostVerticalScrollSuppressed: Boolean = false,
        verticalReversed: Boolean = false,
        eventTime: Long = 0L,
    ): TouchDetail = TouchDetail(
        phase = phase,
        x = downX + netDx,
        y = 100f,
        downX = downX,
        downY = 100f,
        downTime = 0L,
        eventTime = eventTime,
        pointerCount = 1,
        netDx = netDx,
        netDy = 0f,
        maxAbsDx = maxAbsDx,
        maxAbsDy = pathAbsDy,
        pathAbsDx = pathAbsDx,
        pathAbsDy = pathAbsDy,
        velocityXPxPerSecond = velocityX,
        velocityYPxPerSecond = 0f,
        durationMs = eventTime,
        screenWidthPx = 1080f,
        startedFromSystemBackEdge = startedFromSystemBackEdge,
        isTapCandidate = false,
        isPostVerticalScrollSuppressed = isPostVerticalScrollSuppressed,
        isChapterSwipeAllowed = isChapterAllowed,
        verticalReversed = verticalReversed,
    )
}
