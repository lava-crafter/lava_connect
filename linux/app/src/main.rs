// SPDX-License-Identifier: GPL-3.0-or-later
#![deny(unsafe_op_in_unsafe_fn)]
mod platform;
mod settings;
mod theme;
use anyhow::{Context, Result, bail};
use lava_core::{Command, CoreConfig, CoreEvent, CoreHandle, DEFAULT_GROUP_ID, Snapshot};
use settings::UiSettings;
use slint::{ComponentHandle, Model, ModelRc, VecModel};
use std::{
    cell::RefCell,
    path::PathBuf,
    rc::Rc,
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
        mpsc,
    },
    time::Duration,
};
slint::include_modules!();

/// True when the user's locale prefers Chinese; used for text produced outside Slint
/// (tray menu, platform errors). Slint strings go through the bundled gettext catalog.
pub fn chinese() -> bool {
    locale().is_some_and(|l| l.starts_with("zh"))
}

/// Pick the English or Chinese variant of a message produced in Rust.
pub fn t(en: &'static str, zh: &'static str) -> &'static str {
    if chinese() { zh } else { en }
}

fn locale() -> Option<String> {
    ["LC_ALL", "LC_MESSAGES", "LANG", "LANGUAGE"]
        .iter()
        .filter_map(|key| std::env::var(key).ok())
        .find(|value| !value.is_empty() && value != "C" && value != "POSIX")
        .map(|value| {
            value
                .split([':', '.', '@'])
                .next()
                .unwrap_or_default()
                .to_owned()
        })
}

enum Intent {
    Core(Command),
    SendClipboard,
    SendText(String),
    Copy(String),
    Show,
    Pause,
    Quit,
}

/// Status line messages; the numbers match `App.status-text` in ui/app.slint.
#[derive(Clone, Copy)]
enum StatusKind {
    Discovering = 0,
    Paused = 1,
    Resumed = 2,
    Received = 3,
    Error = 4,
    ResumeFirst = 5,
    Copied = 6,
    Sent = 7,
}

#[derive(Clone)]
struct Status {
    kind: StatusKind,
    detail: String,
}

