// SPDX-License-Identifier: GPL-3.0-or-later
use crate::{
    Command, CoreEvent, Device, Snapshot,
    identity::{Identity, TrustStore, TrustedDevice, fingerprint},
    security,
};
use anyhow::{Context, Result, bail, ensure};
use lava_protocol::{self as proto, Message, envelope::Body};
use mdns_sd::{ServiceDaemon, ServiceEvent, ServiceInfo};
use sha2::{Digest, Sha256};
use std::{
    collections::{BTreeMap, HashSet, VecDeque},
    net::{IpAddr, SocketAddr},
    path::PathBuf,
    sync::{Arc, Mutex, mpsc},
    thread,
    time::{Duration, Instant},
};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    sync::mpsc as async_mpsc,
};

const SERVICE_TYPE: &str = "_lava-connect._udp.local.";
const MAX_PEERS: usize = 16;

pub struct CoreConfig {
    pub storage_dir: PathBuf,
    pub device_name: String,
    pub platform: String,
    pub listen_port: u16,
    pub storage_key: Option<String>,
    pub discovery: bool,
}

pub struct CoreHandle {
    commands: async_mpsc::Sender<Command>,
    shutdown: Option<tokio::sync::oneshot::Sender<()>>,
    snapshot: Arc<Mutex<Snapshot>>,
    events: mpsc::Receiver<CoreEvent>,
    thread: Option<thread::JoinHandle<()>>,
    port: u16,
    lock: Option<std::fs::File>,
}

impl CoreHandle {
    pub fn start(config: CoreConfig) -> Result<Self> {
        use fs2::FileExt;
        std::fs::create_dir_all(&config.storage_dir)?;
        let mut options = std::fs::OpenOptions::new();
        options.create(true).read(true).write(true);
        #[cfg(unix)]
        {
            use std::os::unix::fs::OpenOptionsExt;
            options.mode(0o600);
        }
        let lock = options.open(config.storage_dir.join(".lock"))?;
        lock.try_lock_exclusive()
            .context("another Lava Connect process is using this identity")?;
        ensure!(
            matches!(config.platform.as_str(), "android" | "linux"),
            "unsupported platform"
        );
        ensure!(
            !config.device_name.trim().is_empty() && config.device_name.len() <= 128,
            "device name must be 1–128 bytes"
        );
        let identity = Identity::load(&config.storage_dir, config.storage_key.as_deref())?;
        let trust = TrustStore::load(&config.storage_dir)?;
        let snapshot = Arc::new(Mutex::new(Snapshot {
            device_id: identity.id(),
            device_name: config.device_name.clone(),
            ..Snapshot::default()
        }));
        let (commands_tx, commands) = async_mpsc::channel(16);
        let (events_tx, events) = mpsc::sync_channel(8);
        let (shutdown_tx, shutdown) = tokio::sync::oneshot::channel();
        let (ready_tx, ready) = mpsc::sync_channel(1);
        let shared = snapshot.clone();
        let worker = thread::Builder::new()
            .name("lava-core".into())
            .spawn(move || {
                let outcome = (|| -> Result<()> {
                    let rt = tokio::runtime::Builder::new_multi_thread()
                        .worker_threads(2)
                        .enable_all()
                        .build()?;
                    rt.block_on(async {
                        let mut actor =
                            Actor::new(config, identity, trust, shared.clone(), events_tx.clone())?;
                        let _ = ready_tx.send(Ok(actor.endpoint.local_addr()?.port()));
                        actor.run(commands, shutdown).await
                    })
                })();
                if let Err(error) = outcome {
                    let message = format!("Connection service failed: {error}");
                    let _ = ready_tx.send(Err(message.clone()));
                    shared.lock().unwrap().error = Some(message.clone());
                    let _ = events_tx.try_send(CoreEvent::Error { message });
                }
                shared.lock().unwrap().running = false;
            })?;
        let port = ready
            .recv()
            .context("core failed to start")?
            .map_err(anyhow::Error::msg)?;
        Ok(Self {
            commands: commands_tx,
            shutdown: Some(shutdown_tx),
            snapshot,
            events,
            thread: Some(worker),
            port,
            lock: Some(lock),
        })
    }
    pub fn command(&self, command: Command) -> Result<()> {
        self.commands
            .try_send(command)
            .map_err(|_| anyhow::anyhow!("service is stopped or busy"))
    }
    pub fn snapshot(&self) -> Snapshot {
        self.snapshot.lock().unwrap().clone()
    }
    pub fn drain_events(&self) -> Vec<CoreEvent> {
        self.events.try_iter().collect()
    }
    pub fn next_event(&self) -> Option<CoreEvent> {
        self.events.try_recv().ok()
    }
    pub fn listen_port(&self) -> u16 {
        self.port
    }
    pub fn stop(&mut self) {
        if let Some(tx) = self.shutdown.take() {
            let _ = tx.send(());
        }
        if let Some(worker) = self.thread.take() {
            let _ = worker.join();
        }
        self.lock.take();
    }
    /// Explicit address injection for tests and networks where multicast is unavailable.
    pub fn connect_address(&self, device_id: String, address: SocketAddr) -> Result<()> {
        // Routed through the same authentication path as discovered addresses.
        self.commands
            .try_send(Command::ConnectAddress {
                device_id,
                address: address.to_string(),
            })
            .map_err(|_| anyhow::anyhow!("service is stopped or busy"))
    }
}
impl Drop for CoreHandle {
    fn drop(&mut self) {
        self.stop();
    }
}

