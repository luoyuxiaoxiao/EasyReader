package io.github.luoyuxiaoxiao.easyreader.ui.reader

object ReaderScrollProgress {
    const val CHAPTER_START_PROGRESSION = 0.0
    private const val END_SNAP_TOLERANCE_PX = 2f

    fun webViewContentHeightPx(contentHeight: Int): Float = contentHeight.toFloat()

    fun chapterProgression(
        scrollY: Int,
        viewportHeightPx: Int,
        contentHeightPx: Float,
        nonScrollableProgression: Double? = 1.0,
    ): Double? {
        val scrollableHeight = contentHeightPx - viewportHeightPx
        if (scrollableHeight <= 0f) return nonScrollableProgression?.coerceIn(0.0, 1.0)
        if (scrollY >= scrollableHeight - END_SNAP_TOLERANCE_PX) return 1.0
        return (scrollY / scrollableHeight).coerceIn(0f, 1f).toDouble()
    }

    fun totalProgression(
        chapterWeights: List<Int>,
        readingOrderIndex: Int,
        chapterProgression: Double?,
    ): Double? {
        if (chapterWeights.isEmpty() || chapterProgression == null) return null
        val boundedIndex = readingOrderIndex.coerceIn(0, chapterWeights.lastIndex)
        val totalWeight = chapterWeights.sum().takeIf { it > 0 } ?: return null
        val completedWeight = chapterWeights.take(boundedIndex).sum()
        val currentWeight = chapterWeights[boundedIndex].coerceAtLeast(1)
        return ((completedWeight + currentWeight * chapterProgression.coerceIn(0.0, 1.0)) / totalWeight.toDouble())
            .coerceIn(0.0, 1.0)
    }

    fun chapterStartTotalProgression(
        chapterWeights: List<Int>,
        readingOrderIndex: Int,
    ): Double? =
        // 切章成功后 WebView 可能还没产生滚动事件，先用章节起点刷新显示进度。
        totalProgression(
            chapterWeights = chapterWeights,
            readingOrderIndex = readingOrderIndex,
            chapterProgression = CHAPTER_START_PROGRESSION,
        )

    fun syntheticNonScrollableProgression(
        readingOrderIndex: Int,
        readingOrderCount: Int,
    ): Double? =
        when {
            // 首页多半是封面，主动测量不可滚动时不覆盖切章起点的 0% 显示。
            readingOrderIndex <= 0 -> null
            // 最后一页如果不可滚动，需要补成 100%，否则总进度会停在章节起点。
            readingOrderCount > 0 && readingOrderIndex >= readingOrderCount - 1 -> 1.0
            else -> null
        }
}
