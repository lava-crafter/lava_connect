// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.lavacrafter.connect.R

data class LavaActionConfirmation(
    val title: String,
    val message: String,
    val confirmLabel: String,
)

/**
 * Action model shared by visible overflow and long-click context entry points.
 *
 * Destructive styling is independent from the optional confirmation policy.
 */
data class LavaContextAction(
    val id: String,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val contentDescription: String = label,
    val destructive: Boolean = false,
    val confirmation: LavaActionConfirmation? = null,
    val leadingIcon: (@Composable () -> Unit)? = null,
    val modifier: Modifier = Modifier,
    val selectable: Boolean = false,
    val selected: Boolean = false,
    val children: List<LavaContextAction> = emptyList(),
)

/** A labeled group of related actions in an overflow menu. */
data class LavaMenuSection(
    val label: String,
    val actions: List<LavaContextAction>,
)

/** Shared menu facade for context and overflow actions. */
val LavaMenuItemIdKey = SemanticsPropertyKey<String>("LavaMenuItemId")
val LavaSubmenuIdKey = SemanticsPropertyKey<String>("LavaSubmenuId")

@Composable
fun LavaMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<LavaContextAction> = emptyList(),
    sections: List<LavaMenuSection> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var pendingConfirmation by remember { mutableStateOf<LavaContextAction?>(null) }
    var openSubmenuId by remember { mutableStateOf<String?>(null) }
    val resolvedSections =
        if (sections.isNotEmpty()) {
            sections.filter { it.actions.isNotEmpty() }
        } else {
            listOf(LavaMenuSection(label = "", actions = actions))
        }

    LaunchedEffect(expanded) {
        if (!expanded) openSubmenuId = null
    }

    fun dismissHierarchy() {
        openSubmenuId = null
        onDismissRequest()
    }

    fun selectAction(action: LavaContextAction) {
        dismissHierarchy()
        if (action.confirmation != null) {
            pendingConfirmation = action
        } else {
            action.onClick()
        }
    }

    BackHandler(enabled = expanded && openSubmenuId != null) {
        openSubmenuId = null
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = ::dismissHierarchy,
        modifier = modifier,
        properties = MenuDefaults.DefaultMenuProperties,
    ) {
        if (resolvedSections.size == 1 && resolvedSections.single().label.isBlank()) {
            val section = resolvedSections.single()
            section.actions.forEachIndexed { actionIndex, action ->
                LavaMenuActionItem(
                    action = action,
                    itemIndex = actionIndex,
                    itemCount = section.actions.size,
                    expanded = openSubmenuId == action.id,
                    onOpenSubmenu = { openSubmenuId = action.id },
                    onSelectAction = ::selectAction,
                    onDismissHierarchy = ::dismissHierarchy,
                )
            }
        } else {
            resolvedSections.forEachIndexed { index, section ->
                DropdownMenuGroup(
                    shapes = MenuDefaults.groupShape(index, resolvedSections.size),
                ) {
                    if (section.label.isNotBlank()) {
                        MenuDefaults.DropdownMenuGroupLabel {
                            Text(
                                text = section.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { contentDescription = section.label },
                            )
                        }
                    }
                    section.actions.forEachIndexed { actionIndex, action ->
                        LavaMenuActionItem(
                            action = action,
                            itemIndex = actionIndex,
                            itemCount = section.actions.size,
                            expanded = openSubmenuId == action.id,
                            onOpenSubmenu = { openSubmenuId = action.id },
                            onSelectAction = ::selectAction,
                            onDismissHierarchy = ::dismissHierarchy,
                        )
                    }
                }
            }
        }
    }

    pendingConfirmation?.let { action ->
        action.confirmation?.let { confirmation ->
            LavaDialog(
                title = confirmation.title,
                text = confirmation.message,
                confirmLabel = confirmation.confirmLabel,
                dismissLabel = stringResource(R.string.common_cancel),
                destructive = action.destructive,
                onDismissRequest = { pendingConfirmation = null },
                onConfirm = {
                    pendingConfirmation?.let { pendingAction ->
                        pendingConfirmation = null
                        pendingAction.onClick()
                    }
                },
            )
        }
    }
}

