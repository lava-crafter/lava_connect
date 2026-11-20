// SPDX-License-Identifier: GPL-3.0-or-later
use lava_core::{Command, CoreConfig, CoreEvent, CoreHandle};
use std::{
    net::{Ipv4Addr, SocketAddr},
    path::Path,
    thread,
    time::{Duration, Instant},
};

fn config(dir: &Path, name: &str) -> CoreConfig {
    CoreConfig {
        storage_dir: dir.into(),
        device_name: name.into(),
        platform: "linux".into(),
        listen_port: 0,
        storage_key: None,
        discovery: false,
    }
}
fn test_directory() -> tempfile::TempDir {
    let preferred = Path::new("/tmp/opencode");
    if preferred.is_dir() {
        tempfile::tempdir_in(preferred)
    } else {
        tempfile::tempdir()
    }
    .unwrap()
}
fn wait(mut check: impl FnMut() -> bool) {
    let start = Instant::now();
    while !check() {
        assert!(
            start.elapsed() < Duration::from_secs(10),
            "condition timed out"
        );
        thread::sleep(Duration::from_millis(25));
    }
}
fn knows(a: &CoreHandle, b: &CoreHandle) {
    a.connect_address(
        b.snapshot().device_id,
        SocketAddr::from((Ipv4Addr::LOCALHOST, b.listen_port())),
    )
    .unwrap();
    wait(|| {
        a.snapshot()
            .devices
            .iter()
            .any(|d| d.id == b.snapshot().device_id)
    });
}
fn connected(a: &CoreHandle) -> bool {
    a.snapshot().devices.iter().any(|d| d.state == "connected")
}
fn clipboard(a: &CoreHandle, mime: &str, bytes: &[u8]) {
    a.command(Command::SendClipboard {
        mime_type: mime.into(),
        data: bytes.into(),
    })
    .unwrap();
}
fn receive(b: &CoreHandle, mime: &str, bytes: &[u8]) {
    wait(|| {
        b.drain_events().into_iter().any(|e| matches!(e, CoreEvent::ClipboardReceived { mime_type, data, .. } if mime_type == mime && data == bytes))
    });
}