struct Advertisement {
    name: String,
    platform: String,
    addresses: Vec<SocketAddr>,
}
struct Session {
    id: String,
    serial: uuid::Uuid,
    info: proto::DeviceInfo,
    connection: quinn::Connection,
    sender: async_mpsc::Sender<proto::Envelope>,
    code: String,
    pairing: bool,
    local_confirm: bool,
    remote_confirm: bool,
    remote_ready: bool,
    remote_allowed: Vec<i32>,
    started: Instant,
}
impl Session {
    fn send(&self, body: Body) -> Result<()> {
        self.sender
            .try_send(proto::Envelope { body: Some(body) })
            .map_err(|_| anyhow::anyhow!("peer is busy or disconnected"))
    }
    fn close(&self) {
        self.connection.close(0u8.into(), b"session closed");
    }
}
enum NetworkEvent {
    Session(Box<Session>),
    Wire {
        id: String,
        serial: uuid::Uuid,
        message: proto::Envelope,
    },
    Closed {
        id: String,
        serial: uuid::Uuid,
    },
    DialFailed {
        id: String,
    },
}

struct Actor {
    config: CoreConfig,
    id: String,
    hello: proto::DeviceInfo,
    endpoint: quinn::Endpoint,
    trust: TrustStore,
    advertisements: BTreeMap<String, Advertisement>,
    sessions: BTreeMap<String, Session>,
    dialing: HashSet<String>,
    pending_pair: HashSet<String>,
    // Bounded history covers both received clipboard IDs and local watcher echoes.
    hashes: VecDeque<(Vec<u8>, Instant)>,
    ids: VecDeque<String>,
    snapshot: Arc<Mutex<Snapshot>>,
    events: mpsc::SyncSender<CoreEvent>,
    network_tx: async_mpsc::Sender<NetworkEvent>,
    network_rx: async_mpsc::Receiver<NetworkEvent>,
    mdns: Option<ServiceDaemon>,
    mdns_rx: Option<mdns_sd::Receiver<ServiceEvent>>,
    service_name: Option<String>,
}

