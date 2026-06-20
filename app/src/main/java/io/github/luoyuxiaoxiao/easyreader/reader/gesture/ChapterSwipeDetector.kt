package io.github.luoyuxiaoxiao.easyreader.reader.gesture

import kotlin.math.abs
import kotlin.math.max

enum class ChapterSwipeDecision {
    KeepReading,
    PreviousChapter,
    NextChapter,
}

class ChapterSwipeDetector(
    screenWidthPx: Float,
    density: Float,
) {
    private val minHorizontalDistancePx = max(72f * density, screenWidthPx * 0.24f)
    private val fastDistancePx = 48f * density
    private val fastVelocityPxPerSecond = 800f * density
    private val directionRatio = 1.8f

    fun evaluate(
        dxPx: Float,
        dyPx: Float,
        velocityXPxPerSecond: Float,
    ): ChapterSwipeDecision {
        val horizontal = abs(dxPx)
        val vertical = abs(dyPx)

        // 先锁定纵向滚动，避免普通阅读滚动被误判为切章。
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
