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
