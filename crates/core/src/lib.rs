// SPDX-License-Identifier: GPL-3.0-or-later
#![deny(unsafe_op_in_unsafe_fn)]
mod identity;
mod runtime;
mod security;
pub use runtime::{CoreConfig, CoreHandle};

pub const DEFAULT_GROUP_ID: &str = "00000000-0000-0000-0000-000000000000";
/// UDP port the apps listen on unless the user picks another one. `0` means a random port.
pub const DEFAULT_PORT: u16 = 45893;

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct ClipboardGroup {
    pub id: String,
    pub name: String,
    pub members: Vec<String>,
}

use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Device {
    pub id: String,
    pub name: String,
    pub platform: String,
    pub state: String, // available, connecting, pairing, connected, offline
    pub trusted: bool,
    pub pairing_code: Option<String>,
    pub text_enabled: bool,
    pub image_enabled: bool,
    pub sync_in_group: bool,
    pub group_compatible: bool,
}

#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct Snapshot {
    pub device_id: String,
    pub device_name: String,
    pub running: bool,
    pub devices: Vec<Device>,
    pub error: Option<String>,
    pub active_group_id: String,
    pub groups: Vec<ClipboardGroup>,
    /// UDP port the QUIC endpoint is bound to.
    pub listen_port: u16,
    /// This device's `ip:port` addresses (IPv4 first, then routable IPv6 in brackets), for
    /// entering on another device.
    pub addresses: Vec<String>,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum CoreEvent {
    Changed,
    ClipboardReceived {
        device_id: String,
        group_id: String,
        mime_type: String,
        data: Vec<u8>,
        content_hash: String,
    },
    Error {
        message: String,
    },
}

#[derive(Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Command {
    CreateGroup {
        name: String,
    },
    JoinGroup {
        group_id: String,
        name: String,
    },
    SelectGroup {
        group_id: String,
    },
    DeleteGroup {
        group_id: String,
    },
    SetGroupMember {
        group_id: String,
        device_id: String,
        enabled: bool,
    },
    MarkClipboardApplied {
        content_hash: String,
    },
    ConnectAddress {
        device_id: String,
        address: String,
    },
    /// Connect to an IPv4 or IPv6 address, with or without a port (`1.2.3.4`, `1.2.3.4:5`,
    /// `2001:db8::1`, `[2001:db8::1]:5`, `fe80::1%wlan0`), and start pairing. The peer's identity
    /// is learned from its TLS certificate; pairing still needs both confirmations.
    AddAddress {
        address: String,
    },
    Pair {
        device_id: String,
    },
    ConfirmPair {
        device_id: String,
        pairing_code: String,
    },
    CancelPair {
        device_id: String,
    },
    Unpair {
        device_id: String,
    },
    SetCapability {
        device_id: String,
        capability: i32,
        enabled: bool,
    },
    SendClipboard {
        mime_type: String,
        data: Vec<u8>,
    },
}

pub fn greeting() -> &'static str {
    "Hello from Lava Connect Core"
}

#[cfg(test)]
pub(crate) fn test_directory() -> tempfile::TempDir {
    let preferred = std::path::Path::new("/tmp/opencode");
    if preferred.is_dir() {
        tempfile::tempdir_in(preferred)
    } else {
        tempfile::tempdir()
    }
    .unwrap()
}
