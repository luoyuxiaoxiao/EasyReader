package io.github.luoyuxiaoxiao.easyreader.ui.reader

internal class ReaderChapterStartGuard(
    private val windowMs: Long,
    private val maxAcceptedProgression: Double,
    private val locatorWindowMs: Long = windowMs * 4,
) {
    private var pendingReadingOrderIndex: Int? = null
    private var pendingScrollUntilMs: Long = 0L
    private var pendingLocatorUntilMs: Long = 0L

    fun mark(readingOrderIndex: Int, nowMs: Long) {
        pendingReadingOrderIndex = readingOrderIndex
        pendingScrollUntilMs = nowMs + windowMs
        pendingLocatorUntilMs = nowMs + locatorWindowMs
    }

    fun shouldIgnoreLocator(readingOrderIndex: Int, progression: Double?, nowMs: Long): Boolean {
        val pendingIndex = activePendingIndex(nowMs, pendingLocatorUntilMs) ?: return false
        // 切章动画期间旧 locator 可能仍来自上一章；只放行目标章节开头附近的 locator。
        if (readingOrderIndex != pendingIndex) return true
        return (progression ?: 0.0) > maxAcceptedProgression
    }

    fun acceptLocatorIfAtStart(readingOrderIndex: Int, progression: Double?, nowMs: Long) {
        val pendingIndex = activePendingIndex(nowMs, pendingLocatorUntilMs) ?: return
        if (readingOrderIndex == pendingIndex && (progression ?: 0.0) <= maxAcceptedProgression) {
            // 不清理滚动采样保护。Readium locator 已到达目标章节开头时，旧 WebView 仍可能在
            // 后续 rebind 里贡献一次底部 scrollY，需要继续挡到保护窗口结束。
        }
    }

    fun shouldIgnoreScrollSample(readingOrderIndex: Int, progression: Double?, nowMs: Long): Boolean {
        val pendingIndex = activePendingIndex(nowMs, pendingScrollUntilMs) ?: return false
        return readingOrderIndex == pendingIndex &&
            progression != null &&
            progression > maxAcceptedProgression
    }

    private fun activePendingIndex(nowMs: Long, untilMs: Long): Int? {
        val pendingIndex = pendingReadingOrderIndex ?: return null
        return pendingIndex.takeIf { nowMs <= untilMs }
    }
}
