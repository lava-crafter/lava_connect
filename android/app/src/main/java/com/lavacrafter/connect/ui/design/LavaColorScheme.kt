// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private val lightSurface = Color(0xFFF7F7F8)
private val darkSurface = Color(0xFF1F1F21)
private const val DEFAULT_ACCENT = "#1976D2"

fun lavaColorScheme(
    isDarkMode: Boolean,
    accentValue: String,
): ColorScheme {
    val accentColor = lavaAccentColor(accentValue)
    return if (isDarkMode) {
        buildLavaDarkColorScheme(accentColor)
    } else {
        buildLavaLightColorScheme(accentColor)
    }
}

internal fun buildLavaLightColorScheme(accentColor: Color): ColorScheme =
    lightColorScheme(
        primary = accentColor,
        onPrimary = Color.White,
        primaryContainer = accentOverlay(Color.White, accentColor, 0.18f),
        onPrimaryContainer = accentOverlay(Color.Black, accentColor, 0.32f),
        secondary = accentOverlay(accentColor, Color.Black, 0.08f),
        onSecondary = Color.White,
        secondaryContainer = accentOverlay(Color.White, accentColor, 0.14f),
        onSecondaryContainer = accentOverlay(Color.Black, accentColor, 0.28f),
        tertiary = Color(0xFF0277BD),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFCBEAFF),
        onTertiaryContainer = Color(0xFF001E2B),
        surface = lightSurface,
        onSurface = Color(0xFF171C22),
        surfaceVariant = accentOverlay(lightSurface, accentColor, 0.10f),
        onSurfaceVariant = Color(0xFF414852),
        surfaceTint = accentColor,
        surfaceDim = accentOverlay(lightSurface, Color.Black, 0.05f),
        surfaceBright = Color(0xFFFCFCFD),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = accentOverlay(lightSurface, accentColor, 0.04f),
        surfaceContainer = accentOverlay(lightSurface, accentColor, 0.07f),
        surfaceContainerHigh = accentOverlay(lightSurface, accentColor, 0.10f),
        surfaceContainerHighest = accentOverlay(lightSurface, accentColor, 0.14f),
        background = lightSurface,
        onBackground = Color(0xFF171C22),
        outline = Color(0xFF717985),
        outlineVariant = accentOverlay(Color(0xFFC1C8D2), accentColor, 0.08f),
        inverseSurface = Color(0xFF2C3138),
        inverseOnSurface = Color(0xFFF0F1F5),
        inversePrimary = accentOverlay(accentColor, Color.White, 0.55f),
        scrim = Color.Black,
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    )

internal fun buildLavaDarkColorScheme(accentColor: Color): ColorScheme =
    darkColorScheme(
        primary = accentColor,
        onPrimary = Color.White,
        primaryContainer = accentOverlay(darkSurface, accentColor, 0.38f),
        onPrimaryContainer = accentOverlay(Color.White, accentColor, 0.12f),
        secondary = accentOverlay(accentColor, Color.White, 0.28f),
        onSecondary = Color(0xFF002F4A),
        secondaryContainer = accentOverlay(darkSurface, accentColor, 0.25f),
        onSecondaryContainer = accentOverlay(Color.White, accentColor, 0.10f),
        tertiary = Color(0xFF81D4FA),
        onTertiary = Color(0xFF003547),
        tertiaryContainer = Color(0xFF0B4054),
        onTertiaryContainer = Color(0xFFCBEAFF),
        surface = darkSurface,
        onSurface = Color(0xFFE6E8EC),
        surfaceVariant = accentOverlay(darkSurface, accentColor, 0.08f),
        onSurfaceVariant = Color(0xFFC1C7D0),
        surfaceTint = accentColor,
        surfaceDim = Color(0xFF1B1B1D),
        surfaceBright = accentOverlay(darkSurface, accentColor, 0.16f),
        surfaceContainerLowest = Color(0xFF19191B),
        surfaceContainerLow = accentOverlay(darkSurface, accentColor, 0.04f),
        surfaceContainer = accentOverlay(darkSurface, accentColor, 0.07f),
        surfaceContainerHigh = accentOverlay(darkSurface, accentColor, 0.11f),
        surfaceContainerHighest = accentOverlay(darkSurface, accentColor, 0.15f),
        background = darkSurface,
        onBackground = Color(0xFFE6E8EC),
        outline = Color(0xFF8A929E),
        outlineVariant = accentOverlay(Color(0xFF3D4652), accentColor, 0.08f),
        inverseSurface = Color(0xFFE6E8EC),
        inverseOnSurface = Color(0xFF25272B),
        inversePrimary = accentOverlay(accentColor, Color.Black, 0.10f),
        scrim = Color.Black,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    )

internal fun accentOverlay(
    base: Color,
    accent: Color,
    amount: Float,
): Color = lerp(base, accent, amount.coerceIn(0f, 1f))

internal fun lavaAccentColor(value: String): Color =
    Color(
        android.graphics.Color.parseColor(normalizeAccent(value)),
    )

internal fun normalizeAccent(value: String): String {
    val normalized = value.trim()
    return normalized.takeIf { it.matches(HEX_COLOR_REGEX) } ?: DEFAULT_ACCENT
}

private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")
