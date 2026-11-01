// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.ui.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.lavacrafter.connect.DeviceInfo
import com.lavacrafter.connect.R
import com.lavacrafter.connect.ui.design.LavaSpacing
import com.lavacrafter.connect.ui.design.component.LavaDialog

/** Final check before trusting a device: both sides must show the same code and fingerprint. */
@Composable
internal fun PairingDialog(
    device: DeviceInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    LavaDialog(
        title = stringResource(R.string.pairing_title),
        onDismissRequest = onDismiss,
        onConfirm = onConfirm,
    ) {
        Column {
            Text(stringResource(R.string.pairing_message), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(LavaSpacing.large))
            Text(stringResource(R.string.pairing_code), style = MaterialTheme.typography.labelLarge)
            Text(
                device.pairingCode.orEmpty(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(LavaSpacing.medium))
            Text(device.name, style = MaterialTheme.typography.titleSmall)
            Text(device.id, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

@Composable
internal fun SendTextDialog(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    val text = rememberTextFieldState()
    LavaDialog(
        title = stringResource(R.string.send_text),
        confirmLabel = stringResource(R.string.send_action),
        confirmEnabled = text.text.isNotEmpty(),
        onDismissRequest = onDismiss,
        onConfirm = { onSend(text.text.toString()) },
    ) {
        OutlinedTextField(
            state = text,
            label = { Text(stringResource(R.string.send_text_label)) },
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 8),
        )
    }
}
