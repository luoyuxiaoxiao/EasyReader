package io.github.luoyuxiaoxiao.easyreader.ui.reader

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
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
    var onVerticalScrollStarted: () -> Unit = {}

    private val detector = ChapterSwipeDetector(
        screenWidthPx = resources.displayMetrics.widthPixels.toFloat(),
        density = resources.displayMetrics.density,
    )
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var verticalLocked = false
    private var lastSwitchAt = 0L

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                verticalLocked = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                // 状态流转：按下后先观察方向，纵向胜出就锁定阅读滚动，抬手时仅横向手势可切章。
                if (!verticalLocked && abs(dy) > abs(dx) * 1.2f && abs(dy) > 12f * resources.displayMetrics.density) {
                    verticalLocked = true
                    onVerticalScrollStarted()
                }
            }

            MotionEvent.ACTION_UP -> {
                val elapsedSeconds = ((event.eventTime - downTime).coerceAtLeast(1L)) / 1000f
                val dx = event.x - downX
                val dy = event.y - downY
                val velocityX = dx / elapsedSeconds
                if (!verticalLocked && event.eventTime - lastSwitchAt >= SWITCH_COOLDOWN_MS) {
                    when (detector.evaluate(downX, dx, dy, velocityX)) {
                        ChapterSwipeDecision.NextChapter -> {
                            lastSwitchAt = event.eventTime
                            onNextChapter()
                        }

                        ChapterSwipeDecision.PreviousChapter -> {
                            lastSwitchAt = event.eventTime
                            onPreviousChapter()
                        }

                        ChapterSwipeDecision.KeepReading -> Unit
                    }
                }
                verticalLocked = false
            }

            MotionEvent.ACTION_CANCEL -> verticalLocked = false
        }

        return super.dispatchTouchEvent(event)
    }

    private companion object {
        const val SWITCH_COOLDOWN_MS = 250L
    }
}
