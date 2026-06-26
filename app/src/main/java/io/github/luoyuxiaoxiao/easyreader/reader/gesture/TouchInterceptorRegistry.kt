package io.github.luoyuxiaoxiao.easyreader.reader.gesture

class TouchInterceptorRegistry {
    private val interceptors = mutableListOf<TouchInterceptor>()
    private var owner: TouchInterceptor? = null

    var gestureConsumed = false
        private set

    fun add(interceptor: TouchInterceptor) {
        interceptors.add(interceptor)
    }

    fun remove(tag: String) {
        interceptors.removeAll { it.tag == tag }
    }

    fun dispatch(detail: TouchDetail): TouchDisposition {
        if (detail.phase == TouchPhase.DOWN) {
            owner = null
            gestureConsumed = false
            var firstHandled = TouchDisposition.pass()
            for (interceptor in sortedInterceptors()) {
                val result = interceptor.onTouchEvent(detail)
                if (firstHandled.result == GestureResult.PASS && result.result != GestureResult.PASS) {
                    firstHandled = result
                }
            }
            return firstHandled
        }

        owner?.let { active ->
            val result = active.onTouchEvent(detail).let { disposition ->
                if (disposition.result == GestureResult.PASS) {
                    TouchDisposition.consumed(cancelChild = false)
                } else {
                    disposition
                }
            }
            if (detail.phase == TouchPhase.UP || detail.phase == TouchPhase.CANCEL) {
                owner = null
                gestureConsumed = false
            }
            return result
        }

        for (interceptor in sortedInterceptors()) {
            val result = interceptor.onTouchEvent(detail)
            if (result.result != GestureResult.PASS) {
                if (result.result == GestureResult.CONSUMED &&
                    detail.phase != TouchPhase.UP &&
                    detail.phase != TouchPhase.CANCEL
                ) {
                    // 当前手势一旦被消费者认领，后续 MOVE/UP/CANCEL 必须稳定交给同一 owner，
                    // 避免低优先级消费者在中途抢走手势。
                    owner = interceptor
                    gestureConsumed = true
                }
                return result
            }
        }

        if (detail.phase == TouchPhase.CANCEL) {
            owner = null
            gestureConsumed = false
        }

        return TouchDisposition.pass()
    }

    fun reset() {
        owner = null
        gestureConsumed = false
    }

    private fun sortedInterceptors(): List<TouchInterceptor> =
        interceptors.sortedByDescending { it.priority }
}