impl Status {
    fn new(kind: StatusKind) -> Self {
        Status {
            kind,
            detail: String::new(),
        }
    }
    fn error(error: impl ToString) -> Self {
        Status {
            kind: StatusKind::Error,
            detail: error.to_string(),
        }
    }
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
    fn icon_pixmap(&self) -> Vec<ksni::Icon> {
        vec![ksni::Icon {
            width: 32,
            height: 32,
            data: include_bytes!("../../packaging/desktop/lava-connect-tray.argb").to_vec(),
        }]
    }
    fn activate(&mut self, _: i32, _: i32) {
        let _ = self.tx.send(Intent::Show);
    }
    fn menu(&self) -> Vec<ksni::MenuItem<Self>> {
        use ksni::menu::StandardItem;
        [
            (t("Open Lava Connect", "打开 Lava Connect"), 0),
            (t("Send clipboard", "发送剪贴板"), 1),
            (t("Pause / resume sync", "暂停 / 恢复同步"), 2),
            (t("Quit", "退出"), 3),
        ]
        .into_iter()
        .map(|(label, action)| {
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

thread_local! {
    /// Last snapshot seen by the UI thread, so filtering and selection can re-render
    /// without waiting for the controller's next poll.
    static LAST_SNAPSHOT: RefCell<Option<Snapshot>> = const { RefCell::new(None) };
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
    // Must run after the first component exists. Prefer the full locale (zh_CN), then the
    // language alone; unknown locales keep the English source strings.
    if let Some(locale) = locale() {
        let language = locale.split('_').next().unwrap_or_default().to_owned();
        if slint::select_bundled_translation(&locale).is_err() {
            let _ = slint::select_bundled_translation(&language);
        }
    }
    let settings = Rc::new(RefCell::new(UiSettings::load()));
    apply_appearance(&ui, &settings.borrow());
    {
        let s = settings.borrow();
        let app = ui.global::<App>();
        app.set_version(env!("CARGO_PKG_VERSION").into());
        app.set_page(match s.page {
            1 => Page::Groups,
            2 => Page::Settings,
            _ => Page::Devices,
        });
        ui.window()
            .set_size(slint::LogicalSize::new(s.width as f32, s.height as f32));
    }
    wire_callbacks(&ui, &tx, &settings);

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
                Err(error) => eprintln!(
                    "{}: {error}",
                    t(
                        "System tray unavailable; closing the window stops syncing",
                        "系统托盘不可用；关闭窗口会停止同步"
                    )
                ),
            }
        });
    });
    let hide_weak = ui.as_weak();
    let hide_settings = settings.clone();
    let hide_tray = tray_available.clone();
    ui.global::<App>().on_hide_window(move || {
        if !hide_tray.load(Ordering::Relaxed) {
            return;
        }
        if let Some(ui) = hide_weak.upgrade() {
            remember_size(&ui, &hide_settings);
            let _ = ui.hide();
        }
    });
    let close_weak = ui.as_weak();
    let close_settings = settings.clone();
    let send = tx.clone();
    ui.window().on_close_requested(move || {
        if let Some(ui) = close_weak.upgrade() {
            remember_size(&ui, &close_settings);
        }
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
    let weak = ui.as_weak();
    let worker = std::thread::spawn(move || controller(&mut core, rx, Some(weak), stopped));
    ui.show()?;
    slint::run_event_loop_until_quit()?;
    remember_size(&ui, &settings);
    let _ = tx.send(Intent::Quit);
    let _ = worker.join();
    // The tray's runtime exits with the process; never block UI shutdown on D-Bus.
    drop(tray);
    Ok(())
}

fn remember_size(ui: &MainWindow, settings: &RefCell<UiSettings>) {
    let window = ui.window();
    if !window.is_visible() {
        return;
    }
    let size = window.size().to_logical(window.scale_factor());
    let mut s = settings.borrow_mut();
    s.width = size.width.round() as u32;
    s.height = size.height.round() as u32;
    s.save();
}

fn apply_appearance(ui: &MainWindow, s: &UiSettings) {
    let theme = ui.global::<Theme>();
    let index = s.accent_index.min(theme::ACCENT_PRESETS.len() - 1);
    let accent = theme::Rgb::hex(theme::ACCENT_PRESETS[index]);
    theme.set_light(theme::Scheme::new(false, accent).to_slint());
    theme.set_dark(theme::Scheme::new(true, accent).to_slint());
    theme.set_mode(s.theme_mode);
    theme.set_square(s.square);
    theme.set_accent_index(index as i32);
    theme.set_accents(ModelRc::new(VecModel::from(
        theme::ACCENT_PRESETS
            .iter()
            .map(|&hex| theme::slint_color(theme::Rgb::hex(hex)))
            .collect::<Vec<_>>(),
    )));
}

fn wire_callbacks(ui: &MainWindow, tx: &mpsc::Sender<Intent>, settings: &Rc<RefCell<UiSettings>>) {
    let app = ui.global::<App>();
    let core = |command: Command| Intent::Core(command);

    let send = tx.clone();
    app.on_device_action(move |action, id, code| {
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
        let _ = send.send(core(command));
    });
    let send = tx.clone();
    app.on_capability(move |id, capability, enabled| {
        let _ = send.send(core(Command::SetCapability {
            device_id: id.to_string(),
            capability,
            enabled,
        }));
    });
    let send = tx.clone();
    app.on_group_action(move |action, name, group_id| {
        let command = match action.as_str() {
            "create" => Command::CreateGroup {
                name: name.trim().to_string(),
            },
            "join" => Command::JoinGroup {
                group_id: group_id.trim().to_string(),
                name: name.trim().to_string(),
            },
            "select" => Command::SelectGroup {
                group_id: group_id.to_string(),
            },
            "delete" => Command::DeleteGroup {
                group_id: group_id.to_string(),
            },
            _ => return,
        };
        let _ = send.send(core(command));
    });
    let send = tx.clone();
    app.on_group_member(move |group_id, device_id, enabled| {
        let _ = send.send(core(Command::SetGroupMember {
            group_id: group_id.to_string(),
            device_id: device_id.to_string(),
            enabled,
        }));
    });
    let send = tx.clone();
    app.on_send_clipboard(move || {
        let _ = send.send(Intent::SendClipboard);
    });
    let send = tx.clone();
    app.on_send_text(move |text| {
        let _ = send.send(Intent::SendText(text.to_string()));
    });
    let send = tx.clone();
    app.on_toggle_pause(move || {
        let _ = send.send(Intent::Pause);
    });
    let send = tx.clone();
    app.on_copy(move |text| {
        let _ = send.send(Intent::Copy(text.to_string()));
    });
    let send = tx.clone();
    app.on_quit(move || {
        let _ = send.send(Intent::Quit);
    });

    let weak = ui.as_weak();
    app.on_select_device(move |id| {
        let Some(ui) = weak.upgrade() else { return };
        ui.global::<App>().set_selected_device_id(id);
        render_devices(&ui);
    });
    let weak = ui.as_weak();
    app.on_filter_changed(move |_| {
        if let Some(ui) = weak.upgrade() {
            render_devices(&ui);
        }
    });
    let weak = ui.as_weak();
    app.on_move_selection(move |delta| {
        let Some(ui) = weak.upgrade() else { return };
        let app = ui.global::<App>();
        let rows = app.get_devices();
        if rows.row_count() == 0 {
            return;
        }
        let current = app.get_selected_device_id();
        let index = rows.iter().position(|row| row.id == current);
        let next = match index {
            None if delta > 0 => 0,
            None => rows.row_count() - 1,
            Some(i) => (i as i32 + delta).clamp(0, rows.row_count() as i32 - 1) as usize,
        };
        if let Some(row) = rows.row_data(next) {
            app.set_selected_device_id(row.id);
            render_devices(&ui);
        }
    });
    let weak = ui.as_weak();
    let send = tx.clone();
    app.on_activate_selected(move || {
        let Some(ui) = weak.upgrade() else { return };
        let app = ui.global::<App>();
        if !app.get_has_selected_device() {
            return;
        }
        let device = app.get_selected_device();
        if device.state == "pairing" && !device.code.is_empty() {
            app.set_dialog_device(device);
            app.set_dialog(DialogKind::ConfirmPairing);
        } else if !device.trusted && device.state == "available" {
            let _ = send.send(Intent::Core(Command::Pair {
                device_id: device.id.to_string(),
            }));
        }
    });

    let weak = ui.as_weak();
    let s = settings.clone();
    app.on_appearance_changed(move || {
        let Some(ui) = weak.upgrade() else { return };
        let theme = ui.global::<Theme>();
        let mut settings = s.borrow_mut();
        settings.theme_mode = theme.get_mode();
        settings.accent_index = theme.get_accent_index().max(0) as usize;
        settings.square = theme.get_square();
        settings.save();
        apply_appearance(&ui, &settings);
    });
    let weak = ui.as_weak();
    let s = settings.clone();
    app.on_page_changed(move || {
        let Some(ui) = weak.upgrade() else { return };
        let mut settings = s.borrow_mut();
        settings.page = match ui.global::<App>().get_page() {
            Page::Devices => 0,
            Page::Groups => 1,
            Page::Settings => 2,
        };
        settings.save();
    });
}

fn controller(
    core: &mut CoreHandle,
    rx: mpsc::Receiver<Intent>,
    ui: Option<slint::Weak<MainWindow>>,
    stopped: Arc<AtomicBool>,
) {
    let mut paused = false;
    let mut previous: Option<(String, Vec<u8>)> = None;
    let mut status = Status::new(StatusKind::Discovering);
    while !stopped.load(Ordering::Relaxed) {
        match rx.recv_timeout(Duration::from_millis(600)) {
            Ok(Intent::Quit) => break,
            Ok(Intent::Pause) => {
                paused = !paused;
                status = Status::new(if paused {
                    StatusKind::Paused
                } else {
                    StatusKind::Resumed
                });
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
                    status = Status::error(error);
                }
            }
            Ok(Intent::SendClipboard) => match platform::read_clipboard() {
                Ok((mime_type, data)) if !paused => {
                    status = match core.command(Command::SendClipboard { mime_type, data }) {
                        Ok(()) => Status::new(StatusKind::Sent),
                        Err(error) => Status::error(error),
                    };
                }
                Ok(_) => status = Status::new(StatusKind::ResumeFirst),
                Err(error) => status = Status::error(error),
            },
            Ok(Intent::SendText(text)) if !paused => {
                status = match core.command(Command::SendClipboard {
                    mime_type: "text/plain".into(),
                    data: text.into_bytes(),
                }) {
                    Ok(()) => Status::new(StatusKind::Sent),
                    Err(error) => Status::error(error),
                };
            }
            Ok(Intent::SendText(_)) => status = Status::new(StatusKind::ResumeFirst),
            Ok(Intent::Copy(text)) => {
                let data = text.into_bytes();
                status = match platform::write_clipboard("text/plain", &data) {
                    Ok(()) => {
                        // Copying an ID is a local action; don't sync it as a new clipboard item.
                        previous = Some(("text/plain".into(), data));
                        Status::new(StatusKind::Copied)
                    }
                    Err(error) => Status::error(error),
                };
            }
            Err(mpsc::RecvTimeoutError::Disconnected) if ui.is_some() => break,
            _ => {}
        }
        for event in core.drain_events() {
            match event {
                CoreEvent::ClipboardReceived {
                    mime_type,
                    data,
                    content_hash,
                    group_id,
                    device_id,
                } if !paused => {
                    let current = core.snapshot();
                    if current.active_group_id != group_id
                        || !current
                            .devices
                            .iter()
                            .any(|d| d.id == device_id && d.trusted)
                        || !current
                            .groups
                            .iter()
                            .any(|g| g.id == group_id && g.members.contains(&device_id))
                    {
                        continue;
                    }
                    if let Err(error) = core.command(Command::MarkClipboardApplied { content_hash })
                    {
                        status = Status::error(error);
                        continue;
                    }
                    match platform::write_clipboard(&mime_type, &data) {
                        Ok(()) => {
                            previous = Some((mime_type, data));
                            status = Status::new(StatusKind::Received);
                        }
                        Err(error) => status = Status::error(error),
                    }
                }
                CoreEvent::Error { message } => status = Status::error(message),
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
                            status = Status::error(error);
                        }
                    }
                }
                Err(error) => status = Status::error(error),
            }
        }
        if let Some(ui) = &ui {
            let message = status.clone();
            let _ = ui.upgrade_in_event_loop(move |ui| update_ui(&ui, snapshot, &message, paused));
        }
    }
    core.stop();
    stopped.store(true, Ordering::Relaxed);
    if ui.is_some() {
        let _ = slint::quit_event_loop();
    }
}

