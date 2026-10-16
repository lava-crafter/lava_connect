// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PendingClipboardTest {
    @Test
    fun writesWithoutForegroundCallbackAndMarksBeforeWrite() {
        val pending = PendingClipboard<String>()
        val calls = mutableListOf<String>()
        pending.offer("Hello Android", "text-hash")
        pending.apply({ calls += "mark:$it" }, { calls += "write:$it" })
        pending.apply({ calls += "unexpected-mark" }, { calls += "unexpected-write" })
        assertEquals(listOf("mark:text-hash", "write:Hello Android"), calls)
    }

    @Test
    fun failedWriteRetainsImageAndRefreshesMarkOnRetry() {
        val pending = PendingClipboard<ByteArray>()
        val image = byteArrayOf(1, 2, 3)
        val calls = mutableListOf<String>()
        pending.offer(image, "image-hash")
        assertThrows(SecurityException::class.java) {
            pending.apply({ calls += "mark:$it" }, { throw SecurityException("denied") })
        }
        pending.apply({ calls += "mark:$it" }, { org.junit.Assert.assertSame(image, it) })
        pending.apply({ calls += "unexpected-mark" }, { calls += "unexpected-write" })
        assertEquals(listOf("mark:image-hash", "mark:image-hash"), calls)
    }

    @Test
    fun failedMarkDoesNotWriteOrDiscardClipboard() {
        val pending = PendingClipboard<String>()
        pending.offer("text", "hash")
        var writes = 0
        assertThrows(IllegalStateException::class.java) {
            pending.apply({ error("core unavailable") }, { writes++ })
        }
        assertEquals(0, writes)
        pending.apply({}, { writes++ })
        assertEquals(1, writes)
    }

    @Test
    fun newerClipboardReplacesFailedWrite() {
        val pending = PendingClipboard<String>()
        pending.offer("old", "old-hash")
        assertThrows(SecurityException::class.java) {
            pending.apply({}, { throw SecurityException("denied") })
        }
        pending.offer("new", "new-hash")
        pending.apply({ assertEquals("new-hash", it) }, { assertEquals("new", it) })
    }

    @Test
    fun writingDoesNotDiscardReentrantNewValue() {
        val pending = PendingClipboard<String>()
        pending.offer("old", "old-hash")
        pending.apply({}, { pending.offer("new", "new-hash") })
        pending.apply({ assertEquals("new-hash", it) }, { assertEquals("new", it) })
    }
}
