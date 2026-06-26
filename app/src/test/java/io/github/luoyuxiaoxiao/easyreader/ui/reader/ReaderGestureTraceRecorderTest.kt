package io.github.luoyuxiaoxiao.easyreader.ui.reader

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderGestureTraceRecorderTest {
    @Test
    fun recordsRawEventsAndEmitsSummaryAtGestureEnd() {
        val logs = mutableListOf<String>()
        val recorder = ReaderGestureTraceRecorder(
            enabled = true,
            logger = logs::add,
        )

        recorder.recordRaw(MotionEvent.ACTION_DOWN, x = 700f, y = 900f, eventTime = 0L)
        recorder.recordRaw(MotionEvent.ACTION_MOVE, x = 430f, y = 904f, eventTime = 120L)
        recorder.recordRaw(MotionEvent.ACTION_UP, x = 690f, y = 906f, eventTime = 260L)

        assertEquals(3, recorder.snapshot().size)
        assertEquals(1, logs.size)
        assertTrue(logs.single().contains("netDx=-10.0"))
        assertTrue(logs.single().contains("pathDx=530.0"))
        assertTrue(logs.single().contains("DOWN@0ms(700.0,900.0"))
        assertTrue(logs.single().contains("MOVE@120ms(430.0,904.0"))
        assertTrue(logs.single().contains("UP@260ms(690.0,906.0"))
    }

    @Test
    fun expandsHistoricalMovePointsInGestureSummary() {
        val logs = mutableListOf<String>()
        val recorder = ReaderGestureTraceRecorder(
            enabled = true,
            logger = logs::add,
        )

        recorder.record(motion(MotionEvent.ACTION_DOWN, x = 700f, y = 900f, eventTime = 0L), false, false, false)
        recorder.record(
            motion(MotionEvent.ACTION_MOVE, x = 600f, y = 930f, eventTime = 120L).apply {
                addBatch(40L, 700f, 1200f, 1f, 1f, 0)
                addBatch(80L, 660f, 1080f, 1f, 1f, 0)
            },
            verticalLocked = true,
            horizontalLocked = false,
            scaling = false,
        )
        recorder.record(motion(MotionEvent.ACTION_UP, x = 560f, y = 930f, eventTime = 180L), true, false, false)

        val log = logs.single()
        assertTrue(log.contains("MOVE@40ms(700.0,1200.0"))
        assertTrue(log.contains("MOVE@80ms(660.0,1080.0"))
        assertTrue(log.contains("verticalReversed=true"))
    }

    private fun motion(action: Int, x: Float, y: Float, eventTime: Long): MotionEvent =
        MotionEvent.obtain(0L, eventTime, action, x, y, 0)
}