#[test]
fn pair_both_sides_clipboard_permissions_restart_and_revocation() {
    let dir = test_directory();
    let a_path = dir.path().join("a");
    let b_path = dir.path().join("b");
    let mut a = CoreHandle::start(config(&a_path, "Desktop")).unwrap();
    let mut b = CoreHandle::start(config(&b_path, "Phone")).unwrap();
    assert!(CoreHandle::start(config(&a_path, "Duplicate")).is_err());
    knows(&a, &b);
    let a_id = a.snapshot().device_id;
    let b_id = b.snapshot().device_id;
    a.command(Command::Pair {
        device_id: b_id.clone(),
    })
    .unwrap();
    wait(|| b.snapshot().devices.iter().any(|d| d.state == "pairing"));
    let code_a = a.snapshot().devices[0].pairing_code.clone().unwrap();
    let code_b = b.snapshot().devices[0].pairing_code.clone().unwrap();
    assert_eq!(code_a, code_b);
    assert_eq!(code_a.len(), 7);
    a.command(Command::ConfirmPair {
        device_id: b_id.clone(),
        pairing_code: "stale code".into(),
    })
    .unwrap();
    wait(|| {
        a.snapshot()
            .error
            .as_deref()
            .is_some_and(|e| e.contains("pairing session changed"))
    });
    assert!(
        !a.snapshot().devices[0].trusted,
        "stale dialog must not confirm a different session"
    );
    assert!(!connected(&a) && !connected(&b));
    a.command(Command::ConfirmPair {
        device_id: b_id.clone(),
        pairing_code: code_a,
    })
    .unwrap();
    thread::sleep(Duration::from_millis(150));
    assert!(
        !connected(&a) && !connected(&b),
        "one-sided confirmation must not authorize"
    );
    clipboard(&a, "text/plain", b"private before pairing");
    thread::sleep(Duration::from_millis(100));
    assert!(
        !b.drain_events()
            .iter()
            .any(|e| matches!(e, CoreEvent::ClipboardReceived { .. }))
    );
    b.command(Command::ConfirmPair {
        device_id: a_id.clone(),
        pairing_code: code_b,
    })
    .unwrap();
    wait(|| connected(&a) && connected(&b));
    a.drain_events();
    b.drain_events();
    clipboard(&a, "text/plain", b"Hello Linux");
    receive(&b, "text/plain", b"Hello Linux");
    clipboard(&b, "text/plain", b"Hello Android");
    receive(&a, "text/plain", b"Hello Android");
    // Echo from the platform watcher must not bounce back to the origin.
    a.drain_events();
    clipboard(&b, "text/plain", b"Hello Linux");
    thread::sleep(Duration::from_millis(200));
    assert!(
        !a.drain_events()
            .iter()
            .any(|e| matches!(e, CoreEvent::ClipboardReceived { .. }))
    );
    // Real 1×1 black and red PNGs (IHDR/IDAT/IEND with valid CRCs).
    let png_bytes = hex::decode("89504e470d0a1a0a0000000d4948445200000001000000010802000000907753de0000000c49444154789c63606060000000040001f61738550000000049454e44ae426082").unwrap();
    let png = png_bytes.as_slice();
    clipboard(&a, "image/png", png);
    receive(&b, "image/png", png);
    let png2_bytes = hex::decode("89504e470d0a1a0a0000000d4948445200000001000000010802000000907753de0000000c49444154789c63f8cfc0000003010100c9fe92ef0000000049454e44ae426082").unwrap();
    let png2 = png2_bytes.as_slice();
    clipboard(&b, "image/png", png2);
    receive(&a, "image/png", png2);
    b.command(Command::SetCapability {
        device_id: a_id.clone(),
        capability: 1,
        enabled: false,
    })
    .unwrap();
    wait(|| !b.snapshot().devices[0].text_enabled);
    thread::sleep(Duration::from_millis(100));
    b.drain_events();
    clipboard(&a, "text/plain", b"disabled");
    thread::sleep(Duration::from_millis(200));
    assert!(
        !b.drain_events()
            .iter()
            .any(|e| matches!(e, CoreEvent::ClipboardReceived { .. }))
    );
    // A new pairing request on a trusted session must still be shown as pairing,
    // and confirming it must preserve explicitly disabled permissions.
    a.command(Command::Pair {
        device_id: b_id.clone(),
    })
    .unwrap();
    wait(|| {
        a.snapshot().devices[0].state == "pairing" && b.snapshot().devices[0].state == "pairing"
    });
    a.command(Command::ConfirmPair {
        device_id: b_id.clone(),
        pairing_code: a.snapshot().devices[0].pairing_code.clone().unwrap(),
    })
    .unwrap();
    b.command(Command::ConfirmPair {
        device_id: a_id.clone(),
        pairing_code: b.snapshot().devices[0].pairing_code.clone().unwrap(),
    })
    .unwrap();
    wait(|| connected(&a) && connected(&b));
    assert!(
        !b.snapshot().devices[0].text_enabled,
        "re-pairing must not reset capability permissions"
    );
    a.stop();
    b.stop();
    let a = CoreHandle::start(config(&a_path, "Desktop")).unwrap();
    let b = CoreHandle::start(config(&b_path, "Phone")).unwrap();
    assert_eq!(a.snapshot().device_id, a_id);
    assert_eq!(b.snapshot().device_id, b_id);
    knows(&a, &b);
    knows(&b, &a);
    wait(|| connected(&a) && connected(&b));
    assert!(
        !b.snapshot().devices[0].text_enabled,
        "permissions must survive restart"
    );
    a.drain_events();
    b.drain_events();
    clipboard(&a, "image/png", png);
    receive(&b, "image/png", png);
    b.command(Command::Unpair { device_id: a_id }).unwrap();
    wait(|| !connected(&b));
    thread::sleep(Duration::from_millis(200));
    b.drain_events();
    clipboard(&a, "image/png", png2);
    thread::sleep(Duration::from_millis(200));
    assert!(
        !b.drain_events()
            .iter()
            .any(|e| matches!(e, CoreEvent::ClipboardReceived { .. }))
    );
}

#[test]
fn discovery_id_cannot_impersonate_a_different_tls_identity() {
    let dir = test_directory();
    let a = CoreHandle::start(config(&dir.path().join("a"), "A")).unwrap();
    let b = CoreHandle::start(config(&dir.path().join("b"), "B")).unwrap();
    let fake = "ab".repeat(32);
    a.connect_address(
        fake.clone(),
        SocketAddr::from((Ipv4Addr::LOCALHOST, b.listen_port())),
    )
    .unwrap();
    wait(|| !a.snapshot().devices.is_empty());
    a.command(Command::Pair { device_id: fake }).unwrap();
    thread::sleep(Duration::from_millis(500));
    assert!(
        !a.snapshot()
            .devices
            .iter()
            .any(|d| d.trusted || d.state == "pairing" || d.state == "connected")
    );
    assert!(
        !b.snapshot()
            .devices
            .iter()
            .any(|d| d.trusted || d.state == "pairing" || d.state == "connected")
    );
}

