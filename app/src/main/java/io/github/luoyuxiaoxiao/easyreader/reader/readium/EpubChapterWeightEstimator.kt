package io.github.luoyuxiaoxiao.easyreader.reader.readium

import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

object EpubChapterWeightEstimator {
    private const val IMAGE_WEIGHT = 1000
    private const val NOTE_LINK_WEIGHT = 120

    fun estimate(file: File): List<Int> =
        runCatching {
            ZipFile(file).use { zip ->
                val opfPath = zip.readRootfilePath()
                val opfBasePath = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
                val opf = zip.readTextEntry(opfPath).toXmlDocument()
                val manifest = opf.elements("item").associate { item ->
                    item.attribute("id") to item.attribute("href")
                }
                opf.elements("itemref").mapNotNull { itemRef ->
                    val idRef = itemRef.attribute("idref").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val href = manifest[idRef] ?: idRef
                    val zipPath = opfBasePath.resolveZipPath(href)
                    val xhtml = runCatching { zip.readTextEntry(zipPath) }.getOrNull() ?: return@mapNotNull 1
                    estimateXhtmlWeight(xhtml)
                }
            }
        }.getOrDefault(emptyList())

    fun estimateXhtmlWeight(xhtml: String): Int =
        runCatching {
            val document = xhtml.toXmlDocument()
            val body = document.elements("body").firstOrNull() ?: document.documentElement
            val visibleTextLength = body.textContent
                ?.replace(Regex("\\s+"), "")
                ?.length
                ?: 0
            val imageWeight = (document.elements("img").size + document.elements("svg").size) * IMAGE_WEIGHT
            val noteWeight = document.elements("a").count { anchor ->
                val rel = anchor.attribute("rel")
                val type = anchor.attribute("type").ifBlank { anchor.attribute("epub:type") }
                val href = anchor.attribute("href")
                rel.contains("note", ignoreCase = true) ||
                    type.contains("note", ignoreCase = true) ||
                    href.contains("note", ignoreCase = true)
            } * NOTE_LINK_WEIGHT

            (visibleTextLength + imageWeight + noteWeight).coerceAtLeast(1)
        }.getOrDefault(1)

    private fun ZipFile.readRootfilePath(): String {
        val container = readTextEntry("META-INF/container.xml").toXmlDocument()
        return container.elements("rootfile").firstOrNull()?.attribute("full-path")
            ?: error("EPUB container.xml does not declare a rootfile")
    }

    private fun ZipFile.readTextEntry(path: String): String {
        val entry = getEntry(path) ?: error("Missing EPUB entry: $path")
        return getInputStream(entry).bufferedReader().use { it.readText() }
    }

    private fun String.resolveZipPath(href: String): String =
        if (isBlank()) href else "$this/$href"

    private fun String.toXmlDocument(): Document =
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

    private fun Document.elements(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { it as Element }
    }

    private fun Element.attribute(name: String): String = getAttribute(name).trim()
}
