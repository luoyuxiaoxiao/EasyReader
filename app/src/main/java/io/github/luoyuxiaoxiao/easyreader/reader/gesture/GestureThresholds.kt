package io.github.luoyuxiaoxiao.easyreader.reader.gesture

object GestureThresholds {
    const val HORIZONTAL_ARM_DISTANCE_DP = 72f
    const val HORIZONTAL_ARM_WIDTH_RATIO = 0.18f
    const val FAST_HORIZONTAL_DISTANCE_DP = 48f
    const val FAST_HORIZONTAL_WIDTH_RATIO = 0.14f
    const val DELIBERATE_DIRECTION_RATIO = 2.0f
    const val FAST_DIRECTION_RATIO = 3.0f
    const val MIN_GESTURE_DURATION_MS = 180L
    const val MIN_FLING_VELOCITY_PX_PER_SECOND = 1800f
    const val SYSTEM_BACK_EDGE_DP = 32f
    const val VERTICAL_LOCK_RATIO = 1.2f
    const val DIRECTION_LOCK_SLOP_DP = 12f
    const val TAP_SLOP_DP = 8f
    const val TAP_TIMEOUT_MS = 250L
    const val POST_VERTICAL_SCROLL_SUPPRESS_MS = 450L
    const val SWITCH_COOLDOWN_MS = 250L
    const val CONTENT_TAP_CONSUME_WAIT_MS = 180L
    const val TOP_CHROME_CONTROLS_HEIGHT_DP = 96f
}
