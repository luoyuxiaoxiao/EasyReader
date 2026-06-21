package io.github.luoyuxiaoxiao.easyreader.reader.readium

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubChapterWeightEstimatorTest {
    @Test
    fun estimatesWeightsInSpineOrderWithTextImagesAndFootnotes() {
        val epub = kotlin.io.path.createTempFile(suffix = ".epub").toFile()
        writeEpub(epub)

        val weights = EpubChapterWeightEstimator.estimate(epub)

        assertEquals(2, weights.size)
        assertTrue(weights[0] > weights[1])
        assertTrue(weights[0] >= "第一章正文很多文字".length + 2 * 1000 + 2 * 120)
    }

    @Test
    fun keepsMinimumWeightForEmptyChapter() {
        val weight = EpubChapterWeightEstimator.estimateXhtmlWeight("<html><body></body></html>")

        assertEquals(1, weight)
    }

    private fun writeEpub(file: File) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.writeEntry(
                "META-INF/container.xml",
                """
                <container>
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf"/>
                  </rootfiles>
                </container>
                """.trimIndent(),
            )
            zip.writeEntry(
                "OEBPS/content.opf",
                """
                <package>
                  <manifest>
                    <item id="c1" href="chapter1.xhtml"/>
                    <item id="c2" href="chapter2.xhtml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
                """.trimIndent(),
            )
            zip.writeEntry(
                "OEBPS/chapter1.xhtml",
                """
                <html xmlns:epub="http://www.idpf.org/2007/ops"><body>
                  <p>第一章正文很多文字</p>
                  <img src="a.png"/>
                  <svg></svg>
                  <a href="#note1">注</a>
                  <a epub:type="noteref">注</a>
                </body></html>
                """.trimIndent(),
            )
            zip.writeEntry("OEBPS/chapter2.xhtml", "<html><body><p>短章</p></body></html>")
        }
    }

    private fun ZipOutputStream.writeEntry(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray())
        closeEntry()
    }
}
