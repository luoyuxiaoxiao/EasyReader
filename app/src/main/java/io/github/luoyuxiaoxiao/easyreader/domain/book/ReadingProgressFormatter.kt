package io.github.luoyuxiaoxiao.easyreader.domain.book

import java.util.Locale

object ReadingProgressFormatter {
    fun percent(progression: Double?): String {
        // 进度统一归一化到 0..1，避免异常 Locator 数据污染 UI 展示。
        val normalized = (progression ?: 0.0).coerceIn(0.0, 1.0)
        return String.format(Locale.US, "%.2f%%", normalized * 100.0)
    }
}
