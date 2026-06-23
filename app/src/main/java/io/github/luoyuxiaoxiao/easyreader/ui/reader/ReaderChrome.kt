package io.github.luoyuxiaoxiao.easyreader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
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
    }
}
