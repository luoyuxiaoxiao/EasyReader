package io.github.luoyuxiaoxiao.easyreader.reader.gesture

import android.view.MotionEvent

enum class TouchPhase {
    DOWN,
    MOVE,
    UP,
    CANCEL,
    POINTER_DOWN,
    POINTER_UP,
    ;

    companion object {
        fun fromActionMasked(action: Int): TouchPhase = when (action) {
            MotionEvent.ACTION_DOWN -> DOWN
            MotionEvent.ACTION_MOVE -> MOVE
            MotionEvent.ACTION_UP -> UP
            MotionEvent.ACTION_CANCEL -> CANCEL
            MotionEvent.ACTION_POINTER_DOWN -> POINTER_DOWN
            MotionEvent.ACTION_POINTER_UP -> POINTER_UP
            else -> CANCEL
        }
    }
}

data class TouchDetail(
    val phase: TouchPhase,
    val x: Float,
    val y: Float,
    val downX: Float,
    val downY: Float,
    val downTime: Long,
    val eventTime: Long,
    val pointerCount: Int,
    val netDx: Float,
    val netDy: Float,
    val maxAbsDx: Float,
    val maxAbsDy: Float,
    val pathAbsDx: Float,
    val pathAbsDy: Float,
    val velocityXPxPerSecond: Float,
    val velocityYPxPerSecond: Float,
    val durationMs: Long,
    val screenWidthPx: Float,
    val startedFromSystemBackEdge: Boolean,
    val isTapCandidate: Boolean,
    val isPostVerticalScrollSuppressed: Boolean,
    val isChapterSwipeAllowed: Boolean,
    val verticalReversed: Boolean = false,
)

enum class GestureResult {
    CONSUMED,
    HANDLED_BUT_PASS_TO_CHILD,
    PASS,
}

data class TouchDisposition(
    val result: GestureResult,
    val cancelChild: Boolean = false,
) {
    companion object {
        fun pass(): TouchDisposition = TouchDisposition(GestureResult.PASS)
        fun consumed(cancelChild: Boolean): TouchDisposition =
            TouchDisposition(GestureResult.CONSUMED, cancelChild = cancelChild)

        fun handledButPassToChild(): TouchDisposition =
            TouchDisposition(GestureResult.HANDLED_BUT_PASS_TO_CHILD)
    }
}

interface TouchInterceptor {
    val priority: Int
    val tag: String
    fun onTouchEvent(detail: TouchDetail): TouchDisposition

    companion object {
        const val PRIORITY_PINCH_SCALE = 100
        const val PRIORITY_VERTICAL_SCROLL = 80
        const val PRIORITY_CHAPTER_SWIPE = 60
        const val PRIORITY_TAP_CHROME = 20
    }
}