fn device_id(core: &CoreHandle) -> String {
    core.snapshot().device_id
}
fn local_address(core: &CoreHandle) -> String {
    format!("127.0.0.1:{}", core.listen_port())
}
fn code(core: &CoreHandle) -> Option<String> {
    core.snapshot()
        .devices
        .iter()
        .find_map(|d| d.pairing_code.clone())
}
/// `a` adds `b` by address only (no device ID) and both users confirm the code.
fn pair_by_address(a: &CoreHandle, b: &CoreHandle) {
    pair_at(a, b, local_address(b));
}
fn pair_at(a: &CoreHandle, b: &CoreHandle, address: String) {
    a.command(Command::AddAddress { address }).unwrap();
    wait(|| code(a).is_some() && code(b).is_some());
    let code_a = code(a).unwrap();
    assert_eq!(code_a, code(b).unwrap());
    a.command(Command::ConfirmPair {
        device_id: device_id(b),
        pairing_code: code_a,
    })
    .unwrap();
    b.command(Command::ConfirmPair {
        device_id: device_id(a),
        pairing_code: code(b).unwrap(),
    })
    .unwrap();
    wait(|| connected(a) && connected(b));
}
fn saved_address(dir: &Path, peer: &str) -> Option<String> {
    let store: serde_json::Value =
        serde_json::from_slice(&std::fs::read(dir.join("trusted_devices.json")).unwrap()).unwrap();
    store["devices"][peer]["address"]
        .as_str()
        .map(str::to_owned)
}
fn restart_on(dir: &Path, name: &str, port: u16) -> CoreHandle {
    let mut config = config(dir, name);
    config.listen_port = port;
    CoreHandle::start(config).unwrap()
}

#[test]
fn manual_address_pairs_and_reconnects_after_restart() {
    // Run both directions so the peer with the higher device ID also has to dial.
    for swap in [false, true] {
        let dir = test_directory();
        let (a_path, b_path) = (dir.path().join("a"), dir.path().join("b"));
        let mut a = CoreHandle::start(config(&a_path, "Desktop")).unwrap();
        let mut b = CoreHandle::start(config(&b_path, "Phone")).unwrap();
        let (dialer, listener) = if swap { (&b, &a) } else { (&a, &b) };
        pair_by_address(dialer, listener);
        let (dialer_path, listener_path) = if swap {
            (&b_path, &a_path)
        } else {
            (&a_path, &b_path)
        };
        assert_eq!(
            saved_address(dialer_path, &device_id(listener)),
            Some(local_address(listener))
        );
        assert_eq!(saved_address(listener_path, &device_id(dialer)), None);
        let (a_port, b_port) = (a.listen_port(), b.listen_port());
        a.stop();
        b.stop();
        // No discovery and no explicit address: only the saved address can reconnect them.
        let a = restart_on(&a_path, "Desktop", a_port);
        let b = restart_on(&b_path, "Phone", b_port);
        wait(|| connected(&a) && connected(&b));
        a.drain_events();
        b.drain_events();
        clipboard(&a, "text/plain", b"after restart");
        receive(&b, "text/plain", b"after restart");
    }
}

#[test]
fn peers_that_saved_each_others_address_keep_one_session() {
    let dir = test_directory();
    let (a_path, b_path) = (dir.path().join("a"), dir.path().join("b"));
    let mut a = CoreHandle::start(config(&a_path, "Desktop")).unwrap();
    let mut b = CoreHandle::start(config(&b_path, "Phone")).unwrap();
    pair_by_address(&a, &b);
    // Entering the address of an already trusted, connected peer only records the address.
    b.command(Command::AddAddress {
        address: local_address(&a),
    })
    .unwrap();
    wait(|| saved_address(&b_path, &device_id(&a)).is_some());
    let (a_port, b_port) = (a.listen_port(), b.listen_port());
    a.stop();
    b.stop();
    let a = restart_on(&a_path, "Desktop", a_port);
    let b = restart_on(&b_path, "Phone", b_port);
    wait(|| connected(&a) && connected(&b));
    // Both sides dial; duplicate sessions must settle on one without dropping the peer.
    thread::sleep(Duration::from_secs(5));
    assert!(connected(&a) && connected(&b));
    assert_eq!(a.snapshot().devices.len(), 1);
    a.drain_events();
    b.drain_events();
    clipboard(&b, "text/plain", b"one session");
    receive(&a, "text/plain", b"one session");
}