@Composable
private fun LavaMenuActionItem(
    action: LavaContextAction,
    itemIndex: Int,
    itemCount: Int,
    expanded: Boolean,
    onOpenSubmenu: () -> Unit,
    onSelectAction: (LavaContextAction) -> Unit,
    onDismissHierarchy: () -> Unit,
) {
    val submenuStateDescription =
        if (expanded) {
            stringResource(R.string.lava_menu_submenu_expanded)
        } else {
            stringResource(R.string.lava_menu_submenu_collapsed)
        }

    Box {
        if (action.selectable && action.children.isEmpty()) {
            SelectableLavaMenuItem(
                action = action,
                itemIndex = itemIndex,
                itemCount = itemCount,
                onSelectAction = onSelectAction,
            )
        } else {
            DropdownMenuItem(
                onClick = {
                    if (action.children.isNotEmpty()) onOpenSubmenu() else onSelectAction(action)
                },
                text = {
                    Text(
                        text = action.label,
                        color =
                            if (action.destructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color.Unspecified
                            },
                    )
                },
                shape = MenuDefaults.itemShape(itemIndex, itemCount).shape,
                modifier =
                    Modifier
                        .testTag("lava-menu-item:${action.id}")
                        .then(action.modifier)
                        .semantics(mergeDescendants = true) {
                            this[LavaMenuItemIdKey] = "lava-menu-item:${action.id}"
                            contentDescription = action.contentDescription
                            role = Role.Button
                            if (action.children.isNotEmpty()) {
                                this[LavaSubmenuIdKey] = "lava-submenu:${action.id}"
                                stateDescription = submenuStateDescription
                            }
                        },
                leadingIcon = action.leadingIcon,
                trailingContent =
                    if (action.children.isNotEmpty()) {
                        {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        }
                    } else {
                        null
                    },
                enabled = action.enabled,
            )
        }

        if (expanded) {
            DropdownMenuPopup(
                expanded = true,
                onDismissRequest = onDismissHierarchy,
                popupPositionProvider =
                    MenuDefaults.rememberDropdownMenuPopupPositionProvider(
                        MenuAnchorPosition.Right,
                    ),
                properties = MenuDefaults.DefaultMenuProperties,
            ) {
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                    action.children.forEachIndexed { childIndex, child ->
                        LavaMenuChildItem(
                            action = child,
                            itemIndex = childIndex,
                            itemCount = action.children.size,
                            onSelectAction = onSelectAction,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectableLavaMenuItem(
    action: LavaContextAction,
    itemIndex: Int,
    itemCount: Int,
    onSelectAction: (LavaContextAction) -> Unit,
) {
    DropdownMenuItem(
        selected = action.selected,
        onClick = { onSelectAction(action) },
        text = {
            Text(
                text = action.label,
                color =
                    if (action.destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    },
            )
        },
        shapes = MenuDefaults.itemShape(itemIndex, itemCount),
        modifier =
            Modifier
                .testTag("lava-menu-item:${action.id}")
                .then(action.modifier)
                .semantics(mergeDescendants = true) {
                    this[LavaMenuItemIdKey] = "lava-menu-item:${action.id}"
                    contentDescription = action.contentDescription
                    role = Role.RadioButton
                    selected = action.selected
                },
        leadingIcon = action.leadingIcon,
        enabled = action.enabled,
    )
}

@Composable
private fun LavaMenuChildItem(
    action: LavaContextAction,
    itemIndex: Int,
    itemCount: Int,
    onSelectAction: (LavaContextAction) -> Unit,
) {
    DropdownMenuItem(
        onClick = { onSelectAction(action) },
        text = {
            Text(
                text = action.label,
                color =
                    if (action.destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    },
            )
        },
        shape = MenuDefaults.itemShape(itemIndex, itemCount).shape,
        modifier =
            Modifier
                .testTag("lava-menu-item:${action.id}")
                .then(action.modifier)
                .semantics(mergeDescendants = true) {
                    this[LavaMenuItemIdKey] = "lava-menu-item:${action.id}"
                    contentDescription = action.contentDescription
                    role = Role.Button
                },
        leadingIcon = action.leadingIcon,
        enabled = action.enabled,
    )
}
