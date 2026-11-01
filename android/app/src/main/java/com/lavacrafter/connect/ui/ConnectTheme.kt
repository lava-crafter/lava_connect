// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.lavacrafter.connect.ui.design.LavaTheme
import com.lavacrafter.connect.ui.design.lavaColorScheme
import com.lavacrafter.connect.ui.design.lavaThemeProfile

@Composable
internal fun ConnectTheme(
    appearance: Appearance,
    content: @Composable () -> Unit,
) {
    val dark =
        when (appearance.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    LavaTheme(
        colorScheme = lavaColorScheme(isDarkMode = dark, accentValue = appearance.accent),
        profile = lavaThemeProfile(appearance.shapeProfile),
        content = content,
    )
}
