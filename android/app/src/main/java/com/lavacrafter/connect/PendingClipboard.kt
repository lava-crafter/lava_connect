// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

/** Main-thread-only, latest-value clipboard slot. Failed writes remain available for retry. */
internal class PendingClipboard<T> {
    private class Entry<T>(
        val value: T,
        val hash: String,
    )

    private var pending: Entry<T>? = null

    fun clear() {
        pending = null
    }

    fun offer(
        value: T,
        hash: String,
    ) {
        pending = Entry(value, hash)
    }

    fun apply(
        markApplied: (String) -> Unit,
        write: (T) -> Unit,
    ): Boolean {
        val entry = pending ?: return false
        // Mark before writing: a local watcher may react immediately to the write.
        // Repeat on retry so delayed writes also have a fresh suppression window.
        markApplied(entry.hash)
        write(entry.value)
        if (pending === entry) pending = null
        return true
    }
}
