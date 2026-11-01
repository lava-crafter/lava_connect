// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object LavaShapes {
    private val expressive =
        Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(32.dp),
        )

    private val square =
        Shapes(
            extraSmall = RoundedCornerShape(0.dp),
            small = RoundedCornerShape(0.dp),
            medium = RoundedCornerShape(0.dp),
            large = RoundedCornerShape(0.dp),
            extraLarge = RoundedCornerShape(0.dp),
        )

    fun forProfile(profile: UiShapeProfile): Shapes =
        when (profile) {
            UiShapeProfile.EXPRESSIVE -> expressive
            UiShapeProfile.SQUARE -> square
        }
}
