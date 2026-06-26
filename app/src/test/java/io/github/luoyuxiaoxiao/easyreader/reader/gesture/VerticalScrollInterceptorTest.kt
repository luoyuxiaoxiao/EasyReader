package io.github.luoyuxiaoxiao.easyreader.reader.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerticalScrollInterceptorTest {
    @Test
    fun verticalDominantMoveStartsScrollAndPassesToChild() {
        var starts = 0
        var finishes = 0
        val interceptor = VerticalScrollInterceptor(
            density = 3f,
            onScrollStarted = { starts++ },
            onScrollFinished = { finishes++ },
        )

        val result = interceptor.onTouchEvent(
            detail(TouchPhase.MOVE, pathAbsDx = 10f, pathAbsDy = 50f, eventTime = 100L),
        )

        assertEquals(GestureResult.HANDLED_BUT_PASS_TO_CHILD, result.result)
        assertFalse(result.cancelChild)
        assertEquals(1, starts)
        assertEquals(0, finishes)
    }

    @Test
    fun lockedScrollStartsOnceAndFinishesOnce() {
        var starts = 0
        var finishes = 0
        val interceptor = VerticalScrollInterceptor(
            density = 3f,
            onScrollStarted = { starts++ },
            onScrollFinished = { finishes++ },
        )

        interceptor.onTouchEvent(detail(TouchPhase.MOVE, pathAbsDx = 10f, pathAbsDy = 50f, eventTime = 100L))
        interceptor.onTouchEvent(detail(TouchPhase.MOVE, pathAbsDx = 20f, pathAbsDy = 90f, eventTime = 140L))
        val up = interceptor.onTouchEvent(detail(TouchPhase.UP, pathAbsDx = 20f, pathAbsDy = 90f, eventTime = 180L))

        assertEquals(GestureResult.HANDLED_BUT_PASS_TO_CHILD, up.result)
        assertEquals(1, starts)
        assertEquals(1, finishes)
        assertTrue(interceptor.isSuppressed(400L))
        assertFalse(interceptor.isSuppressed(700L))
    }

    @Test
    fun horizontalOrShortMoveDoesNotStartScroll() {
        var starts = 0
        val interceptor = VerticalScrollInterceptor(
            density = 3f,
            onScrollStarted = { starts++ },
            onScrollFinished = {},
        )

        val horizontal = interceptor.onTouchEvent(
            detail(TouchPhase.MOVE, pathAbsDx = 80f, pathAbsDy = 60f, eventTime = 100L),
        )
        val short = interceptor.onTouchEvent(
            detail(TouchPhase.MOVE, pathAbsDx = 0f, pathAbsDy = 20f, eventTime = 120L),
        )

        assertEquals(GestureResult.PASS, horizontal.result)
        assertEquals(GestureResult.PASS, short.result)
        assertEquals(0, starts)
    }

    @Test
    fun cancelFinishesLockedScrollAndSuppressesChapterSwipe() {
        var finishes = 0
        val interceptor = VerticalScrollInterceptor(
            density = 3f,
            onScrollStarted = {},
            onScrollFinished = { finishes++ },
        )

        interceptor.onTouchEvent(detail(TouchPhase.MOVE, pathAbsDx = 5f, pathAbsDy = 60f, eventTime = 100L))
        val cancel = interceptor.onTouchEvent(detail(TouchPhase.CANCEL, pathAbsDx = 5f, pathAbsDy = 60f, eventTime = 160L))

        assertEquals(GestureResult.HANDLED_BUT_PASS_TO_CHILD, cancel.result)
        assertEquals(1, finishes)
        assertTrue(interceptor.isSuppressed(500L))
    }

    @Test
    fun lockedFoldedVerticalScrollWithHorizontalTailCancelsChildUp() {
        var finishes = 0
        val interceptor = VerticalScrollInterceptor(
            density = 3f,
            onScrollStarted = {},
            onScrollFinished = { finishes++ },
        )

        interceptor.onTouchEvent(detail(TouchPhase.MOVE, pathAbsDx = 10f, pathAbsDy = 300f, eventTime = 80L))
        val up = interceptor.onTouchEvent(
            detail(
                TouchPhase.UP,
                pathAbsDx = 260f,
                pathAbsDy = 570f,
                maxAbsDx = 240f,
                verticalReversed = true,
                eventTime = 260L,
            ),
        )

        assertEquals(GestureResult.CONSUMED, up.result)
        assertTrue(up.cancelChild)
        assertEquals(1, finishes)
    }

    private fun detail(
        phase: TouchPhase,
        pathAbsDx: Float,
        pathAbsDy: Float,
        eventTime: Long,
        maxAbsDx: Float = pathAbsDx,
        verticalReversed: Boolean = false,
    ): TouchDetail = TouchDetail(
        phase = phase,
        x = 100f,
        y = 100f,
        downX = 100f,
        downY = 100f,
        downTime = 0L,
        eventTime = eventTime,
        pointerCount = 1,
        netDx = 0f,
        netDy = 0f,
        maxAbsDx = maxAbsDx,
        maxAbsDy = pathAbsDy,
        pathAbsDx = pathAbsDx,
        pathAbsDy = pathAbsDy,
        velocityXPxPerSecond = 0f,
        velocityYPxPerSecond = 0f,
        durationMs = eventTime,
        screenWidthPx = 1080f,
        startedFromSystemBackEdge = false,
        isTapCandidate = false,
        isPostVerticalScrollSuppressed = false,
        isChapterSwipeAllowed = true,
        verticalReversed = verticalReversed,
    )
}
