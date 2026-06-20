package io.github.luoyuxiaoxiao.easyreader.fixtures

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object MinimalEpubFixture {
    fun writeTo(file: File) {
        file.parentFile?.mkdirs()
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
                            <dc:identifier id="book-id">minimal-epub</dc:identifier>
                            <dc:title>Minimal EPUB</dc:title>
                            <dc:creator>EasyReader</dc:creator>
                            <dc:language>zh-CN</dc:language>
                        </metadata>
                        <manifest>
                            <item id="chapter-1" href="chapter-1.xhtml" media-type="application/xhtml+xml"/>
                            <item id="chapter-2" href="chapter-2.xhtml" media-type="application/xhtml+xml"/>
                        </manifest>
                        <spine>
                            <itemref idref="chapter-1"/>
                            <itemref idref="chapter-2"/>
                        </spine>
                    </package>
                """.trimIndent()
            )
            zip.writeDeflatedEntry("OEBPS/chapter-1.xhtml", chapter("Chapter 1"))
            zip.writeDeflatedEntry("OEBPS/chapter-2.xhtml", chapter("Chapter 2"))
        }
    }

    private fun chapter(title: String): String =
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>$title</title></head>
                <body><h1>$title</h1><p>EasyReader fixture.</p></body>
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
}
