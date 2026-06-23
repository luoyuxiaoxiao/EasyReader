package io.github.luoyuxiaoxiao.easyreader.reader.readium

import io.github.luoyuxiaoxiao.easyreader.core.result.EasyReaderResult
import io.github.luoyuxiaoxiao.easyreader.domain.book.Book
import io.github.luoyuxiaoxiao.easyreader.domain.book.ReadingProgress
import java.io.File
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.mediatype.MediaType

data class EpubReaderSessionState(
    val publication: Publication,
    val navigatorFactory: EpubNavigatorFactory,
    val initialLocator: Locator?,
    val initialPreferences: EpubPreferences,
    val initialReadingOrderIndex: Int,
    val chapterWeights: List<Int>,
)

class EpubReaderSession(
    private val readiumServices: ReadiumServices,
) {
    private var openedPublication: Publication? = null

    suspend fun open(
        book: Book,
        savedProgress: ReadingProgress?,
        initialPreferences: EpubPreferences,
    ): EasyReaderResult<EpubReaderSessionState> {
        close()

        val asset = readiumServices.assetRetriever
            .retrieve(File(book.filePath), MediaType.EPUB)
            .getOrNull()
            ?: return EasyReaderResult.Failure("无法打开 EPUB 文件")

        val publication = readiumServices.publicationOpener
            .open(asset, allowUserInteraction = true)
            .getOrNull()
            ?: return EasyReaderResult.Failure("无法解析 EPUB 文件")

        if (publication.isRestricted) {
            publication.close()
            return EasyReaderResult.Failure("这本书受 DRM 或权限限制，暂时无法打开")
        }

        val initialLocator = savedProgress?.locatorJson
            ?.let { json -> runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull() }

        // 同一本书阅读期间 Publication 只打开一次，横滑切章只做 Navigator 导航，避免重建 WebView 造成卡顿。
        openedPublication = publication
        return EasyReaderResult.Success(
            EpubReaderSessionState(
                publication = publication,
                navigatorFactory = EpubNavigatorFactory(publication),
                initialLocator = initialLocator,
                initialPreferences = initialPreferences,
                initialReadingOrderIndex = savedProgress?.readingOrderIndex ?: 0,
                chapterWeights = EpubChapterWeightEstimator.estimate(File(book.filePath)),
            )
        )
    }

    fun close() {
        openedPublication?.close()
        openedPublication = null
    }
}
