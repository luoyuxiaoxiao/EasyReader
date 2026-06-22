package io.github.luoyuxiaoxiao.easyreader.ui.reader

import android.content.Context
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
    var topChromeControlsVisible: Boolean = false

    private val density = resources.displayMetrics.density
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
    private var maxAbsDx = 0f
    private var maxAbsDy = 0f
    private var verticalLocked = false
    private var horizontalLocked = false
    private var scaling = false
    private var passThroughToChromeControls = false
    private var lastSwitchAt = -SWITCH_COOLDOWN_MS

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
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
                maxAbsDx = 0f
                maxAbsDy = 0f
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
                val dx = event.x - downX
                val dy = event.y - downY
                maxAbsDx = maxOf(maxAbsDx, abs(dx))
                maxAbsDy = maxOf(maxAbsDy, abs(dy))
                // 状态流转：超过轻点范围后立即锁定方向；横向锁定后消费事件，避免 WebView 抢走切章手势。
                if (!verticalLocked && !horizontalLocked && abs(dy) > abs(dx) * 1.2f && abs(dy) > DIRECTION_LOCK_SLOP_DP * density) {
                    verticalLocked = true
                    onVerticalScrollStarted()
                }
                if (!verticalLocked && !horizontalLocked && abs(dx) > abs(dy) * 1.05f && abs(dx) > DIRECTION_LOCK_SLOP_DP * density) {
                    horizontalLocked = true
                    cancelChildTouch()
                    return true
                }
                if (horizontalLocked) return true
            }

            MotionEvent.ACTION_UP -> {
                var handledByReader = horizontalLocked
                val elapsedSeconds = ((event.eventTime - downTime).coerceAtLeast(1L)) / 1000f
                val dx = event.x - downX
                val dy = event.y - downY
                maxAbsDx = maxOf(maxAbsDx, abs(dx))
                maxAbsDy = maxOf(maxAbsDy, abs(dy))
                val velocityX = dx / elapsedSeconds
                val explicitTap = isExplicitTap(event, maxAbsDx, maxAbsDy)
                if (verticalLocked) {
                    onVerticalScrollFinished()
                } else if (!scaling && !explicitTap && event.eventTime - lastSwitchAt >= SWITCH_COOLDOWN_MS) {
                    when (chapterSwipeDecision(netDx = dx, maxAbsDx = maxAbsDx, maxAbsDy = maxAbsDy, velocityX = velocityX)) {
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
                    cancelChildTouch()
                    onChromeTap()
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
                verticalLocked = false
                horizontalLocked = false
                scaling = false
                passThroughToChromeControls = false
            }
        }

        return super.dispatchTouchEvent(event)
    }

    private fun isExplicitTap(event: MotionEvent, maxAbsTravelX: Float, maxAbsTravelY: Float): Boolean {
        val movement = maxOf(maxAbsTravelX, maxAbsTravelY)
        val duration = event.eventTime - downTime
        return movement <= TAP_SLOP_DP * density && duration <= TAP_TIMEOUT_MS
    }

    private fun chapterSwipeDecision(
        netDx: Float,
        maxAbsDx: Float,
        maxAbsDy: Float,
        velocityX: Float,
    ): ChapterSwipeDecision {
        val gestureWidth = width.takeIf { it > 0 }?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat()
        val effectiveDx =
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
            dxPx = effectiveDx,
            dyPx = maxAbsDy,
            velocityXPxPerSecond = velocityX,
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
        const val DIRECTION_LOCK_SLOP_DP = 12f
        const val TAP_SLOP_DP = 8f
        const val TAP_TIMEOUT_MS = 250L
        const val TOP_CHROME_CONTROLS_HEIGHT_DP = 96f
    }
}
