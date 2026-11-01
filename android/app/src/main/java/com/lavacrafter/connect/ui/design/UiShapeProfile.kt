// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), data/AppPreferences.kt.
package com.lavacrafter.connect.ui.design

enum class UiShapeProfile {
    EXPRESSIVE,
    SQUARE,
    ;

    companion object {
        fun fromStored(value: String?): UiShapeProfile = entries.firstOrNull { it.name == value } ?: EXPRESSIVE
    }
}
