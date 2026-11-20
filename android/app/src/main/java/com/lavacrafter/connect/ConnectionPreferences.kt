// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Connection settings read by [ConnectService] when it starts the native core. */
object ConnectionPreferences {
    /** Same as `lava_core::DEFAULT_PORT`. */
    const val DEFAULT_PORT = 45893
    private const val FILE = "connection"
    private const val KEY_PORT = "listen_port"
    private var flow: MutableStateFlow<Int>? = null

    /** 0 picks a random port; ports below 1024 cannot be bound by apps. */
    fun validPort(port: Int): Boolean = port == 0 || port in 1024..65535

    fun port(context: Context): StateFlow<Int> =
        synchronized(this) {
            flow ?: MutableStateFlow(load(context)).also { flow = it }
        }.asStateFlow()

    fun setPort(
        context: Context,
        port: Int,
    ) {
        require(validPort(port)) { "Port must be 0 or 1024–65535" }
        port(context)
        flow!!.value = port
        context
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PORT, port)
            .apply()
    }

    private fun load(context: Context): Int =
        context
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_PORT, DEFAULT_PORT)
            .takeIf(::validPort) ?: DEFAULT_PORT
}
