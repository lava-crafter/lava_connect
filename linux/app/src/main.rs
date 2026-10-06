// SPDX-License-Identifier: GPL-3.0-or-later
#![deny(unsafe_op_in_unsafe_fn)]
mod platform;
use anyhow::{Context, Result, bail};
use lava_core::{Command, CoreConfig, CoreEvent, CoreHandle, Snapshot};
use slint::{ComponentHandle, ModelRc, VecModel};
use std::{
    path::PathBuf,
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
        mpsc,
    },
    time::Duration,
};
slint::include_modules!();

enum Intent {
    Core(Command),
    SendClipboard,
    Show,
    Pause,
    Quit,
}
struct Tray {
    tx: mpsc::Sender<Intent>,
}
impl ksni::Tray for Tray {
    fn id(&self) -> String {
        "lava-connect".into()
    }
    fn title(&self) -> String {
        "Lava Connect".into()
    }
    fn icon_name(&self) -> String {
        "lava-connect".into()
    }
    fn activate(&mut self, _: i32, _: i32) {
        let _ = self.tx.send(Intent::Show);
    }
    fn menu(&self) -> Vec<ksni::MenuItem<Self>> {
        use ksni::menu::StandardItem;
        [
            ("打开 Lava Connect", Intent::Show),
            ("发送剪贴板", Intent::SendClipboard),
            ("暂停 / 恢复同步", Intent::Pause),
            ("退出", Intent::Quit),
        ]
        .into_iter()
        .map(|(label, intent)| {
            let action = match intent {
                Intent::Show => 0,
                Intent::SendClipboard => 1,
                Intent::Pause => 2,
                _ => 3,
            };
            StandardItem {
                label: label.into(),
                activate: Box::new(move |tray: &mut Self| {
                    let _ = tray.tx.send(match action {
                        0 => Intent::Show,
                        1 => Intent::SendClipboard,
                        2 => Intent::Pause,
                        _ => Intent::Quit,
                    });
                }),
                ..Default::default()
            }
            .into()
        })
        .collect()
    }
}

fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
        .init();
    let (config, headless) = arguments()?;
    let mut core = CoreHandle::start(config)?;
    let (tx, rx) = mpsc::channel();
    let signal = tx.clone();
    ctrlc::set_handler(move || {
        let _ = signal.send(Intent::Quit);
    })?;
    let stopped = Arc::new(AtomicBool::new(false));
    if headless {
        controller(&mut core, rx, None, stopped);
        return Ok(());
    }
    let ui = MainWindow::new()?;
    let weak = ui.as_weak();
    let send = tx.clone();
    ui.on_action(move |action, id, code| {
        let device_id = id.to_string();
        let command = match action.as_str() {
            "confirm" => Command::ConfirmPair {
                device_id,
                pairing_code: code.to_string(),
            },
            "cancel" => Command::CancelPair { device_id },
            "unpair" => Command::Unpair { device_id },
            _ => Command::Pair { device_id },
        };
        let _ = send.send(Intent::Core(command));
    });
    let send = tx.clone();
    ui.on_capability(move |id, capability, enabled| {
        let _ = send.send(Intent::Core(Command::SetCapability {
            device_id: id.to_string(),
            capability,
            enabled,
        }));
    });
    let send = tx.clone();
    ui.on_send_clipboard(move || {
        let _ = send.send(Intent::SendClipboard);
    });
    let send = tx.clone();
    ui.on_quit(move || {
        let _ = send.send(Intent::Quit);
    });
    let tray_available = Arc::new(AtomicBool::new(false));
    let available = tray_available.clone();
    let send = tx.clone();
    let tray = std::thread::spawn(move || {
        use ksni::TrayMethods;
        let runtime = tokio::runtime::Runtime::new().expect("tray runtime");
        runtime.block_on(async {
            match (Tray { tx: send }).spawn().await {
                Ok(handle) => {
                    available.store(true, Ordering::Relaxed);
                    std::future::pending::<()>().await;
                    drop(handle);
                }
                Err(error) => eprintln!("系统托盘不可用：{error}；关闭窗口会停止同步"),
            }
        });
    });
    let close_weak = ui.as_weak();
    let send = tx.clone();
    ui.window().on_close_requested(move || {
        if tray_available.load(Ordering::Relaxed) {
            if let Some(ui) = close_weak.upgrade() {
                let _ = ui.hide();
            }
            slint::CloseRequestResponse::KeepWindowShown
        } else {
            let _ = send.send(Intent::Quit);
            slint::CloseRequestResponse::HideWindow
        }
    });
    let worker = std::thread::spawn(move || controller(&mut core, rx, Some(weak), stopped));
    ui.show()?;
    slint::run_event_loop_until_quit()?;
    let _ = tx.send(Intent::Quit);
    let _ = worker.join();
    // The tray's runtime exits with the process; never block UI shutdown on D-Bus.
    drop(tray);
    Ok(())
}