fn group_name(id: &str, name: &str) -> String {
    if id == DEFAULT_GROUP_ID {
        t("Default", "默认").into()
    } else {
        name.into()
    }
}

fn update_ui(ui: &MainWindow, snapshot: Snapshot, status: &Status, paused: bool) {
    let app = ui.global::<App>();
    app.set_identity(snapshot.device_id.clone().into());
    app.set_local_name(snapshot.device_name.clone().into());
    app.set_paused(paused);
    app.set_status_kind(status.kind as i32);
    app.set_status_detail(status.detail.clone().into());
    app.set_connected_count(
        snapshot
            .devices
            .iter()
            .filter(|d| d.state == "connected")
            .count() as i32,
    );
    app.set_active_group_id(snapshot.active_group_id.clone().into());
    app.set_active_group_name(
        snapshot
            .groups
            .iter()
            .find(|g| g.id == snapshot.active_group_id)
            .map(|g| group_name(&g.id, &g.name))
            .unwrap_or_default()
            .into(),
    );
    app.set_groups(ModelRc::new(VecModel::from(
        snapshot
            .groups
            .iter()
            .map(|g| GroupRow {
                id: g.id.clone().into(),
                name: group_name(&g.id, &g.name).into(),
                members: g.members.len() as i32,
            })
            .collect::<Vec<_>>(),
    )));
    LAST_SNAPSHOT.with(|last| *last.borrow_mut() = Some(snapshot));
    render_devices(ui);
}