impl Actor {
    fn new(
        config: CoreConfig,
        identity: Identity,
        trust: TrustStore,
        snapshot: Arc<Mutex<Snapshot>>,
        events: mpsc::SyncSender<CoreEvent>,
    ) -> Result<Self> {
        let id = identity.id();
        let endpoint = security::endpoint(&identity, config.listen_port)?;
        let hello = proto::DeviceInfo {
            id: id.clone(),
            name: config.device_name.clone(),
            platform: platform_number(&config.platform),
            version: env!("CARGO_PKG_VERSION").into(),
            capabilities: vec![1, 2],
            protocol_version: proto::PROTOCOL_VERSION,
        };
        let (network_tx, network_rx) = async_mpsc::channel(32);
        let mut actor = Self {
            config,
            id,
            hello,
            endpoint,
            trust,
            advertisements: BTreeMap::new(),
            sessions: BTreeMap::new(),
            dialing: HashSet::new(),
            pending_pair: HashSet::new(),
            hashes: VecDeque::new(),
            ids: VecDeque::new(),
            snapshot,
            events,
            network_tx,
            network_rx,
            mdns: None,
            mdns_rx: None,
            service_name: None,
        };
        if actor.config.discovery {
            let mdns = ServiceDaemon::new()?;
            let port = actor.endpoint.local_addr()?.port();
            let props = [
                ("id", actor.id.as_str()),
                ("name", actor.config.device_name.as_str()),
                ("platform", actor.config.platform.as_str()),
                ("version", "1"),
            ];
            let service = ServiceInfo::new(
                SERVICE_TYPE,
                &actor.id[..16],
                &format!("lava-{}.local.", &actor.id[..16]),
                "",
                port,
                &props[..],
            )?
            .enable_addr_auto();
            actor.service_name = Some(service.get_fullname().to_owned());
            mdns.register(service)?;
            actor.mdns_rx = Some(mdns.browse(SERVICE_TYPE)?);
            actor.mdns = Some(mdns);
        }
        actor.snapshot.lock().unwrap().running = true;
        actor.refresh();
        Ok(actor)
    }
    async fn run(
        &mut self,
        mut commands: async_mpsc::Receiver<Command>,
        mut shutdown: tokio::sync::oneshot::Receiver<()>,
    ) -> Result<()> {
        let mut tick = tokio::time::interval(Duration::from_secs(2));
        let incoming_limit = Arc::new(tokio::sync::Semaphore::new(MAX_PEERS));
        loop {
            tokio::select! {
                _ = &mut shutdown => break,
                command = commands.recv() => match command { Some(command) => { if let Err(error) = self.command(command) { self.error(error.to_string()); } }, None => break },
                incoming = self.endpoint.accept() => if let Some(incoming) = incoming {
                    if let Ok(permit) = incoming_limit.clone().try_acquire_owned() {
                        let hello = self.hello.clone();
                        let tx = self.network_tx.clone();
                        tokio::spawn(async move {
                            let _permit = permit;
                            if let Ok(Ok(connection)) = tokio::time::timeout(Duration::from_secs(10), incoming).await
                                && let Err(error) = serve_connection(connection, hello, None, false, tx).await { tracing::debug!(%error, "incoming session ended"); }
                        });
                    } else { incoming.refuse(); }
                },
                Some(event) = self.network_rx.recv() => { if let Err(error) = self.network_event(event) { self.error(error.to_string()); } },
                _ = tick.tick() => self.tick(),
            }
        }
        for session in self.sessions.values() {
            session.close();
        }
        self.endpoint.close(0u8.into(), b"service stopped");
        if let Some(mdns) = &self.mdns {
            if let Some(name) = &self.service_name {
                let _ = mdns.unregister(name);
            }
            let _ = mdns.shutdown();
        }
        self.snapshot.lock().unwrap().running = false;
        self.refresh();
        Ok(())
    }
    fn error(&self, message: String) {
        tracing::warn!(%message, "core error");
        self.snapshot.lock().unwrap().error = Some(message.clone());
        let _ = self.events.try_send(CoreEvent::Error { message });
    }
    fn refresh(&self) {
        let mut devices = BTreeMap::new();
        for (id, trusted) in &self.trust.0 {
            devices.insert(
                id.clone(),
                Device {
                    id: id.clone(),
                    name: trusted.name.clone(),
                    platform: trusted.platform.clone(),
                    state: "offline".into(),
                    trusted: true,
                    pairing_code: None,
                    text_enabled: trusted.allowed.contains(&1),
                    image_enabled: trusted.allowed.contains(&2),
                },
            );
        }
        for (id, ad) in &self.advertisements {
            let device = devices.entry(id.clone()).or_insert(Device {
                id: id.clone(),
                name: ad.name.clone(),
                platform: ad.platform.clone(),
                state: "available".into(),
                trusted: false,
                pairing_code: None,
                text_enabled: true,
                image_enabled: true,
            });
            device.state = if self.dialing.contains(id) {
                "connecting"
            } else {
                "available"
            }
            .into();
        }
        for (id, session) in &self.sessions {
            let trusted = self.trust.0.get(id);
            let allowed = trusted.map(|t| t.allowed.as_slice()).unwrap_or(&[1, 2]);
            devices.insert(
                id.clone(),
                Device {
                    id: id.clone(),
                    name: session.info.name.clone(),
                    platform: platform_name(session.info.platform).into(),
                    state: if session.pairing {
                        "pairing"
                    } else if trusted.is_some() && session.remote_ready {
                        "connected"
                    } else {
                        "connecting"
                    }
                    .into(),
                    trusted: trusted.is_some(),
                    pairing_code: session.pairing.then(|| session.code.clone()),
                    text_enabled: allowed.contains(&1),
                    image_enabled: allowed.contains(&2),
                },
            );
        }
        let devices: Vec<_> = devices.into_values().collect();
        let mut snapshot = self.snapshot.lock().unwrap();
        if snapshot.devices != devices {
            snapshot.devices = devices;
            let _ = self.events.try_send(CoreEvent::Changed);
        }
    }
    fn dial(&mut self, id: &str) -> Result<()> {
        if self.sessions.contains_key(id) || self.dialing.contains(id) {
            return Ok(());
        }
        ensure!(
            self.sessions.len() + self.dialing.len() < MAX_PEERS,
            "too many connections"
        );
        let address = *self
            .advertisements
            .get(id)
            .and_then(|a| a.addresses.first())
            .context("device is not currently available")?;
        let connecting = self.endpoint.connect(address, "lava-connect.local")?;
        self.dialing.insert(id.into());
        let expected = id.to_string();
        let hello = self.hello.clone();
        let tx = self.network_tx.clone();
        tokio::spawn(async move {
            let result = async {
                let connection =
                    tokio::time::timeout(Duration::from_secs(10), connecting).await??;
                serve_connection(connection, hello, Some(expected.clone()), true, tx.clone()).await
            }
            .await;
            if let Err(error) = result {
                tracing::debug!(%error, "outgoing session ended");
            }
            let _ = tx.send(NetworkEvent::DialFailed { id: expected }).await;
        });
        self.refresh();
        Ok(())
    }
    fn begin_pair(&mut self, id: &str) -> Result<()> {
        let session = self.sessions.get_mut(id).context("not connected")?;
        if !session.pairing {
            session.pairing = true;
            session.started = Instant::now();
            session.local_confirm = false;
            session.remote_confirm = false;
            session.send(Body::PairRequest(proto::PairRequest {}))?;
        }
        self.refresh();
        Ok(())
    }
    fn finish_pair(&mut self, id: &str) -> Result<()> {
        let session = self.sessions.get(id).context("not connected")?;
        if session.pairing && session.local_confirm && session.remote_confirm {
            // Repairing a peer's lost trust must not re-enable capabilities the
            // local user previously disabled.
            let allowed = self
                .trust
                .0
                .get(id)
                .map(|t| t.allowed.clone())
                .unwrap_or_else(|| vec![1, 2]);
            let old = self.trust.0.insert(
                id.to_string(),
                TrustedDevice {
                    name: session.info.name.clone(),
                    platform: platform_name(session.info.platform).into(),
                    allowed,
                },
            );
            if let Err(error) = self.trust.save(&self.config.storage_dir) {
                if let Some(old) = old {
                    self.trust.0.insert(id.to_string(), old);
                } else {
                    self.trust.0.remove(id);
                }
                session.close();
                return Err(error);
            }
            session.send(Body::Ready(proto::Ready {
                allowed: self.trust.0[id].allowed.clone(),
            }))?;
            let session = self.sessions.get_mut(id).unwrap();
            session.pairing = false;
        }
        self.refresh();
        Ok(())
    }
    fn command(&mut self, command: Command) -> Result<()> {
        match command {
            Command::MarkClipboardApplied { content_hash } => {
                let hash = hex::decode(content_hash)?;
                ensure!(hash.len() == 32, "invalid clipboard hash");
                // Android may apply an event hours after receipt. Refresh echo
                // suppression at actual platform application, not just arrival.
                self.hashes.retain(|(old, _)| *old != hash);
                self.hashes.push_back((hash, Instant::now()));
                while self.hashes.len() > 256 {
                    self.hashes.pop_front();
                }
            }
            Command::ConnectAddress { device_id, address } => {
                ensure!(
                    valid_id(&device_id) && device_id != self.id,
                    "invalid device ID"
                );
                let address: SocketAddr = address.parse()?;
                ensure!(address.is_ipv4(), "v0.1 address must be IPv4");
                self.advertisements.insert(
                    device_id,
                    Advertisement {
                        name: "Manual address".into(),
                        platform: "unknown".into(),
                        addresses: vec![address],
                    },
                );
            }
            Command::Pair { device_id } => {
                if self.sessions.contains_key(&device_id) {
                    self.begin_pair(&device_id)?;
                } else {
                    self.dial(&device_id)?;
                    self.pending_pair.insert(device_id);
                }
            }
            Command::ConfirmPair {
                device_id,
                pairing_code,
            } => {
                let session = self
                    .sessions
                    .get_mut(&device_id)
                    .context("device disconnected")?;
                ensure!(session.pairing, "no pending pairing request");
                ensure!(
                    session.code == pairing_code,
                    "pairing session changed; compare the current code again"
                );
                session.local_confirm = true;
                session.send(Body::PairConfirm(proto::PairConfirm {}))?;
                self.finish_pair(&device_id)?;
            }
            Command::CancelPair { device_id } => {
                self.pending_pair.remove(&device_id);
                if let Some(session) = self.sessions.remove(&device_id) {
                    let _ = session.send(Body::PairCancel(proto::PairCancel {}));
                    session.close();
                }
            }
            Command::Unpair { device_id } => {
                let old = self.trust.0.remove(&device_id);
                if let Err(error) = self.trust.save(&self.config.storage_dir) {
                    if let Some(old) = old {
                        self.trust.0.insert(device_id, old);
                    }
                    return Err(error);
                }
                self.pending_pair.remove(&device_id);
                if let Some(session) = self.sessions.remove(&device_id) {
                    session.close();
                }
            }
            Command::SetCapability {
                device_id,
                capability,
                enabled,
            } => {
                ensure!([1, 2].contains(&capability), "unsupported capability");
                let trusted = self
                    .trust
                    .0
                    .get_mut(&device_id)
                    .context("device is not trusted")?;
                let old = trusted.allowed.clone();
                trusted.allowed.retain(|v| *v != capability);
                if enabled {
                    trusted.allowed.push(capability);
                }
                if let Err(error) = self.trust.save(&self.config.storage_dir) {
                    self.trust.0.get_mut(&device_id).unwrap().allowed = old;
                    return Err(error);
                }
                if let Some(session) = self.sessions.get(&device_id) {
                    session.send(Body::Ready(proto::Ready {
                        allowed: self.trust.0[&device_id].allowed.clone(),
                    }))?;
                }
            }
            Command::SendClipboard { mime_type, data } => {
                let capability = validate_clipboard(&mime_type, &data)?;
                let hash = clipboard_hash(&mime_type, &data);
                if self
                    .hashes
                    .iter()
                    .any(|(h, seen)| *h == hash && seen.elapsed() < Duration::from_secs(30))
                {
                    return Ok(());
                }
                let payload = proto::ClipboardPayload {
                    id: uuid::Uuid::new_v4().to_string(),
                    origin_device_id: self.id.clone(),
                    content_hash: hash.clone(),
                    mime_type,
                    data,
                    timestamp: std::time::SystemTime::now()
                        .duration_since(std::time::UNIX_EPOCH)?
                        .as_millis() as i64,
                };
                let mut sent = false;
                for (id, session) in &self.sessions {
                    if self
                        .trust
                        .0
                        .get(id)
                        .is_some_and(|t| t.allowed.contains(&capability))
                        && session.remote_ready
                        && !session.pairing
                        && session.info.capabilities.contains(&capability)
                        && session.remote_allowed.contains(&capability)
                    {
                        session.send(Body::Clipboard(payload.clone()))?;
                        sent = true;
                    }
                }
                if sent {
                    self.remember(hash, payload.id);
                }
            }
        }
        self.refresh();
        Ok(())
    }
    fn remember(&mut self, hash: Vec<u8>, id: String) {
        self.hashes.push_back((hash, Instant::now()));
        self.ids.push_back(id);
        while self.hashes.len() > 256 {
            self.hashes.pop_front();
        }
        while self.ids.len() > 256 {
            self.ids.pop_front();
        }
    }
    fn network_event(&mut self, event: NetworkEvent) -> Result<()> {
        match event {
            NetworkEvent::Session(session) => {
                let id = session.id.clone();
                self.dialing.remove(&id);
                if self.sessions.len() >= MAX_PEERS {
                    session.close();
                    return Ok(());
                }
                if self.sessions.contains_key(&id) {
                    // Never replace an active or user-confirmed pairing with another session.
                    session.close();
                    return Ok(());
                }
                if let Some(trusted) = self.trust.0.get(&id) {
                    session.send(Body::Ready(proto::Ready {
                        allowed: trusted.allowed.clone(),
                    }))?;
                }
                self.sessions.insert(id.clone(), *session);
                if self.pending_pair.remove(&id) {
                    self.begin_pair(&id)?;
                }
            }
            NetworkEvent::Wire {
                id,
                serial,
                message,
            } => {
                let Some(session) = self.sessions.get_mut(&id).filter(|s| s.serial == serial)
                else {
                    return Ok(());
                };
                let result: Result<()> = (|| {
                    match message.body.context("empty protocol message")? {
                        Body::PairRequest(_) => {
                            if !session.pairing {
                                // A peer cannot reset confirmations/code by repeatedly requesting.
                                session.pairing = true;
                                session.started = Instant::now();
                                session.local_confirm = false;
                                session.remote_confirm = false;
                            }
                        }
                        Body::PairConfirm(_) => {
                            ensure!(session.pairing, "unexpected pairing confirmation");
                            session.remote_confirm = true;
                        }
                        Body::PairCancel(_) => {
                            session.close();
                        }
                        Body::Ready(ready) => {
                            ensure!(
                                ready.allowed.len() <= 2
                                    && ready.allowed.iter().all(|c| [1, 2].contains(c)),
                                "invalid permissions"
                            );
                            session.remote_ready = true;
                            session.remote_allowed = ready.allowed;
                        }
                        Body::Clipboard(payload) => {
                            let capability = validate_clipboard(&payload.mime_type, &payload.data)?;
                            ensure!(
                                self.trust
                                    .0
                                    .get(&id)
                                    .is_some_and(|t| t.allowed.contains(&capability))
                                    && session.remote_ready
                                    && !session.pairing
                                    && session.info.capabilities.contains(&capability),
                                "unauthorized clipboard message"
                            );
                            ensure!(payload.origin_device_id == id, "invalid clipboard origin");
                            ensure!(
                                uuid::Uuid::parse_str(&payload.id).is_ok(),
                                "invalid clipboard ID"
                            );
                            ensure!(
                                payload.content_hash
                                    == clipboard_hash(&payload.mime_type, &payload.data),
                                "clipboard checksum mismatch"
                            );
                            if !self.ids.contains(&payload.id)
                                && !self.hashes.iter().any(|(h, seen)| {
                                    *h == payload.content_hash
                                        && seen.elapsed() < Duration::from_secs(30)
                                })
                            {
                                self.events
                                    .try_send(CoreEvent::ClipboardReceived {
                                        device_id: id.clone(),
                                        mime_type: payload.mime_type,
                                        data: payload.data,
                                        content_hash: hex::encode(&payload.content_hash),
                                    })
                                    .map_err(|_| anyhow::anyhow!("clipboard receiver is busy"))?;
                                self.hashes
                                    .push_back((payload.content_hash, Instant::now()));
                                self.ids.push_back(payload.id);
                                while self.hashes.len() > 256 {
                                    self.hashes.pop_front();
                                }
                                while self.ids.len() > 256 {
                                    self.ids.pop_front();
                                }
                            }
                        }
                        Body::Hello(_) => bail!("duplicate hello"),
                        Body::Ping(_) => {}
                    }
                    Ok(())
                })();
                if result.is_err() {
                    session.close();
                }
                result?;
                self.finish_pair(&id)?;
            }
            NetworkEvent::Closed { id, serial } => {
                if self.sessions.get(&id).is_some_and(|s| s.serial == serial) {
                    self.sessions.remove(&id);
                }
            }
            NetworkEvent::DialFailed { id } => {
                self.dialing.remove(&id);
                self.pending_pair.remove(&id);
            }
        }
        self.refresh();
        Ok(())
    }
    fn tick(&mut self) {
        if let Some(rx) = &self.mdns_rx {
            for _ in 0..64 {
                let Ok(event) = rx.try_recv() else {
                    break;
                };
                match event {
                    ServiceEvent::ServiceResolved(info) => {
                        let Some(id) = info.get_property_val_str("id") else {
                            continue;
                        };
                        if !valid_id(id)
                            || id == self.id
                            || info.get_property_val_str("version") != Some("1")
                        {
                            continue;
                        }
                        if self.advertisements.len() >= 64 && !self.advertisements.contains_key(id)
                        {
                            continue;
                        }
                        let addresses: Vec<_> = info
                            .get_addresses()
                            .iter()
                            .filter_map(|ip| match ip {
                                IpAddr::V4(v4) => {
                                    Some(SocketAddr::new((*v4).into(), info.get_port()))
                                }
                                _ => None,
                            })
                            .collect();
                        if addresses.is_empty() {
                            continue;
                        }
                        self.advertisements.insert(
                            id.to_owned(),
                            Advertisement {
                                name: info
                                    .get_property_val_str("name")
                                    .unwrap_or("Unknown device")
                                    .chars()
                                    .take(128)
                                    .collect(),
                                platform: info
                                    .get_property_val_str("platform")
                                    .unwrap_or("unknown")
                                    .chars()
                                    .take(16)
                                    .collect(),
                                addresses,
                            },
                        );
                    }
                    ServiceEvent::ServiceRemoved(_, fullname) => {
                        self.advertisements
                            .retain(|id, _| !fullname.starts_with(&id[..16]));
                    }
                    _ => {}
                }
            }
        }
        self.sessions.retain(|id, s| {
            if (!self.trust.0.contains_key(id) || !s.remote_ready || s.pairing)
                && s.started.elapsed() > Duration::from_secs(120)
            {
                s.close();
                false
            } else {
                true
            }
        });
        for session in self.sessions.values() {
            let _ = session.send(Body::Ping(proto::Ping {}));
        }
        let reconnect: Vec<_> = self
            .trust
            .0
            .keys()
            .filter(|id| self.id < **id && self.advertisements.contains_key(*id))
            .cloned()
            .collect();
        for id in reconnect {
            if let Err(error) = self.dial(&id) {
                tracing::debug!(%error, "reconnect deferred");
            }
        }
        self.refresh();
    }
}

