package io.github.luoyuxiaoxiao.easyreader.ui.reader

import android.util.Log
import android.view.MotionEvent
import io.github.luoyuxiaoxiao.easyreader.BuildConfig
import kotlin.math.abs

internal class ReaderGestureTraceRecorder(
    private val enabled: Boolean = BuildConfig.DEBUG,
    private val logger: (String) -> Unit = { message -> Log.d(TAG, message) },
) {
    data class Sample(
        val action: Int,
        val x: Float,
        val y: Float,
        val eventTime: Long,
        val pointerCount: Int,
        val verticalLocked: Boolean,
        val horizontalLocked: Boolean,
        val scaling: Boolean,
    )

    private val samples = ArrayDeque<Sample>()
    private var gestureId = 0

    fun record(
        event: MotionEvent,
        verticalLocked: Boolean,
        horizontalLocked: Boolean,
        scaling: Boolean,
    ) {
        if (!enabled) return
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            samples.clear()
            gestureId += 1
        }
        if (action != MotionEvent.ACTION_DOWN) {
            // Android 会把高频触控采样折叠进一个 MOVE 的 historical points；
            // 调试折返路径时必须展开这些点，否则日志会漏掉真实轨迹。
            for (index in 0 until event.historySize) {
                recordRaw(
                    action = MotionEvent.ACTION_MOVE,
                    x = event.getHistoricalX(index),
                    y = event.getHistoricalY(index),
                    eventTime = event.getHistoricalEventTime(index),
                    pointerCount = event.pointerCount,
                    verticalLocked = verticalLocked,
                    horizontalLocked = horizontalLocked,
                    scaling = scaling,
                    resetOnDown = false,
                    logOnEnd = false,
                )
            }
        }
        recordRaw(
            action = action,
            x = event.x,
            y = event.y,
            eventTime = event.eventTime,
            pointerCount = event.pointerCount,
            verticalLocked = verticalLocked,
            horizontalLocked = horizontalLocked,
            scaling = scaling,
            resetOnDown = false,
            logOnEnd = true,
        )
    }

    internal fun recordRaw(
        action: Int,
        x: Float,
        y: Float,
        eventTime: Long,
        pointerCount: Int = 1,
        verticalLocked: Boolean = false,
        horizontalLocked: Boolean = false,
        scaling: Boolean = false,
        resetOnDown: Boolean = true,
        logOnEnd: Boolean = true,
    ) {
        if (!enabled) return
        if (resetOnDown && action == MotionEvent.ACTION_DOWN) {
            samples.clear()
            gestureId += 1
        }
        if (samples.size == MAX_SAMPLES) {
            samples.removeFirst()
        }
        samples.addLast(
            Sample(
                action = action,
                x = x,
                y = y,
                eventTime = eventTime,
                pointerCount = pointerCount,
                verticalLocked = verticalLocked,
                horizontalLocked = horizontalLocked,
                scaling = scaling,
            ),
        )
        if (logOnEnd && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
            logSummary()
        }
    }

    fun snapshot(): List<Sample> = samples.toList()

    private fun logSummary() {
        val first = samples.firstOrNull() ?: return
        val last = samples.lastOrNull() ?: return
        var pathDx = 0f
        var pathDy = 0f
        var lastVerticalSign = 0
        var verticalReversed = false
        var previous = first
        samples.drop(1).forEach { sample ->
            val dx = sample.x - previous.x
            val dy = sample.y - previous.y
            pathDx += abs(dx)
            pathDy += abs(dy)
            if (abs(dy) > MIN_REVERSAL_DELTA_PX) {
                val sign = if (dy > 0f) 1 else -1
                if (lastVerticalSign != 0 && sign != lastVerticalSign) {
                    verticalReversed = true
                }
                lastVerticalSign = sign
            }
            previous = sample
        }
        val trace = samples.joinToString(separator = " -> ") { sample ->
            val relativeTimeMs = sample.eventTime - first.eventTime
            "${actionName(sample.action)}@${relativeTimeMs}ms(${sample.x},${sample.y},p=${sample.pointerCount},v=${sample.verticalLocked},h=${sample.horizontalLocked},s=${sample.scaling})"
        }
        // debug 版只输出日志，不绘制浮层，避免调试设施本身参与触摸命中。
        logger(
            "gesture#$gestureId count=${samples.size} duration=${last.eventTime - first.eventTime}ms " +
                "last=${actionName(last.action)} netDx=${last.x - first.x} netDy=${last.y - first.y} " +
                "pathDx=$pathDx pathDy=$pathDy pathRatio=${pathRatio(pathDx, pathDy)} " +
                "verticalReversed=$verticalReversed trace=$trace",
        )
    }

    private companion object {
        const val TAG = "EasyReaderGesture"
        const val MAX_SAMPLES = 96
        const val MIN_REVERSAL_DELTA_PX = 4f

        fun pathRatio(pathDx: Float, pathDy: Float): String =
            if (pathDy == 0f) "inf" else "%.3f".format(pathDx / pathDy)

        fun actionName(action: Int): String =
            when (action) {
                MotionEvent.ACTION_DOWN -> "DOWN"
                MotionEvent.ACTION_MOVE -> "MOVE"
                MotionEvent.ACTION_UP -> "UP"
                MotionEvent.ACTION_CANCEL -> "CANCEL"
                MotionEvent.ACTION_POINTER_DOWN -> "POINTER_DOWN"
                MotionEvent.ACTION_POINTER_UP -> "POINTER_UP"
                else -> action.toString()
            }
    }
}
