package io.github.luoyuxiaoxiao.easyreader.ui.reader

import android.text.Html
import android.text.TextUtils
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

private val ReaderAccent = Color(0xFFFF8A00)

@Composable
fun ReaderChrome(
    state: ReaderUiState,
    onBack: () -> Unit,
    footnoteHtml: String? = null,
    onDismissFootnote: () -> Unit = {},
    imagePreviewUrl: String? = null,
    onDismissImagePreview: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        BackHandler(enabled = imagePreviewUrl != null || footnoteHtml != null) {
            when {
                imagePreviewUrl != null -> onDismissImagePreview()
                footnoteHtml != null -> onDismissFootnote()
            }
        }

        if (state.topChromeVisible) {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        // 顶部背景覆盖状态栏区域，只让内部控件避开系统栏，避免上方出现无法显示文字的空白条。
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Text("返回")
                    }
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }
            }
        }

        if (state.bottomChromeVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    state.totalProgressText,
                    color = ReaderAccent,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    state.edgeMessage ?: state.chapterProgressText,
                    color = ReaderAccent,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        state.fontSizeOverlayText?.let { text ->
            Text(
                text = text,
                color = ReaderAccent,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        footnoteHtml?.let { html ->
            ReaderFootnoteSheet(
                html = html,
                onDismiss = onDismissFootnote,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        imagePreviewUrl?.let { url ->
            ReaderImagePreview(
                imageUrl = url,
                onDismiss = onDismissImagePreview,
            )
        }
    }
}

@Composable
private fun ReaderFootnoteSheet(
    html: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss),
    )
    Surface(
        tonalElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp)
            .clickable(onClick = {}),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim(),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ReaderImagePreview(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun close() {
                                post { onDismiss() }
                            }
                        },
                        "EasyReaderPreview",
                    )
                    loadImagePreview(imageUrl)
                }
            },
            update = { webView ->
                if (webView.tag != imageUrl) {
                    webView.loadImagePreview(imageUrl)
                }
            },
        )
    }
}

private fun WebView.loadImagePreview(imageUrl: String) {
    tag = imageUrl
    loadDataWithBaseURL(imageUrl, imagePreviewHtml(imageUrl), "text/html", "UTF-8", null)
}

private fun imagePreviewHtml(imageUrl: String): String {
    val encodedUrl = TextUtils.htmlEncode(imageUrl)
    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=8.0, user-scalable=yes">
          <style>
            html, body { margin: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
            body { display: flex; align-items: center; justify-content: center; }
            img { max-width: 100%; max-height: 100%; object-fit: contain; }
          </style>
        </head>
        <body onclick="EasyReaderPreview.close()">
          <img src="$encodedUrl" />
        </body>
        </html>
    """.trimIndent()
}
