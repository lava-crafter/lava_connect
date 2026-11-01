// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lavacrafter.connect.ClipboardGroup
import com.lavacrafter.connect.ConnectState
import com.lavacrafter.connect.R
import com.lavacrafter.connect.ui.adaptive.LavaSettingsLayout
import com.lavacrafter.connect.ui.adaptive.LavaWindowClass
import com.lavacrafter.connect.ui.common.CopyableId
import com.lavacrafter.connect.ui.common.DEFAULT_GROUP_ID
import com.lavacrafter.connect.ui.common.PaneTitle
import com.lavacrafter.connect.ui.common.SettingsGroup
import com.lavacrafter.connect.ui.common.SettingsSwitchItem
import com.lavacrafter.connect.ui.common.StatusChip
import com.lavacrafter.connect.ui.common.groupDisplayName
import com.lavacrafter.connect.ui.common.platformIcon
import com.lavacrafter.connect.ui.design.LavaSpacing
import com.lavacrafter.connect.ui.design.component.LavaCard
import com.lavacrafter.connect.ui.design.component.LavaDialog
import com.lavacrafter.connect.ui.design.component.LavaEmptyState

private enum class GroupDialog { CREATE, JOIN, DELETE }

@Composable
internal fun GroupsScreen(
    state: ConnectState,
    windowClass: LavaWindowClass,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    onJoin: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMember: (String, Boolean) -> Unit,
    onCopy: (String) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<GroupDialog?>(null) }
    val active = state.groups.firstOrNull { it.id == state.activeGroupId }
    Column(Modifier.fillMaxSize()) {
        PaneTitle(stringResource(R.string.nav_groups)) {
            if (state.running) {
                OutlinedButton(onClick = { dialog = GroupDialog.JOIN }, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(LavaSpacing.small))
                    Text(stringResource(R.string.groups_join))
                }
                FilledTonalButton(onClick = { dialog = GroupDialog.CREATE }, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(LavaSpacing.small))
                    Text(stringResource(R.string.groups_create))
                }
            }
        }
        if (!state.running) {
            LavaEmptyState(title = stringResource(R.string.groups_stopped), icon = Icons.Filled.Workspaces)
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val twoPane = windowClass.settingsLayout(maxWidth) == LavaSettingsLayout.TWO_PANE
            if (twoPane) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(LavaSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(LavaSpacing.small),
                    ) { GroupList(state, onSelect) }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        active?.let { ActiveGroup(it, state, onMember, onCopy) { dialog = GroupDialog.DELETE } }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(
                        Modifier.padding(horizontal = LavaSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(LavaSpacing.small),
                    ) { GroupList(state, onSelect) }
                    active?.let { ActiveGroup(it, state, onMember, onCopy) { dialog = GroupDialog.DELETE } }
                }
            }
        }
    }

    when (dialog) {
        GroupDialog.CREATE -> {
            CreateGroupDialog(onDismiss = { dialog = null }) {
                onCreate(it)
                dialog = null
            }
        }

        GroupDialog.JOIN -> {
            JoinGroupDialog(onDismiss = { dialog = null }) { id, name ->
                onJoin(id, name)
                dialog = null
            }
        }

        GroupDialog.DELETE -> {
            active?.let { group ->
                LavaDialog(
                    title = stringResource(R.string.groups_delete_title, groupDisplayName(group.id, group.name)),
                    text = stringResource(R.string.groups_delete_message),
                    confirmLabel = stringResource(R.string.groups_delete),
                    destructive = true,
                    onDismissRequest = { dialog = null },
                    onConfirm = {
                        onDelete(group.id)
                        dialog = null
                    },
                )
            }
        }

        null -> {}
    }
}

@Composable
private fun ColumnScope.GroupList(
    state: ConnectState,
    onSelect: (String) -> Unit,
) {
    state.groups.forEach { group ->
        val selected = group.id == state.activeGroupId
        LavaCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { if (!selected) onSelect(group.id) },
            selected = selected,
            contentDescription = groupDisplayName(group.id, group.name),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        groupDisplayName(group.id, group.name),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${group.members.size} · ${stringResource(R.string.groups_members)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) StatusChip(stringResource(R.string.groups_active), active = true)
            }
        }
    }
}

@Composable
private fun ActiveGroup(
    group: ClipboardGroup,
    state: ConnectState,
    onMember: (String, Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val trusted = state.devices.filter { it.trusted }
    Column(Modifier.widthIn(max = 760.dp).padding(top = LavaSpacing.large)) {
        Column(Modifier.padding(horizontal = LavaSpacing.large)) {
            Text(groupDisplayName(group.id, group.name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(LavaSpacing.medium))
            CopyableId(
                label = stringResource(R.string.groups_code),
                value = group.id,
                copyLabel = stringResource(R.string.groups_copy_code),
                onCopy = onCopy,
            )
            Text(
                stringResource(R.string.groups_code_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsGroup(stringResource(R.string.groups_members)) {
            if (trusted.isEmpty()) {
                Text(
                    stringResource(R.string.groups_members_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = LavaSpacing.large, vertical = LavaSpacing.small),
                )
            }
            trusted.forEach { device ->
                SettingsSwitchItem(
                    title = device.name,
                    description =
                        stringResource(if (device.groupCompatible) R.string.device_group_ready else R.string.device_group_not_ready),
                    icon = platformIcon(device.platform),
                    checked = device.syncInGroup,
                    onCheckedChange = { onMember(device.id, it) },
                )
            }
        }
        if (group.id != DEFAULT_GROUP_ID) {
            OutlinedButton(
                onClick = onDelete,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.padding(LavaSpacing.large),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(LavaSpacing.small))
                Text(stringResource(R.string.groups_delete))
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val name = rememberTextFieldState()
    LavaDialog(
        title = stringResource(R.string.groups_create),
        confirmLabel = stringResource(R.string.groups_create),
        confirmEnabled = name.text.isNotBlank(),
        onDismissRequest = onDismiss,
        onConfirm = { onCreate(name.text.trim().toString()) },
    ) {
        OutlinedTextField(
            state = name,
            label = { Text(stringResource(R.string.groups_name)) },
            lineLimits = TextFieldLineLimits.SingleLine,
        )
    }
}

@Composable
private fun JoinGroupDialog(
    onDismiss: () -> Unit,
    onJoin: (String, String) -> Unit,
) {
    val code = rememberTextFieldState()
    val name = rememberTextFieldState()
    LavaDialog(
        title = stringResource(R.string.groups_join),
        confirmLabel = stringResource(R.string.groups_join),
        confirmEnabled = code.text.isNotBlank() && name.text.isNotBlank(),
        onDismissRequest = onDismiss,
        onConfirm = { onJoin(code.text.trim().toString(), name.text.trim().toString()) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(LavaSpacing.small)) {
            OutlinedTextField(
                state = code,
                label = { Text(stringResource(R.string.groups_code)) },
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            OutlinedTextField(
                state = name,
                label = { Text(stringResource(R.string.groups_join_name)) },
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            Text(stringResource(R.string.groups_code_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}
