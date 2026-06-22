package io.github.luoyuxiaoxiao.easyreader.domain.importer

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.luoyuxiaoxiao.easyreader.data.local.BookRepository
import io.github.luoyuxiaoxiao.easyreader.domain.book.Book
import io.github.luoyuxiaoxiao.easyreader.domain.book.Chapter
import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

data class EpubImportResult(
    val uri: Uri,
    val book: Book?,
    val duplicate: Boolean,
)

class EpubImportService(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val bookRepository: BookRepository,
    private val booksDirectory: File = File(context.filesDir, "books"),
) {
    suspend fun importUris(uris: List<Uri>): List<EpubImportResult> =
        uris.map { uri -> importUri(uri) }

    private suspend fun importUri(uri: Uri): EpubImportResult {
        // 导入分两段执行：先复制和哈希，再解析元数据。这样重复书籍不会污染私有书库目录。
        val sha256 = contentResolver.openRequiredInputStream(uri).use { Sha256Hasher.hash(it) }
        val duplicate = bookRepository.findDuplicate(sha256)
        if (duplicate != null) {
            return EpubImportResult(uri = uri, book = duplicate, duplicate = true)
        }

        val bookId = sha256
        val bookDirectory = File(booksDirectory, bookId).apply { mkdirs() }
        val epubFile = File(bookDirectory, "book.epub")
        contentResolver.openRequiredInputStream(uri).use { input ->
            epubFile.outputStream().use { output -> input.copyTo(output) }
        }

        val metadata = EpubMetadataParser.parse(epubFile)
        // 封面是书柜展示增强信息，解析或解码失败不能阻断书籍导入。
        val coverPath = metadata.cover?.let { cover ->
            runCatching {
                ZipFile(epubFile).use { zip ->
                    val entry = zip.getEntry(cover.zipPath) ?: return@runCatching null
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    saveCoverThumbnail(bytes, bookDirectory)
                }
            }.getOrNull()
        }
        val now = System.currentTimeMillis()
        val book = Book(
            id = bookId,
            title = metadata.title,
            author = metadata.author,
            filePath = epubFile.absolutePath,
            sha256 = sha256,
            coverPath = coverPath,
            metadataSeries = metadata.series,
            metadataSeriesIndex = metadata.seriesIndex,
            manualSeries = null,
            manualSeriesIndex = null,
            createdAt = now,
            updatedAt = now,
            lastOpenedAt = null,
        )
        val chapters = metadata.chapters.mapIndexed { index, chapter ->
            Chapter(
                id = chapter.id,
                bookId = bookId,
                index = index,
                href = chapter.href,
                title = chapter.title,
                linear = chapter.linear,
            )
        }

        bookRepository.saveImportedBook(book, chapters)
        return EpubImportResult(uri = uri, book = book, duplicate = false)
    }

    private fun ContentResolver.openRequiredInputStream(uri: Uri) =
        requireNotNull(openInputStream(uri)) { "Cannot open EPUB uri: $uri" }

    private fun saveCoverThumbnail(bytes: ByteArray, bookDirectory: File): String? {
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val maxEdge = maxOf(original.width, original.height)
        val bitmap = if (maxEdge > COVER_MAX_LONG_EDGE) {
            val scale = COVER_MAX_LONG_EDGE.toFloat() / maxEdge.toFloat()
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            original
        }
        val target = File(bookDirectory, "cover.jpg")
        target.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, output)
        }
        if (bitmap !== original) bitmap.recycle()
        original.recycle()
        return target.absolutePath
    }

    private companion object {
        const val COVER_MAX_LONG_EDGE = 512
        const val COVER_JPEG_QUALITY = 85
    }
}

internal data class ParsedEpub(
    val title: String,
    val author: String?,
    val chapters: List<ParsedChapter>,
    val cover: ParsedCover?,
    val series: String?,
    val seriesIndex: Double?,
)

internal data class ParsedChapter(
    val id: String,
    val href: String,
    val title: String,
    val linear: Boolean,
)

internal data class ParsedCover(
    val zipPath: String,
    val extension: String,
)

private data class ManifestItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val properties: String,
)

