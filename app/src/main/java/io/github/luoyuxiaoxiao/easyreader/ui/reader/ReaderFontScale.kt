package io.github.luoyuxiaoxiao.easyreader.ui.reader

import kotlin.math.roundToInt

object ReaderFontScale {
    private const val BASE_FONT_SIZE_SP = 20
    private const val MIN_SCALE = 0.7f
    private const val MAX_SCALE = 1.6f

    fun adjust(currentScale: Float, gestureScaleFactor: Float): Float =
        (currentScale * gestureScaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)

    fun labelFor(fontScale: Float): String =
        (fontScale * BASE_FONT_SIZE_SP).roundToInt().toString()
}
