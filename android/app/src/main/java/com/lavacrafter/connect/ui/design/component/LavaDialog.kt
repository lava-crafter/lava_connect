// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.design.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lavacrafter.connect.R

/** Shared confirmation/dialog surface with consistent shape and destructive action semantics. */
@Composable
fun LavaDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    confirmLabel: String? = null,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
    destructive: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val resolvedConfirmLabel = confirmLabel ?: stringResource(R.string.common_confirm)
    val resolvedDismissLabel = dismissLabel ?: stringResource(R.string.common_cancel)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text =
            if (text != null || content != null) {
                {
                    if (text != null) Text(text)
                    content?.invoke()
                }
            } else {
                null
            },
        dismissButton =
            resolvedDismissLabel.let { label ->
                {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(label)
                    }
                }
            },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                shape = MaterialTheme.shapes.small,
                colors =
                    if (destructive) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
            ) {
                Text(resolvedConfirmLabel)
            }
        },
    )
}