internal object EpubMetadataParser {
    fun parse(file: File): ParsedEpub =
        ZipFile(file).use { zip ->
            val opfPath = zip.readRootfilePath()
            val opfXml = zip.readTextEntry(opfPath)
            val opf = opfXml.toXmlDocument()
            val opfBasePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")

            val title = opf.firstText("title")?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
            val author = opf.firstText("creator")?.takeIf { it.isNotBlank() }
            val manifest = opf.elements("item").associate { item ->
                val id = item.attribute("id")
                id to ManifestItem(
                    id = id,
                    href = item.attribute("href"),
                    mediaType = item.attribute("media-type"),
                    properties = item.attribute("properties"),
                )
            }
            val series = opf.calibreMeta("series") ?: opf.epub3SeriesName()
            val seriesIndex = opf.calibreMeta("series_index")?.toDoubleOrNull() ?: opf.epub3SeriesIndex()
            val cover = resolveCover(opfBasePath, manifest, opf)
            val chapters = opf.elements("itemref").mapIndexedNotNull { index, itemRef ->
                val idRef = itemRef.attribute("idref").takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                val href = manifest[idRef]?.href ?: idRef
                val zipPath = opfBasePath.resolveZipPath(href)
                val chapterTitle = zip.readChapterTitle(zipPath) ?: "Chapter ${index + 1}"
                ParsedChapter(
                    id = idRef,
                    href = href,
                    title = chapterTitle,
                    linear = itemRef.attribute("linear") != "no",
                )
            }

            ParsedEpub(
                title = title,
                author = author,
                chapters = chapters,
                cover = cover,
                series = series,
                seriesIndex = seriesIndex,
            )
        }

    private fun ZipFile.readRootfilePath(): String {
        val container = readTextEntry("META-INF/container.xml").toXmlDocument()
        return container.elements("rootfile").firstOrNull()?.attribute("full-path")
            ?: error("EPUB container.xml does not declare a rootfile")
    }

    private fun ZipFile.readChapterTitle(path: String): String? =
        runCatching { readTextEntry(path).toXmlDocument().firstText("title")?.takeIf { it.isNotBlank() } }
            .getOrNull()

    private fun ZipFile.readTextEntry(path: String): String {
        val entry = getEntry(path) ?: error("Missing EPUB entry: $path")
        return getInputStream(entry).bufferedReader().use { it.readText() }
    }

    private fun String.resolveZipPath(href: String): String =
        if (isBlank()) href else "$this/$href"

    private fun resolveCover(
        opfBasePath: String,
        manifest: Map<String, ManifestItem>,
        document: org.w3c.dom.Document,
    ): ParsedCover? {
        // 按 EPUB3、OPF2、文件名兜底的顺序找封面，匹配常见制作工具输出。
        val coverItem = manifest.values.firstOrNull { item ->
            item.properties.splitToSequence(' ', '\t', '\n', '\r').any { it == "cover-image" }
        } ?: document.elements("meta")
            .firstOrNull { it.attribute("name") == "cover" }
            ?.attribute("content")
            ?.let { manifest[it] }
            ?: manifest.values.firstOrNull {
                it.mediaType.startsWith("image/") && it.href.contains("cover", ignoreCase = true)
            }

        val item = coverItem ?: return null
        val path = opfBasePath.resolveZipPath(item.href)
        val extension = path.substringAfterLast('.', "jpg").lowercase()
        return ParsedCover(path, extension)
    }

    private fun String.toXmlDocument() =
        DocumentBuilderFactory.newInstance()
            .apply {
                isNamespaceAware = true
                safeSetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                safeSetFeature("http://xml.org/sax/features/external-general-entities", false)
                safeSetFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(this)))

    private fun DocumentBuilderFactory.safeSetFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
            .onFailure { error ->
                if (error !is ParserConfigurationException) throw error
            }
    }

    private fun org.w3c.dom.Document.firstText(localName: String): String? =
        elements(localName).firstOrNull()?.textContent?.trim()

    private fun org.w3c.dom.Document.calibreMeta(name: String): String? =
        elements("meta")
            .firstOrNull { it.attribute("name") == "calibre:$name" }
            ?.attribute("content")
            ?.takeIf { it.isNotBlank() }

    private fun org.w3c.dom.Document.epub3SeriesName(): String? {
        val collection = elements("meta").firstOrNull { meta ->
            meta.attribute("property") == "belongs-to-collection" &&
                elements("meta").any { refine ->
                    refine.attribute("refines") == "#${meta.attribute("id")}" &&
                        refine.attribute("property") == "collection-type" &&
                        refine.textContent.trim() == "series"
                }
        }
        return collection?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun org.w3c.dom.Document.epub3SeriesIndex(): Double? {
        val collectionId = elements("meta").firstOrNull { meta ->
            meta.attribute("property") == "belongs-to-collection"
        }?.attribute("id")?.takeIf { it.isNotBlank() } ?: return null
        return elements("meta")
            .firstOrNull { it.attribute("refines") == "#$collectionId" && it.attribute("property") == "group-position" }
            ?.textContent
            ?.trim()
            ?.toDoubleOrNull()
    }

    private fun org.w3c.dom.Document.elements(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { it as Element }
    }

    private fun Element.attribute(name: String): String = getAttribute(name).trim()
}
