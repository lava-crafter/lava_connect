// SPDX-License-Identifier: GPL-3.0-or-later
//! Window and appearance preferences, kept separate from the core's identity store.
use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct UiSettings {
    /// 0 = follow system, 1 = light, 2 = dark (same order as Android's ThemeMode).
    pub theme_mode: i32,
    pub accent_index: usize,
    pub square: bool,
    pub page: i32,
    pub width: u32,
    pub height: u32,
}

impl Default for UiSettings {
    fn default() -> Self {
        UiSettings {
            theme_mode: 0,
            accent_index: 0,
            square: false,
            page: 0,
            width: 960,
            height: 640,
        }
    }
}

fn path() -> Option<PathBuf> {
    let base = std::env::var_os("XDG_CONFIG_HOME")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("HOME").map(|home| PathBuf::from(home).join(".config")))?;
    Some(base.join("lava-connect/ui.json"))
}

impl UiSettings {
    pub fn load() -> Self {
        path()
            .and_then(|path| std::fs::read(path).ok())
            .and_then(|bytes| serde_json::from_slice(&bytes).ok())
            .unwrap_or_default()
    }

    pub fn save(&self) {
        let Some(path) = path() else { return };
        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        if let Ok(json) = serde_json::to_vec_pretty(self) {
            let _ = std::fs::write(path, json);
        }
    }
}
