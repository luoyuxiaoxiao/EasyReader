package io.github.luoyuxiaoxiao.easyreader.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSettingsTest {
    @Test
    fun resolvesSystemThemeFromDeviceTheme() {
        assertTrue(AppThemeMode.System.resolveDarkTheme(systemDarkTheme = true))
        assertFalse(AppThemeMode.System.resolveDarkTheme(systemDarkTheme = false))
    }

    @Test
    fun explicitThemeOverridesSystemTheme() {
        assertFalse(AppThemeMode.Light.resolveDarkTheme(systemDarkTheme = true))
        assertTrue(AppThemeMode.Dark.resolveDarkTheme(systemDarkTheme = false))
    }
}
