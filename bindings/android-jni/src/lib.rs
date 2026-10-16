// SPDX-License-Identifier: GPL-3.0-or-later
#![deny(unsafe_op_in_unsafe_fn)]
use base64::{Engine, engine::general_purpose::STANDARD};
use jni::{
    Env, EnvUnowned,
    errors::ErrorPolicy,
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

fn string(env: &Env<'_>, value: JString<'_>) -> anyhow::Result<String> {
    anyhow::ensure!(!value.is_null(), "JNI string must not be null");
    Ok(value.try_to_string(env)?)
}
fn output(env: &mut Env<'_>, text: String) -> jni::errors::Result<jstring> {
    Ok(env.new_string(text)?.into_raw())
}

fn throw_illegal_state(env: &mut Env<'_>, message: impl AsRef<str>) -> jni::errors::Result<()> {
    if env.exception_check() {
        return Ok(());
    }
    match env.throw_new(
        jni::jni_str!("java/lang/IllegalStateException"),
        jni::strings::JNIString::new(message),
    ) {
        Ok(()) | Err(jni::errors::Error::JavaException) => Ok(()),
        Err(error) => Err(error),
    }
}

struct IllegalState;
impl<T: Default, E: std::fmt::Display> ErrorPolicy<T, E> for IllegalState {
    type Captures<'a: 'b, 'b> = ();
    fn on_error<'a: 'b, 'b>(env: &mut Env<'a>, _: &mut (), err: E) -> jni::errors::Result<T> {
        throw_illegal_state(env, err.to_string())?;
        Ok(T::default())
    }
    fn on_panic<'a: 'b, 'b>(
        env: &mut Env<'a>,
        _: &mut (),
        _: Box<dyn std::any::Any + Send>,
    ) -> jni::errors::Result<T> {
        throw_illegal_state(env, "native operation panicked")?;
        Ok(T::default())
    }
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
pub extern "system" fn Java_com_lavacrafter_connect_nativecore_NativeCore_greeting<'local>(
    mut unowned: EnvUnowned<'local>,
    _: JClass<'local>,
) -> jstring {
    unowned
        .with_env(|env| output(env, lava_core::greeting().to_owned()))
        .resolve::<IllegalState>()
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lavacrafter_connect_nativecore_NativeCore_create<'local>(
    mut unowned: EnvUnowned<'local>,
    _: JClass<'local>,
    dir: JString<'local>,
    name: JString<'local>,
    key: JString<'local>,
) -> jlong {
    unowned
        .with_env(|env| -> anyhow::Result<jlong> {
            let config = CoreConfig {
                storage_dir: PathBuf::from(string(env, dir)?),
                device_name: string(env, name)?,
                platform: "android".into(),
                listen_port: 0,
                storage_key: Some(string(env, key)?),
                discovery: true,
            };
            let core = CoreHandle::start(config)?;
            let id = NEXT.fetch_add(1, Ordering::Relaxed);
            registry()
                .lock()
                .map_err(|_| anyhow::anyhow!("native handle registry poisoned"))?
                .insert(id, core);
            Ok(id)
        })
        .resolve::<IllegalState>()
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lavacrafter_connect_nativecore_NativeCore_stop<'local>(
    mut unowned: EnvUnowned<'local>,
    _: JClass<'local>,
    id: jlong,
) {
    unowned
        .with_env(|_| -> anyhow::Result<()> {
            if let Ok(mut map) = registry().lock()
                && let Some(mut core) = map.remove(&id)
            {
                core.stop();
            }
            Ok(())
        })
        .resolve::<IllegalState>();
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lavacrafter_connect_nativecore_NativeCore_command<'local>(
    mut unowned: EnvUnowned<'local>,
    _: JClass<'local>,
    id: jlong,
    json: JString<'local>,
) {
    unowned
        .with_env(|env| -> anyhow::Result<()> {
            let json = string(env, json)?;
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
        })
        .resolve::<IllegalState>();
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lavacrafter_connect_nativecore_NativeCore_snapshot<'local>(
    mut unowned: EnvUnowned<'local>,
    _: JClass<'local>,
    id: jlong,
) -> jstring {
    unowned
        .with_env(|env| -> anyhow::Result<jstring> {
            let mut map = registry()
                .lock()
                .map_err(|_| anyhow::anyhow!("native handle registry poisoned"))?;
            let core = map
                .get_mut(&id)
                .ok_or_else(|| anyhow::anyhow!("invalid native handle"))?;
            let snapshot = core.snapshot();
            drop(map);
            Ok(output(env, serde_json::to_string(&snapshot)?)?)
        })
        .resolve::<IllegalState>()
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lavacrafter_connect_nativecore_NativeCore_events<'local>(
    mut unowned: EnvUnowned<'local>,
    _: JClass<'local>,
    id: jlong,
) -> jstring {
    unowned
        .with_env(|env| -> anyhow::Result<jstring> {
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
            Ok(output(env, serde_json::to_string(&events)?)?)
        })
        .resolve::<IllegalState>()
}
