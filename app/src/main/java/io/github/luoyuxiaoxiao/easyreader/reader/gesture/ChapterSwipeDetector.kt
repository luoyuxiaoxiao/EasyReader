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
    private val minHorizontalDistancePx = max(48f * density, screenWidthPx * 0.12f)
    private val fastDistancePx = 48f * density
    private val fastVelocityPxPerSecond = 800f * density
    private val systemBackEdgePx = 32f * density
    private val directionRatio = 2.0f

    fun evaluate(
        startXPx: Float,
        dxPx: Float,
        dyPx: Float,
        velocityXPxPerSecond: Float,
    ): ChapterSwipeDecision {
        // 左右边缘保留给系统返回手势；阅读器只处理中间区域的横向切章。
        if (startXPx <= systemBackEdgePx || startXPx >= screenWidthPx - systemBackEdgePx) {
            return ChapterSwipeDecision.KeepReading
        }

        val horizontal = abs(dxPx)
        val vertical = abs(dyPx)

        // 横向必须至少达到纵向 2 倍，快速 fling 也不能绕过方向约束，避免纵向滚动误触。
        if (vertical > 0f && horizontal < vertical * directionRatio) {
            return ChapterSwipeDecision.KeepReading
        }

        val distanceTriggered = horizontal >= minHorizontalDistancePx
        val flingTriggered =
            horizontal >= fastDistancePx && abs(velocityXPxPerSecond) >= fastVelocityPxPerSecond

        if (!distanceTriggered && !flingTriggered) {
            return ChapterSwipeDecision.KeepReading
        }

        return if (dxPx < 0f) {
            ChapterSwipeDecision.NextChapter
        } else {
            ChapterSwipeDecision.PreviousChapter
        }
    }
}
