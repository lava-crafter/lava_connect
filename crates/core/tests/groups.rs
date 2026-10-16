// SPDX-License-Identifier: GPL-3.0-or-later
use lava_core::{Command, CoreConfig, CoreEvent, CoreHandle, DEFAULT_GROUP_ID};
use std::{
    net::{Ipv4Addr, SocketAddr},
    path::Path,
    thread,
    time::{Duration, Instant},
};

fn config(dir: &Path, name: &str, platform: &str) -> CoreConfig {
    CoreConfig {
        storage_dir: dir.into(),
        device_name: name.into(),
        platform: platform.into(),
        listen_port: 0,
        storage_key: None,
        discovery: false,
    }
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

fn pair(a: &CoreHandle, b: &CoreHandle) {
    let aid = a.snapshot().device_id;
    let bid = b.snapshot().device_id;
    knows(a, b);
    knows(b, a);
    a.command(Command::Pair {
        device_id: bid.clone(),
    })
    .unwrap();
    wait(|| {
        a.snapshot()
            .devices
            .iter()
            .any(|d| d.id == bid && d.state == "pairing")
            && b.snapshot()
                .devices
                .iter()
                .any(|d| d.id == aid && d.state == "pairing")
    });
    let acode = a
        .snapshot()
        .devices
        .iter()
        .find(|d| d.id == bid)
        .unwrap()
        .pairing_code
        .clone()
        .unwrap();
    let bcode = b
        .snapshot()
        .devices
        .iter()
        .find(|d| d.id == aid)
        .unwrap()
        .pairing_code
        .clone()
        .unwrap();
    a.command(Command::ConfirmPair {
        device_id: bid,
        pairing_code: acode,
    })
    .unwrap();
    b.command(Command::ConfirmPair {
        device_id: aid,
        pairing_code: bcode,
    })
    .unwrap();
    wait(|| connected(a, b));
    a.drain_events();
    b.drain_events();
}

fn connected(a: &CoreHandle, b: &CoreHandle) -> bool {
    a.snapshot()
        .devices
        .iter()
        .any(|d| d.id == b.snapshot().device_id && d.state == "connected")
        && b.snapshot()
            .devices
            .iter()
            .any(|d| d.id == a.snapshot().device_id && d.state == "connected")
}

fn send(a: &CoreHandle, mime: &str, data: &[u8]) {
    a.command(Command::SendClipboard {
        mime_type: mime.into(),
        data: data.into(),
    })
    .unwrap();
}

fn received(b: &CoreHandle, mime: &str, data: &[u8]) -> bool {
    b.drain_events().into_iter().any(|e| matches!(e, CoreEvent::ClipboardReceived { mime_type, data: got, group_id, .. } if mime_type == mime && got == data && group_id == b.snapshot().active_group_id))
}

fn receive(b: &CoreHandle, mime: &str, data: &[u8]) {
    wait(|| received(b, mime, data));
}

fn no_receive(b: &CoreHandle) {
    thread::sleep(Duration::from_millis(200));
    assert!(
        !b.drain_events()
            .iter()
            .any(|e| matches!(e, CoreEvent::ClipboardReceived { .. }))
    );
}

#[test]
fn four_node_default_mesh_fanout_and_no_forwarding() {
    let dir = tempfile::tempdir().unwrap();
    let mut nodes = vec![
        CoreHandle::start(config(&dir.path().join("a"), "Linux A", "linux")).unwrap(),
        CoreHandle::start(config(&dir.path().join("b"), "Linux B", "linux")).unwrap(),
        CoreHandle::start(config(&dir.path().join("c"), "Android C", "android")).unwrap(),
        CoreHandle::start(config(&dir.path().join("d"), "Android D", "android")).unwrap(),
    ];
    for i in 0..nodes.len() {
        for j in (i + 1)..nodes.len() {
            pair(&nodes[i], &nodes[j]);
        }
    }
    for (i, node) in nodes.iter().enumerate() {
        let text = format!("node-{i}");
        send(node, "text/plain", text.as_bytes());
        for (j, other) in nodes.iter().enumerate() {
            if i != j {
                receive(other, "text/plain", text.as_bytes());
            }
        }
    }
    let png = hex::decode("89504e470d0a1a0a0000000d4948445200000001000000010802000000907753de0000000c49444154789c63606060000000040001f61738550000000049454e44ae426082").unwrap();
    send(&nodes[0], "image/png", &png);
    for node in &nodes[1..] {
        receive(node, "image/png", &png);
    }
    let png2 = hex::decode("89504e470d0a1a0a0000000d4948445200000001000000010802000000907753de0000000c49444154789c63f8cfc0000003010100c9fe92ef0000000049454e44ae426082").unwrap();
    send(&nodes[2], "image/png", &png2);
    for i in [0, 1, 3] {
        receive(&nodes[i], "image/png", &png2);
    }

    // Remove A's direct links to C/D: B must not relay A's clipboard.
    for i in [2, 3] {
        nodes[0]
            .command(Command::Unpair {
                device_id: nodes[i].snapshot().device_id,
            })
            .unwrap();
        wait(|| !connected(&nodes[0], &nodes[i]));
    }
    for node in &nodes {
        node.drain_events();
    }
    send(&nodes[0], "text/plain", b"no relay");
    receive(&nodes[1], "text/plain", b"no relay");
    // Simulate B's platform watcher echo as well as checking no core relay.
    send(&nodes[1], "text/plain", b"no relay");
    no_receive(&nodes[2]);
    no_receive(&nodes[3]);
    for mut node in nodes.drain(..) {
        node.stop();
    }
}

#[test]
fn custom_groups_require_mutual_membership_and_persist() {
    let dir = tempfile::tempdir().unwrap();
    let paths = (0..4)
        .map(|i| dir.path().join(format!("n{i}")))
        .collect::<Vec<_>>();
    let mut nodes = vec![
        CoreHandle::start(config(&paths[0], "Linux A", "linux")).unwrap(),
        CoreHandle::start(config(&paths[1], "Linux B", "linux")).unwrap(),
        CoreHandle::start(config(&paths[2], "Android C", "android")).unwrap(),
        CoreHandle::start(config(&paths[3], "Android D", "android")).unwrap(),
    ];
    for i in 0..4 {
        for j in i + 1..4 {
            pair(&nodes[i], &nodes[j]);
        }
    }

    nodes[0]
        .command(Command::CreateGroup {
            name: "Shared".into(),
        })
        .unwrap();
    wait(|| {
        nodes[0]
            .snapshot()
            .groups
            .iter()
            .any(|g| g.name == "Shared")
    });
    let group = nodes[0]
        .snapshot()
        .groups
        .into_iter()
        .find(|g| g.name == "Shared")
        .unwrap()
        .id;
    assert!(
        nodes[0]
            .snapshot()
            .groups
            .iter()
            .find(|g| g.id == group)
            .unwrap()
            .members
            .is_empty()
    );
    for node in &nodes[1..3] {
        node.command(Command::JoinGroup {
            group_id: group.clone(),
            name: "Shared".into(),
        })
        .unwrap();
    }
    wait(|| {
        nodes[1..3]
            .iter()
            .all(|node| node.snapshot().groups.iter().any(|g| g.id == group))
    });
    nodes[3]
        .command(Command::CreateGroup {
            name: "Shared".into(),
        })
        .unwrap();
    wait(|| {
        nodes[3]
            .snapshot()
            .groups
            .iter()
            .filter(|g| g.name == "Shared")
            .count()
            == 1
    });
    let other_group = nodes[3]
        .snapshot()
        .groups
        .iter()
        .find(|g| g.name == "Shared")
        .unwrap()
        .id
        .clone();
    assert_ne!(other_group, group, "same-name groups must remain isolated");
    let ids = nodes
        .iter()
        .map(|n| n.snapshot().device_id)
        .collect::<Vec<_>>();
    for (i, node) in nodes.iter().enumerate().take(3) {
        for (j, id) in ids.iter().enumerate().take(3) {
            if i != j {
                node.command(Command::SetGroupMember {
                    group_id: group.clone(),
                    device_id: id.clone(),
                    enabled: true,
                })
                .unwrap();
            }
        }
    }
    nodes[0]
        .command(Command::SelectGroup {
            group_id: group.clone(),
        })
        .unwrap();
    nodes[1]
        .command(Command::SelectGroup {
            group_id: group.clone(),
        })
        .unwrap();
    nodes[2]
        .command(Command::SelectGroup {
            group_id: group.clone(),
        })
        .unwrap();
    wait(|| {
        (0..3).all(|i| {
            nodes[i]
                .snapshot()
                .devices
                .iter()
                .filter(|d| ids[..3].contains(&d.id))
                .all(|d| d.sync_in_group && d.group_compatible)
        })
    });
    for node in &nodes {
        node.drain_events();
    }
    send(&nodes[0], "text/plain", b"in group");
    receive(&nodes[1], "text/plain", b"in group");
    receive(&nodes[2], "text/plain", b"in group");
    no_receive(&nodes[3]);

    // Local membership alone is insufficient; inconsistent active groups block both directions.
    nodes[1]
        .command(Command::SelectGroup {
            group_id: DEFAULT_GROUP_ID.into(),
        })
        .unwrap();
    wait(|| {
        nodes[1].snapshot().active_group_id == DEFAULT_GROUP_ID
            && !nodes[0]
                .snapshot()
                .devices
                .iter()
                .find(|d| d.id == ids[1])
                .unwrap()
                .group_compatible
    });
    for node in &nodes {
        node.drain_events();
    }
    send(&nodes[0], "text/plain", b"mismatch");
    no_receive(&nodes[1]);
    nodes[1]
        .command(Command::SelectGroup {
            group_id: group.clone(),
        })
        .unwrap();
    wait(|| {
        nodes[0]
            .snapshot()
            .devices
            .iter()
            .find(|d| d.id == ids[1])
            .unwrap()
            .group_compatible
    });
    nodes[0]
        .command(Command::SetGroupMember {
            group_id: group.clone(),
            device_id: ids[1].clone(),
            enabled: false,
        })
        .unwrap();
    wait(|| {
        !nodes[0]
            .snapshot()
            .devices
            .iter()
            .find(|d| d.id == ids[1])
            .unwrap()
            .sync_in_group
    });
    for node in &nodes {
        node.drain_events();
    }
    send(&nodes[0], "text/plain", b"removed");
    no_receive(&nodes[1]);
    nodes[0]
        .command(Command::SetGroupMember {
            group_id: group.clone(),
            device_id: ids[1].clone(),
            enabled: true,
        })
        .unwrap();
    nodes[1]
        .command(Command::SetGroupMember {
            group_id: group.clone(),
            device_id: ids[0].clone(),
            enabled: false,
        })
        .unwrap();
    wait(|| {
        !nodes[1]
            .snapshot()
            .devices
            .iter()
            .find(|d| d.id == ids[0])
            .unwrap()
            .sync_in_group
    });
    for node in &nodes {
        node.drain_events();
    }
    send(&nodes[0], "text/plain", b"one sided");
    no_receive(&nodes[1]);
    nodes[1]
        .command(Command::SetGroupMember {
            group_id: group.clone(),
            device_id: ids[0].clone(),
            enabled: true,
        })
        .unwrap();
    nodes[1]
        .command(Command::SetCapability {
            device_id: ids[0].clone(),
            capability: 1,
            enabled: false,
        })
        .unwrap();
    wait(|| {
        !nodes[1]
            .snapshot()
            .devices
            .iter()
            .find(|d| d.id == ids[0])
            .unwrap()
            .text_enabled
    });
    for node in &nodes {
        node.drain_events();
    }
    send(&nodes[0], "text/plain", b"capability");
    no_receive(&nodes[1]);

    // Text permission remains off, but PNG still reaches only the custom group's members.
    for node in &nodes {
        node.drain_events();
    }
    let png = hex::decode("89504e470d0a1a0a0000000d4948445200000001000000010802000000907753de0000000c49444154789c63606060000000040001f61738550000000049454e44ae426082").unwrap();
    send(&nodes[0], "image/png", &png);
    receive(&nodes[1], "image/png", &png);
    receive(&nodes[2], "image/png", &png);
    no_receive(&nodes[3]);

    nodes[0].stop();
    nodes[1].stop();
    nodes[2].stop();
    nodes[3].stop();
    let mut a = CoreHandle::start(config(&paths[0], "Linux A", "linux")).unwrap();
    let mut b = CoreHandle::start(config(&paths[1], "Linux B", "linux")).unwrap();
    assert_eq!(a.snapshot().active_group_id, group);
    assert_eq!(b.snapshot().active_group_id, group);
    assert!(
        a.snapshot()
            .groups
            .iter()
            .find(|g| g.id == group)
            .unwrap()
            .members
            .contains(&ids[2])
    );
    knows(&a, &b);
    knows(&b, &a);
    wait(|| connected(&a, &b));
    assert!(
        !b.snapshot()
            .devices
            .iter()
            .find(|d| d.id == ids[0])
            .unwrap()
            .text_enabled
    );
    a.command(Command::DeleteGroup { group_id: group }).unwrap();
    wait(|| a.snapshot().active_group_id == DEFAULT_GROUP_ID);
    b.command(Command::Unpair {
        device_id: ids[0].clone(),
    })
    .unwrap();
    wait(|| {
        b.snapshot()
            .groups
            .iter()
            .all(|g| !g.members.contains(&ids[0]))
    });
    a.stop();
    b.stop();
}

#[test]
fn storage_failure_stops_core_instead_of_continuing_with_uncertain_grants() {
    let dir = tempfile::tempdir().unwrap();
    let core = CoreHandle::start(config(dir.path(), "Storage test", "linux")).unwrap();
    std::fs::create_dir(dir.path().join("trusted_devices.json")).unwrap();
    core.command(Command::CreateGroup {
        name: "Cannot persist".into(),
    })
    .unwrap();
    wait(|| !core.snapshot().running);
    assert!(
        core.snapshot()
            .error
            .unwrap()
            .contains("persistence failed")
    );
}

#[test]
fn knowing_group_code_does_not_authorize_an_unpaired_device() {
    let dir = tempfile::tempdir().unwrap();
    let a = CoreHandle::start(config(&dir.path().join("a"), "A", "linux")).unwrap();
    let b = CoreHandle::start(config(&dir.path().join("b"), "B", "android")).unwrap();
    knows(&a, &b);
    a.command(Command::CreateGroup {
        name: "Private".into(),
    })
    .unwrap();
    wait(|| a.snapshot().active_group_id != DEFAULT_GROUP_ID);
    let group = a.snapshot().active_group_id;
    b.command(Command::JoinGroup {
        group_id: group.clone(),
        name: "Private".into(),
    })
    .unwrap();
    wait(|| b.snapshot().active_group_id == group);
    a.command(Command::SetGroupMember {
        group_id: group,
        device_id: b.snapshot().device_id,
        enabled: true,
    })
    .unwrap();
    wait(|| a.snapshot().error.as_deref() == Some("device is not trusted"));
    assert!(a.snapshot().groups.iter().all(|g| g.members.is_empty()));
    assert!(a.snapshot().devices.iter().all(|d| !d.trusted));
    for node in [&a, &b] {
        node.drain_events();
    }
    send(&a, "text/plain", b"group code is not authorization");
    no_receive(&b);
}
