// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.ui.devices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lavacrafter.connect.ConnectState
import com.lavacrafter.connect.DeviceInfo
import com.lavacrafter.connect.R
import com.lavacrafter.connect.ui.adaptive.LavaWindowClass
import com.lavacrafter.connect.ui.adaptive.LavaWindowWidthClass
import com.lavacrafter.connect.ui.common.CopyableId
import com.lavacrafter.connect.ui.common.PaneTitle
import com.lavacrafter.connect.ui.common.StatusChip
import com.lavacrafter.connect.ui.common.deviceStateLabel
import com.lavacrafter.connect.ui.common.groupDisplayName
import com.lavacrafter.connect.ui.common.platformIcon
import com.lavacrafter.connect.ui.design.LavaSpacing
import com.lavacrafter.connect.ui.design.component.LavaActionConfirmation
import com.lavacrafter.connect.ui.design.component.LavaCard
import com.lavacrafter.connect.ui.design.component.LavaContextAction
import com.lavacrafter.connect.ui.design.component.LavaContextActions
import com.lavacrafter.connect.ui.design.component.LavaEmptyState

@Composable
internal fun DevicesScreen(
    state: ConnectState,
    windowClass: LavaWindowClass,
    selectedDeviceId: String?,
    onSelectDevice: (String?) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShareImage: () -> Unit,
    onPair: (String) -> Unit,
    onCancelPair: (String) -> Unit,
    onConfirmPair: (String, String) -> Unit,
    onUnpair: (String) -> Unit,
    onCapability: (String, Int, Boolean) -> Unit,
    onGroupMember: (String, Boolean) -> Unit,
    onCopy: (String) -> Unit,
) {
    var pairingId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.devices.firstOrNull { it.id == selectedDeviceId }
    val twoPane = windowClass.width == LavaWindowWidthClass.EXPANDED
    val detail: @Composable (DeviceInfo) -> Unit = { device ->
        DeviceDetail(
            device = device,
            activeGroupName = state.groups.firstOrNull { it.id == state.activeGroupId }?.let { groupDisplayName(it.id, it.name) },
            showBack = !twoPane,
            onBack = { onSelectDevice(null) },
            onPair = { onPair(device.id) },
            onComparePairing = { pairingId = device.id },
            onUnpair = { onUnpair(device.id) },
            onCapability = { capability, enabled -> onCapability(device.id, capability, enabled) },
            onGroupMember = { onGroupMember(device.id, it) },
            onCopy = onCopy,
        )
    }
    val list: @Composable (Modifier) -> Unit = { modifier ->
        DeviceList(
            state = state,
            selectedId = if (twoPane) selected?.id else null,
            modifier = modifier,
            onSelect = { onSelectDevice(it) },
            onStart = onStart,
            onStop = onStop,
            onShareImage = onShareImage,
            onPair = onPair,
            onComparePairing = { pairingId = it },
            onCancelPair = onCancelPair,
            onUnpair = onUnpair,
            onCopy = onCopy,
        )
    }

    if (twoPane) {
        Row(Modifier.fillMaxSize()) {
            list(Modifier.widthIn(max = 440.dp).weight(0.45f).fillMaxHeight())
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Box(Modifier.weight(0.55f).fillMaxHeight()) {
                if (selected != null) {
                    detail(selected)
                } else {
                    LavaEmptyState(
                        title = stringResource(R.string.devices_select_hint),
                        icon = Icons.Filled.WifiFind,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    } else if (selected != null) {
        BackHandler { onSelectDevice(null) }
        detail(selected)
    } else {
        list(Modifier.fillMaxSize())
    }

    state.devices.firstOrNull { it.id == pairingId && it.pairingCode != null }?.let { device ->
        PairingDialog(
            device = device,
            onDismiss = { pairingId = null },
            onConfirm = {
                onConfirmPair(device.id, device.pairingCode.orEmpty())
                pairingId = null
            },
        )
    }
}

@Composable
private fun DeviceList(
    state: ConnectState,
    selectedId: String?,
    modifier: Modifier,
    onSelect: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShareImage: () -> Unit,
    onPair: (String) -> Unit,
    onComparePairing: (String) -> Unit,
    onCancelPair: (String) -> Unit,
    onUnpair: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    Column(modifier) {
        PaneTitle(stringResource(R.string.nav_devices))
        DeviceListItems(
            state,
            selectedId,
            onSelect,
            onStart,
            onStop,
            onShareImage,
            onPair,
            onComparePairing,
            onCancelPair,
            onUnpair,
            onCopy,
        )
    }
}

@Composable
private fun DeviceListItems(
    state: ConnectState,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShareImage: () -> Unit,
    onPair: (String) -> Unit,
    onComparePairing: (String) -> Unit,
    onCancelPair: (String) -> Unit,
    onUnpair: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Bottom padding leaves room for the send FAB menu.
        contentPadding = PaddingValues(start = LavaSpacing.large, end = LavaSpacing.large, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(LavaSpacing.medium),
    ) {
        item { LocalDeviceCard(state, onStart, onStop, onCopy) }
        if (state.imageAvailable) {
            item { ReceivedImageCard(onShareImage) }
        }
        if (state.devices.isEmpty()) {
            item {
                LavaEmptyState(
                    title = stringResource(R.string.devices_empty_title),
                    description =
                        stringResource(if (state.running) R.string.devices_empty_running else R.string.devices_empty_stopped),
                    icon = Icons.Filled.WifiFind,
                    actionLabel = if (state.running) null else stringResource(R.string.local_start),
                    onAction = if (state.running) null else onStart,
                )
            }
        }
        items(state.devices, key = { it.id }) { device ->
            DeviceCard(
                device = device,
                selected = device.id == selectedId,
                onClick = { onSelect(device.id) },
                onPair = { onPair(device.id) },
                onComparePairing = { onComparePairing(device.id) },
                onCancelPair = { onCancelPair(device.id) },
                onUnpair = { onUnpair(device.id) },
                onCopyId = { onCopy(device.id) },
            )
        }
    }
}

@Composable
private fun LocalDeviceCard(
    state: ConnectState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopy: (String) -> Unit,
) {
    LavaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(platformIcon("android"), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(LavaSpacing.medium))
            Column(Modifier.weight(1f)) {
                Text(
                    state.localName.ifBlank { stringResource(R.string.local_device) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusChip(
                    stringResource(if (state.running) R.string.local_running else R.string.local_stopped),
                    active = state.running,
                )
            }
            if (state.running) {
                OutlinedButton(onClick = onStop, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.local_stop)) }
            } else {
                Button(onClick = onStart, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.local_start)) }
            }
        }
        if (state.localId.isNotBlank()) {
            Spacer(Modifier.height(LavaSpacing.medium))
            CopyableId(
                label = stringResource(R.string.device_fingerprint),
                value = state.localId,
                copyLabel = stringResource(R.string.device_copy_id),
                onCopy = onCopy,
            )
        }
        Spacer(Modifier.height(LavaSpacing.small))
        Text(
            stringResource(R.string.local_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReceivedImageCard(onShare: () -> Unit) {
    LavaCard(Modifier.fillMaxWidth(), onClick = onShare) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(LavaSpacing.medium))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.received_image_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.received_image_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onShare, shape = MaterialTheme.shapes.small) {
                Text(stringResource(R.string.received_image_share))
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceInfo,
    selected: Boolean,
    onClick: () -> Unit,
    onPair: () -> Unit,
    onComparePairing: () -> Unit,
    onCancelPair: () -> Unit,
    onUnpair: () -> Unit,
    onCopyId: () -> Unit,
) {
    val pairing = device.state == "pairing" && device.pairingCode != null
    val actions =
        buildList {
            if (pairing) add(LavaContextAction("cancel", stringResource(R.string.common_cancel), onCancelPair))
            add(
                LavaContextAction(
                    "copy",
                    stringResource(R.string.device_copy_id),
                    onCopyId,
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                ),
            )
            if (device.trusted) {
                add(
                    LavaContextAction(
                        id = "unpair",
                        label = stringResource(R.string.device_unpair),
                        onClick = onUnpair,
                        destructive = true,
                        leadingIcon = { Icon(Icons.Filled.LinkOff, null) },
                        confirmation =
                            LavaActionConfirmation(
                                title = stringResource(R.string.device_unpair_title, device.name),
                                message = stringResource(R.string.device_unpair_message),
                                confirmLabel = stringResource(R.string.device_unpair),
                            ),
                    ),
                )
            }
        }
    LavaCard(Modifier.fillMaxWidth(), selected = selected, contentDescription = device.name) {
        LavaContextActions(actions = actions, onClick = onClick) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(platformIcon(device.platform), contentDescription = null)
                Spacer(Modifier.size(LavaSpacing.medium))
                Column(Modifier.weight(1f)) {
                    Text(
                        device.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(LavaSpacing.extraSmall)) {
                        StatusChip(deviceStateLabel(device.state), active = device.state == "connected")
                        if (device.trusted) StatusChip(stringResource(R.string.device_trusted), active = false)
                    }
                    if (pairing) {
                        Text(
                            device.pairingCode.orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                when {
                    pairing -> {
                        Button(onClick = onComparePairing, shape = MaterialTheme.shapes.small) {
                            Text(stringResource(R.string.device_confirm_pairing))
                        }
                    }

                    !device.trusted && device.state == "available" -> {
                        FilledTonalButton(onClick = onPair, shape = MaterialTheme.shapes.small) {
                            Text(stringResource(R.string.device_pair))
                        }
                    }
                }
            }
        }
    }
}
