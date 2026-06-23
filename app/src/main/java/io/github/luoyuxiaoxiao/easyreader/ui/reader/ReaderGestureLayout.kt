package io.github.luoyuxiaoxiao.easyreader.ui.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import io.github.luoyuxiaoxiao.easyreader.reader.gesture.ChapterSwipeDecision
import io.github.luoyuxiaoxiao.easyreader.reader.gesture.ChapterSwipeDetector
import kotlin.math.abs

class ReaderGestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    var onNextChapter: () -> Unit = {}
    var onPreviousChapter: () -> Unit = {}
    var onChromeTap: () -> Unit = {}
    var onVerticalScrollStarted: () -> Unit = {}
    var onVerticalScrollFinished: () -> Unit = {}
    var onFontScaleChanged: (Float) -> Unit = {}
    var onFontScaleFinished: () -> Unit = {}
    var onReaderTapCandidate: (Float, Float) -> Unit = { _, _ -> }
    var onReaderContentTapConsumed: () -> Boolean = { false }
    var topChromeControlsVisible: Boolean = false

    private val density = resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                onFontScaleChanged(detector.scaleFactor)
                return true
            }
        },
    )
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var maxAbsDx = 0f
    private var maxAbsDy = 0f
    private var pathAbsDx = 0f
    private var pathAbsDy = 0f
    private var verticalLocked = false
    private var horizontalLocked = false
    private var scaling = false
    private var passThroughToChromeControls = false
    private var lastSwitchAt = -SWITCH_COOLDOWN_MS
    private var lastVerticalScrollFinishedAt = -POST_VERTICAL_SCROLL_SUPPRESS_MS
    private var pendingChromeTap: Runnable? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            cancelPendingChromeTap()
            passThroughToChromeControls = topChromeControlsVisible && event.y <= TOP_CHROME_CONTROLS_HEIGHT_DP * density
        }
        if (passThroughToChromeControls) {
            val handled = super.dispatchTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                passThroughToChromeControls = false
            }
            return handled
        }

        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                lastX = event.x
                lastY = event.y
                maxAbsDx = 0f
                maxAbsDy = 0f
                pathAbsDx = 0f
                pathAbsDy = 0f
                verticalLocked = false
                horizontalLocked = false
                scaling = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 双指缩放期间不参与滚动锁定、点击和切章，避免阅读手势互相打架。
                scaling = true
                verticalLocked = false
                horizontalLocked = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaling || event.pointerCount > 1) return super.dispatchTouchEvent(event)
                updateGesturePath(event)
                val dx = event.x - downX
                val dy = event.y - downY
                maxAbsDx = maxOf(maxAbsDx, abs(dx))
                maxAbsDy = maxOf(maxAbsDy, abs(dy))
                // 状态流转使用累计路径判断方向，避免“下滚后上滚”的折返路径被终点位移抵消。
                if (!verticalLocked && !horizontalLocked && pathAbsDy > pathAbsDx * VERTICAL_LOCK_RATIO && pathAbsDy > DIRECTION_LOCK_SLOP_DP * density) {
                    verticalLocked = true
                    onVerticalScrollStarted()
                }
                if (
                    !isPostVerticalScrollSuppressed(event.eventTime) &&
                    !verticalLocked &&
                    !horizontalLocked &&
                    pathAbsDx > pathAbsDy * HORIZONTAL_LOCK_RATIO &&
                    pathAbsDx > HORIZONTAL_LOCK_DISTANCE_DP * density
                ) {
                    horizontalLocked = true
                    cancelChildTouch()
                    return true
                }
                if (horizontalLocked) return true
            }

            MotionEvent.ACTION_UP -> {
                updateGesturePath(event)
                var handledByReader = horizontalLocked
                val elapsedMs = event.eventTime - downTime
                val elapsedSeconds = elapsedMs.coerceAtLeast(1L) / 1000f
                val dx = event.x - downX
                val dy = event.y - downY
                maxAbsDx = maxOf(maxAbsDx, abs(dx))
                maxAbsDy = maxOf(maxAbsDy, abs(dy))
                val velocityX = dx / elapsedSeconds
                val explicitTap = isExplicitTap(event, maxOf(maxAbsDx, pathAbsDx), maxOf(maxAbsDy, pathAbsDy))
                if (verticalLocked) {
                    onVerticalScrollFinished()
                    lastVerticalScrollFinishedAt = event.eventTime
                } else if (
                    !scaling &&
                    !explicitTap &&
                    !isPostVerticalScrollSuppressed(event.eventTime) &&
                    event.eventTime - lastSwitchAt >= SWITCH_COOLDOWN_MS
                ) {
                    when (
                        chapterSwipeDecision(
                            netDx = dx,
                            maxAbsDx = maxAbsDx,
                            pathAbsDy = pathAbsDy,
                            velocityX = velocityX,
                            durationMs = elapsedMs,
                        )
                    ) {
                        ChapterSwipeDecision.NextChapter -> {
                            lastSwitchAt = event.eventTime
                            onNextChapter()
                            handledByReader = true
                        }

                        ChapterSwipeDecision.PreviousChapter -> {
                            lastSwitchAt = event.eventTime
                            onPreviousChapter()
                            handledByReader = true
                        }

                        ChapterSwipeDecision.KeepReading -> Unit
                    }
                } else if (!scaling && explicitTap) {
                    val handledByChild = super.dispatchTouchEvent(event)
                    onReaderTapCandidate(event.x, event.y)
                    if (onReaderContentTapConsumed()) {
                        verticalLocked = false
                        horizontalLocked = false
                        scaling = false
                        return true
                    }
                    if (!handledByChild) {
                        cancelChildTouch()
                        onChromeTap()
                    } else {
                        scheduleChromeTapAfterContentDecision()
                    }
                    handledByReader = true
                }
                if (scaling) onFontScaleFinished()
                verticalLocked = false
                horizontalLocked = false
                scaling = false
                if (handledByReader) return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (verticalLocked) onVerticalScrollFinished()
                if (scaling) onFontScaleFinished()
                if (verticalLocked) lastVerticalScrollFinishedAt = event.eventTime
                verticalLocked = false
                horizontalLocked = false
                scaling = false
                passThroughToChromeControls = false
                cancelPendingChromeTap()
            }
        }

        return super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        cancelPendingChromeTap()
        super.onDetachedFromWindow()
    }

    private fun isExplicitTap(event: MotionEvent, maxAbsDx: Float, maxAbsDy: Float): Boolean {
        val movement = maxOf(maxAbsDx, maxAbsDy)
        val duration = event.eventTime - downTime
        return movement <= TAP_SLOP_DP * density && duration <= TAP_TIMEOUT_MS
    }

    private fun isPostVerticalScrollSuppressed(eventTime: Long): Boolean =
        // 竖向滚动刚结束时，用户常会反向滚动并带一点横向漂移；保护窗内不抢 WebView 事件，也不切章。
        eventTime - lastVerticalScrollFinishedAt <= POST_VERTICAL_SCROLL_SUPPRESS_MS

    private fun updateGesturePath(event: MotionEvent) {
        pathAbsDx += abs(event.x - lastX)
        pathAbsDy += abs(event.y - lastY)
        lastX = event.x
        lastY = event.y
    }

    private fun scheduleChromeTapAfterContentDecision() {
        cancelPendingChromeTap()
        // WebView 内的图片、脚注等点击可能通过 JS/Readium 回调稍晚标记为已消费；
        // 轻点先交给内容层，短暂等待后再决定是否切换阅读器 chrome。
        val tap = Runnable {
            pendingChromeTap = null
            if (!onReaderContentTapConsumed()) {
                onChromeTap()
            }
        }
        pendingChromeTap = tap
        mainHandler.postDelayed(tap, CONTENT_TAP_CONSUME_WAIT_MS)
    }

    private fun cancelPendingChromeTap() {
        pendingChromeTap?.let(mainHandler::removeCallbacks)
        pendingChromeTap = null
    }

    private fun chapterSwipeDecision(
        netDx: Float,
        maxAbsDx: Float,
        pathAbsDy: Float,
        velocityX: Float,
        durationMs: Long,
    ): ChapterSwipeDecision {
        val gestureWidth = width.takeIf { it > 0 }?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat()
        val signedMaxAbsDx =
            when {
                netDx < 0f -> -maxAbsDx
                netDx > 0f -> maxAbsDx
                else -> 0f
            }
        return ChapterSwipeDetector(
            screenWidthPx = gestureWidth,
            density = density,
        ).evaluate(
            startXPx = downX,
            dxPx = signedMaxAbsDx,
            dyPx = pathAbsDy,
            velocityXPxPerSecond = velocityX,
            durationMs = durationMs,
        )
    }

    private fun cancelChildTouch() {
        val cancel = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_CANCEL, downX, downY, 0)
        try {
            super.dispatchTouchEvent(cancel)
        } finally {
            cancel.recycle()
        }
    }

    private companion object {
        const val SWITCH_COOLDOWN_MS = 250L
        const val POST_VERTICAL_SCROLL_SUPPRESS_MS = 450L
        const val CONTENT_TAP_CONSUME_WAIT_MS = 180L
        const val VERTICAL_LOCK_RATIO = 1.2f
        const val HORIZONTAL_LOCK_RATIO = 3.0f
        const val HORIZONTAL_LOCK_DISTANCE_DP = 72f
        const val DIRECTION_LOCK_SLOP_DP = 12f
        const val TAP_SLOP_DP = 8f
        const val TAP_TIMEOUT_MS = 250L
        const val TOP_CHROME_CONTROLS_HEIGHT_DP = 96f
    }
}
