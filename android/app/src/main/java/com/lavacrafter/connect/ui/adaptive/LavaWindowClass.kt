// SPDX-License-Identifier: Apache-2.0
// Adapted from lava_note (Copyright 2026 Muchen Jiang), ui/design.
package com.lavacrafter.connect.ui.adaptive

import android.app.Activity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class LavaWindowClass(
    val width: LavaWindowWidthClass,
) {
    /**
     * Keeps feature screens independent of Material's window-size classes while
     * making the app's navigation presentation explicit.
     */
    val navigation: LavaNavigationLayout
        get() =
            when (width) {
                LavaWindowWidthClass.COMPACT -> LavaNavigationLayout.MODAL
                LavaWindowWidthClass.MEDIUM -> LavaNavigationLayout.RAIL
                LavaWindowWidthClass.EXPANDED -> LavaNavigationLayout.PERMANENT
            }

    /** Settings presentation follows the feature's actual available width. */
    fun settingsLayout(availableWidth: Dp): LavaSettingsLayout =
        if (availableWidth >= SETTINGS_TWO_PANE_MIN_WIDTH) {
            LavaSettingsLayout.TWO_PANE
        } else {
            LavaSettingsLayout.SINGLE_PANE
        }

    companion object {
        val Compact = LavaWindowClass(LavaWindowWidthClass.COMPACT)
    }
}

enum class LavaNavigationLayout {
    MODAL,
    RAIL,
    PERMANENT,
}

enum class LavaSettingsLayout {
    SINGLE_PANE,
    TWO_PANE,
}

private val SETTINGS_TWO_PANE_MIN_WIDTH = 720.dp

enum class LavaWindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
    ;

    companion object {
        fun fromMaterial3(widthSizeClass: WindowWidthSizeClass): LavaWindowWidthClass =
            when (widthSizeClass) {
                WindowWidthSizeClass.Compact -> COMPACT
                WindowWidthSizeClass.Medium -> MEDIUM
                else -> EXPANDED
            }

        fun fromWidth(width: Dp): LavaWindowWidthClass =
            when {
                width < 600.dp -> COMPACT
                width < 840.dp -> MEDIUM
                else -> EXPANDED
            }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun calculateLavaWindowClass(activity: Activity): LavaWindowClass =
    LavaWindowClass(
        width = LavaWindowWidthClass.fromMaterial3(calculateWindowSizeClass(activity).widthSizeClass),
    )
