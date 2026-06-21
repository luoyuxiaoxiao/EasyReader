package io.github.luoyuxiaoxiao.easyreader.ui.reader

import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

object ReaderStartLocator {
    fun select(latestLocatorJson: String?, fallbackLocator: Locator?): Locator? {
        // 旋转重建 Navigator 时优先使用内存中的最新 locator；解析失败再回退到打开书籍时的初始位置。
        val latestLocator = latestLocatorJson
            ?.let { json -> runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull() }
        return latestLocator ?: fallbackLocator
    }
}
