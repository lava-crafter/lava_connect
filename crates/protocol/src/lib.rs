// SPDX-License-Identifier: GPL-3.0-or-later
#![deny(unsafe_op_in_unsafe_fn)]
include!(concat!(env!("OUT_DIR"), "/lava.connect.v1.rs"));
pub use prost::Message;
pub const PROTOCOL_VERSION: u32 = 1;
pub const MAX_CLIPBOARD_BYTES: usize = 8 * 1024 * 1024;
pub const MAX_FRAME_BYTES: usize = MAX_CLIPBOARD_BYTES + 4096;
