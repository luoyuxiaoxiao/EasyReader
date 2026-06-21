package io.github.luoyuxiaoxiao.easyreader.ui.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderStartLocatorTest {
    @Test
    fun prefersLatestSessionLocatorOverOriginalOpenLocator() {
        val fallback = locator("chapter-1.xhtml")
        val latest = locator("chapter-2.xhtml")

        val selected = ReaderStartLocator.select(
            latestLocatorJson = latest.toJSON().toString(),
            fallbackLocator = fallback,
        )

        assertEquals("chapter-2.xhtml", selected?.href.toString())
    }

    @Test
    fun fallsBackToOriginalLocatorWhenLatestLocatorIsInvalid() {
        val fallback = locator("chapter-1.xhtml")

        val selected = ReaderStartLocator.select(
            latestLocatorJson = """{"bad":true}""",
            fallbackLocator = fallback,
        )

        assertEquals("chapter-1.xhtml", selected?.href.toString())
    }

    private fun locator(href: String): Locator =
        requireNotNull(Locator.fromJSON(
            JSONObject(
                """
                    {
                      "href": "$href",
                      "type": "application/xhtml+xml",
                      "locations": {
                        "progression": 0.5,
                        "totalProgression": 0.5
                      }
                    }
                """.trimIndent()
            )
        ))
}
