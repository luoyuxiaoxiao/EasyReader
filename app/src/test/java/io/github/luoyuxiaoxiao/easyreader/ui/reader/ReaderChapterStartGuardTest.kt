package io.github.luoyuxiaoxiao.easyreader.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChapterStartGuardTest {
    @Test
    fun acceptedTopLocatorDoesNotDisableScrollSampleGuard() {
        val guard = ReaderChapterStartGuard(windowMs = 1200L, maxAcceptedProgression = 0.05)
        guard.mark(readingOrderIndex = 2, nowMs = 1000L)

        assertFalse(guard.shouldIgnoreLocator(readingOrderIndex = 2, progression = 0.0, nowMs = 1100L))
        guard.acceptLocatorIfAtStart(readingOrderIndex = 2, progression = 0.0, nowMs = 1100L)

        assertTrue(guard.shouldIgnoreScrollSample(readingOrderIndex = 2, progression = 1.0, nowMs = 1200L))
    }

    @Test
    fun scrollSampleGuardExpiresAfterWindow() {
        val guard = ReaderChapterStartGuard(windowMs = 1200L, maxAcceptedProgression = 0.05)
        guard.mark(readingOrderIndex = 2, nowMs = 1000L)

        assertFalse(guard.shouldIgnoreScrollSample(readingOrderIndex = 2, progression = 1.0, nowMs = 2301L))
    }

    @Test
    fun highTargetLocatorRemainsIgnoredAfterShortScrollWindowUntilLocatorWindowExpires() {
        val guard = ReaderChapterStartGuard(windowMs = 1200L, maxAcceptedProgression = 0.05)
        guard.mark(readingOrderIndex = 2, nowMs = 1000L)

        assertTrue(guard.shouldIgnoreLocator(readingOrderIndex = 2, progression = 1.0, nowMs = 2301L))
        assertFalse(guard.shouldIgnoreLocator(readingOrderIndex = 2, progression = 1.0, nowMs = 6101L))
    }
}
