// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lavacrafter.connect.BackgroundRead
import com.lavacrafter.connect.R
import com.lavacrafter.connect.ShizukuGrant
import com.lavacrafter.connect.ui.common.SettingsClickableItem
import com.lavacrafter.connect.ui.common.SettingsGroup
import com.lavacrafter.connect.ui.common.SettingsSwitchItem
import com.lavacrafter.connect.ui.design.LavaSpacing
import rikka.shizuku.Shizuku

private data class BackgroundReadPermissions(
    val shizuku: ShizukuGrant.Status,
    val logsGranted: Boolean,
    val logsReadable: Boolean,
    val overlay: Boolean,
) {
    val ready get() = logsReadable && overlay
}

/** Settings group for Shizuku-assisted background clipboard reading (Android 10+). */
@Composable
internal fun BackgroundReadSettings(
    status: String?,
    onChanged: () -> Unit,
    onError: (String) -> Unit,
) {
    if (!BackgroundRead.supported()) return
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    var granting by remember { mutableStateOf(false) }
    var manualExpanded by rememberSaveable { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(BackgroundRead.enabled(context)) }
    DisposableEffect(lifecycleOwner) {
        // Permissions may change in Settings, adb or Shizuku while the app is paused.
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        val received = Shizuku.OnBinderReceivedListener { refresh++ }
        val dead = Shizuku.OnBinderDeadListener { refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
        }
    }
    val permissions =
        remember(refresh) {
            BackgroundReadPermissions(
                ShizukuGrant.status(),
                BackgroundRead.logsGranted(context),
                BackgroundRead.logsReadable(),
                BackgroundRead.overlayGranted(context),
            )
        }

    SettingsGroup(stringResource(R.string.background_title)) {
        Text(
            stringResource(R.string.background_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = LavaSpacing.large, vertical = LavaSpacing.small),
        )
        SettingsSwitchItem(
            title = stringResource(R.string.background_auto_send),
            description =
                if (permissions.ready) {
                    status ?: stringResource(R.string.background_auto_send_description)
                } else {
                    stringResource(R.string.background_not_set_up)
                },
            icon = Icons.Filled.ContentPasteGo,
            checked = enabled && permissions.ready,
            enabled = permissions.ready,
            onCheckedChange = {
                enabled = it
                BackgroundRead.setEnabled(context, it)
                onChanged()
            },
        )
        val needsGrant = !permissions.logsGranted || !permissions.overlay
        SettingsClickableItem(
            title = stringResource(R.string.background_shizuku),
            description =
                stringResource(
                    when (permissions.shizuku) {
                        ShizukuGrant.Status.UNAVAILABLE -> R.string.background_shizuku_unavailable
                        ShizukuGrant.Status.NEEDS_PERMISSION -> R.string.background_shizuku_needs_permission
                        ShizukuGrant.Status.READY -> R.string.background_shizuku_ready
                    },
                ),
            icon = Icons.Filled.AdminPanelSettings,
            onClick = null,
            trailing =
                if (needsGrant) {
                    {
                        FilledTonalButton(
                            onClick = {
                                granting = true
                                ShizukuGrant.grant(context) { error ->
                                    granting = false
                                    refresh++
                                    error?.let(onError)
                                }
                            },
                            enabled = permissions.shizuku != ShizukuGrant.Status.UNAVAILABLE && !granting,
                            shape = MaterialTheme.shapes.small,
                        ) { Text(stringResource(if (granting) R.string.background_granting else R.string.background_grant)) }
                    }
                } else {
                    null
                },
        )
        SettingsClickableItem(
            title = stringResource(R.string.background_logs),
            description =
                stringResource(
                    when {
                        permissions.logsReadable -> R.string.background_logs_granted
                        permissions.logsGranted -> R.string.background_logs_restart
                        else -> R.string.background_missing
                    },
                ),
            icon = Icons.Filled.Receipt,
            onClick = null,
            trailing =
                if (permissions.logsGranted && !permissions.logsReadable && activity != null) {
                    {
                        FilledTonalButton(onClick = { BackgroundRead.restart(activity) }, shape = MaterialTheme.shapes.small) {
                            Text(stringResource(R.string.background_restart))
                        }
                    }
                } else {
                    null
                },
        )
        SettingsClickableItem(
            title = stringResource(R.string.background_overlay),
            description = stringResource(if (permissions.overlay) R.string.background_logs_granted else R.string.background_missing),
            icon = Icons.Filled.Layers,
            onClick = null,
        )
        SettingsClickableItem(
            title = stringResource(R.string.background_manual),
            description = stringResource(R.string.background_manual_description),
            icon = Icons.Filled.Terminal,
            onClick = { manualExpanded = !manualExpanded },
            trailing = { Icon(if (manualExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null) },
        )
        AnimatedVisibility(manualExpanded) {
            Column(Modifier.padding(horizontal = LavaSpacing.large, vertical = LavaSpacing.small)) {
                SelectionContainer {
                    Text(
                        "adb shell pm grant ${context.packageName} ${BackgroundRead.READ_LOGS}\n" +
                            "adb shell appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
        Text(
            stringResource(R.string.background_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = LavaSpacing.large, vertical = LavaSpacing.small),
        )
    }
}
