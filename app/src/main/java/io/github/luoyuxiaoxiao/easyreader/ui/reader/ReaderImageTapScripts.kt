package io.github.luoyuxiaoxiao.easyreader.ui.reader

import java.util.Locale

internal object ReaderImageTapScripts {
    private const val DEFAULT_BRIDGE_NAME = "EasyReaderImageBridge"

    fun probeScript(
        clientX: Float,
        clientY: Float,
        bridgeName: String = DEFAULT_BRIDGE_NAME,
    ): String {
        val x = String.format(Locale.US, "%.2f", clientX)
        val y = String.format(Locale.US, "%.2f", clientY)
        return """
            (function() {
              ${imageOpenSupportScript(bridgeName)}
              var node = window.__easyReaderFindImageNode(document.elementFromPoint($x, $y));
              if (!node) return false;
              return window.__easyReaderOpenImageFromNode(node);
            })();
        """.trimIndent()
    }

    fun clickBridgeScript(bridgeName: String): String =
        """
            (function() {
              ${imageOpenSupportScript(bridgeName)}
              if (window.__easyReaderImageTapInstalled) return;
              window.__easyReaderImageTapInstalled = true;
              document.addEventListener('click', function(event) {
                var node = window.__easyReaderFindImageNode(event.target);
                if (!node) return;
                event.preventDefault();
                event.stopPropagation();
                if (event.stopImmediatePropagation) event.stopImmediatePropagation();
                window.__easyReaderOpenImageFromNode(node);
              }, true);
            })();
        """.trimIndent()

    fun consumeProbeResultScript(): String =
        """
            (function() {
              var value = window.__easyReaderLastImagePreviewSource || null;
              window.__easyReaderLastImagePreviewSource = null;
              return value;
            })();
        """.trimIndent()

    private fun imageOpenSupportScript(bridgeName: String): String =
        """
            window.__easyReaderFindImageNode = function(node) {
              while (node && node.tagName && node.tagName.toLowerCase() !== 'img') {
                node = node.parentElement;
              }
              if (!node || !node.tagName || node.tagName.toLowerCase() !== 'img') return null;
              return node;
            };
            window.__easyReaderResolveImageSource = function(node) {
              var src = node.currentSrc || node.src || node.getAttribute('src');
              if (!src) return null;
              try {
                return new URL(src, document.baseURI).href;
              } catch (error) {
                // 保留原始 src，便于 data: 或 WebView 特殊 URL 继续按原方式加载。
                return src;
              }
            };
            window.__easyReaderDeliverImagePreview = function(src) {
              if (!src) return false;
              var publish = function(value) {
                window.__easyReaderLastImagePreviewSource = value;
                if (window.$bridgeName && window.$bridgeName.open) {
                  window.$bridgeName.open(value);
                }
              };
              if (src.indexOf('data:') === 0) {
                publish(src);
                return true;
              }
              try {
                // 在 reader WebView 内读取 Readium 拦截到的资源，再把可独立渲染的 data URL 交给预览层。
                fetch(src)
                  .then(function(response) {
                    if (!response.ok) throw new Error('Image request failed: ' + response.status);
                    return response.blob();
                  })
                  .then(function(blob) {
                    var reader = new FileReader();
                    reader.onloadend = function() {
                      if (typeof reader.result === 'string' && reader.result.length > 0) {
                        publish(reader.result);
                      } else {
                        publish(src);
                      }
                    };
                    reader.onerror = function() {
                      publish(src);
                    };
                    try {
                      reader.readAsDataURL(blob);
                    } catch (error) {
                      publish(src);
                    }
                  })
                  .catch(function() {
                    publish(src);
                  });
              } catch (error) {
                publish(src);
              }
              return true;
            };
            window.__easyReaderOpenImageFromNode = function(node) {
              window.__easyReaderLastImagePreviewSource = null;
              var src = window.__easyReaderResolveImageSource(node);
              return window.__easyReaderDeliverImagePreview(src);
            };
        """.trimIndent()

    fun parseProbeResult(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == "null" || trimmed == "false") return null
        return if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
            trimmed.substring(1, trimmed.lastIndex)
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .takeIf { it.isNotBlank() }
        } else {
            trimmed
        }
    }

    fun parseProbeHit(raw: String?): Boolean = raw?.trim() == "true"
}
