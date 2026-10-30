// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import java.io.File
import kotlin.system.exitProcess

/** Settings and permission state for KDE Connect-style background clipboard reading. */
internal object BackgroundRead {
    private const val PREFS = "settings"
    private const val KEY_ENABLED = "background_read"
    private const val AID_LOG = "1007"
    const val READ_LOGS = "android.permission.READ_LOGS"

    /** Below Android 10 the foreground listener restriction does not apply. */
    fun supported() = Build.VERSION.SDK_INT >= 29

    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun logsGranted(context: Context) = context.checkSelfPermission(READ_LOGS) == PackageManager.PERMISSION_GRANTED

    /** READ_LOGS maps to the `log` gid, which is only assigned when the process is forked. */
    fun logsReadable() =
        runCatching {
            File("/proc/self/status")
                .useLines { lines -> lines.firstOrNull { it.startsWith("Groups:") } }
                ?.substringAfter(':')
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.contains(AID_LOG) == true
        }.getOrDefault(false)

    fun overlayGranted(context: Context) = Settings.canDrawOverlays(context)

    fun ready(context: Context) = supported() && logsReadable() && overlayGranted(context)

    // Same filters as KDE Connect; Android 16 changed the accepted filterspec.
    fun logcatFilter() = if (Build.VERSION.SDK_INT > 35) "E ClipboardService" else "ClipboardService:E"

    fun restart(activity: Activity) {
        activity.stopService(Intent(activity, ConnectService::class.java))
        activity.startActivity(Intent.makeRestartActivityTask(ComponentName(activity, MainActivity::class.java)))
        exitProcess(0)
    }
}
