// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lavacrafter.connect.ui.AppearancePreferences
import com.lavacrafter.connect.ui.ConnectApp
import com.lavacrafter.connect.ui.ConnectTheme
import com.lavacrafter.connect.ui.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appearance = AppearancePreferences.state(applicationContext)
        setContent {
            val current by appearance.collectAsState()
            val dark = current.isDark()
            // SystemBarStyle.auto follows the system's night mode; follow the in-app Theme
            // setting instead so the bar icons stay readable when the two differ.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            ConnectTheme(current) { ConnectApp() }
        }
    }

    private companion object {
        // androidx.activity's default navigation bar scrims (used on API < 29 and in 3-button mode).
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
