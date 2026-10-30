// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** App-side helper: Shizuku is only used once to grant the permissions background reading needs. */
internal object ShizukuGrant {
    private const val REQUEST_CODE = 0x1c0

    enum class Status { UNAVAILABLE, NEEDS_PERMISSION, READY }

    fun status(): Status =
        try {
            when {
                !Shizuku.pingBinder() || Shizuku.isPreV11() -> Status.UNAVAILABLE
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Status.NEEDS_PERMISSION
                else -> Status.READY
            }
        } catch (_: Exception) {
            Status.UNAVAILABLE
        }

    /** Calls [done] on the main thread with null on success or an error message. */
    fun grant(
        context: Context,
        done: (String?) -> Unit,
    ) {
        val app = context.applicationContext
        when (status()) {
            Status.UNAVAILABLE -> {
                done("Shizuku is not running")
            }

            Status.READY -> {
                bind(app, done)
            }

            Status.NEEDS_PERMISSION -> {
                val listener =
                    object : Shizuku.OnRequestPermissionResultListener {
                        override fun onRequestPermissionResult(
                            requestCode: Int,
                            grantResult: Int,
                        ) {
                            if (requestCode != REQUEST_CODE) return
                            Shizuku.removeRequestPermissionResultListener(this)
                            if (grantResult == PackageManager.PERMISSION_GRANTED) bind(app, done) else done("Shizuku permission denied")
                        }
                    }
                Shizuku.addRequestPermissionResultListener(listener)
                Shizuku.requestPermission(REQUEST_CODE)
            }
        }
    }

    private fun bind(
        context: Context,
        done: (String?) -> Unit,
    ) {
        val main = Handler(Looper.getMainLooper())
        val args =
            Shizuku
                .UserServiceArgs(ComponentName(context.packageName, ShizukuGrantService::class.java.name))
                .daemon(false)
                .processNameSuffix("grant")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)
        val connection =
            object : ServiceConnection {
                private var handled = false

                override fun onServiceConnected(
                    name: ComponentName?,
                    binder: IBinder?,
                ) {
                    if (handled) return
                    handled = true
                    val connection = this
                    val service = IShizukuGrantService.Stub.asInterface(binder)
                    thread(name = "shizuku-grant") {
                        val error =
                            runCatching { service.grant(context.packageName) }
                                .getOrElse { it.message ?: "Shizuku grant failed" }
                        main.post {
                            runCatching { Shizuku.unbindUserService(args, connection, true) }
                            done(error)
                        }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }
        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            done(e.message ?: "Unable to start Shizuku service")
        }
    }
}

/** Runs inside Shizuku's shell (or root) process, never in the app process. */
class ShizukuGrantService : IShizukuGrantService.Stub() {
    private companion object {
        val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    }

    override fun grant(packageName: String): String? {
        require(PACKAGE.matches(packageName)) { "Invalid package name" }
        // Fixed argv without a shell; the package name is the only variable part.
        return listOf(
            listOf("pm", "grant", packageName, BackgroundRead.READ_LOGS),
            listOf("appops", "set", packageName, "SYSTEM_ALERT_WINDOW", "allow"),
        ).mapNotNull(::run).joinToString("\n").ifEmpty { null }
    }

    private fun run(argv: List<String>): String? {
        val process = ProcessBuilder(argv).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        return if (process.waitFor() == 0) null else "${argv.joinToString(" ")}: $output"
    }

    override fun destroy() {
        exitProcess(0)
    }
}