fn device_row(d: &lava_core::Device) -> DeviceRow {
    DeviceRow {
        id: d.id.clone().into(),
        name: d.name.clone().into(),
        platform: d.platform.clone().into(),
        state: d.state.clone().into(),
        trusted: d.trusted,
        code: d.pairing_code.clone().unwrap_or_default().into(),
        text_enabled: d.text_enabled,
        image_enabled: d.image_enabled,
        in_group: d.sync_in_group,
        group_compatible: d.group_compatible,
    }
}

/// Applies the filter to the device list and refreshes the selected device's details.
fn render_devices(ui: &MainWindow) {
    LAST_SNAPSHOT.with(|last| {
        if let Some(snapshot) = last.borrow().as_ref() {
            render_snapshot_devices(ui, snapshot);
        }
    });
}

fn render_snapshot_devices(ui: &MainWindow, snapshot: &Snapshot) {
    let app = ui.global::<App>();
    let filter = app.get_filter_text().to_lowercase();
    let rows = snapshot
        .devices
        .iter()
        .filter(|d| {
            filter.is_empty()
                || d.name.to_lowercase().contains(&filter)
                || d.id.to_lowercase().contains(&filter)
                || d.platform.to_lowercase().contains(&filter)
        })
        .map(device_row)
        .collect::<Vec<_>>();
    app.set_devices(ModelRc::new(VecModel::from(rows)));
    let selected = app.get_selected_device_id();
    match snapshot.devices.iter().find(|d| d.id == selected.as_str()) {
        Some(device) => {
            app.set_selected_device(device_row(device));
            app.set_has_selected_device(true);
        }
        None => app.set_has_selected_device(false),
    }
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
