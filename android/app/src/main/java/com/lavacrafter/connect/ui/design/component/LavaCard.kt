// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.lavacrafter.connect.ui.design.LavaSpacing

/** Card surface with the same interaction, selected-state, and shape tokens as other primitives. */
@Composable
fun LavaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit,
) {
    LavaInteractiveSurface(
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        selectionSemantics = selected,
        contentDescription = contentDescription,
        onClick = onClick,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(LavaSpacing.large),
    ) {
        Column(content = content)
    }
}
