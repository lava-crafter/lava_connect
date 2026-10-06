// SPDX-License-Identifier: GPL-3.0-or-later
#![deny(unsafe_op_in_unsafe_fn)]
use base64::{Engine, engine::general_purpose::STANDARD};
use jni::{
    JNIEnv,
    objects::{JClass, JString},
    sys::{jlong, jstring},
};
use lava_core::{Command, CoreConfig, CoreEvent, CoreHandle};
use std::{
    collections::HashMap,
    path::PathBuf,
    sync::{
        Mutex, OnceLock,
        atomic::{AtomicI64, Ordering},
    },
};
static NEXT: AtomicI64 = AtomicI64::new(1);
static HANDLES: OnceLock<Mutex<HashMap<i64, CoreHandle>>> = OnceLock::new();
fn registry() -> &'static Mutex<HashMap<i64, CoreHandle>> {
    HANDLES.get_or_init(|| Mutex::new(HashMap::new()))
}
fn string(env: &mut JNIEnv<'_>, value: JString<'_>) -> Result<String, jni::errors::Error> {
    env.get_string(&value).map(Into::into)
}
fn output(env: &mut JNIEnv<'_>, text: String) -> jstring {
    env.new_string(text)
        .map_or(std::ptr::null_mut(), |s| s.into_raw())
}
fn error(env: &mut JNIEnv<'_>, msg: impl ToString) {
    let _ = env.throw_new("java/lang/IllegalStateException", msg.to_string());
}
#[derive(serde::Serialize)]
#[serde(tag = "type", rename_all = "snake_case")]
enum AndroidEvent {
    Changed,
    Error {
        message: String,
    },
    ClipboardReceived {
        device_id: String,
        mime_type: String,
        data: String,
        content_hash: String,
    },
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lavacrafter_connect_nativecore_NativeCore_greeting(
    mut env: JNIEnv<'_>,
    _: JClass<'_>,
) -> jstring {
    output(&mut env, lava_core::greeting().to_owned())
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lavacrafter_connect_nativecore_NativeCore_create(
    mut env: JNIEnv<'_>,
    _: JClass<'_>,
    dir: JString<'_>,
    name: JString<'_>,
    key: JString<'_>,
) -> jlong {
    let result = (|| -> anyhow::Result<i64> {
        let config = CoreConfig {
            storage_dir: PathBuf::from(string(&mut env, dir)?),
            device_name: string(&mut env, name)?,
            platform: "android".into(),
            listen_port: 0,
            storage_key: Some(string(&mut env, key)?),
            discovery: true,
        };
        let core = CoreHandle::start(config)?;
        let id = NEXT.fetch_add(1, Ordering::Relaxed);
        registry()
            .lock()
            .map_err(|_| anyhow::anyhow!("native handle registry poisoned"))?
            .insert(id, core);
        Ok(id)
    })();
    match result {
        Ok(id) => id,
        Err(e) => {
            error(&mut env, e);
            0
        }
    }
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lavacrafter_connect_nativecore_NativeCore_stop(
    _: JNIEnv<'_>,
    _: JClass<'_>,
    id: jlong,
) {
    if let Ok(mut map) = registry().lock()
        && let Some(mut core) = map.remove(&id)
    {
        core.stop();
    }
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lavacrafter_connect_nativecore_NativeCore_command(
    mut env: JNIEnv<'_>,
    _: JClass<'_>,
    id: jlong,
    json: JString<'_>,
) {
    let result = (|| -> anyhow::Result<()> {
        let json = string(&mut env, json)?;
        anyhow::ensure!(
            json.len() <= 12 * 1024 * 1024,
            "JNI command exceeds size limit"
        );
        let value: serde_json::Value = serde_json::from_str(&json)?;
        let command = if value["type"] == "send_clipboard" {
            Command::SendClipboard {
                mime_type: value["mime_type"]
                    .as_str()
                    .ok_or_else(|| anyhow::anyhow!("missing MIME"))?
                    .into(),
                data: STANDARD.decode(
                    value["data"]
                        .as_str()
                        .ok_or_else(|| anyhow::anyhow!("expected base64 clipboard bytes"))?,
                )?,
            }
        } else {
            serde_json::from_value(value)?
        };
        registry()
            .lock()
            .map_err(|_| anyhow::anyhow!("native handle registry poisoned"))?
            .get(&id)
            .ok_or_else(|| anyhow::anyhow!("invalid native handle"))?
            .command(command)?;
        Ok(())
    })();
    if let Err(e) = result {
        error(&mut env, e);
    }
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lavacrafter_connect_nativecore_NativeCore_snapshot(
    mut env: JNIEnv<'_>,
    _: JClass<'_>,
    id: jlong,
) -> jstring {
    let result = registry()
        .lock()
        .map_err(|_| "native handle registry poisoned".to_owned())
        .and_then(|mut map| {
            map.get_mut(&id)
                .map(|core| serde_json::to_string(&core.snapshot()).unwrap_or_default())
                .ok_or_else(|| "invalid native handle".into())
        });
    match result {
        Ok(json) => output(&mut env, json),
        Err(e) => {
            error(&mut env, e);
            std::ptr::null_mut()
        }
    }
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lavacrafter_connect_nativecore_NativeCore_events(
    mut env: JNIEnv<'_>,
    _: JClass<'_>,
    id: jlong,
) -> jstring {
    let result = (|| -> anyhow::Result<String> {
        let map = registry()
            .lock()
            .map_err(|_| anyhow::anyhow!("native handle registry poisoned"))?;
        let core = map
            .get(&id)
            .ok_or_else(|| anyhow::anyhow!("invalid native handle"))?;
        let mut events = Vec::new();
        while let Some(event) = core.next_event() {
            let clipboard = matches!(event, CoreEvent::ClipboardReceived { .. });
            events.push(event);
            if clipboard || events.len() == 8 {
                break;
            }
        }
        drop(map);
        let events: Vec<_> = events
            .into_iter()
            .map(|event| match event {
                CoreEvent::Changed => AndroidEvent::Changed,
                CoreEvent::Error { message } => AndroidEvent::Error { message },
                CoreEvent::ClipboardReceived {
                    device_id,
                    mime_type,
                    data,
                    content_hash,
                } => AndroidEvent::ClipboardReceived {
                    device_id,
                    mime_type,
                    data: STANDARD.encode(data),
                    content_hash,
                },
            })
            .collect();
        Ok(serde_json::to_string(&events)?)
    })();
    match result {
        Ok(json) => output(&mut env, json),
        Err(e) => {
            error(&mut env, e);
            std::ptr::null_mut()
        }
    }
}
