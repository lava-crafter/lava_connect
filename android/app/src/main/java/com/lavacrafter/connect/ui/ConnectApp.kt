// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lavacrafter.connect.ConnectViewModel
import com.lavacrafter.connect.R
import com.lavacrafter.connect.ui.adaptive.LavaNavigationLayout
import com.lavacrafter.connect.ui.adaptive.LavaWindowClass
import com.lavacrafter.connect.ui.adaptive.calculateLavaWindowClass
import com.lavacrafter.connect.ui.design.component.LavaFabMenu
import com.lavacrafter.connect.ui.design.component.LavaFabMenuAction
import com.lavacrafter.connect.ui.devices.DevicesScreen
import com.lavacrafter.connect.ui.devices.SendTextDialog
import com.lavacrafter.connect.ui.groups.GroupsScreen
import com.lavacrafter.connect.ui.settings.SettingsScreen

internal enum class Destination(
    val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    DEVICES(R.string.nav_devices, Icons.Outlined.Devices, Icons.Filled.Devices),
    GROUPS(R.string.nav_groups, Icons.Outlined.Workspaces, Icons.Filled.Workspaces),
    SETTINGS(R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** Copies text without it being sent as a new clipboard item by the foreground listener. */
internal class ClipboardCopier(
    private val context: Context,
    private val suppress: () -> Unit,
) {
    fun copy(text: String) {
        suppress()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Lava Connect", text))
    }
}

@Composable
internal fun ConnectApp(vm: ConnectViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val nativeGreeting by vm.nativeGreeting.collectAsState()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val appearance by remember { AppearancePreferences.state(context) }.collectAsState()
    val windowClass = activity?.let { calculateLavaWindowClass(it) } ?: LavaWindowClass.Compact
    var destination by rememberSaveable { mutableStateOf(Destination.DEVICES) }
    var selectedDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var sendTextOpen by rememberSaveable { mutableStateOf(false) }
    var suppressNextClip by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val copier = remember(context) { ClipboardCopier(context) { suppressNextClip = true } }
    val localNetworkError = stringResource(R.string.error_local_network)
    val noShareTarget = stringResource(R.string.error_no_share_target)
    val chooserTitle = stringResource(R.string.received_image_chooser)

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) vm.sendImage(uri)
        }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (Build.VERSION.SDK_INT < 37 ||
                context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") == PackageManager.PERMISSION_GRANTED
            ) {
                vm.start()
            } else {
                vm.showError(localNetworkError)
            }
        }
    val start: () -> Unit = {
        val needed =
            buildList {
                if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
                ) {
                    add("android.permission.POST_NOTIFICATIONS")
                }
                if (Build.VERSION.SDK_INT >= 37 &&
                    context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED
                ) {
                    add("android.permission.ACCESS_LOCAL_NETWORK")
                }
            }
        if (needed.isEmpty()) vm.start() else permissions.launch(needed.toTypedArray())
    }
    val shareImage = {
        vm.receivedImage()?.let { uri ->
            runCatching {
                val share =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(context.contentResolver, "Lava Connect", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                context.startActivity(Intent.createChooser(share, chooserTitle))
            }.onFailure { vm.showError(it.message ?: noShareTarget) }
        }
        Unit
    }

    ForegroundClipboard(vm, state.running, consumeSuppressed = {
        val suppressed = suppressNextClip
        suppressNextClip = false
        suppressed
    })

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(error)
        vm.clearError()
    }

    BackHandler(enabled = destination != Destination.DEVICES) { destination = Destination.DEVICES }

    val navigation = windowClass.navigation
    val showFab = destination == Destination.DEVICES && state.running

    Row(Modifier.fillMaxSize()) {
        if (navigation != LavaNavigationLayout.MODAL) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Destination.entries.forEach { item ->
                    NavigationRailItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(if (destination == item) item.selectedIcon else item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                        alwaysShowLabel = true,
                    )
                }
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (navigation == LavaNavigationLayout.MODAL) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Destination.entries.forEach { item ->
                            NavigationBarItem(
                                selected = destination == item,
                                onClick = { destination = item },
                                icon = { Icon(if (destination == item) item.selectedIcon else item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.label)) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (showFab) {
                    LavaFabMenu(
                        expanded = fabExpanded,
                        onExpandedChange = { fabExpanded = it },
                        fabContentDescription = stringResource(R.string.send_menu),
                        expandedStateDescription = stringResource(R.string.common_expanded),
                        collapsedStateDescription = stringResource(R.string.common_collapsed),
                        actions =
                            listOf(
                                LavaFabMenuAction(
                                    "clipboard",
                                    stringResource(R.string.send_clipboard),
                                    Icons.Filled.ContentPaste,
                                    vm::sendClipboard,
                                ),
                                LavaFabMenuAction(
                                    "image",
                                    stringResource(R.string.send_image),
                                    Icons.Filled.Image,
                                    { picker.launch("image/*") },
                                ),
                                LavaFabMenuAction("text", stringResource(R.string.send_text), Icons.AutoMirrored.Filled.Send, {
                                    sendTextOpen =
                                        true
                                }),
                            ),
                    )
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        // Same rhythm as lava_note's AppNavHost: short slide forward, fade only back.
                        val forward = targetState.ordinal > initialState.ordinal
                        val enter =
                            if (forward) {
                                fadeIn(tween(320)) + slideInHorizontally(tween(320)) { it / 12 }
                            } else {
                                fadeIn(tween(200))
                            }
                        enter togetherWith fadeOut(tween(200))
                    },
                    label = "destination",
                ) { current ->
                    when (current) {
                        Destination.DEVICES -> {
                            DevicesScreen(
                                state = state,
                                windowClass = windowClass,
                                selectedDeviceId = selectedDeviceId,
                                onSelectDevice = { selectedDeviceId = it },
                                onStart = start,
                                onStop = vm::stop,
                                onShareImage = shareImage,
                                onPair = { vm.command("pair", it) },
                                onCancelPair = { vm.command("cancel_pair", it) },
                                onConfirmPair = vm::confirmPair,
                                onUnpair = { vm.command("unpair", it) },
                                onCapability = { id, capability, enabled -> vm.command("set_capability", id, capability, enabled) },
                                onGroupMember = { id, enabled -> vm.setGroupMember(state.activeGroupId, id, enabled) },
                                onCopy = copier::copy,
                            )
                        }

                        Destination.GROUPS -> {
                            GroupsScreen(
                                state = state,
                                windowClass = windowClass,
                                onSelect = vm::selectGroup,
                                onCreate = vm::createGroup,
                                onJoin = vm::joinGroup,
                                onDelete = vm::deleteGroup,
                                onMember = { id, enabled -> vm.setGroupMember(state.activeGroupId, id, enabled) },
                                onCopy = copier::copy,
                            )
                        }

                        Destination.SETTINGS -> {
                            SettingsScreen(
                                state = state,
                                appearance = appearance,
                                nativeGreeting = nativeGreeting,
                                onAppearance = { transform -> AppearancePreferences.update(context, transform) },
                                onStart = start,
                                onStop = vm::stop,
                                onBackgroundChanged = vm::updateBackgroundRead,
                                onError = vm::showError,
                                onTestNative = vm::testRust,
                            )
                        }
                    }
                }
            }
        }
    }

    if (sendTextOpen) {
        SendTextDialog(
            onDismiss = { sendTextOpen = false },
            onSend = {
                vm.send(it)
                sendTextOpen = false
            },
        )
    }
}

/**
 * While the app is resumed, local copies are sent and pending received clips are applied.
 * Moved unchanged from the previous single-screen MainActivity.
 */
@Composable
private fun ForegroundClipboard(
    vm: ConnectViewModel,
    running: Boolean,
    consumeSuppressed: () -> Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(running, lifecycleOwner) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var active = false
        val applyReceived: () -> Unit = { vm.applyClipboard() }
        val listener =
            ClipboardManager.OnPrimaryClipChangedListener {
                if (consumeSuppressed()) return@OnPrimaryClipChangedListener
                if (active && running) vm.sendClipboard()
            }

        fun update() {
            active = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            clipboard.removePrimaryClipChangedListener(listener)
            if (active) clipboard.addPrimaryClipChangedListener(listener)
            vm.foreground(if (active) applyReceived else null)
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
}
