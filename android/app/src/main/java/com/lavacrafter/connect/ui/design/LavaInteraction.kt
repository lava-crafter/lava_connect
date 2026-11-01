// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design

data class LavaInteractionTokens(
    val selectedContainerAlpha: Float = 0.16f,
    val hoveredContainerAlpha: Float = 0.08f,
    val pressedContainerAlpha: Float = 0.12f,
    val focusedBorderAlpha: Float = 0.72f,
)

object LavaInteraction {
    val defaults = LavaInteractionTokens()
}
