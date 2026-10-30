// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ConnectViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = ConnectRepository(application)
    private val _state = MutableStateFlow(ConnectState())
    val state = _state.asStateFlow()
    private val _nativeGreeting = MutableStateFlow("")
    val nativeGreeting = _nativeGreeting.asStateFlow()

    fun testRust() {
        runCatching {
            repository.greeting()
        }.onSuccess { _nativeGreeting.value = it }.onFailure { showError("Native core unavailable: build with scripts/build-android.sh") }
    }

    init {
        viewModelScope.launch { repository.state.collect { _state.value = it } }
    }

    fun start() = runCatching { repository.start() }.onFailure { _state.value = _state.value.copy(error = it.message) }

    fun stop() {
        repository.stop()
        _state.value = ConnectState()
    }

    fun foreground(callback: (() -> Unit)?) = repository.foreground(callback)

    fun sendClipboard() = runCatching { repository.sendClipboard() }.onFailure { _state.value = _state.value.copy(error = it.message) }

    fun send(text: String) = runCatching { repository.sendText(text) }.onFailure { _state.value = _state.value.copy(error = it.message) }

    fun sendPng(bytes: ByteArray) =
        runCatching { repository.sendPng(bytes) }.onFailure {
            _state.value =
                _state.value.copy(error = it.message)
        }

    fun command(
        type: String,
        id: String,
        capability: Int? = null,
        enabled: Boolean? = null,
    ) = runCatching {
        val obj =
            org.json
                .JSONObject()
                .put("type", type)
                .put("device_id", id)
        if (capability != null) obj.put("capability", capability).put("enabled", enabled)
        repository.command(obj)
    }.onFailure { _state.value = _state.value.copy(error = it.message) }

    private fun groupCommand(
        type: String,
        fields: Map<String, String>,
    ) = runCatching {
        val obj = org.json.JSONObject().put("type", type)
        fields.forEach { (key, value) -> obj.put(key, value) }
        repository.command(obj)
    }.onFailure { _state.value = _state.value.copy(error = it.message) }

    fun createGroup(name: String) = groupCommand("create_group", mapOf("name" to name))

    fun joinGroup(
        groupId: String,
        name: String,
    ) = groupCommand("join_group", mapOf("group_id" to groupId, "name" to name))

    fun selectGroup(groupId: String) = groupCommand("select_group", mapOf("group_id" to groupId))

    fun deleteGroup(groupId: String) = groupCommand("delete_group", mapOf("group_id" to groupId))

    fun setGroupMember(
        groupId: String,
        deviceId: String,
        enabled: Boolean,
    ) = runCatching {
        repository.command(
            org.json
                .JSONObject()
                .put("type", "set_group_member")
                .put("group_id", groupId)
                .put("device_id", deviceId)
                .put("enabled", enabled),
        )
    }.onFailure { _state.value = _state.value.copy(error = it.message) }

    fun receivedImage() = repository.receivedImage()

    fun updateBackgroundRead() = repository.updateBackgroundRead()

    fun confirmPair(
        id: String,
        code: String,
    ) = runCatching {
        repository.command(
            org.json
                .JSONObject()
                .put("type", "confirm_pair")
                .put("device_id", id)
                .put("pairing_code", code),
        )
    }.onFailure { showError(it.message ?: "Pairing failed") }

    fun sendImage(uri: android.net.Uri) =
        runCatching { repository.sendImage(uri) }.onFailure {
            showError(it.message ?: "Unable to send image")
        }

    fun applyClipboard() = runCatching { repository.applyClipboard() }.onFailure { showError(it.message ?: "Unable to apply clipboard") }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    fun showError(message: String) {
        _state.value = _state.value.copy(error = message)
    }
}
