// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lavacrafter.connect.R
import com.lavacrafter.connect.ui.design.LavaSpacing
import com.lavacrafter.connect.ui.design.component.LavaIconButton
import com.lavacrafter.connect.ui.design.component.LavaTooltip
import com.lavacrafter.connect.ui.design.component.lavaFocusIndicator

internal const val DEFAULT_GROUP_ID = "00000000-0000-0000-0000-000000000000"

internal fun platformIcon(platform: String): ImageVector =
    when (platform.lowercase()) {
        "android" -> Icons.Filled.PhoneAndroid
        "linux", "windows", "macos" -> Icons.Filled.Computer
        else -> Icons.Filled.Devices
    }

@Composable
internal fun deviceStateLabel(state: String): String =
    when (state) {
        "available" -> stringResource(R.string.device_state_available)
        "connecting" -> stringResource(R.string.device_state_connecting)
        "pairing" -> stringResource(R.string.device_state_pairing)
        "connected" -> stringResource(R.string.device_state_connected)
        "offline" -> stringResource(R.string.device_state_offline)
        else -> state
    }

/** Small tonal status label; [active] uses the accent so connected/running states stand out. */
@Composable
internal fun StatusChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val container = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier =
            modifier
                .background(container, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = LavaSpacing.small, vertical = 2.dp),
    )
}

/** Fingerprint/UUID text with a copy button; identifiers are shown monospaced for comparison. */
@Composable
internal fun CopyableId(
    label: String,
    value: String,
    copyLabel: String,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
        LavaTooltip(text = copyLabel) {
            LavaIconButton(onClick = { onCopy(value) }, contentDescription = copyLabel) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Settings-style group header and divider, matching lava_note's SettingsGroup. */
@Composable
internal fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(vertical = LavaSpacing.small)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = LavaSpacing.large, vertical = LavaSpacing.small),
        )
        content()
        HorizontalDivider(
            modifier = Modifier.padding(top = LavaSpacing.small),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
internal fun SettingsSwitchItem(
    title: String,
    description: String?,
    icon: ImageVector?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier =
            Modifier
                .lavaFocusIndicator(enabled = enabled)
                .semantics { contentDescription = title }
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

@Composable
internal fun SettingsClickableItem(
    title: String,
    description: String?,
    icon: ImageVector?,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier =
            if (onClick != null) {
                Modifier
                    .lavaFocusIndicator(enabled = enabled)
                    .semantics {
                        contentDescription = title
                        role = Role.Button
                    }.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
    )
}

/** Title row used at the top of panes. */
@Composable
internal fun PaneTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = LavaSpacing.large, vertical = LavaSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LavaSpacing.small),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** The core names the built-in group "Default"; show it in the user's language. */
@Composable
internal fun groupDisplayName(
    id: String,
    name: String,
): String = if (id == DEFAULT_GROUP_ID) stringResource(R.string.groups_default) else name