fn controller(
    core: &mut CoreHandle,
    rx: mpsc::Receiver<Intent>,
    ui: Option<slint::Weak<MainWindow>>,
    stopped: Arc<AtomicBool>,
) {
    let mut paused = false;
    let mut previous: Option<(String, Vec<u8>)> = None;
    let mut status = "正在发现同一局域网中的设备…".to_string();
    while !stopped.load(Ordering::Relaxed) {
        match rx.recv_timeout(Duration::from_millis(600)) {
            Ok(Intent::Quit) => break,
            Ok(Intent::Pause) => {
                paused = !paused;
                status = if paused {
                    "剪贴板同步已暂停"
                } else {
                    "剪贴板同步已恢复"
                }
                .into();
            }
            Ok(Intent::Show) => {
                if let Some(ui) = &ui {
                    let _ = ui.upgrade_in_event_loop(|ui| {
                        let _ = ui.show();
                    });
                }
            }
            Ok(Intent::Core(command)) => {
                if let Err(error) = core.command(command) {
                    status = error.to_string();
                }
            }
            Ok(Intent::SendClipboard) => match platform::read_clipboard() {
                Ok((mime_type, data)) if !paused => {
                    if let Err(error) = core.command(Command::SendClipboard { mime_type, data }) {
                        status = error.to_string();
                    }
                }
                Ok(_) => status = "请先恢复剪贴板同步".into(),
                Err(error) => status = error.to_string(),
            },
            Err(mpsc::RecvTimeoutError::Disconnected) if ui.is_some() => break,
            _ => {}
        }
        for event in core.drain_events() {
            match event {
                CoreEvent::ClipboardReceived {
                    mime_type,
                    data,
                    content_hash,
                    ..
                } if !paused => {
                    if let Err(error) = core.command(Command::MarkClipboardApplied { content_hash })
                    {
                        status = error.to_string();
                        continue;
                    }
                    match platform::write_clipboard(&mime_type, &data) {
                        Ok(()) => {
                            previous = Some((mime_type, data));
                            status = "已接收远端剪贴板".into();
                        }
                        Err(error) => status = error.to_string(),
                    }
                }
                CoreEvent::Error { message } => status = message,
                _ => {}
            }
        }
        let snapshot = core.snapshot();
        if !paused && snapshot.devices.iter().any(|d| d.state == "connected") {
            match platform::read_clipboard() {
                Ok((mime, data)) => {
                    if previous.as_ref() != Some(&(mime.clone(), data.clone())) {
                        previous = Some((mime.clone(), data.clone()));
                        if let Err(error) = core.command(Command::SendClipboard {
                            mime_type: mime,
                            data,
                        }) {
                            status = error.to_string();
                        }
                    }
                }
                Err(error) => status = error.to_string(),
            }
        }
        if let Some(ui) = &ui {
            let message = status.clone();
            let _ = ui.upgrade_in_event_loop(move |ui| update_ui(&ui, snapshot, &message));
        }
    }
    core.stop();
    stopped.store(true, Ordering::Relaxed);
    if ui.is_some() {
        let _ = slint::quit_event_loop();
    }
}
fn update_ui(ui: &MainWindow, snapshot: Snapshot, status: &str) {
    ui.set_identity(snapshot.device_id.into());
    ui.set_status(status.into());
    let rows = snapshot
        .devices
        .into_iter()
        .map(|d| DeviceRow {
            id: d.id.into(),
            name: d.name.into(),
            state: d.state.into(),
            trusted: d.trusted,
            code: d.pairing_code.unwrap_or_default().into(),
            text_enabled: d.text_enabled,
            image_enabled: d.image_enabled,
        })
        .collect::<Vec<_>>();
    ui.set_devices(ModelRc::new(VecModel::from(rows)));
}
fn arguments() -> Result<(CoreConfig, bool)> {
    let home = std::env::var_os("HOME").context("HOME is not set")?;
    let data = std::env::var_os("XDG_DATA_HOME")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from(home).join(".local/share"));
    let mut config = CoreConfig {
        storage_dir: data.join("lava-connect"),
        device_name: std::env::var("HOSTNAME").unwrap_or_else(|_| "Lava Desktop".into()),
        platform: "linux".into(),
        listen_port: 0,
        storage_key: None,
        discovery: true,
    };
    let mut headless = false;
    let mut args = std::env::args().skip(1);
    while let Some(arg) = args.next() {
        match arg.as_str() {
            "--headless" => headless = true,
            "--no-discovery" => config.discovery = false,
            "--data-dir" => {
                config.storage_dir = args.next().context("missing --data-dir value")?.into()
            }
            "--name" => config.device_name = args.next().context("missing --name value")?,
            "--port" => {
                config.listen_port = args.next().context("missing --port value")?.parse()?
            }
            "--help" | "-h" => {
                println!(
                    "lava-connect [--headless] [--name NAME] [--data-dir PATH] [--port PORT] [--no-discovery]"
                );
                std::process::exit(0);
            }
            _ => bail!("unknown argument: {arg}"),
        }
    }
    Ok((config, headless))
}
