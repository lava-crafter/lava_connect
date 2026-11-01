// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LavaTheme(
    colorScheme: ColorScheme,
    profile: LavaThemeProfile,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLavaMotion provides profile.motion) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = profile.typography,
            shapes = LavaShapes.forProfile(profile.shapeProfile),
            motionScheme = LavaMotion.materialSchemeFor(profile.shapeProfile),
            content = content,
        )
    }
}
