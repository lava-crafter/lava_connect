// SPDX-License-Identifier: GPL-3.0-or-later
use crate::{ClipboardGroup, DEFAULT_GROUP_ID};
use aes_gcm::{Aes256Gcm, KeyInit, aead::Aead};
use anyhow::{Context, Result, bail, ensure};
use rand::{TryRng, rngs::SysRng};
use rcgen::{CertifiedKey, generate_simple_self_signed};
use rustls::pki_types::{CertificateDer, PrivateKeyDer, PrivatePkcs8KeyDer};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    collections::BTreeMap,
    fs::{self, OpenOptions},
    io::Write,
    path::Path,
};

#[derive(Serialize, Deserialize)]
pub struct Identity {
    certificate: Vec<u8>,
    key: Vec<u8>,
}

impl Identity {
    pub fn load(dir: &Path, storage_key: Option<&str>) -> Result<Self> {
        fs::create_dir_all(dir)?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(dir, fs::Permissions::from_mode(0o700))?;
        }
        let path = dir.join("identity.bin");
        if path.exists() {
            let bytes = fs::read(&path)?;
            let plain = match storage_key {
                Some(key) => {
                    if !bytes.starts_with(b"LC01") || bytes.len() < 16 {
                        bail!("invalid encrypted identity");
                    }
                    let nonce: &[u8; 12] = bytes[4..16].try_into()?;
                    cipher(key)?
                        .decrypt(nonce.into(), &bytes[16..])
                        .map_err(|_| anyhow::anyhow!("cannot decrypt identity"))?
                }
                None => bytes,
            };
            let identity: Self = serde_json::from_slice(&plain)
                .context("invalid identity; refusing to replace it")?;
            return Ok(identity);
        }
        let CertifiedKey { cert, signing_key } =
            generate_simple_self_signed(vec!["lava-connect.local".into()])?;
        let identity = Self {
            certificate: cert.der().to_vec(),
            key: signing_key.serialize_der(),
        };
        let plain = serde_json::to_vec(&identity)?;
        let bytes = if let Some(key) = storage_key {
            let mut nonce = [0u8; 12];
            SysRng
                .try_fill_bytes(&mut nonce)
                .context("cannot generate identity encryption nonce")?;
            let mut bytes = b"LC01".to_vec();
            bytes.extend(nonce);
            bytes.extend(
                cipher(key)?
                    .encrypt((&nonce).into(), plain.as_ref())
                    .map_err(|_| anyhow::anyhow!("identity encryption failed"))?,
            );
            bytes
        } else {
            plain
        };
        // create_new prevents racing processes from replacing an established identity.
        write_new(&path, &bytes)?;
        Ok(identity)
    }
    pub fn id(&self) -> String {
        fingerprint(&self.certificate)
    }
    pub fn certificate(&self) -> CertificateDer<'static> {
        self.certificate.clone().into()
    }
    pub fn private_key(&self) -> PrivateKeyDer<'static> {
        PrivatePkcs8KeyDer::from(self.key.clone()).into()
    }
}

fn cipher(key: &str) -> Result<Aes256Gcm> {
    let key = hex::decode(key).context("invalid storage key")?;
    Aes256Gcm::new_from_slice(&key).map_err(|_| anyhow::anyhow!("storage key must be 32 bytes"))
}
pub fn fingerprint(certificate: &[u8]) -> String {
    hex::encode(Sha256::digest(certificate))
}

fn write_new(path: &Path, bytes: &[u8]) -> Result<()> {
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    let mut file = options.open(path)?;
    file.write_all(bytes)?;
    file.sync_all()?;
    Ok(())
}

#[derive(Clone, Serialize, Deserialize)]
pub struct TrustedDevice {
    pub name: String,
    pub platform: String,
    pub allowed: Vec<i32>,
}
#[derive(Clone, Serialize, Deserialize)]
pub struct TrustStore {
    version: u32,
    pub devices: BTreeMap<String, TrustedDevice>,
    pub groups: Vec<ClipboardGroup>,
    pub active_group_id: String,
}

#[derive(Debug)]
pub struct PersistenceFailure;
impl std::fmt::Display for PersistenceFailure {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("trust/group persistence failed; restart after resolving storage error")
    }
}
impl std::error::Error for PersistenceFailure {}
impl Default for TrustStore {
    fn default() -> Self {
        Self {
            version: 2,
            devices: BTreeMap::new(),
            groups: vec![ClipboardGroup {
                id: DEFAULT_GROUP_ID.into(),
                name: "Default".into(),
                members: Vec::new(),
            }],
            active_group_id: DEFAULT_GROUP_ID.into(),
        }
    }
}
impl TrustStore {
    pub fn load(dir: &Path) -> Result<Self> {
        let path = dir.join("trusted_devices.json");
        if path.exists() {
            let value: serde_json::Value = serde_json::from_slice(&fs::read(path)?)?;
            let store: Self = if value.get("version").is_some() {
                serde_json::from_value(value).context("invalid trust/group store")?
            } else {
                let devices: BTreeMap<String, TrustedDevice> =
                    serde_json::from_value(value).context("invalid legacy trust store")?;
                let mut store = Self::default();
                store.groups[0].members = devices.keys().cloned().collect();
                store.devices = devices;
                store
            };
            store.validate()?;
            Ok(store)
        } else {
            Ok(Self::default())
        }
    }
    pub fn save(&self, dir: &Path) -> Result<()> {
        self.save_inner(dir).context(PersistenceFailure)
    }

