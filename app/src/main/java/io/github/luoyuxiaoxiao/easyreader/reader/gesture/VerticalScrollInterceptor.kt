package io.github.luoyuxiaoxiao.easyreader.reader.gesture

class VerticalScrollInterceptor(
    private val density: Float,
    private val onScrollStarted: () -> Unit,
    private val onScrollFinished: () -> Unit,
) : TouchInterceptor {
    override val priority: Int = TouchInterceptor.PRIORITY_VERTICAL_SCROLL
    override val tag: String = "VerticalScroll"

    private val lockSlopPx = GestureThresholds.DIRECTION_LOCK_SLOP_DP * density
    private val horizontalTailSlopPx = GestureThresholds.TAP_SLOP_DP * density
    private var locked = false
    private var lastFinishedAt = -GestureThresholds.POST_VERTICAL_SCROLL_SUPPRESS_MS

    val isLocked: Boolean
        get() = locked

    fun isSuppressed(eventTime: Long): Boolean =
        eventTime - lastFinishedAt <= GestureThresholds.POST_VERTICAL_SCROLL_SUPPRESS_MS

    override fun onTouchEvent(detail: TouchDetail): TouchDisposition {
        return when (detail.phase) {
            TouchPhase.DOWN -> {
                locked = false
                TouchDisposition.pass()
            }

            TouchPhase.MOVE -> {
                if (locked) {
                    TouchDisposition.handledButPassToChild()
                } else if (shouldLock(detail)) {
                    // 竖向滚动只阻止低优先级章节横滑接管，不取消子 WebView 的正文滚动。
                    locked = true
                    onScrollStarted()
                    TouchDisposition.handledButPassToChild()
                } else {
                    TouchDisposition.pass()
                }
            }

            TouchPhase.UP,
            TouchPhase.CANCEL,
            -> {
                if (locked) {
                    val cancelFoldedChildUp = detail.phase == TouchPhase.UP &&
                        detail.verticalReversed &&
                        hasHorizontalTail(detail)
                    locked = false
                    lastFinishedAt = detail.eventTime
                    onScrollFinished()
                    if (cancelFoldedChildUp) {
                        // 竖向折返夹带横向尾迹时，正文滚动已经结束，但不能把 UP 留给 Readium 结算翻页。
                        TouchDisposition.consumed(cancelChild = true)
                    } else {
                        TouchDisposition.handledButPassToChild()
                    }
                } else {
                    TouchDisposition.pass()
                }
            }

            else -> TouchDisposition.pass()
        }
    }

    private fun shouldLock(detail: TouchDetail): Boolean =
        detail.pointerCount == 1 &&
            detail.pathAbsDy > detail.pathAbsDx * GestureThresholds.VERTICAL_LOCK_RATIO &&
            detail.pathAbsDy > lockSlopPx

    private fun hasHorizontalTail(detail: TouchDetail): Boolean =
        detail.maxAbsDx > horizontalTailSlopPx
}
