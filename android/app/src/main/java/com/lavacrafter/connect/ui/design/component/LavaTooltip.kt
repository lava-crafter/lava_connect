// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/** A stable semantic key used by UI tests and accessibility tooling to identify tooltip anchors. */
val LavaTooltipTextKey = SemanticsPropertyKey<String>("LavaTooltipText")

var SemanticsPropertyReceiver.lavaTooltipText: String
    get() = error("LavaTooltipText is write-only")
    set(value) {
        this[LavaTooltipTextKey] = value
    }

/**
 * Tooltip facade for icon-first controls. Material3 tooltip APIs stay behind this design-system
 * boundary so feature screens do not depend on experimental APIs directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LavaTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Above,
            ),
        tooltip = {
            PlainTooltip(shape = androidx.compose.material3.MaterialTheme.shapes.small) {
                androidx.compose.material3.Text(text)
            }
        },
        state = rememberTooltipState(),
        modifier =
            modifier.semantics {
                lavaTooltipText = text
            },
        content = content,
    )
}
