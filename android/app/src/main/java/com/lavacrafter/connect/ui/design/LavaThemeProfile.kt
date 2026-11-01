// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design

import androidx.compose.material3.Typography

data class LavaThemeProfile(
    val shapeProfile: UiShapeProfile,
    val colorStrategy: LavaColorStrategy = LavaColorStrategy.Accent,
    val typography: Typography = LavaTypography.default,
    val motion: LavaMotionSpec = LavaMotion.standard,
)

enum class LavaColorStrategy {
    Accent,
}

fun lavaThemeProfile(shapeProfile: UiShapeProfile): LavaThemeProfile =
    LavaThemeProfile(
        shapeProfile = shapeProfile,
    )
