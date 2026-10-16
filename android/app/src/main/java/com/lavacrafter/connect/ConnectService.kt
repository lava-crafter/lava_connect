// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import android.app.*
import android.content.*
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.lavacrafter.connect.nativecore.NativeCore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class DeviceInfo(
    val id: String,
    val name: String,
    val platform: String,
    val state: String,
    val trusted: Boolean,
    val pairingCode: String?,
    val text: Boolean,
    val image: Boolean,
)

data class ConnectState(
    val running: Boolean = false,
    val localId: String = "",
    val devices: List<DeviceInfo> = emptyList(),
    val error: String? = null,
    val imageAvailable: Boolean = false,
)

class ConnectService : Service() {
    private companion object {
        const val CLIPBOARD_WRITE_ERROR = "Unable to write received clipboard; return to the app to retry"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var handle = 0L
    private var starting = false

    @Volatile private var destroyed = false
    private var lock: WifiManager.MulticastLock? = null
    private val _state = MutableStateFlow(ConnectState())
    val state = _state.asStateFlow()
    private var foreground = false
    private var applyPending: (() -> Unit)? = null
    private val pendingClipboard = PendingClipboard<ClipData>()
    private var latestImage: Uri? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun service() = this@ConnectService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        try {
            if (!foreground) {
                startForeground(1, notification())
                foreground = true
            }
            if (handle == 0L && !starting) startCore()
        } catch (e: Exception) {
            fail(e.message ?: "Unable to start foreground service")
            endConnection()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        getSystemService(
            NotificationManager::class.java,
        ).createNotificationChannel(NotificationChannel("connection", "Lava Connect", NotificationManager.IMPORTANCE_LOW))
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat
            .Builder(
                this,
                "connection",
            ).setSmallIcon(
                android.R.drawable.stat_sys_data_bluetooth,
            ).setContentTitle("Lava Connect is active")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun startCore() {
        starting = true
        scope.launch {
            try {
                val wifi = getSystemService(WIFI_SERVICE) as WifiManager
                lock =
                    wifi.createMulticastLock("lava-connect-mdns").apply {
                        setReferenceCounted(false)
                        acquire()
                    }
                // Always publish or dispose the native handle, even if Service dies during startup.
                withContext(NonCancellable + Dispatchers.IO) {
                    val key = StorageIdentity.getOrCreate(filesDir)
                    val native =
                        NativeCore.create(
                            File(filesDir, "core").absolutePath,
                            android.os.Build.MODEL
                                .take(64),
                            key,
                        )
                    withContext(NonCancellable + Dispatchers.Main) {
                        if (destroyed) {
                            NativeCore.stop(native)
                        } else {
                            handle = native
                        }
                    }
                }
                check(handle != 0L) { "Native core failed to start" }
                val native = handle
                while (isActive && !destroyed) {
                    val (snapshot, events) =
                        withContext(Dispatchers.IO) {
                            JSONObject(NativeCore.snapshot(native)) to
                                JSONArray(NativeCore.events(native))
                        }
                    val list = snapshot.optJSONArray("devices") ?: JSONArray()
                    val devices =
                        (0 until list.length()).map { i ->
                            list.getJSONObject(i).let { d ->
                                DeviceInfo(
                                    d.optString("id"),
                                    d.optString("name"),
                                    d.optString("platform"),
                                    d.optString("state"),
                                    d.optBoolean("trusted"),
                                    d.optString("pairing_code").takeUnless {
                                        it ==
                                            "null" ||
                                            it.isBlank()
                                    },
                                    d.optBoolean("text_enabled"),
                                    d.optBoolean("image_enabled"),
                                )
                            }
                        }
                    _state.value =
                        _state.value.copy(
                            running = snapshot.optBoolean("running"),
                            localId = snapshot.optString("device_id"),
                            devices = devices,
                            error =
                                snapshot.optString("error").takeUnless { it == "null" || it.isBlank() } ?: _state.value.error,
                        )
                    processEvents(events)
                    delay(400)
                }
            } catch (
                e: CancellationException,
            ) {
                throw e
            } catch (
                e: LinkageError,
            ) {
                fail("Native library missing: build with scripts/build-android.sh")
                endConnection()
                stopSelf()
            } catch (
                e: Exception,
            ) {
                fail(e.message ?: "Connection service failed")
                endConnection()
                stopSelf()
            } finally {
                starting = false
            }
        }
    }

    private suspend fun processEvents(events: JSONArray) {
        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            when (event.optString("type")) {
                "error" -> {
                    fail(event.optString("message"))
                }

                "clipboard_received" -> {
                    try {
                        val mime = event.getString("mime_type")
                        val clip =
                            withContext(Dispatchers.IO) {
                                val data = Base64.decode(event.getString("data"), Base64.NO_WRAP)
                                require(data.size <= ClipboardImages.LIMIT) { "Clipboard exceeds 8 MiB" }
                                if (mime == "text/plain") {
                                    ClipData.newPlainText("Lava Connect", data.toString(Charsets.UTF_8))
                                } else {
                                    require(mime == "image/png")
                                    ClipboardImages.validate(data)
                                    val dir = File(cacheDir, "shared").apply { mkdirs() }
                                    val file = File(dir, "${UUID.randomUUID()}.png").apply { writeBytes(data) }
                                    val uri = FileProvider.getUriForFile(this@ConnectService, "$packageName.files", file)
                                    // Keep several previous URIs usable while bounding cache growth.
                                    dir
                                        .listFiles()
                                        ?.sortedByDescending { it.lastModified() }
                                        ?.drop(8)
                                        ?.forEach { it.delete() }
                                    ClipData("Lava Connect image", arrayOf("image/png"), ClipData.Item(uri))
                                }
                            }
                        pendingClipboard.offer(clip, event.getString("content_hash"))
                        if (mime ==
                            "image/png"
                        ) {
                            latestImage = clip.getItemAt(0).uri
                            _state.value = _state.value.copy(imageAvailable = true)
                        }
                        // Android restricts background reads, not ordinary clipboard writes.
                        // Receiving must not depend on an Activity being RESUMED.
                        applyReceivedClipboard()
                    } catch (e: Exception) {
                        fail(e.message ?: "Unable to receive clipboard")
                    }
                }
            }
        }
    }

    fun setForeground(callback: (() -> Unit)?) {
        applyPending = callback
        callback?.invoke()
    }

    fun applyReceivedClipboard() {
        try {
            val applied =
                pendingClipboard.apply(
                    markApplied = { hash ->
                        NativeCore.command(
                            requireHandle(),
                            JSONObject().put("type", "mark_clipboard_applied").put("content_hash", hash).toString(),
                        )
                    },
                    write = { clip ->
                        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                    },
                )
            if (applied && _state.value.error == CLIPBOARD_WRITE_ERROR) {
                _state.value = _state.value.copy(error = null)
            }
        } catch (_: Exception) {
            // Keep the latest received clip. setForeground retries without reading
            // the clipboard, and a platform refusal must not stop the connection.
            fail(CLIPBOARD_WRITE_ERROR)
        }
    }

    fun receivedImage(): Uri? = latestImage

    fun sendCurrentClipboard() {
        val clip = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip ?: error("Clipboard is empty")
        require(clip.itemCount > 0)
        val item = clip.getItemAt(0)
        if (item.uri != null &&
            (clip.description.hasMimeType("image/*") || contentResolver.getType(item.uri)?.startsWith("image/") == true)
        ) {
            sendImage(item.uri)
        } else {
            sendText(item.text?.toString() ?: item.coerceToText(this).toString())
        }
    }

    fun sendImage(uri: Uri) {
        work {
            val png =
                contentResolver.openInputStream(uri)?.use { ClipboardImages.png(ClipboardImages.read(it)) } ?: error("Cannot open image")
            sendBytes("image/png", png)
        }
    }

    fun sendText(text: String) {
        work { sendBytes("text/plain", text.toByteArray(Charsets.UTF_8)) }
    }

    fun sendPng(bytes: ByteArray) {
        work {
            ClipboardImages.validate(bytes)
            sendBytes("image/png", bytes)
        }
    }

    fun command(value: JSONObject) {
        val native = requireHandle()
        work { NativeCore.command(native, value.toString()) }
    }

    private fun sendBytes(
        mime: String,
        bytes: ByteArray,
    ) {
        require(bytes.size <= ClipboardImages.LIMIT) { "Clipboard exceeds 8 MiB" }
        NativeCore.command(
            requireHandle(),
            JSONObject()
                .put(
                    "type",
                    "send_clipboard",
                ).put("mime_type", mime)
                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                .toString(),
        )
    }

    private fun requireHandle(): Long = handle.also { check(it != 0L) { "Connection service is not running" } }

    private fun work(action: () -> Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
            } catch (e: Exception) {
                fail(e.message ?: "Clipboard operation failed")
            }
        }
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    override fun onDestroy() {
        destroyed = true
        applyPending = null
        scope.cancel()
        endConnection()
        super.onDestroy()
    }

    private fun endConnection() {
        if (handle != 0L) {
            runCatching { NativeCore.stop(handle) }
            handle = 0L
        }
        lock?.let { if (it.isHeld) it.release() }
        lock = null
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        _state.value = _state.value.copy(running = false)
    }
}