fn platform_number(platform: &str) -> i32 {
    if platform == "android" { 1 } else { 2 }
}
fn platform_name(platform: i32) -> &'static str {
    match platform {
        1 => "android",
        2 => "linux",
        _ => "unknown",
    }
}
fn valid_id(id: &str) -> bool {
    id.len() == 64
        && id
            .bytes()
            .all(|v| v.is_ascii_hexdigit() && !v.is_ascii_uppercase())
}
pub fn clipboard_hash(mime: &str, data: &[u8]) -> Vec<u8> {
    let mut hash = Sha256::new();
    hash.update(mime.as_bytes());
    hash.update([0]);
    hash.update(data);
    hash.finalize().to_vec()
}
fn validate_clipboard(mime: &str, data: &[u8]) -> Result<i32> {
    ensure!(
        data.len() <= proto::MAX_CLIPBOARD_BYTES,
        "clipboard exceeds 8 MiB limit"
    );
    match mime {
        "text/plain" => {
            std::str::from_utf8(data).context("clipboard is not UTF-8")?;
            Ok(1)
        }
        "image/png" => {
            ensure!(
                data.len() >= 45 && data.starts_with(b"\x89PNG\r\n\x1a\n"),
                "clipboard is not PNG"
            );
            ensure!(
                data[8..12] == 13u32.to_be_bytes() && &data[12..16] == b"IHDR",
                "invalid PNG header"
            );
            let width = u32::from_be_bytes(data[16..20].try_into()?);
            let height = u32::from_be_bytes(data[20..24].try_into()?);
            ensure!(
                width > 0 && height > 0 && u64::from(width) * u64::from(height) <= 16_000_000,
                "PNG exceeds 16 megapixels"
            );
            ensure!(
                &data[data.len() - 12..] == b"\x00\x00\x00\x00IEND\xae\x42\x60\x82",
                "incomplete PNG"
            );
            Ok(2)
        }
        _ => bail!("unsupported clipboard MIME type"),
    }
}

