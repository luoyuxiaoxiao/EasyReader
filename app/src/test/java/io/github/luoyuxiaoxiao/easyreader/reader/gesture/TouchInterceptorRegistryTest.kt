package io.github.luoyuxiaoxiao.easyreader.reader.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchInterceptorRegistryTest {
    @Test
    fun higherPriorityInterceptorConsumesBeforeLowerPriority() {
        val calls = mutableListOf<String>()
        val registry = TouchInterceptorRegistry()
        registry.add(recordingInterceptor(tag = "low", priority = 1, calls = calls))
        registry.add(
            recordingInterceptor(
                tag = "high",
                priority = 10,
                calls = calls,
                result = TouchDisposition.consumed(cancelChild = true),
            ),
        )

        val result = registry.dispatch(detail(TouchPhase.MOVE))

        assertEquals(listOf("high:MOVE"), calls)
        assertEquals(GestureResult.CONSUMED, result.result)
        assertTrue(result.cancelChild)
        assertTrue(registry.gestureConsumed)
    }

    @Test
    fun consumedMoveLocksGestureOwnerUntilUp() {
        val calls = mutableListOf<String>()
        val registry = TouchInterceptorRegistry()
        registry.add(
            recordingInterceptor(
                tag = "owner",
                priority = 10,
                calls = calls,
                result = TouchDisposition.consumed(cancelChild = true),
            ),
        )
        registry.add(
            recordingInterceptor(
                tag = "other",
                priority = 1,
                calls = calls,
                result = TouchDisposition.consumed(cancelChild = true),
            ),
        )

        registry.dispatch(detail(TouchPhase.MOVE))
        registry.dispatch(detail(TouchPhase.MOVE))
        val up = registry.dispatch(detail(TouchPhase.UP))

        assertEquals(listOf("owner:MOVE", "owner:MOVE", "owner:UP"), calls)
        assertEquals(GestureResult.CONSUMED, up.result)
        assertFalse(registry.gestureConsumed)
    }

    @Test
    fun cancelClearsGestureOwner() {
        val calls = mutableListOf<String>()
        val registry = TouchInterceptorRegistry()
        registry.add(
            recordingInterceptor(
                tag = "owner",
                priority = 10,
                calls = calls,
                result = TouchDisposition.consumed(cancelChild = true),
            ),
        )

        registry.dispatch(detail(TouchPhase.MOVE))
        registry.dispatch(detail(TouchPhase.CANCEL))
        registry.dispatch(detail(TouchPhase.MOVE))

        assertEquals(listOf("owner:MOVE", "owner:CANCEL", "owner:MOVE"), calls)
        assertTrue(registry.gestureConsumed)
    }

    @Test
    fun handledButPassToChildDoesNotLockOwner() {
        val calls = mutableListOf<String>()
        val registry = TouchInterceptorRegistry()
        registry.add(
            recordingInterceptor(
                tag = "vertical",
                priority = 10,
                calls = calls,
                result = TouchDisposition.handledButPassToChild(),
            ),
        )
        registry.add(
            recordingInterceptor(
                tag = "chapter",
                priority = 1,
                calls = calls,
                result = TouchDisposition.consumed(cancelChild = true),
            ),
        )

        val first = registry.dispatch(detail(TouchPhase.MOVE))
        val second = registry.dispatch(detail(TouchPhase.MOVE))

        assertEquals(listOf("vertical:MOVE", "vertical:MOVE"), calls)
        assertEquals(GestureResult.HANDLED_BUT_PASS_TO_CHILD, first.result)
        assertEquals(GestureResult.HANDLED_BUT_PASS_TO_CHILD, second.result)
        assertFalse(registry.gestureConsumed)
    }

    private fun recordingInterceptor(
        tag: String,
        priority: Int,
        calls: MutableList<String>,
        result: TouchDisposition = TouchDisposition.pass(),
    ): TouchInterceptor = object : TouchInterceptor {
        override val priority: Int = priority
        override val tag: String = tag

        override fun onTouchEvent(detail: TouchDetail): TouchDisposition {
            calls += "$tag:${detail.phase}"
            return result
        }
    }

    private fun detail(phase: TouchPhase): TouchDetail = TouchDetail(
        phase = phase,
        x = 100f,
        y = 100f,
        downX = 100f,
        downY = 100f,
        downTime = 0L,
        eventTime = 16L,
        pointerCount = 1,
        netDx = 0f,
        netDy = 0f,
        maxAbsDx = 0f,
        maxAbsDy = 0f,
        pathAbsDx = 0f,
        pathAbsDy = 0f,
        velocityXPxPerSecond = 0f,
        velocityYPxPerSecond = 0f,
        durationMs = 16L,
        screenWidthPx = 1080f,
        startedFromSystemBackEdge = false,
        isTapCandidate = false,
        isPostVerticalScrollSuppressed = false,
        isChapterSwipeAllowed = true,
    )
}
