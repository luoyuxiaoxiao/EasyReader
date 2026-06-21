package io.github.luoyuxiaoxiao.easyreader.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSettingsTest {
    @Test
    fun mapsScrollModeToReadiumPreferences() {
        assertTrue(ReaderSettings(scroll = true).toEpubPreferences().scroll!!)
        assertFalse(ReaderSettings(scroll = false).toEpubPreferences().scroll!!)
    }

    @Test
    fun mapsBasicVisualPreferencesToReadiumPreferences() {
        val preferences = ReaderSettings(
            fontScale = 1.25f,
            publisherStyles = false,
        ).toEpubPreferences()

        assertEquals(1.25, preferences.fontSize!!, 0.0001)
        assertFalse(preferences.publisherStyles!!)
    }
}
