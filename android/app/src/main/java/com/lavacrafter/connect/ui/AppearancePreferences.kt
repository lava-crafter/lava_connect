// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.ui

import android.content.Context
import com.lavacrafter.connect.ui.design.UiShapeProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: String = ACCENT_PRESETS.first(),
    val shapeProfile: UiShapeProfile = UiShapeProfile.EXPRESSIVE,
)

/** Same presets as lava_note and the Linux client (linux/app/src/theme.rs). */
val ACCENT_PRESETS = listOf("#1976D2", "#42A5F5", "#3F51B5", "#0097A7", "#0D47A1")

/** Process-wide appearance settings; small enough that SharedPreferences is sufficient. */
object AppearancePreferences {
    private const val FILE = "appearance"
    private var flow: MutableStateFlow<Appearance>? = null

    fun state(context: Context): StateFlow<Appearance> =
        synchronized(this) {
            flow ?: MutableStateFlow(load(context)).also { flow = it }
        }.asStateFlow()

    fun update(
        context: Context,
        transform: (Appearance) -> Appearance,
    ) {
        state(context)
        val next = transform(flow!!.value)
        flow!!.value = next
        context
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", next.themeMode.name)
            .putString("accent", next.accent)
            .putString("shape_profile", next.shapeProfile.name)
            .apply()
    }

    private fun load(context: Context): Appearance {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return Appearance(
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme_mode", null) } ?: ThemeMode.SYSTEM,
            accent = prefs.getString("accent", null) ?: ACCENT_PRESETS.first(),
            shapeProfile = UiShapeProfile.fromStored(prefs.getString("shape_profile", null)),
        )
    }
}