#[test]
fn re_pairing_by_address_after_one_side_forgot_keeps_the_session_and_address() {
    // Swapping who forgets covers both orders of the lower-device-ID rule.
    for swap in [false, true] {
        let dir = test_directory();
        let (a_path, b_path) = (dir.path().join("a"), dir.path().join("b"));
        let a = CoreHandle::start(config(&a_path, "Desktop")).unwrap();
        let b = CoreHandle::start(config(&b_path, "Phone")).unwrap();
        let (keeper, forgetter, forgetter_path) = if swap {
            (&b, &a, &a_path)
        } else {
            (&a, &b, &b_path)
        };
        // `keeper` saves `forgetter`'s address, so it keeps redialing after being forgotten.
        pair_by_address(keeper, forgetter);
        forgetter
            .command(Command::Unpair {
                device_id: device_id(keeper),
            })
            .unwrap();
        // The keeper reconnects by its saved address; the forgetter sees an untrusted peer.
        wait(|| {
            forgetter
                .snapshot()
                .devices
                .iter()
                .any(|d| d.id == device_id(keeper) && !d.trusted)
        });
        // Entering the keeper's address opens a second connection to an already connected
        // peer. Both sides must keep the same one and pairing must finish over it.
        pair_at(forgetter, keeper, local_address(keeper));
        assert_eq!(
            saved_address(forgetter_path, &device_id(keeper)),
            Some(local_address(keeper)),
            "the entered address must be saved even though pairing reused the old session"
        );
        thread::sleep(Duration::from_secs(4));
        assert!(connected(keeper) && connected(forgetter));
        keeper.drain_events();
        forgetter.drain_events();
        clipboard(keeper, "text/plain", b"re-paired");
        receive(forgetter, "text/plain", b"re-paired");
    }
}

#[test]
fn ipv6_address_pairs_and_reconnects_on_the_same_port() {
    if std::net::UdpSocket::bind("[::1]:0").is_err() {
        eprintln!("skipped: IPv6 loopback unavailable");
        return;
    }
    let dir = test_directory();
    let (a_path, b_path) = (dir.path().join("a"), dir.path().join("b"));
    let mut a = CoreHandle::start(config(&a_path, "Desktop")).unwrap();
    let mut b = CoreHandle::start(config(&b_path, "Phone")).unwrap();
    // The dual-stack endpoint serves IPv6 on the same port the IPv4 tests use.
    let address = format!("[::1]:{}", b.listen_port());
    pair_at(&a, &b, address.clone());
    assert_eq!(saved_address(&a_path, &device_id(&b)), Some(address));
    let (a_port, b_port) = (a.listen_port(), b.listen_port());
    a.stop();
    b.stop();
    let a = restart_on(&a_path, "Desktop", a_port);
    let b = restart_on(&b_path, "Phone", b_port);
    wait(|| connected(&a) && connected(&b));
    a.drain_events();
    b.drain_events();
    clipboard(&b, "text/plain", b"over IPv6");
    receive(&a, "text/plain", b"over IPv6");
}

#[test]
fn manual_address_errors_are_reported() {
    let dir = test_directory();
    let a = CoreHandle::start(config(&dir.path().join("a"), "A")).unwrap();
    let error = |a: &CoreHandle, needle: &str| {
        wait(|| {
            a.snapshot()
                .error
                .as_deref()
                .is_some_and(|e| e.contains(needle))
        })
    };
    a.command(Command::AddAddress {
        address: "not an address".into(),
    })
    .unwrap();
    error(&a, "IP address such as");
    a.command(Command::AddAddress {
        address: "[ff02::1]:45893".into(),
    })
    .unwrap();
    error(&a, "single device");
    a.command(Command::AddAddress {
        address: "fe80::1".into(),
    })
    .unwrap();
    error(&a, "needs its interface");
    a.command(Command::AddAddress {
        address: local_address(&a),
    })
    .unwrap();
    error(&a, "cannot connect to self");
    assert!(a.snapshot().devices.is_empty());
}

#[test]
#[ignore = "requires a multicast-capable LAN"]
fn two_real_mdns_nodes_discover_each_other() {
    let dir = test_directory();
    let mut ac = config(&dir.path().join("a"), "Lava test desktop");
    ac.discovery = true;
    let mut bc = config(&dir.path().join("b"), "Lava test phone");
    bc.discovery = true;
    let a = CoreHandle::start(ac).unwrap();
    let b = CoreHandle::start(bc).unwrap();
    wait(|| {
        a.snapshot()
            .devices
            .iter()
            .any(|d| d.id == b.snapshot().device_id)
            && b.snapshot()
                .devices
                .iter()
                .any(|d| d.id == a.snapshot().device_id)
    });
}