async fn write_frame(stream: &mut quinn::SendStream, message: &proto::Envelope) -> Result<()> {
    let bytes = message.encode_to_vec();
    ensure!(
        bytes.len() <= proto::MAX_FRAME_BYTES,
        "protocol frame too large"
    );
    stream.write_u32(bytes.len() as u32).await?;
    stream.write_all(&bytes).await?;
    Ok(())
}
async fn read_frame(stream: &mut quinn::RecvStream) -> Result<proto::Envelope> {
    let length = stream.read_u32().await? as usize;
    ensure!(
        length > 0 && length <= proto::MAX_FRAME_BYTES,
        "invalid protocol frame length"
    );
    let mut bytes = vec![0u8; length];
    stream.read_exact(&mut bytes).await?;
    Ok(proto::Envelope::decode(bytes.as_slice())?)
}
async fn serve_connection(
    connection: quinn::Connection,
    hello: proto::DeviceInfo,
    expected: Option<String>,
    outgoing: bool,
    tx: async_mpsc::Sender<NetworkEvent>,
) -> Result<()> {
    let handshake = async {
        let certs = connection
            .peer_identity()
            .context("missing peer certificate")?
            .downcast::<Vec<rustls::pki_types::CertificateDer<'static>>>()
            .map_err(|_| anyhow::anyhow!("invalid peer identity"))?;
        ensure!(certs.len() == 1, "identity must have one certificate");
        let id = fingerprint(certs[0].as_ref());
        if let Some(expected) = expected {
            ensure!(id == expected, "device fingerprint changed");
        }
        ensure!(id != hello.id, "cannot connect to self");
        let (mut send, mut recv) = if outgoing {
            connection.open_bi().await?
        } else {
            connection.accept_bi().await?
        };
        write_frame(
            &mut send,
            &proto::Envelope {
                body: Some(Body::Hello(hello.clone())),
            },
        )
        .await?;
        let message = read_frame(&mut recv).await?;
        let Some(Body::Hello(info)) = message.body else {
            bail!("expected device hello");
        };
        ensure!(
            info.id == id,
            "hello fingerprint does not match TLS identity"
        );
        ensure!(
            info.protocol_version == proto::PROTOCOL_VERSION,
            "unsupported protocol version"
        );
        ensure!(
            info.name.len() <= 128
                && !info.name.trim().is_empty()
                && info.version.len() <= 32
                && info.capabilities.len() <= 32,
            "invalid device hello"
        );
        let code = security::pairing_code(&connection, &hello.id, &id)?;
        Ok::<_, anyhow::Error>((id, info, code, send, recv))
    };
    let handshake = tokio::time::timeout(Duration::from_secs(10), handshake).await;
    let (id, info, code, mut send, mut recv) = match handshake {
        Ok(Ok(value)) => value,
        error => {
            connection.close(1u8.into(), b"handshake rejected");
            return Err(anyhow::anyhow!("handshake failed: {error:?}"));
        }
    };
    let serial = uuid::Uuid::new_v4();
    let (sender, mut messages) = async_mpsc::channel(4);
    tx.send(NetworkEvent::Session(Box::new(Session {
        id: id.clone(),
        serial,
        info,
        connection: connection.clone(),
        sender,
        code,
        pairing: false,
        local_confirm: false,
        remote_confirm: false,
        remote_ready: false,
        remote_allowed: Vec::new(),
        started: Instant::now(),
    })))
    .await?;
    let writer = async {
        while let Some(message) = messages.recv().await {
            tokio::time::timeout(Duration::from_secs(20), write_frame(&mut send, &message))
                .await??;
        }
        Ok::<(), anyhow::Error>(())
    };
    let reader = async {
        loop {
            // A partial frame must not indefinitely hold a session/large allocation.
            let message =
                tokio::time::timeout(Duration::from_secs(60), read_frame(&mut recv)).await??;
            tx.send(NetworkEvent::Wire {
                id: id.clone(),
                serial,
                message,
            })
            .await?;
        }
        #[allow(unreachable_code)]
        Ok::<(), anyhow::Error>(())
    };
    tokio::select! { result = writer => { if let Err(error) = result { tracing::debug!(%error, "writer closed"); } }, result = reader => { if let Err(error) = result { tracing::debug!(%error, "reader closed"); } }, _ = connection.closed() => {} }
    connection.close(0u8.into(), b"session ended");
    let _ = tx.send(NetworkEvent::Closed { id, serial }).await;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn applying_delayed_clipboard_refreshes_echo_window() {
        let dir = crate::test_directory();
        let identity = Identity::load(dir.path(), None).unwrap();
        let config = CoreConfig {
            storage_dir: dir.path().into(),
            device_name: "Delayed clipboard test".into(),
            platform: "linux".into(),
            listen_port: 0,
            storage_key: None,
            discovery: false,
        };
        let (events, _rx) = mpsc::sync_channel(8);
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut actor = Actor::new(
                config,
                identity,
                TrustStore::default(),
                Arc::new(Mutex::new(Snapshot::default())),
                events,
            )
            .unwrap();
            let hash = clipboard_hash("text/plain", b"queued while Android is backgrounded");
            actor
                .hashes
                .push_back((hash.clone(), Instant::now() - Duration::from_secs(60)));
            assert!(actor.hashes[0].1.elapsed() > Duration::from_secs(30));
            actor
                .command(Command::MarkClipboardApplied {
                    content_hash: hex::encode(&hash),
                })
                .unwrap();
            assert_eq!(actor.hashes.len(), 1);
            assert_eq!(actor.hashes[0].0, hash);
            assert!(actor.hashes[0].1.elapsed() < Duration::from_secs(1));
            assert!(
                actor
                    .command(Command::MarkClipboardApplied {
                        content_hash: "ab".into()
                    })
                    .is_err()
            );
        });
    }
    #[test]
    fn untrusted_tls_peer_cannot_send_clipboard_even_after_claiming_ready() {
        let dir = crate::test_directory();
        let victim = CoreHandle::start(CoreConfig {
            storage_dir: dir.path().join("victim"),
            device_name: "Victim".into(),
            platform: "linux".into(),
            listen_port: 0,
            storage_key: None,
            discovery: false,
        })
        .unwrap();
        let identity = Identity::load(&dir.path().join("attacker"), None).unwrap();
        let rt = tokio::runtime::Runtime::new().unwrap();
        rt.block_on(async {
            let endpoint = security::endpoint(&identity, 0).unwrap();
            let connection = endpoint
                .connect(
                    ([127, 0, 0, 1], victim.listen_port()).into(),
                    "lava-connect.local",
                )
                .unwrap()
                .await
                .unwrap();
            let (mut send, mut recv) = connection.open_bi().await.unwrap();
            let info = proto::DeviceInfo {
                id: identity.id(),
                name: "Attacker".into(),
                platform: 2,
                version: "0.1.0".into(),
                capabilities: vec![1, 2],
                protocol_version: 1,
            };
            write_frame(
                &mut send,
                &proto::Envelope {
                    body: Some(Body::Hello(info)),
                },
            )
            .await
            .unwrap();
            read_frame(&mut recv).await.unwrap();
            write_frame(
                &mut send,
                &proto::Envelope {
                    body: Some(Body::Ready(proto::Ready {
                        allowed: vec![1, 2],
                    })),
                },
            )
            .await
            .unwrap();
            let data = b"must never be delivered".to_vec();
            let payload = proto::ClipboardPayload {
                id: uuid::Uuid::new_v4().to_string(),
                origin_device_id: identity.id(),
                content_hash: clipboard_hash("text/plain", &data),
                mime_type: "text/plain".into(),
                data,
                timestamp: 0,
            };
            write_frame(
                &mut send,
                &proto::Envelope {
                    body: Some(Body::Clipboard(payload)),
                },
            )
            .await
            .unwrap();
            tokio::time::timeout(Duration::from_secs(3), connection.closed())
                .await
                .unwrap();
            assert!(
                !victim
                    .drain_events()
                    .iter()
                    .any(|event| matches!(event, CoreEvent::ClipboardReceived { .. }))
            );
            assert!(victim.snapshot().devices.iter().all(|d| !d.trusted));
            endpoint.close(0u8.into(), b"test done");
        });
    }
    #[test]
    fn oversized_frame_is_rejected_before_payload_read() {
        let dir = crate::test_directory();
        let victim = CoreHandle::start(CoreConfig {
            storage_dir: dir.path().join("victim"),
            device_name: "Victim".into(),
            platform: "linux".into(),
            listen_port: 0,
            storage_key: None,
            discovery: false,
        })
        .unwrap();
        let identity = Identity::load(&dir.path().join("peer"), None).unwrap();
        tokio::runtime::Runtime::new().unwrap().block_on(async {
            let endpoint = security::endpoint(&identity, 0).unwrap();
            let connection = endpoint
                .connect(
                    ([127, 0, 0, 1], victim.listen_port()).into(),
                    "lava-connect.local",
                )
                .unwrap()
                .await
                .unwrap();
            let (mut send, _recv) = connection.open_bi().await.unwrap();
            send.write_u32((proto::MAX_FRAME_BYTES + 1) as u32)
                .await
                .unwrap();
            tokio::time::timeout(Duration::from_secs(3), connection.closed())
                .await
                .unwrap();
            assert!(
                !victim
                    .drain_events()
                    .iter()
                    .any(|event| matches!(event, CoreEvent::ClipboardReceived { .. }))
            );
            endpoint.close(0u8.into(), b"test done");
        });
    }
    #[test]
    fn payload_validation_and_domain_separated_hash() {
        assert!(validate_clipboard("text/plain", b"hello").is_ok());
        assert!(validate_clipboard("text/plain", &[255]).is_err());
        assert!(validate_clipboard("image/png", b"not png").is_err());
        assert!(validate_clipboard("text/html", b"hello").is_err());
        assert!(
            validate_clipboard("text/plain", &vec![0; proto::MAX_CLIPBOARD_BYTES + 1]).is_err()
        );
        assert_ne!(
            clipboard_hash("text/plain", b"same"),
            clipboard_hash("image/png", b"same")
        );
    }
}
