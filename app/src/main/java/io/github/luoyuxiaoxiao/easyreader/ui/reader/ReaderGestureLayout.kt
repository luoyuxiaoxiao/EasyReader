package io.github.luoyuxiaoxiao.easyreader.ui.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import io.github.luoyuxiaoxiao.easyreader.reader.gesture.ChapterSwipeInterceptor
import io.github.luoyuxiaoxiao.easyreader.reader.gesture.GestureResult
import io.github.luoyuxiaoxiao.easyreader.reader.gesture.GestureThresholds
import io.github.luoyuxiaoxiao.easyreader.reader.gesture.TouchGestureSampler
import io.github.luoyuxiaoxiao.easyreader.reader.gesture.TouchInterceptorRegistry
import io.github.luoyuxiaoxiao.easyreader.reader.gesture.VerticalScrollInterceptor
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
    private val sampler = TouchGestureSampler(density = density)
    private val traceRecorder = ReaderGestureTraceRecorder()
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                onFontScaleChanged(detector.scaleFactor)
                return true
            }
        },
    )

    private val registry = TouchInterceptorRegistry()
    private val verticalScrollInterceptor = VerticalScrollInterceptor(
        density = density,
        onScrollStarted = { onVerticalScrollStarted() },
        onScrollFinished = { onVerticalScrollFinished() },
    )
    private val chapterSwipeInterceptor = ChapterSwipeInterceptor(
        density = density,
        onNextChapter = {
            onNextChapter()
        },
        onPreviousChapter = {
            onPreviousChapter()
        },
    )

    init {
        registry.add(verticalScrollInterceptor)
        registry.add(chapterSwipeInterceptor)
    }

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var scaling = false
    private var passThroughToChromeControls = false
    private var pendingChromeTap: Runnable? = null
    private var childCancelled = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            cancelPendingChromeTap()
            downX = event.x
            downY = event.y
            downTime = event.eventTime
            childCancelled = false
            passThroughToChromeControls = topChromeControlsVisible &&
                event.y <= GestureThresholds.TOP_CHROME_CONTROLS_HEIGHT_DP * density
        }
        if (passThroughToChromeControls) {
            traceRecorder.record(
                event = event,
                verticalLocked = verticalScrollInterceptor.isLocked,
                horizontalLocked = registry.gestureConsumed,
                scaling = scaling,
            )
            if (
                event.actionMasked == MotionEvent.ACTION_MOVE ||
                event.actionMasked == MotionEvent.ACTION_UP
            ) {
                if (childCancelled || isBeyondTapSlop(event)) {
                    cancelChildOnce()
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        passThroughToChromeControls = false
                    }
                    return true
                }
            }
            val handled = super.dispatchTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                passThroughToChromeControls = false
            }
            return handled
        }

        scaleDetector.onTouchEvent(event)
        traceRecorder.record(
            event = event,
            verticalLocked = verticalScrollInterceptor.isLocked,
            horizontalLocked = registry.gestureConsumed,
            scaling = scaling,
        )

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                scaling = false
                childCancelled = false

                registry.dispatch(sample(event))
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                scaling = true
                cancelChildOnce()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaling || event.pointerCount > 1) {
                    scaling = true
                    cancelChildOnce()
                    return true
                }

                val detail = sample(event)
                val result = registry.dispatch(detail)

                if (result.result == GestureResult.CONSUMED) {
                    if (result.cancelChild) cancelChildOnce()
                    return true
                }
                if (result.result == GestureResult.HANDLED_BUT_PASS_TO_CHILD) {
                    return super.dispatchTouchEvent(event)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (scaling) {
                    onFontScaleFinished()
                    scaling = false
                    return true
                }

                val detail = sample(event)
                val result = registry.dispatch(detail)

                if (registry.gestureConsumed || result.result == GestureResult.CONSUMED) {
                    if (result.cancelChild) cancelChildOnce()
                    return true
                }
                if (result.result == GestureResult.HANDLED_BUT_PASS_TO_CHILD) {
                    return super.dispatchTouchEvent(event)
                }

                if (detail.isTapCandidate) {
                    val handledByChild = super.dispatchTouchEvent(event)
                    onReaderTapCandidate(event.x, event.y)
                    if (onReaderContentTapConsumed()) {
                        return true
                    }
                    if (!handledByChild) {
                        cancelChildOnce()
                        onChromeTap()
                    } else {
                        scheduleChromeTapAfterContentDecision()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                registry.dispatch(sample(event))
                if (scaling) onFontScaleFinished()
                scaling = false
                passThroughToChromeControls = false
                cancelPendingChromeTap()
                childCancelled = false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (scaling) return true
            }
        }

        return super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        cancelPendingChromeTap()
        super.onDetachedFromWindow()
    }

    private fun sample(event: MotionEvent) =
        sampler.onTouchEvent(
            event = event,
            screenWidthPx = width.takeIf { it > 0 }?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat(),
            isPostVerticalScrollSuppressed = verticalScrollInterceptor.isSuppressed(event.eventTime),
        )

    private fun scheduleChromeTapAfterContentDecision() {
        cancelPendingChromeTap()
        val tap = Runnable {
            pendingChromeTap = null
            if (!onReaderContentTapConsumed()) {
                onChromeTap()
            }
        }
        pendingChromeTap = tap
        mainHandler.postDelayed(tap, GestureThresholds.CONTENT_TAP_CONSUME_WAIT_MS)
    }

    private fun cancelPendingChromeTap() {
        pendingChromeTap?.let(mainHandler::removeCallbacks)
        pendingChromeTap = null
    }

    private fun isBeyondTapSlop(event: MotionEvent): Boolean {
        val movement = maxOf(abs(event.x - downX), abs(event.y - downY))
        return movement > GestureThresholds.TAP_SLOP_DP * density
    }

    private fun cancelChildOnce() {
        if (childCancelled) return
        childCancelled = true
        val cancel = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_CANCEL, downX, downY, 0)
        try {
            super.dispatchTouchEvent(cancel)
        } finally {
            cancel.recycle()
        }
    }
}
