package io.github.luoyuxiaoxiao.easyreader.ui.reader

import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ReaderGestureLayoutTest {
    @Test
    fun shortTapTogglesChromeOnce() {
        val layout = readerGestureLayout()
        var taps = 0
        layout.onChromeTap = { taps++ }

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 540f, y = 800f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 542f, y = 802f, eventTime = 120L))

        assertEquals(1, taps)
    }

    @Test
    fun consumedReaderContentTapDoesNotToggleChrome() {
        val layout = readerGestureLayout()
        val childActions = mutableListOf<Int>()
        var taps = 0
        var contentTapConsumed = false
        layout.onChromeTap = { taps++ }
        layout.onReaderContentTapConsumed = {
            contentTapConsumed.also { contentTapConsumed = false }
        }
        layout.addView(
            object : View(layout.context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    childActions += event.actionMasked
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        contentTapConsumed = true
                    }
                    return true
                }
            }.apply { layout(0, 0, 1080, 1920) },
        )

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 540f, y = 800f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 542f, y = 802f, eventTime = 120L))

        assertEquals(0, taps)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), childActions)
    }

    @Test
    fun delayedReaderContentTapConsumptionDoesNotToggleChrome() {
        val layout = readerGestureLayout()
        var taps = 0
        var contentTapConsumed = false
        layout.onChromeTap = { taps++ }
        layout.onReaderContentTapConsumed = {
            contentTapConsumed.also { contentTapConsumed = false }
        }
        layout.addView(
            object : View(layout.context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        // 模拟 WebView 的 JS/Readium 回调稍晚于 ACTION_UP 返回。
                        Handler(Looper.getMainLooper()).post { contentTapConsumed = true }
                    }
                    return true
                }
            }.apply { layout(0, 0, 1080, 1920) },
        )

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 540f, y = 800f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 542f, y = 802f, eventTime = 120L))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

        assertEquals(0, taps)
    }

    @Test
    fun childHandledPlainTapTogglesChromeAfterContentWindow() {
        val layout = readerGestureLayout()
        var taps = 0
        layout.onChromeTap = { taps++ }
        layout.addView(
            object : View(layout.context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean = true
            }.apply { layout(0, 0, 1080, 1920) },
        )

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 540f, y = 800f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 542f, y = 802f, eventTime = 120L))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(220))

        assertEquals(1, taps)
    }

    @Test
    fun topChromeTapPassesThroughToToolbarControls() {
        val layout = readerGestureLayout()
        val childActions = mutableListOf<Int>()
        var taps = 0
        layout.topChromeControlsVisible = true
        layout.onChromeTap = { taps++ }
        layout.addView(
            object : View(layout.context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    childActions += event.actionMasked
                    return true
                }
            }.apply { layout(0, 0, 1080, 1920) },
        )

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 100f, y = 80f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 100f, y = 82f, eventTime = 120L))

        assertEquals(0, taps)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), childActions)
    }

    @Test
    fun topChromeHorizontalDragCancelsChildUp() {
        val layout = readerGestureLayout()
        val childActions = mutableListOf<Int>()
        var taps = 0
        layout.topChromeControlsVisible = true
        layout.onChromeTap = { taps++ }
        layout.addView(
            object : View(layout.context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    childActions += event.actionMasked
                    return true
                }
            }.apply { layout(0, 0, 1080, 1920) },
        )

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 100f, y = 80f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 260f, y = 82f, eventTime = 120L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 420f, y = 82f, eventTime = 240L))

        assertEquals(0, taps)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), childActions)
    }

    @Test
    fun deliberateHorizontalDragSwitchesChapterWithoutTogglingChrome() {
        val layout = readerGestureLayout()
        var taps = 0
        var nextChapters = 0
        layout.onChromeTap = { taps++ }
        layout.onNextChapter = { nextChapters++ }

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 700f, y = 900f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 580f, y = 904f, eventTime = 120L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 460f, y = 906f, eventTime = 240L))

        assertEquals(0, taps)
        assertEquals(1, nextChapters)
    }

    @Test
    fun historicalMoveArmsChapterSwipeAndCancelsChildBeforeUp() {
        val layout = readerGestureLayout()
        val childActions = mutableListOf<Int>()
        var nextChapters = 0
        layout.onNextChapter = { nextChapters++ }
        layout.addView(
            object : View(layout.context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    childActions += event.actionMasked
                    return true
                }
            }.apply { layout(0, 0, 1080, 1920) },
        )

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 800f, y = 900f, eventTime = 0L))
        layout.dispatchTouchEvent(
            motion(MotionEvent.ACTION_MOVE, x = 650f, y = 900f, eventTime = 20L).apply {
                addBatch(40L, 550f, 900f, 1f, 1f, 0)
                addBatch(60L, 700f, 900f, 1f, 1f, 0)
            },
        )
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 560f, y = 900f, eventTime = 240L))

        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), childActions)
        assertEquals(1, nextChapters)
    }

    @Test
    fun fastHorizontalFlingDoesNotSwitchChapter() {
        val layout = readerGestureLayout()
        var nextChapters = 0
        layout.onNextChapter = { nextChapters++ }

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 700f, y = 900f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 580f, y = 904f, eventTime = 35L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 460f, y = 906f, eventTime = 80L))

        assertEquals(0, nextChapters)
    }

    @Test
    fun explicitTapReportsReaderTapCandidateBeforeChromeDecision() {
        val layout = readerGestureLayout()
        val candidates = mutableListOf<Pair<Float, Float>>()
        var taps = 0
        layout.onChromeTap = { taps++ }
        layout.onReaderTapCandidate = { x, y -> candidates += x to y }
        layout.addView(
            object : View(layout.context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean = true
            }.apply { layout(0, 0, 1080, 1920) },
        )

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 540f, y = 800f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 542f, y = 802f, eventTime = 120L))

        assertEquals(listOf(542f to 802f), candidates)
        assertEquals(0, taps)
    }

    @Test
    fun verticalScrollDoesNotToggleChrome() {
        val layout = readerGestureLayout()
        var taps = 0
        var scrollStarts = 0
        var scrollFinishes = 0
        layout.onChromeTap = { taps++ }
        layout.onVerticalScrollStarted = { scrollStarts++ }
        layout.onVerticalScrollFinished = { scrollFinishes++ }

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 540f, y = 600f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 545f, y = 720f, eventTime = 140L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 548f, y = 820f, eventTime = 280L))

        assertEquals(0, taps)
        assertEquals(1, scrollStarts)
        assertEquals(1, scrollFinishes)
    }

    @Test
    fun immediateReverseScrollAfterVerticalScrollDoesNotSwitchChapter() {
        val layout = readerGestureLayout()
        var nextChapters = 0
        layout.onNextChapter = { nextChapters++ }

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 540f, y = 600f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 545f, y = 760f, eventTime = 120L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 550f, y = 820f, eventTime = 240L))

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 550f, y = 820f, eventTime = 320L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 430f, y = 780f, eventTime = 420L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 300f, y = 760f, eventTime = 540L))

        assertEquals(0, nextChapters)
    }

    @Test
    fun horizontalTailDuringPostVerticalSuppressCancelsChildUpWithoutSwitchingChapter() {
        val layout = readerGestureLayout()
        val childActions = mutableListOf<Int>()
        var nextChapters = 0
        layout.onNextChapter = { nextChapters++ }
        layout.addView(
            object : View(layout.context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    childActions += event.actionMasked
                    return true
                }
            }.apply { layout(0, 0, 1080, 1920) },
        )

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 540f, y = 600f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 545f, y = 760f, eventTime = 120L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 550f, y = 820f, eventTime = 240L))

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 550f, y = 820f, eventTime = 320L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 430f, y = 780f, eventTime = 420L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 300f, y = 760f, eventTime = 540L))

        assertEquals(0, nextChapters)
        assertEquals(
            listOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_CANCEL,
            ),
            childActions,
        )
    }

    @Test
    fun systemBackEdgeHorizontalTailCancelsChildUpWithoutSwitchingChapter() {
        val layout = readerGestureLayout()
        val childActions = mutableListOf<Int>()
        var previousChapters = 0
        layout.onPreviousChapter = { previousChapters++ }
        layout.addView(
            object : View(layout.context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    childActions += event.actionMasked
                    return true
                }
            }.apply { layout(0, 0, 1080, 1920) },
        )

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 20f, y = 900f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 180f, y = 906f, eventTime = 120L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 360f, y = 908f, eventTime = 260L))

        assertEquals(0, previousChapters)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_CANCEL), childActions)
    }

    @Test
    fun foldedVerticalPathWithinOneGestureDoesNotSwitchChapter() {
        val layout = readerGestureLayout()
        var nextChapters = 0
        layout.onNextChapter = { nextChapters++ }

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 700f, y = 900f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 620f, y = 950f, eventTime = 90L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 500f, y = 820f, eventTime = 180L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 460f, y = 820f, eventTime = 260L))

        assertEquals(0, nextChapters)
    }

    @Test
    fun foldedVerticalGestureWithHorizontalDriftCancelsChildUpWithoutSwitchingChapter() {
        val layout = readerGestureLayout()
        val childActions = mutableListOf<Int>()
        var nextChapters = 0
        layout.onNextChapter = { nextChapters++ }
        layout.addView(
            object : View(layout.context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    childActions += event.actionMasked
                    return true
                }
            }.apply { layout(0, 0, 1080, 1920) },
        )

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 700f, y = 900f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 590f, y = 960f, eventTime = 90L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 480f, y = 900f, eventTime = 180L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 460f, y = 900f, eventTime = 260L))

        assertEquals(0, nextChapters)
        assertEquals(
            listOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_CANCEL,
            ),
            childActions,
        )
    }

    @Test
    fun lockedVerticalFoldWithHorizontalDriftCancelsChildUpWithoutSwitchingChapter() {
        val layout = readerGestureLayout()
        val childActions = mutableListOf<Int>()
        var nextChapters = 0
        var scrollFinishes = 0
        layout.onNextChapter = { nextChapters++ }
        layout.onVerticalScrollFinished = { scrollFinishes++ }
        layout.addView(
            object : View(layout.context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    childActions += event.actionMasked
                    return true
                }
            }.apply { layout(0, 0, 1080, 1920) },
        )

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 700f, y = 900f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 710f, y = 1200f, eventTime = 80L))
        layout.dispatchTouchEvent(
            motion(MotionEvent.ACTION_MOVE, x = 500f, y = 930f, eventTime = 180L).apply {
                addBatch(120L, 650f, 1120f, 1f, 1f, 0)
                addBatch(150L, 560f, 1000f, 1f, 1f, 0)
            },
        )
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 460f, y = 930f, eventTime = 260L))

        assertEquals(0, nextChapters)
        assertEquals(1, scrollFinishes)
        assertEquals(
            listOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_CANCEL,
            ),
            childActions,
        )
    }

    @Test
    fun multiTouchGestureDoesNotSwitchChapter() {
        val layout = readerGestureLayout()
        val childActions = mutableListOf<Int>()
        var nextChapters = 0
        layout.onNextChapter = { nextChapters++ }
        layout.addView(
            object : View(layout.context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    childActions += event.actionMasked
                    return true
                }
            }.apply { layout(0, 0, 1080, 1920) },
        )

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 700f, y = 900f, eventTime = 0L))
        layout.dispatchTouchEvent(
            multiTouchMotion(
                action = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                firstX = 700f,
                firstY = 900f,
                secondX = 650f,
                secondY = 900f,
                eventTime = 40L,
            ),
        )
        layout.dispatchTouchEvent(
            multiTouchMotion(
                action = MotionEvent.ACTION_MOVE,
                firstX = 500f,
                firstY = 900f,
                secondX = 450f,
                secondY = 900f,
                eventTime = 120L,
            ),
        )
        layout.dispatchTouchEvent(
            multiTouchMotion(
                action = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                firstX = 500f,
                firstY = 900f,
                secondX = 450f,
                secondY = 900f,
                eventTime = 180L,
            ),
        )
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 460f, y = 900f, eventTime = 260L))

        assertEquals(0, nextChapters)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), childActions)
    }

    private fun readerGestureLayout(): ReaderGestureLayout =
        ReaderGestureLayout(RuntimeEnvironment.getApplication()).apply {
            layout(0, 0, 1080, 1920)
        }

    private fun motion(action: Int, x: Float, y: Float, eventTime: Long): MotionEvent =
        MotionEvent.obtain(0L, eventTime, action, x, y, 0)

    private fun multiTouchMotion(
        action: Int,
        firstX: Float,
        firstY: Float,
        secondX: Float,
        secondY: Float,
        eventTime: Long,
    ): MotionEvent {
        val pointerProperties = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            },
            MotionEvent.PointerProperties().apply {
                id = 1
                toolType = MotionEvent.TOOL_TYPE_FINGER
            },
        )
        val pointerCoords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = firstX
                y = firstY
                pressure = 1f
                size = 1f
            },
            MotionEvent.PointerCoords().apply {
                x = secondX
                y = secondY
                pressure = 1f
                size = 1f
            },
        )
        return MotionEvent.obtain(
            0L,
            eventTime,
            action,
            pointerProperties.size,
            pointerProperties,
            pointerCoords,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
    }
}
