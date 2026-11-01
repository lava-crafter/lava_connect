// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import android.content.*
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ConnectRepository(
    private val context: Context,
) {
    private var service: ConnectService? = null
    private var bound = false
    private var callback: (() -> Unit)? = null
    private val mutableState = MutableStateFlow(ConnectState())
    val state: StateFlow<ConnectState> = mutableState
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null
    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                service = (binder as ConnectService.LocalBinder).service()
                callback?.let { service?.setForeground(it) }
                stateJob?.cancel()
                stateJob = scope.launch { service!!.state.collect { mutableState.value = it } }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                stateJob?.cancel()
                service = null
                mutableState.value = ConnectState()
            }
        }

    fun start() {
        if (android.os.Build.VERSION.SDK_INT >=
            37
        ) {
            check(
                context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED,
            ) { "Local network permission is required" }
        }
        val intent = Intent(context, ConnectService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        if (!bound) bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun stop() {
        if (bound) {
            context.unbindService(connection)
            bound = false
        }
        stateJob?.cancel()
        service = null
        mutableState.value =
            ConnectState()
        context.stopService(Intent(context, ConnectService::class.java))
    }

    init {
        bound = context.bindService(Intent(context, ConnectService::class.java), connection, 0)
    }

    fun foreground(callback: (() -> Unit)?) {
        this.callback = callback
        service?.setForeground(callback)
    }

    fun sendClipboard() = requireService().sendCurrentClipboard()

    fun sendText(text: String) = requireService().sendText(text)

    fun sendPng(bytes: ByteArray) = requireService().sendPng(bytes)

    fun sendImage(uri: android.net.Uri) = requireService().sendImage(uri)

    fun command(json: org.json.JSONObject) = requireService().command(json)

    fun applyClipboard() = requireService().applyReceivedClipboard()

    fun receivedImage() = service?.receivedImage()

    fun updateBackgroundRead() = service?.updateBackgroundRead()

    fun greeting() =
        com.lavacrafter.connect.nativecore.NativeCore
            .greeting()

    private fun requireService() = service ?: error("Connection service is not running")

    fun close() {
        foreground(null)
        if (bound) {
            context.unbindService(connection)
            bound = false
        }
        scope.cancel()
        service = null
    }
}
