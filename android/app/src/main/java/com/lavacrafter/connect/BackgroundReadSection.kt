// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import rikka.shizuku.Shizuku

private data class BackgroundReadPermissions(
    val shizuku: ShizukuGrant.Status,
    val logsGranted: Boolean,
    val logsReadable: Boolean,
    val overlay: Boolean,
) {
    val ready get() = logsReadable && overlay
}

@Composable
internal fun BackgroundReadSection(
    status: String?,
    onChanged: () -> Unit,
    onError: (String) -> Unit,
) {
    if (!BackgroundRead.supported()) return
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(false) }
    var granting by remember { mutableStateOf(false) }
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
    val summary =
        when {
            !permissions.ready -> "Not set up"
            !enabled -> "Off"
            else -> status ?: "On"
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Background reading: $summary", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
        TextButton(onClick = { dialog = true }) { Text("Set up") }
    }
    if (!dialog) return
    AlertDialog(
        onDismissRequest = { dialog = false },
        title = { Text("Background clipboard reading") },
        confirmButton = { TextButton(onClick = { dialog = false }) { Text("Done") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "When another app copies, Lava Connect briefly opens an invisible window to read and send the clipboard " +
                        "(the same approach as KDE Connect). Shizuku is only needed once to grant the permissions.",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Shizuku: " +
                        when (permissions.shizuku) {
                            ShizukuGrant.Status.UNAVAILABLE -> "not running"
                            ShizukuGrant.Status.NEEDS_PERMISSION -> "permission needed"
                            ShizukuGrant.Status.READY -> "ready"
                        },
                )
                Text(
                    "Log access: " +
                        when {
                            permissions.logsReadable -> "granted"
                            permissions.logsGranted -> "granted, restart required"
                            else -> "missing"
                        },
                )
                Text("Start from background (overlay): " + if (permissions.overlay) "granted" else "missing")
                if (!permissions.logsGranted || !permissions.overlay) {
                    Button(
                        onClick = {
                            granting = true
                            ShizukuGrant.grant(context) { error ->
                                granting = false
                                refresh++
                                error?.let(onError)
                            }
                        },
                        enabled = permissions.shizuku != ShizukuGrant.Status.UNAVAILABLE && !granting,
                    ) { Text(if (granting) "Granting…" else "Grant with Shizuku") }
                }
                if (permissions.logsGranted && !permissions.logsReadable && activity != null) {
                    Button(onClick = { BackgroundRead.restart(activity) }) { Text("Restart app to apply") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-send clipboard in background", Modifier.weight(1f))
                    Switch(
                        enabled && permissions.ready,
                        {
                            enabled = it
                            BackgroundRead.setEnabled(context, it)
                            onChanged()
                        },
                        enabled = permissions.ready,
                    )
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(8.dp))
                Text("Without Shizuku, run once from a computer:", style = MaterialTheme.typography.labelSmall)
                SelectionContainer {
                    Text(
                        "adb shell pm grant ${context.packageName} ${BackgroundRead.READ_LOGS}\n" +
                            "adb shell appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    "Log access lets an app read all system logs. Lava Connect only follows ClipboardService errors and never " +
                        "stores or sends log lines. Clips marked sensitive (passwords) are not sent automatically. " +
                        "Android 13+ may ask to allow log access when this screen opens.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
    )
}
