package io.github.luoyuxiaoxiao.easyreader.fixtures

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class MinimalEpubOptions(
    val title: String = "Minimal EPUB",
    val author: String = "EasyReader",
    val includeCover: Boolean = false,
    val calibreSeries: String? = null,
    val calibreSeriesIndex: Double? = null,
    val chapter1Body: String? = null,
    val chapter2Body: String? = null,
)

object MinimalEpubFixture {
    fun writeTo(file: File, options: MinimalEpubOptions = MinimalEpubOptions()) {
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
                            <dc:title>${options.title}</dc:title>
                            <dc:creator>${options.author}</dc:creator>
                            <dc:language>zh-CN</dc:language>
                            ${options.calibreSeries?.let { """<meta name="calibre:series" content="$it"/>""" } ?: ""}
                            ${options.calibreSeriesIndex?.let { """<meta name="calibre:series_index" content="$it"/>""" } ?: ""}
                        </metadata>
                        <manifest>
                            ${if (options.includeCover) """<item id="cover-img" href="images/cover.png" media-type="image/png" properties="cover-image"/>""" else ""}
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
            zip.writeDeflatedEntry("OEBPS/chapter-1.xhtml", chapter("Chapter 1", options.chapter1Body))
            zip.writeDeflatedEntry("OEBPS/chapter-2.xhtml", chapter("Chapter 2", options.chapter2Body))
            if (options.includeCover) {
                zip.writeDeflatedEntry("OEBPS/images/cover.png", tinyPng)
            }
        }
    }

    private fun chapter(title: String, body: String? = null): String =
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>$title</title></head>
                <body>${body ?: "<h1>$title</h1><p>EasyReader fixture.</p>"}</body>
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

    private val tinyPng: ByteArray by lazy {
        // 使用 Android Bitmap 生成夹具封面，避免手写 PNG 字节被平台解码器拒绝。
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.rgb(30, 165, 88))
        }
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }
}
