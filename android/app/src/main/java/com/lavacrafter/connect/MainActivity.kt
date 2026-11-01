// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lavacrafter.connect.ui.AppearancePreferences
import com.lavacrafter.connect.ui.ConnectApp
import com.lavacrafter.connect.ui.ConnectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appearance = AppearancePreferences.state(applicationContext)
        setContent {
            val current by appearance.collectAsState()
            ConnectTheme(current) { ConnectApp() }
        }
    }
}