    fn save_inner(&self, dir: &Path) -> Result<()> {
        self.validate()?;
        let path = dir.join("trusted_devices.json");
        let temp = dir.join(format!(".trust-{}.tmp", uuid::Uuid::new_v4()));
        write_new(&temp, &serde_json::to_vec_pretty(self)?)?;
        fs::rename(&temp, &path)?;
        fs::File::open(dir)?.sync_all()?;
        Ok(())
    }

    pub fn active_group(&self) -> &ClipboardGroup {
        self.groups
            .iter()
            .find(|g| g.id == self.active_group_id)
            .unwrap()
    }

    pub fn member(&self, device_id: &str) -> bool {
        self.active_group().members.iter().any(|id| id == device_id)
    }

    pub fn validate(&self) -> Result<()> {
        ensure!(self.version == 2, "unsupported trust/group store version");
        ensure!(
            !self.groups.is_empty() && self.groups.len() <= 16,
            "must have 1–16 groups"
        );
        let mut ids = std::collections::HashSet::new();
        for group in &self.groups {
            validate_group_id(&group.id)?;
            validate_group_name(&group.name)?;
            ensure!(ids.insert(&group.id), "duplicate group ID");
            let mut members = std::collections::HashSet::new();
            for id in &group.members {
                ensure!(
                    self.devices.contains_key(id) && members.insert(id),
                    "invalid group member"
                );
            }
        }
        ensure!(
            ids.contains(&DEFAULT_GROUP_ID.to_owned()),
            "default group missing"
        );
        ensure!(ids.contains(&self.active_group_id), "active group missing");
        Ok(())
    }
}

pub fn validate_group_id(id: &str) -> Result<()> {
    let uuid = uuid::Uuid::parse_str(id).context("group code must be a UUID")?;
    ensure!(
        uuid.to_string() == id,
        "group code must be a lowercase hyphenated UUID"
    );
    Ok(())
}

pub fn validate_group_name(name: &str) -> Result<()> {
    ensure!(
        !name.trim().is_empty() && name.len() <= 64 && !name.chars().any(char::is_control),
        "group name must be 1–64 bytes without control characters"
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn legacy_trust_migrates_to_default_group_without_changing_permissions() {
        let dir = crate::test_directory();
        let id = "ab".repeat(32);
        fs::write(
            dir.path().join("trusted_devices.json"),
            serde_json::to_vec(&BTreeMap::from([(
                id.clone(),
                TrustedDevice {
                    name: "Old peer".into(),
                    platform: "android".into(),
                    allowed: vec![2],
                },
            )]))
            .unwrap(),
        )
        .unwrap();
        let mut store = TrustStore::load(dir.path()).unwrap();
        assert_eq!(store.active_group_id, DEFAULT_GROUP_ID);
        assert_eq!(store.active_group().members, vec![id.clone()]);
        assert_eq!(store.devices[&id].allowed, vec![2]);
        store.groups.push(ClipboardGroup {
            id: uuid::Uuid::new_v4().to_string(),
            name: "Work".into(),
            members: vec![id],
        });
        store.active_group_id = store.groups[1].id.clone();
        store.save(dir.path()).unwrap();
        let loaded = TrustStore::load(dir.path()).unwrap();
        assert_eq!(loaded.groups, store.groups);
        assert_eq!(loaded.active_group_id, store.active_group_id);
    }

    #[test]
    fn malformed_group_configuration_is_not_silently_reset() {
        let dir = crate::test_directory();
        let store = TrustStore {
            active_group_id: uuid::Uuid::new_v4().to_string(),
            ..TrustStore::default()
        };
        // Intentionally bypass save() validation to simulate corrupt disk state.
        let bytes = serde_json::to_vec(&store).unwrap();
        let path = dir.path().join("trusted_devices.json");
        fs::write(&path, &bytes).unwrap();
        assert!(TrustStore::load(dir.path()).is_err());
        assert_eq!(fs::read(path).unwrap(), bytes);
        assert!(validate_group_name("\n").is_err());
        assert!(validate_group_id("not-a-group").is_err());
    }

    #[test]
    fn lc01_identity_remains_compatible() {
        // LC01 uses AES-256-GCM with a 12-byte nonce and a 16-byte tag.
        // Fixed ciphertext generated independently with JDK AES/GCM/NoPadding
        // guards the pre-upgrade format against accidental storage changes.
        let temporary = crate::test_directory();
        let mut bytes = b"LC01".to_vec();
        bytes.extend(hex::decode("000102030405060708090a0b").unwrap());
        bytes.extend(hex::decode("5b79653b3c630c3e2d4c38fd1cb814f994cf4767ed2855b1c120b2bc5b9c398817993b90fdba7ce2d22cb3bd2a4ec4b1bfe15d6e74").unwrap());
        fs::write(temporary.path().join("identity.bin"), bytes).unwrap();

        let identity = Identity::load(temporary.path(), Some(&"ab".repeat(32))).unwrap();
        assert_eq!(identity.certificate, [1, 2, 3]);
        assert_eq!(identity.key, [4, 5, 6]);
    }

    #[test]
    fn identity_is_persistent_and_encrypted() {
        let temporary = crate::test_directory();
        let dir = temporary.path().join("identity");
        let key = "ab".repeat(32);
        let a = Identity::load(&dir, Some(&key)).unwrap();
        let b = Identity::load(&dir, Some(&key)).unwrap();
        assert_eq!(a.id(), b.id());
        assert!(Identity::load(&dir, Some(&"cd".repeat(32))).is_err());
        assert!(
            fs::read(dir.join("identity.bin"))
                .unwrap()
                .starts_with(b"LC01")
        );
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            assert_eq!(
                fs::metadata(dir.join("identity.bin"))
                    .unwrap()
                    .permissions()
                    .mode()
                    & 0o777,
                0o600
            );
        }
        fs::remove_dir_all(dir).unwrap();
    }
}
