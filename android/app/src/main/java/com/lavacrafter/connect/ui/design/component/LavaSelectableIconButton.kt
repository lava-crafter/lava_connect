// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lavacrafter.connect.ui.design.LavaSpacing

/** Icon button whose selected state is exposed through semantics and container styling. */
@Composable
fun LavaSelectableIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    containerColor: Color = Color.Transparent,
    contentColor: Color? = null,
    minimumSize: Dp = 48.dp,
    visualInset: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(LavaSpacing.small),
    content: @Composable BoxScope.() -> Unit,
) {
    LavaInteractiveSurface(
        modifier = modifier,
        enabled = enabled,
        selected = selected,
        selectionSemantics = true,
        contentDescription = contentDescription,
        onClick = onClick,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        minWidth = minimumSize,
        minHeight = minimumSize,
        visualInset = visualInset,
        contentPadding = contentPadding,
        content = content,
    )
}
