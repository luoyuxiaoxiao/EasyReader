package io.github.luoyuxiaoxiao.easyreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import io.github.luoyuxiaoxiao.easyreader.data.settings.AppThemeMode
import io.github.luoyuxiaoxiao.easyreader.data.settings.resolveDarkTheme

@Composable
fun EasyReaderTheme(
    mode: AppThemeMode,
    content: @Composable () -> Unit,
) {
    val darkTheme = mode.resolveDarkTheme(isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
