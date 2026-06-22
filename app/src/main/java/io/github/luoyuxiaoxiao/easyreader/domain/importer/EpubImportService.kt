package io.github.luoyuxiaoxiao.easyreader.domain.importer

import android.content.ContentResolver
import android.content.Context
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
        val now = System.currentTimeMillis()
        val book = Book(
            id = bookId,
            title = metadata.title,
            author = metadata.author,
            filePath = epubFile.absolutePath,
            sha256 = sha256,
            coverPath = null,
            metadataSeries = null,
            metadataSeriesIndex = null,
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
}

private data class ParsedEpub(
    val title: String,
    val author: String?,
    val chapters: List<ParsedChapter>,
)

private data class ParsedChapter(
    val id: String,
    val href: String,
    val title: String,
    val linear: Boolean,
)

private object EpubMetadataParser {
    fun parse(file: File): ParsedEpub =
        ZipFile(file).use { zip ->
            val opfPath = zip.readRootfilePath()
            val opfXml = zip.readTextEntry(opfPath)
            val opf = opfXml.toXmlDocument()
            val opfBasePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")

            val title = opf.firstText("title")?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
            val author = opf.firstText("creator")?.takeIf { it.isNotBlank() }
            val manifest = opf.elements("item").associate { item ->
                item.attribute("id") to item.attribute("href")
            }
            val chapters = opf.elements("itemref").mapIndexedNotNull { index, itemRef ->
                val idRef = itemRef.attribute("idref").takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                val href = manifest[idRef] ?: idRef
                val zipPath = opfBasePath.resolveZipPath(href)
                val chapterTitle = zip.readChapterTitle(zipPath) ?: "Chapter ${index + 1}"
                ParsedChapter(
                    id = idRef,
                    href = href,
                    title = chapterTitle,
                    linear = itemRef.attribute("linear") != "no",
                )
            }

            ParsedEpub(title = title, author = author, chapters = chapters)
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

    private fun org.w3c.dom.Document.elements(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { it as Element }
    }

    private fun Element.attribute(name: String): String = getAttribute(name).trim()
}
