// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lavacrafter.connect.BackgroundRead
import com.lavacrafter.connect.BuildConfig
import com.lavacrafter.connect.ConnectState
import com.lavacrafter.connect.R
import com.lavacrafter.connect.ui.ACCENT_PRESETS
import com.lavacrafter.connect.ui.Appearance
import com.lavacrafter.connect.ui.ThemeMode
import com.lavacrafter.connect.ui.adaptive.LavaSettingsLayout
import com.lavacrafter.connect.ui.adaptive.LavaWindowClass
import com.lavacrafter.connect.ui.common.PaneTitle
import com.lavacrafter.connect.ui.common.SettingsClickableItem
import com.lavacrafter.connect.ui.common.SettingsGroup
import com.lavacrafter.connect.ui.common.SettingsSwitchItem
import com.lavacrafter.connect.ui.design.LavaSpacing
import com.lavacrafter.connect.ui.design.UiShapeProfile
import com.lavacrafter.connect.ui.design.component.LavaCard
import com.lavacrafter.connect.ui.design.component.LavaSegment
import com.lavacrafter.connect.ui.design.component.LavaSegmentedSelector
import com.lavacrafter.connect.ui.design.component.lavaFocusIndicator

private enum class SettingsCategory(
    val title: Int,
    val icon: ImageVector,
) {
    CONNECTION(R.string.settings_connection, Icons.Filled.Sync),
    BACKGROUND(R.string.background_title, Icons.Filled.ContentPasteGo),
    APPEARANCE(R.string.settings_appearance, Icons.Filled.Palette),
    ABOUT(R.string.settings_about, Icons.Filled.Info),
}

private val accentLabels =
    listOf(
        R.string.settings_color_blue,
        R.string.settings_color_light_blue,
        R.string.settings_color_indigo,
        R.string.settings_color_cyan,
        R.string.settings_color_deep_blue,
    )

@Composable
internal fun SettingsScreen(
    state: ConnectState,
    appearance: Appearance,
    nativeGreeting: String,
    onAppearance: ((Appearance) -> Appearance) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBackgroundChanged: () -> Unit,
    onError: (String) -> Unit,
    onTestNative: () -> Unit,
) {
    val categories = SettingsCategory.entries.filter { it != SettingsCategory.BACKGROUND || BackgroundRead.supported() }
    var category by rememberSaveable { mutableStateOf(SettingsCategory.CONNECTION) }
    val section: @Composable (SettingsCategory) -> Unit = { item ->
        when (item) {
            SettingsCategory.CONNECTION -> {
                SettingsGroup(stringResource(R.string.settings_connection)) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_sync),
                        description = stringResource(if (state.running) R.string.settings_sync_on else R.string.settings_sync_off),
                        icon = Icons.Filled.Sync,
                        checked = state.running,
                        onCheckedChange = { if (it) onStart() else onStop() },
                    )
                }
            }

            SettingsCategory.BACKGROUND -> {
                BackgroundReadSettings(state.backgroundRead, onBackgroundChanged, onError)
            }

            SettingsCategory.APPEARANCE -> {
                AppearanceSettings(appearance, onAppearance)
            }

            SettingsCategory.ABOUT -> {
                AboutSettings(nativeGreeting, onTestNative)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PaneTitle(stringResource(R.string.nav_settings))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (LavaWindowClass.Compact.settingsLayout(maxWidth) == LavaSettingsLayout.TWO_PANE) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(224.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(LavaSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(LavaSpacing.small),
                    ) {
                        categories.forEach { item ->
                            LavaCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { category = item },
                                selected = category == item,
                                contentDescription = stringResource(item.title),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(item.icon, contentDescription = null)
                                    Spacer(Modifier.size(LavaSpacing.medium))
                                    Text(stringResource(item.title), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                            section(category)
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    categories.forEach { section(it) }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettings(
    appearance: Appearance,
    onAppearance: ((Appearance) -> Appearance) -> Unit,
) {
    SettingsGroup(stringResource(R.string.settings_appearance)) {
        ChoiceItem(
            title = stringResource(R.string.settings_theme),
            description = stringResource(R.string.settings_theme_description),
            icon = Icons.Filled.DarkMode,
        ) {
            LavaSegmentedSelector(
                options =
                    listOf(
                        LavaSegment(ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system)),
                        LavaSegment(ThemeMode.LIGHT, stringResource(R.string.settings_theme_light)),
                        LavaSegment(ThemeMode.DARK, stringResource(R.string.settings_theme_dark)),
                    ),
                selected = appearance.themeMode,
                onSelectionChange = { mode -> onAppearance { it.copy(themeMode = mode) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ChoiceItem(
            title = stringResource(R.string.settings_accent),
            description = stringResource(R.string.settings_accent_description),
            icon = Icons.Filled.Palette,
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(LavaSpacing.small),
            ) {
                ACCENT_PRESETS.forEachIndexed { index, value ->
                    val label = stringResource(accentLabels[index])
                    val selected = value.equals(appearance.accent, ignoreCase = true)
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .lavaFocusIndicator()
                                .selectable(selected = selected, role = Role.RadioButton, onClick = {
                                    onAppearance { it.copy(accent = value) }
                                })
                                .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = MaterialTheme.shapes.small,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .background(Color(android.graphics.Color.parseColor(value)), MaterialTheme.shapes.small)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
                            )
                        }
                    }
                }
            }
        }
        ChoiceItem(
            title = stringResource(R.string.settings_shape),
            description = stringResource(R.string.settings_shape_description),
            icon = Icons.Filled.RoundedCorner,
        ) {
            LavaSegmentedSelector(
                options =
                    listOf(
                        LavaSegment(UiShapeProfile.EXPRESSIVE, stringResource(R.string.settings_shape_expressive)),
                        LavaSegment(UiShapeProfile.SQUARE, stringResource(R.string.settings_shape_square)),
                    ),
                selected = appearance.shapeProfile,
                onSelectionChange = { profile -> onAppearance { it.copy(shapeProfile = profile) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Icon, title and description with a choice control underneath (lava_note's SettingsChoiceItem). */
@Composable
private fun ChoiceItem(
    title: String,
    description: String,
    icon: ImageVector,
    control: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = LavaSpacing.large, vertical = LavaSpacing.medium)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(LavaSpacing.large))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(LavaSpacing.medium))
        control()
    }
}

@Composable
private fun AboutSettings(
    nativeGreeting: String,
    onTestNative: () -> Unit,
) {
    val context = LocalContext.current
    SettingsGroup(stringResource(R.string.settings_about)) {
        SettingsClickableItem(
            title = stringResource(R.string.settings_version),
            description = BuildConfig.VERSION_NAME,
            icon = Icons.Filled.Info,
            onClick = null,
        )
        SettingsClickableItem(
            title = stringResource(R.string.settings_license),
            description = stringResource(R.string.settings_license_description),
            icon = Icons.Filled.Gavel,
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html")))
            },
        )
        if (BuildConfig.DEBUG) {
            SettingsClickableItem(
                title = stringResource(R.string.settings_test_native),
                description = nativeGreeting.ifEmpty { stringResource(R.string.settings_test_native_description) },
                icon = Icons.Filled.BugReport,
                onClick = onTestNative,
            )
        }
    }
}
