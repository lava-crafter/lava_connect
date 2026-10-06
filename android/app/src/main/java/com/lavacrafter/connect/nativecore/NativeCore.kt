// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.nativecore

object NativeCore {
    init {
        System.loadLibrary("lava_connect")
    }

    external fun create(
        storageDir: String,
        name: String,
        storageKey: String,
    ): Long

    external fun stop(handle: Long)

    external fun command(
        handle: Long,
        json: String,
    )

    external fun snapshot(handle: Long): String

    external fun events(handle: Long): String

    external fun greeting(): String
}
