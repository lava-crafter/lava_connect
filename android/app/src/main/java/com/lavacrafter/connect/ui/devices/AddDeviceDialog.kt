// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lavacrafter.connect.ConnectionPreferences
import com.lavacrafter.connect.R
import com.lavacrafter.connect.ui.design.LavaSpacing
import com.lavacrafter.connect.ui.design.component.LavaDialog

/**
 * Combines the address and port fields. An address that already carries a port (`1.2.3.4:5`,
 * `[2001:db8::1]:5`) wins; bare IPv6 gets the brackets a port requires. The core parses and
 * validates the result. Same rule as `with_port` in linux/app/src/main.rs.
 */
internal fun withPort(
    host: String,
    port: String,
): String {
    if (port.isEmpty()) return host
    if (host.startsWith('[')) return if (host.endsWith(']')) "$host:$port" else host
    return when (host.count { it == ':' }) {
        0 -> "$host:$port"
        1 -> host
        else -> "[$host]:$port"
    }
}

/**
 * Connects to a device by address when discovery does not find it. The core learns the peer's
 * identity from TLS and starts pairing, so the usual code comparison still protects the user.
 *
 * @param localAddresses this device's `ip:port` addresses, shown for entering on the other device.
 */
@Composable
internal fun AddDeviceDialog(
    localAddresses: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    val ip = rememberTextFieldState()
    val port = rememberTextFieldState(ConnectionPreferences.DEFAULT_PORT.toString())
    val ipText = ip.text.toString().trim()
    val portText = port.text.toString().trim()
    val portValid = portText.isEmpty() || portText.toIntOrNull()?.let { it in 1..65535 } == true
    LavaDialog(
        title = stringResource(R.string.add_device_title),
        confirmLabel = stringResource(R.string.add_device_connect),
        confirmEnabled = ipText.isNotEmpty() && portValid,
        onDismissRequest = onDismiss,
        onConfirm = { onAdd(withPort(ipText, portText)) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(LavaSpacing.medium)) {
            Text(stringResource(R.string.add_device_message), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(LavaSpacing.small)) {
                OutlinedTextField(
                    state = ip,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.add_device_ip)) },
                    placeholder = { Text(stringResource(R.string.add_device_ip_hint)) },
                    supportingText = { Text(stringResource(R.string.add_device_ip_support)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    state = port,
                    modifier = Modifier.width(104.dp),
                    label = { Text(stringResource(R.string.add_device_port)) },
                    isError = !portValid,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
            }
            Text(
                localAddresses.firstOrNull()?.let { stringResource(R.string.add_device_local, it) }
                    ?: stringResource(R.string.add_device_no_address),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
