package io.github.luoyuxiaoxiao.easyreader.reader.gesture

import kotlin.math.abs
import kotlin.math.max

class ChapterSwipeInterceptor(
    density: Float,
    private val onNextChapter: () -> Unit,
    private val onPreviousChapter: () -> Unit,
) : TouchInterceptor {
    override val priority: Int = TouchInterceptor.PRIORITY_CHAPTER_SWIPE
    override val tag: String = "ChapterSwipe"

    private val minSlowDistancePx = GestureThresholds.HORIZONTAL_ARM_DISTANCE_DP * density
    private val minFastDistancePx = GestureThresholds.FAST_HORIZONTAL_DISTANCE_DP * density
    private val tapSlopPx = GestureThresholds.TAP_SLOP_DP * density
    private var armed = false
    private var peakNetDxAbs = 0f
    private var lastSwitchAt = -GestureThresholds.SWITCH_COOLDOWN_MS

    override fun onTouchEvent(detail: TouchDetail): TouchDisposition {
        when (detail.phase) {
            TouchPhase.DOWN -> {
                armed = false
                peakNetDxAbs = 0f
                return TouchDisposition.pass()
            }

            TouchPhase.MOVE -> {
                peakNetDxAbs = max(peakNetDxAbs, detail.maxAbsDx)

                if (!detail.isChapterSwipeAllowed) return TouchDisposition.pass()
                if (detail.pointerCount > 1) return TouchDisposition.pass()
                if (detail.verticalReversed) return TouchDisposition.pass()

                if (isCooldownActive(detail.eventTime)) return TouchDisposition.pass()

                if (isArmCandidate(detail)) {
                    // arm 阶段先取消子层，commit 阶段再决定是否真的切章。
                    armed = true
                    return TouchDisposition.consumed(cancelChild = true)
                }
                return TouchDisposition.pass()
            }

            TouchPhase.UP -> {
                val netDx = detail.netDx
                peakNetDxAbs = max(peakNetDxAbs, detail.maxAbsDx)

                if (!detail.isChapterSwipeAllowed) {
                    armed = false
                    return if (
                        hasHorizontalTail(detail) &&
                        (detail.isPostVerticalScrollSuppressed || detail.startedFromSystemBackEdge)
                    ) {
                        // 外层不切章的保护路径也要吞掉横向尾迹，避免 Readium 子层自行结算翻页。
                        TouchDisposition.consumed(cancelChild = true)
                    } else {
                        TouchDisposition.pass()
                    }
                }

                if (detail.verticalReversed) {
                    armed = false
                    return if (hasHorizontalTail(detail)) {
                        // 折返路径不能切章，但横向尾迹仍要吞掉，避免 Readium 在 UP 阶段自行翻页。
                        TouchDisposition.consumed(cancelChild = true)
                    } else {
                        TouchDisposition.pass()
                    }
                }

                if (!isCooldownActive(detail.eventTime) && isCommitCandidate(detail)) {
                    when {
                        netDx < 0f -> onNextChapter()
                        netDx > 0f -> onPreviousChapter()
                    }
                    lastSwitchAt = detail.eventTime
                    armed = false
                    return TouchDisposition.consumed(cancelChild = true)
                }

                if (armed || hasHorizontalTail(detail)) {
                    armed = false
                    return TouchDisposition.consumed(cancelChild = true)
                }
                return TouchDisposition.pass()
            }

            TouchPhase.CANCEL -> {
                armed = false
                peakNetDxAbs = 0f
                return TouchDisposition.pass()
            }

            else -> return TouchDisposition.pass()
        }
    }

    private fun isCommitCandidate(detail: TouchDetail): Boolean {
        if (detail.durationMs < GestureThresholds.MIN_GESTURE_DURATION_MS) return false
        if (detail.maxAbsDx <= 0f) return false
        return isSlowDeliberateSwipe(detail) || isFastShortSwipe(detail)
    }

    private fun isArmCandidate(detail: TouchDetail): Boolean {
        if (detail.maxAbsDx <= 0f) return false
        return isSlowDeliberateSwipe(detail) || isFastShortSwipe(detail)
    }

    private fun isSlowDeliberateSwipe(detail: TouchDetail): Boolean {
        val distance = max(minSlowDistancePx, detail.screenWidthPx * GestureThresholds.HORIZONTAL_ARM_WIDTH_RATIO)
        return detail.maxAbsDx >= distance &&
            isHorizontalEnough(detail, GestureThresholds.DELIBERATE_DIRECTION_RATIO)
    }

    private fun isFastShortSwipe(detail: TouchDetail): Boolean {
        val distance = max(minFastDistancePx, detail.screenWidthPx * GestureThresholds.FAST_HORIZONTAL_WIDTH_RATIO)
        return detail.maxAbsDx >= distance &&
            abs(detail.velocityXPxPerSecond) >= GestureThresholds.MIN_FLING_VELOCITY_PX_PER_SECOND &&
            isHorizontalEnough(detail, GestureThresholds.FAST_DIRECTION_RATIO)
    }

    private fun isHorizontalEnough(detail: TouchDetail, ratio: Float): Boolean =
        detail.pathAbsDy <= 0f || detail.maxAbsDx >= detail.pathAbsDy * ratio

    private fun hasHorizontalTail(detail: TouchDetail): Boolean =
        !detail.isTapCandidate && detail.maxAbsDx > tapSlopPx

    private fun isCooldownActive(eventTime: Long): Boolean =
        eventTime - lastSwitchAt < GestureThresholds.SWITCH_COOLDOWN_MS
}
