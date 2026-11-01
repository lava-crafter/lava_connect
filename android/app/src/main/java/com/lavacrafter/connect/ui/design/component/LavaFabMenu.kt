// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design.component

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/** An action rendered by the Material 3 Expressive FAB menu. */
data class LavaFabMenuAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val contentDescription: String = label,
    val modifier: Modifier = Modifier,
)

/**
 * Product facade for the Material 3 Expressive FAB menu.
 *
 * Feature screens should use this component instead of depending directly on the experimental
 * Material 3 Expressive primitives.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LavaFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<LavaFabMenuAction>,
    fabContentDescription: String,
    expandedStateDescription: String,
    collapsedStateDescription: String,
    modifier: Modifier = Modifier,
    fabTestTag: String? = null,
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                modifier =
                    Modifier
                        .then(fabTestTag?.let(Modifier::testTag) ?: Modifier)
                        .semantics {
                            contentDescription = fabContentDescription
                            stateDescription =
                                if (expanded) {
                                    expandedStateDescription
                                } else {
                                    collapsedStateDescription
                                }
                            role = Role.Button
                        },
            ) {
                Icon(
                    imageVector = if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = null,
                )
            }
        },
    ) {
        actions.forEach { action ->
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    action.onClick()
                },
                icon = { Icon(action.icon, contentDescription = null) },
                text = { Text(action.label) },
                modifier =
                    action.modifier.semantics {
                        contentDescription = action.contentDescription
                        role = Role.Button
                    },
            )
        }
    }
}
