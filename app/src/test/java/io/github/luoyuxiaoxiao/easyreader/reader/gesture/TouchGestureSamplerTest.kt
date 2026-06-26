package io.github.luoyuxiaoxiao.easyreader.reader.gesture

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TouchGestureSamplerTest {
    @Test
    fun moveDetailAccumulatesHistoricalPathAndDerivedMotionFields() {
        val sampler = TouchGestureSampler(density = 3f)
        sampler.onTouchEvent(
            event = motion(MotionEvent.ACTION_DOWN, x = 100f, y = 100f, eventTime = 0L),
            screenWidthPx = 1080f,
            isPostVerticalScrollSuppressed = false,
        )
        val move = motion(MotionEvent.ACTION_MOVE, x = 120f, y = 110f, eventTime = 10L).apply {
            addBatch(10L, 120f, 110f, 1f, 1f, 0)
            addBatch(20L, 150f, 120f, 1f, 1f, 0)
            addBatch(40L, 160f, 150f, 1f, 1f, 0)
        }

        val detail = sampler.onTouchEvent(
            event = move,
            screenWidthPx = 1080f,
            isPostVerticalScrollSuppressed = false,
        )

        assertEquals(60f, detail.netDx, 0.01f)
        assertEquals(50f, detail.netDy, 0.01f)
        assertEquals(60f, detail.maxAbsDx, 0.01f)
        assertEquals(50f, detail.maxAbsDy, 0.01f)
        assertEquals(60f, detail.pathAbsDx, 0.01f)
        assertEquals(50f, detail.pathAbsDy, 0.01f)
        assertEquals(1500f, detail.velocityXPxPerSecond, 0.01f)
        assertEquals(1250f, detail.velocityYPxPerSecond, 0.01f)
        assertEquals(40L, detail.durationMs)
        assertFalse(detail.isTapCandidate)
        assertTrue(detail.isChapterSwipeAllowed)
    }

    @Test
    fun shortSmallUpIsTapCandidate() {
        val sampler = TouchGestureSampler(density = 3f)
        sampler.onTouchEvent(
            event = motion(MotionEvent.ACTION_DOWN, x = 100f, y = 100f, eventTime = 0L),
            screenWidthPx = 1080f,
            isPostVerticalScrollSuppressed = false,
        )

        val detail = sampler.onTouchEvent(
            event = motion(MotionEvent.ACTION_UP, x = 106f, y = 107f, eventTime = 120L),
            screenWidthPx = 1080f,
            isPostVerticalScrollSuppressed = false,
        )

        assertTrue(detail.isTapCandidate)
        assertFalse(detail.isChapterSwipeAllowed)
    }

    @Test
    fun verticalReversalIsDetectedFromGestureExtremes() {
        val sampler = TouchGestureSampler(density = 3f)
        sampler.onTouchEvent(
            event = motion(MotionEvent.ACTION_DOWN, x = 100f, y = 100f, eventTime = 0L),
            screenWidthPx = 1080f,
            isPostVerticalScrollSuppressed = false,
        )
        sampler.onTouchEvent(
            event = motion(MotionEvent.ACTION_MOVE, x = 140f, y = 160f, eventTime = 80L),
            screenWidthPx = 1080f,
            isPostVerticalScrollSuppressed = false,
        )

        val detail = sampler.onTouchEvent(
            event = motion(MotionEvent.ACTION_MOVE, x = 220f, y = 100f, eventTime = 180L),
            screenWidthPx = 1080f,
            isPostVerticalScrollSuppressed = false,
        )

        assertTrue(detail.verticalReversed)
    }

    @Test
    fun systemBackEdgeDisablesChapterSwipe() {
        val sampler = TouchGestureSampler(density = 3f)

        val detail = sampler.onTouchEvent(
            event = motion(MotionEvent.ACTION_DOWN, x = 80f, y = 100f, eventTime = 0L),
            screenWidthPx = 1080f,
            isPostVerticalScrollSuppressed = false,
        )

        assertTrue(detail.startedFromSystemBackEdge)
        assertFalse(detail.isChapterSwipeAllowed)
    }

    @Test
    fun postVerticalScrollSuppressDisablesChapterSwipe() {
        val sampler = TouchGestureSampler(density = 3f)
        sampler.onTouchEvent(
            event = motion(MotionEvent.ACTION_DOWN, x = 540f, y = 100f, eventTime = 0L),
            screenWidthPx = 1080f,
            isPostVerticalScrollSuppressed = true,
        )

        val detail = sampler.onTouchEvent(
            event = motion(MotionEvent.ACTION_MOVE, x = 700f, y = 110f, eventTime = 200L),
            screenWidthPx = 1080f,
            isPostVerticalScrollSuppressed = true,
        )

        assertFalse(detail.startedFromSystemBackEdge)
        assertTrue(detail.isPostVerticalScrollSuppressed)
        assertFalse(detail.isChapterSwipeAllowed)
    }

    private fun motion(action: Int, x: Float, y: Float, eventTime: Long): MotionEvent =
        MotionEvent.obtain(0L, eventTime, action, x, y, 0)
}
