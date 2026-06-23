package io.github.luoyuxiaoxiao.easyreader.reader.gesture

import kotlin.math.abs
import kotlin.math.max

enum class ChapterSwipeDecision {
    KeepReading,
    PreviousChapter,
    NextChapter,
}

class ChapterSwipeDetector(
    private val screenWidthPx: Float,
    density: Float,
) {
    private val minHorizontalDistancePx = max(72f * density, screenWidthPx * 0.18f)
    private val systemBackEdgePx = 32f * density
    private val directionRatio = 3.0f
    private val minGestureDurationMs = 180L

    fun evaluate(
        startXPx: Float,
        dxPx: Float,
        dyPx: Float,
        velocityXPxPerSecond: Float,
        durationMs: Long = minGestureDurationMs,
    ): ChapterSwipeDecision {
        // 左右边缘保留给系统返回手势；阅读器只处理中间区域的横向切章。
        if (startXPx <= systemBackEdgePx || startXPx >= screenWidthPx - systemBackEdgePx) {
            return ChapterSwipeDecision.KeepReading
        }

        val horizontal = abs(dxPx)
        val vertical = abs(dyPx)

        // 切章只接受“明确横向拖拽”：足够长、足够横、持续时间足够。
        // 不使用速度触发，避免快速下滚或横向 fling 的短促漂移误切章。
        if (durationMs < minGestureDurationMs) {
            return ChapterSwipeDecision.KeepReading
        }

        if (vertical > 0f && horizontal < vertical * directionRatio) {
            return ChapterSwipeDecision.KeepReading
        }

        if (horizontal < minHorizontalDistancePx) {
            return ChapterSwipeDecision.KeepReading
        }

        return if (dxPx < 0f) {
            ChapterSwipeDecision.NextChapter
        } else {
            ChapterSwipeDecision.PreviousChapter
        }
    }
}
