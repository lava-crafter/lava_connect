// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.lavacrafter.connect.DeviceInfo
import com.lavacrafter.connect.R
import com.lavacrafter.connect.ui.common.CopyableId
import com.lavacrafter.connect.ui.common.SettingsGroup
import com.lavacrafter.connect.ui.common.SettingsSwitchItem
import com.lavacrafter.connect.ui.common.StatusChip
import com.lavacrafter.connect.ui.common.deviceStateLabel
import com.lavacrafter.connect.ui.common.platformIcon
import com.lavacrafter.connect.ui.design.LavaSpacing
import com.lavacrafter.connect.ui.design.component.LavaCard
import com.lavacrafter.connect.ui.design.component.LavaDialog
import com.lavacrafter.connect.ui.design.component.LavaIconButton
import com.lavacrafter.connect.ui.design.component.LavaTooltip

@Composable
internal fun DeviceDetail(
    device: DeviceInfo,
    activeGroupName: String?,
    showBack: Boolean,
    onBack: () -> Unit,
    onPair: () -> Unit,
    onComparePairing: () -> Unit,
    onUnpair: () -> Unit,
    onCapability: (Int, Boolean) -> Unit,
    onGroupMember: (Boolean) -> Unit,
    onCopy: (String) -> Unit,
) {
    var confirmUnpair by rememberSaveable(device.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = LavaSpacing.small, vertical = LavaSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                val back = stringResource(R.string.device_back)
                LavaTooltip(text = back) {
                    LavaIconButton(onClick = onBack, contentDescription = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            } else {
                Spacer(Modifier.size(LavaSpacing.small))
            }
            Icon(platformIcon(device.platform), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(LavaSpacing.medium))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(LavaSpacing.extraSmall)) {
                    StatusChip(deviceStateLabel(device.state), active = device.state == "connected")
                    StatusChip(device.platform, active = false)
                }
            }
        }
        Column(Modifier.widthIn(max = 760.dp).padding(horizontal = LavaSpacing.large)) {
            CopyableId(
                label = stringResource(R.string.device_fingerprint),
                value = device.id,
                copyLabel = stringResource(R.string.device_copy_id),
                onCopy = onCopy,
            )
            Spacer(Modifier.size(LavaSpacing.large))
            when {
                device.state == "pairing" && device.pairingCode != null -> {
                    LavaCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pairing_code), style = MaterialTheme.typography.labelLarge)
                        Text(
                            device.pairingCode,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(stringResource(R.string.pairing_message), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.size(LavaSpacing.medium))
                        Button(onClick = onComparePairing, shape = MaterialTheme.shapes.small) {
                            Text(stringResource(R.string.device_confirm_pairing))
                        }
                    }
                }

                !device.trusted -> {
                    LavaCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.device_not_trusted), style = MaterialTheme.typography.bodyMedium)
                        if (device.state == "available") {
                            Spacer(Modifier.size(LavaSpacing.medium))
                            Button(onClick = onPair, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.device_pair)) }
                        }
                    }
                }
            }
        }
        if (device.trusted) {
            SettingsGroup(activeGroupName ?: stringResource(R.string.groups_default)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.device_sync_in_group),
                    description =
                        stringResource(if (device.groupCompatible) R.string.device_group_ready else R.string.device_group_not_ready),
                    icon = Icons.Filled.Workspaces,
                    checked = device.syncInGroup,
                    onCheckedChange = onGroupMember,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.device_text),
                    description = stringResource(R.string.device_text_description),
                    icon = Icons.Filled.TextFields,
                    checked = device.text,
                    onCheckedChange = { onCapability(1, it) },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.device_image),
                    description = stringResource(R.string.device_image_description),
                    icon = Icons.Filled.Image,
                    checked = device.image,
                    onCheckedChange = { onCapability(2, it) },
                )
            }
            OutlinedButton(
                onClick = { confirmUnpair = true },
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.padding(LavaSpacing.large),
            ) {
                Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(LavaSpacing.small))
                Text(stringResource(R.string.device_unpair))
            }
        }
    }
    if (confirmUnpair) {
        LavaDialog(
            title = stringResource(R.string.device_unpair_title, device.name),
            text = stringResource(R.string.device_unpair_message),
            confirmLabel = stringResource(R.string.device_unpair),
            destructive = true,
            onDismissRequest = { confirmUnpair = false },
            onConfirm = {
                confirmUnpair = false
                onUnpair()
            },
        )
    }
}
