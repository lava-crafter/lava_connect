// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.staticCompositionLocalOf

data class LavaMotionSpec(
    val microMillis: Int,
    val standardMillis: Int,
    val expressiveMillis: Int,
)

object LavaMotion {
    val standard =
        LavaMotionSpec(
            microMillis = 120,
            standardMillis = 200,
            expressiveMillis = 320,
        )

    /**
     * Centralizes Material motion policy so feature UI does not depend on
     * experimental Material Expressive APIs directly.
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun materialSchemeFor(profile: UiShapeProfile): MotionScheme =
        when (profile) {
            UiShapeProfile.EXPRESSIVE -> MotionScheme.expressive()
            UiShapeProfile.SQUARE -> MotionScheme.standard()
        }
}

/** Motion tokens consumed by the design-system components. */
val LocalLavaMotion = staticCompositionLocalOf { LavaMotion.standard }
