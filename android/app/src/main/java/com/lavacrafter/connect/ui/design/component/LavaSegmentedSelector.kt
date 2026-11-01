// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lavacrafter.connect.ui.design.LavaSpacing

data class LavaSegment<T>(
    val value: T,
    val label: String,
    val contentDescription: String = label,
    val enabled: Boolean = true,
    val icon: ImageVector? = null,
    val labelStyle: TextStyle? = null,
    val maxLines: Int = Int.MAX_VALUE,
    val overflow: TextOverflow = TextOverflow.Clip,
)

/** Text-based single-choice selector with selected semantics and profile-driven shape tokens. */
@Composable
fun <T> LavaSegmentedSelector(
    options: List<LavaSegment<T>>,
    selected: T,
    onSelectionChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    optionModifier: (T) -> Modifier = { Modifier },
) {
    Row(
        modifier =
            modifier
                .selectableGroup()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        options.forEach { option ->
            LavaInteractiveSurface(
                modifier =
                    optionModifier(option.value)
                        .weight(1f)
                        .semantics {
                            contentDescription = option.contentDescription
                        },
                enabled = option.enabled,
                selected = option.value == selected,
                selectionSemantics = true,
                onClick = { onSelectionChange(option.value) },
                shape = MaterialTheme.shapes.extraSmall,
                minHeight = 48.dp,
                contentPadding =
                    PaddingValues(
                        horizontal = LavaSpacing.medium,
                        vertical = LavaSpacing.small,
                    ),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    option.icon?.let { icon ->
                        androidx.compose.material3.Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(LavaSpacing.extraSmall))
                    }
                    Text(
                        text = option.label,
                        style = option.labelStyle ?: LocalTextStyle.current,
                        maxLines = option.maxLines,
                        overflow = option.overflow,
                    )
                }
            }
        }
    }
}
