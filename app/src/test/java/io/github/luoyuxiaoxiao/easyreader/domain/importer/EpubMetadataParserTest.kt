package io.github.luoyuxiaoxiao.easyreader.domain.importer

import java.io.File
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubMetadataParserTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun parsesCoverAndCalibreSeriesMetadata() {
        val epub = temporaryFolder.newFile("series-cover.epub")
        writeEpub(epub)

        val metadata = EpubMetadataParser.parse(epub)

        assertEquals("Fate stay night", metadata.series)
        assertEquals(1.0, metadata.seriesIndex!!, 0.0001)
        assertNotNull(metadata.cover)
        assertEquals("OEBPS/images/cover.png", metadata.cover!!.zipPath)
    }

    private fun writeEpub(file: File) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.writeStoredEntry("mimetype", "application/epub+zip")
            zip.writeDeflatedEntry(
                "META-INF/container.xml",
                """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                        <rootfiles>
                            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                        </rootfiles>
                    </container>
                """.trimIndent()
            )
            zip.writeDeflatedEntry(
                "OEBPS/content.opf",
                """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package version="3.0" unique-identifier="book-id" xmlns="http://www.idpf.org/2007/opf">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Minimal EPUB</dc:title>
                            <dc:creator>EasyReader</dc:creator>
                            <meta name="calibre:series" content="Fate stay night"/>
                            <meta name="calibre:series_index" content="1.0"/>
                        </metadata>
                        <manifest>
                            <item id="cover-img" href="images/cover.png" media-type="image/png" properties="cover-image"/>
                            <item id="chapter-1" href="chapter-1.xhtml" media-type="application/xhtml+xml"/>
                        </manifest>
                        <spine>
                            <itemref idref="chapter-1"/>
                        </spine>
                    </package>
                """.trimIndent()
            )
            zip.writeDeflatedEntry("OEBPS/chapter-1.xhtml", chapter("Chapter 1"))
            zip.writeDeflatedEntry("OEBPS/images/cover.png", tinyPng)
        }
    }

    private fun chapter(title: String): String =
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>$title</title></head>
                <body><h1>$title</h1></body>
            </html>
        """.trimIndent()

    private fun ZipOutputStream.writeStoredEntry(name: String, content: String) {
        val bytes = content.toByteArray()
        val crc = CRC32().apply { update(bytes) }
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
        putNextEntry(entry)
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.writeDeflatedEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.writeDeflatedEntry(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }

    private val tinyPng: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADUlEQVR42mP8z8BQDwAFgwJ/lD0cWQAAAABJRU5ErkJggg=="
    )
}
