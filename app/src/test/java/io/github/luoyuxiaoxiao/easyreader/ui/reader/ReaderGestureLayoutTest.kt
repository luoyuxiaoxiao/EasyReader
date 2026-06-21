package io.github.luoyuxiaoxiao.easyreader.ui.reader

import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

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
    fun consumedPageTapCancelsChildTouchToAvoidWebViewTextSelection() {
        val layout = readerGestureLayout()
        val childActions = mutableListOf<Int>()
        layout.addView(
            object : View(layout.context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    childActions += event.actionMasked
                    return true
                }
            }.apply { layout(0, 0, 1080, 1920) },
        )

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 540f, y = 800f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 542f, y = 802f, eventTime = 120L))

        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), childActions)
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
    fun horizontalSwipeSwitchesChapterWithoutTogglingChrome() {
        val layout = readerGestureLayout()
        var taps = 0
        var nextChapters = 0
        layout.onChromeTap = { taps++ }
        layout.onNextChapter = { nextChapters++ }

        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x = 700f, y = 900f, eventTime = 0L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x = 610f, y = 904f, eventTime = 120L))
        layout.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x = 520f, y = 906f, eventTime = 240L))

        assertEquals(0, taps)
        assertEquals(1, nextChapters)
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

    private fun readerGestureLayout(): ReaderGestureLayout =
        ReaderGestureLayout(RuntimeEnvironment.getApplication()).apply {
            layout(0, 0, 1080, 1920)
        }

    private fun motion(action: Int, x: Float, y: Float, eventTime: Long): MotionEvent =
        MotionEvent.obtain(0L, eventTime, action, x, y, 0)
}
