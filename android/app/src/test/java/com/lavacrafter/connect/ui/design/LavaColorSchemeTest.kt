// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.ui.design

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Compose and the Linux client (linux/app/src/theme.rs) must derive the same palette from an
 * accent; both are checked against design/palette-fixture.json.
 */
class LavaColorSchemeTest {
    private val fixture = File("../../design/palette-fixture.json").readText()

    private fun section(mode: String): Map<String, Int> {
        val body = Regex("\"$mode\"\\s*:\\s*\\{([^}]*)\\}").find(fixture)!!.groupValues[1]
        return Regex("\"(\\w+)\"\\s*:\\s*\"#([0-9A-Fa-f]{6})\"")
            .findAll(body)
            .associate { it.groupValues[1] to it.groupValues[2].toInt(16) }
    }

    private fun roles(scheme: ColorScheme): Map<String, Color> =
        mapOf(
            "primary" to scheme.primary,
            "onPrimary" to scheme.onPrimary,
            "primaryContainer" to scheme.primaryContainer,
            "onPrimaryContainer" to scheme.onPrimaryContainer,
            "secondary" to scheme.secondary,
            "onSecondary" to scheme.onSecondary,
            "secondaryContainer" to scheme.secondaryContainer,
            "onSecondaryContainer" to scheme.onSecondaryContainer,
            "tertiary" to scheme.tertiary,
            "onTertiary" to scheme.onTertiary,
            "tertiaryContainer" to scheme.tertiaryContainer,
            "onTertiaryContainer" to scheme.onTertiaryContainer,
            "surface" to scheme.surface,
            "onSurface" to scheme.onSurface,
            "surfaceVariant" to scheme.surfaceVariant,
            "onSurfaceVariant" to scheme.onSurfaceVariant,
            "surfaceDim" to scheme.surfaceDim,
            "surfaceBright" to scheme.surfaceBright,
            "surfaceContainerLowest" to scheme.surfaceContainerLowest,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "surfaceContainer" to scheme.surfaceContainer,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
            "surfaceContainerHighest" to scheme.surfaceContainerHighest,
            "outline" to scheme.outline,
            "outlineVariant" to scheme.outlineVariant,
            "inverseSurface" to scheme.inverseSurface,
            "inverseOnSurface" to scheme.inverseOnSurface,
            "inversePrimary" to scheme.inversePrimary,
            "error" to scheme.error,
            "onError" to scheme.onError,
            "errorContainer" to scheme.errorContainer,
            "onErrorContainer" to scheme.onErrorContainer,
        )

    private fun assertMatches(
        mode: String,
        scheme: ColorScheme,
    ) {
        val expected = section(mode)
        val actual = roles(scheme)
        assertTrue("fixture has no $mode roles", expected.size == actual.size)
        expected.forEach { (role, want) ->
            val got = actual.getValue(role).toArgb() and 0xFFFFFF
            for (shift in listOf(16, 8, 0)) {
                val delta = ((want shr shift) and 0xFF) - ((got shr shift) and 0xFF)
                assertTrue(
                    "$mode.$role: expected #%06X, got #%06X".format(want, got),
                    kotlin.math.abs(delta) <= 1,
                )
            }
        }
    }

    @Test
    fun lightSchemeMatchesSharedFixture() = assertMatches("light", buildLavaLightColorScheme(Color(0xFF1976D2)))

    @Test
    fun darkSchemeMatchesSharedFixture() = assertMatches("dark", buildLavaDarkColorScheme(Color(0xFF1976D2)))
}
