package io.github.luoyuxiaoxiao.easyreader.data.settings

import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.SeriesGroupingRule
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.SeriesGroupingRuleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesGroupingRuleStoreTest {
    @Test
    fun encodesAndDecodesSettings() {
        val settings = SeriesGroupingRuleSettings(
            customRules = listOf(
                SeriesGroupingRule(
                    id = "custom-1",
                    name = "Fate",
                    pattern = """(?<series>Fate).+?(?<index>\d+)""",
                    enabled = true,
                    priority = 0,
                    builtIn = false,
                )
            ),
            disabledBuiltInRuleIds = setOf("builtin-number"),
        )

        val encoded = SeriesGroupingRuleSerializer.encode(settings)
        val decoded = SeriesGroupingRuleSerializer.decode(encoded)

        assertEquals(settings, decoded)
    }

    @Test
    fun invalidStoredJsonFallsBackToDefaultSettings() {
        val decoded = SeriesGroupingRuleSerializer.decode("{bad json")

        assertTrue(decoded.customRules.isEmpty())
        assertTrue(decoded.disabledBuiltInRuleIds.isEmpty())
    }
}
