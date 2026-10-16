// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ConnectScreen() } }
    }
}

@Composable private fun ConnectScreen(vm: ConnectViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val nativeGreeting by vm.nativeGreeting.collectAsState()
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var ask by remember { mutableStateOf<DeviceInfo?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) vm.sendImage(uri)
        }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (android.os.Build.VERSION.SDK_INT < 37 ||
                context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                vm.start()
            } else {
                vm.showError("Local network permission is required for discovery and connections")
            }
        }
    DisposableEffect(state.running, lifecycleOwner) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var active = false
        val listener =
            ClipboardManager.OnPrimaryClipChangedListener {
                if (active && state.running) vm.sendClipboard()
            }

        fun update() {
            active = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            clipboard.removePrimaryClipChangedListener(listener)
            if (active) clipboard.addPrimaryClipChangedListener(listener) else clipboard.removePrimaryClipChangedListener(listener)
            vm.foreground(
                if (active) {
                    {
                        vm.applyClipboard()
                        Unit
                    }
                } else {
                    null
                },
            )
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycleOwner.lifecycle.addObserver(observer)
        update()
        onDispose {
            vm.foreground(null)
            clipboard.removePrimaryClipChangedListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text("Lava Connect", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Android restricts background clipboard reading. Incoming text and images are written in the background; if writing fails, return here to retry.",
        )
        Spacer(Modifier.height(10.dp))
        Row {
            Button(onClick = {
                val needed =
                    buildList {
                        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                            context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            add("android.permission.POST_NOTIFICATIONS")
                        }
                        if (android.os.Build.VERSION.SDK_INT >= 37 &&
                            context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            add("android.permission.ACCESS_LOCAL_NETWORK")
                        }
                    }
                if (needed.isEmpty()) vm.start() else permissions.launch(needed.toTypedArray())
            }, enabled = !state.running) { Text("Start connection") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = vm::stop, enabled = state.running) { Text("Stop") }
        }
        Text(if (state.running) "Running · ${state.localId}" else "Stopped")
        state.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
        if (state.imageAvailable) {
            Button(onClick = {
                val uri = vm.receivedImage() ?: return@Button
                runCatching {
                    val share =
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newUri(context.contentResolver, "Lava Connect", uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(android.content.Intent.createChooser(share, "Share received image"))
                }.onFailure { vm.showError(it.message ?: "No image receiver") }
            }) { Text("Open received image") }
        }
        Row {
            Button(onClick = vm::sendClipboard, enabled = state.running) { Text("Send clipboard") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { picker.launch("image/*") }, enabled = state.running) { Text("Send image") }
        }
        OutlinedTextField(text, { text = it }, label = { Text("Text to send") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.send(text) }, enabled = state.running && text.isNotEmpty()) { Text("Send text") }
        Text("© 2026 Lava Connect contributors · GPL-3.0-or-later · No warranty", style = MaterialTheme.typography.labelSmall)
        if (nativeGreeting.isNotEmpty()) Text(nativeGreeting, style = MaterialTheme.typography.labelSmall)
        Row {
            TextButton(onClick = { vm.testRust() }) { Text("Test Rust") }
            TextButton(onClick = {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html"),
                    ),
                )
            }) { Text("License / redistribution terms") }
        }
        Text("Devices", style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.weight(1f)) {
            items(state.devices, key = { it.id }) { device ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text("${device.platform} · ${device.state}\nDevice ID / fingerprint: ${device.id}")
                        if (device.state == "pairing" && device.pairingCode != null) {
                            Text(
                                "Compare on BOTH devices:\n${device.id}\nSix-digit code: ${device.pairingCode}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Row {
                                Button(onClick = { ask = device }) { Text("Confirm") }
                                TextButton(onClick = { vm.command("cancel_pair", device.id) }) { Text("Cancel") }
                            }
                        } else if (!device.trusted &&
                            device.state == "available"
                        ) {
                            Button(onClick = { vm.command("pair", device.id) }) { Text("Pair") }
                        }
                        if (device.trusted) {
                            Row {
                                Text("Text clipboard")
                                Switch(device.text, { vm.command("set_capability", device.id, 1, it) })
                            }
                            Row {
                                Text("Image clipboard")
                                Switch(device.image, { vm.command("set_capability", device.id, 2, it) })
                            }
                            TextButton(onClick = { vm.command("unpair", device.id) }) { Text("Unpair") }
                        }
                    }
                }
            }
        }
    }
    ask?.let { d ->
        AlertDialog(onDismissRequest = {
            ask = null
        }, title = {
            Text("Confirm pairing")
        }, text = {
            Text("Compare the six-digit code on BOTH devices and verify the target device:\n" + "${d.id}\n${d.pairingCode}")
        }, confirmButton = {
            TextButton(onClick = {
                vm.confirmPair(d.id, d.pairingCode ?: "")
                ask =
                    null
            }) { Text("Confirm") }
        }, dismissButton = {
            TextButton(onClick = {
                vm.command("cancel_pair", d.id)
                ask = null
            }) { Text("Cancel") }
        })
    }
}
