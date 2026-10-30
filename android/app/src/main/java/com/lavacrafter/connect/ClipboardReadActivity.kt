// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager

/**
 * Invisible activity started from the log watcher. Android 10+ only lets the focused app read the
 * clipboard, so it reads once on gaining focus, hands the clip to the service and finishes.
 */
class ClipboardReadActivity : Activity() {
    private var service: ConnectService? = null
    private var bound = false
    private var read = false
    private var clip: ClipData? = null
    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                service = (binder as ConnectService.LocalBinder).service()
                flush()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this), ViewGroup.LayoutParams(1, 1))
        window.setDimAmount(0f)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        // Bind early so the service is usually connected by the time focus arrives. Flag 0 never
        // starts the connection service; without it there is nothing to send to.
        bound = bindService(Intent(this, ConnectService::class.java), connection, 0)
        if (!bound) finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || read) return
        read = true
        clip = runCatching { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip }.getOrNull()
        if (clip == null) {
            finish()
            return
        }
        flush()
        // Never linger over other apps waiting for an unavailable service.
        window.decorView.postDelayed({ finish() }, 1_000)
    }

    private fun flush() {
        val target = service ?: return
        val value = clip ?: return
        clip = null
        // Clipboard URI grants last until the clip changes, so image reads may finish after us.
        target.sendBackgroundClip(value)
        finish()
    }

    override fun onDestroy() {
        if (bound) unbindService(connection)
        bound = false
        super.onDestroy()
    }

    companion object {
        fun intent(context: android.content.Context) =
            Intent(context, ClipboardReadActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
