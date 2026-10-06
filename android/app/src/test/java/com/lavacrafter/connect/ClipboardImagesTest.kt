// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ClipboardImagesTest {
    @Test
    fun existingPngIsNotReencodedSoRemoteHashRemainsStable() {
        val data =
            (
                "89504e470d0a1a0a0000000d4948445200000001000000010802000000907753de" +
                    "0000000c49444154789c63606060000000040001f61738550000000049454e44ae426082"
            ).chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        assertSame(data, ClipboardImages.originalPng(data))
    }

    @Test
    fun nonPngOrOversizedInputCannotBypassConversionLimits() {
        assertNull(ClipboardImages.originalPng(byteArrayOf(-1, -40, -1, 0)))
        assertNull(ClipboardImages.originalPng(ByteArray(ClipboardImages.LIMIT + 1)))
    }
}
