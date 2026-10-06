// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal object ClipboardImages {
    const val LIMIT = 8 * 1024 * 1024

    fun read(input: InputStream): ByteArray {
        val out = BoundedOutput()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    fun validate(data: ByteArray) {
        require(data.size <= LIMIT) { "Image exceeds 8 MiB" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000) {
            "Invalid image or image exceeds 16 megapixels"
        }
    }

    fun png(data: ByteArray): ByteArray {
        validate(data)
        // Re-encoding a received PNG would change its hash and defeat the core's
        // remote-clipboard echo suppression. Preserve existing PNG bytes exactly.
        originalPng(data)?.let { return it }
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: error("Cannot decode image")
        return try {
            val output = BoundedOutput()
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    internal fun originalPng(data: ByteArray): ByteArray? {
        val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        return data.takeIf { it.size in 8..LIMIT && it.copyOfRange(0, 8).contentEquals(signature) }
    }

    private class BoundedOutput : ByteArrayOutputStream() {
        override fun write(b: Int) {
            require(count < LIMIT) { "Image exceeds 8 MiB" }
            super.write(b)
        }

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {
            require(count.toLong() + len <= LIMIT) { "Image exceeds 8 MiB" }
            super.write(b, off, len)
        }
    }
}
