package io.github.luoyuxiaoxiao.easyreader.reader.gesture

import android.view.MotionEvent
import kotlin.math.abs

class TouchGestureSampler(
    private val density: Float,
) {
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var maxAbsDx = 0f
    private var maxAbsDy = 0f
    private var pathAbsDx = 0f
    private var pathAbsDy = 0f
    private var minY = 0f
    private var maxY = 0f
    private var verticalReversed = false
    private var startedFromSystemBackEdge = false

    fun onTouchEvent(
        event: MotionEvent,
        screenWidthPx: Float,
        isPostVerticalScrollSuppressed: Boolean,
    ): TouchDetail {
        val phase = TouchPhase.fromActionMasked(event.actionMasked)
        if (phase == TouchPhase.DOWN) {
            downX = event.x
            downY = event.y
            downTime = event.eventTime
            lastX = event.x
            lastY = event.y
            maxAbsDx = 0f
            maxAbsDy = 0f
            pathAbsDx = 0f
            pathAbsDy = 0f
            minY = event.y
            maxY = event.y
            verticalReversed = false
            startedFromSystemBackEdge = isSystemBackEdge(event.x, screenWidthPx)
        } else {
            // Android 会把快速手势拆到历史点里；统一在采样层累计，避免各消费者各算各的。
            for (index in 0 until event.historySize) {
                appendPoint(event.getHistoricalX(index), event.getHistoricalY(index))
            }
            appendPoint(event.x, event.y)
        }

        val netDx = event.x - downX
        val netDy = event.y - downY
        val durationMs = event.eventTime - downTime
        val isTapCandidate = isTapCandidate(durationMs)
        val isChapterSwipeAllowed =
            event.pointerCount == 1 &&
                !startedFromSystemBackEdge &&
                !isTapCandidate &&
                !isPostVerticalScrollSuppressed

        return TouchDetail(
            phase = phase,
            x = event.x,
            y = event.y,
            downX = downX,
            downY = downY,
            downTime = downTime,
            eventTime = event.eventTime,
            pointerCount = event.pointerCount,
            netDx = netDx,
            netDy = netDy,
            maxAbsDx = maxAbsDx,
            maxAbsDy = maxAbsDy,
            pathAbsDx = pathAbsDx,
            pathAbsDy = pathAbsDy,
            velocityXPxPerSecond = velocity(netDx, durationMs),
            velocityYPxPerSecond = velocity(netDy, durationMs),
            durationMs = durationMs,
            screenWidthPx = screenWidthPx,
            startedFromSystemBackEdge = startedFromSystemBackEdge,
            isTapCandidate = isTapCandidate,
            isPostVerticalScrollSuppressed = isPostVerticalScrollSuppressed,
            isChapterSwipeAllowed = isChapterSwipeAllowed,
            verticalReversed = verticalReversed,
        )
    }

    private fun appendPoint(x: Float, y: Float) {
        pathAbsDx += abs(x - lastX)
        pathAbsDy += abs(y - lastY)
        minY = minOf(minY, y)
        maxY = maxOf(maxY, y)
        verticalReversed = verticalReversed || isVerticalReversalAt(y)
        lastX = x
        lastY = y
        maxAbsDx = maxOf(maxAbsDx, abs(x - downX))
        maxAbsDy = maxOf(maxAbsDy, abs(y - downY))
    }

    private fun isVerticalReversalAt(y: Float): Boolean {
        val slopPx = GestureThresholds.DIRECTION_LOCK_SLOP_DP * density
        // 折返必须先离开按下点超过 slop，再从极值回撤超过 slop；用 sticky 标记保留历史轨迹。
        val movedDownThenBack = maxY - downY > slopPx && maxY - y > slopPx
        val movedUpThenBack = downY - minY > slopPx && y - minY > slopPx
        return movedDownThenBack || movedUpThenBack
    }

    private fun isTapCandidate(durationMs: Long): Boolean {
        val movement = maxOf(maxOf(maxAbsDx, pathAbsDx), maxOf(maxAbsDy, pathAbsDy))
        return movement <= GestureThresholds.TAP_SLOP_DP * density &&
            durationMs <= GestureThresholds.TAP_TIMEOUT_MS
    }

    private fun isSystemBackEdge(x: Float, screenWidthPx: Float): Boolean {
        val edgePx = GestureThresholds.SYSTEM_BACK_EDGE_DP * density
        return x <= edgePx || x >= screenWidthPx - edgePx
    }

    private fun velocity(deltaPx: Float, durationMs: Long): Float {
        val elapsedSeconds = durationMs.coerceAtLeast(1L) / 1000f
        return deltaPx / elapsedSeconds
    }
}
