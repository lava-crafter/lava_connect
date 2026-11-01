// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lavacrafter.connect.R

private enum class LavaContextMenuSource {
    OVERFLOW,
    LONG_PRESS,
}

/**
 * Hosts a visible overflow button and a long-click surface backed by one [actions] list.
 *
 * Callers provide the normal item action through [onClick]. The context menu itself is identical
 * regardless of whether it was opened from the overflow button or a long click.
 */
@Composable
fun LavaContextActions(
    actions: List<LavaContextAction>,
    modifier: Modifier = Modifier,
    overflowContentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    actionSlotWidth: Dp = 48.dp,
    overflowModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var menuSource by remember { mutableStateOf<LavaContextMenuSource?>(null) }
    val resolvedOverflowContentDescription =
        overflowContentDescription
            ?: stringResource(R.string.common_more_actions)
    val resolvedActionSlotWidth =
        if (actions.isNotEmpty()) {
            actionSlotWidth.coerceAtLeast(48.dp)
        } else {
            0.dp
        }
    Row(modifier) {
        val contextWidthModifier =
            if (actions.isNotEmpty()) {
                Modifier.weight(1f)
            } else {
                Modifier.fillMaxWidth()
            }
        val contextModifier =
            if (onClick != null) {
                contextWidthModifier
                    .fillMaxHeight()
                    .lavaFocusIndicator()
                    .combinedClickable(
                        onClick = { onClick() },
                        onLongClick = { menuSource = LavaContextMenuSource.LONG_PRESS },
                    )
            } else {
                contextWidthModifier
                    .fillMaxHeight()
                    .pointerInput(actions) {
                        detectTapGestures(onLongPress = { menuSource = LavaContextMenuSource.LONG_PRESS })
                    }
            }

        Box(modifier = contextModifier) {
            content()
            if (actions.isNotEmpty()) {
                LavaMenu(
                    expanded = menuSource == LavaContextMenuSource.LONG_PRESS,
                    onDismissRequest = { menuSource = null },
                    actions = actions,
                    modifier = Modifier.testTag("lava-context-menu-long-press"),
                )
            }
        }

        if (actions.isNotEmpty()) {
            Box(
                modifier =
                    Modifier
                        .width(resolvedActionSlotWidth)
                        .fillMaxHeight(),
            ) {
                LavaTooltip(
                    text = resolvedOverflowContentDescription,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    LavaIconButton(
                        onClick = { menuSource = LavaContextMenuSource.OVERFLOW },
                        contentDescription = resolvedOverflowContentDescription,
                        modifier = overflowModifier,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                        )
                    }
                }
                LavaMenu(
                    expanded = menuSource == LavaContextMenuSource.OVERFLOW,
                    onDismissRequest = { menuSource = null },
                    actions = actions,
                    modifier = Modifier.testTag("lava-context-menu-overflow"),
                )
            }
        }
    }
}
