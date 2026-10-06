// SPDX-License-Identifier: GPL-3.0-or-later
use aes_gcm::{
    Aes256Gcm, KeyInit,
    aead::{Aead, OsRng, rand_core::RngCore},
};
use anyhow::{Context, Result, bail};
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
                    cipher(key)?
                        .decrypt((&bytes[4..16]).into(), &bytes[16..])
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
            OsRng.fill_bytes(&mut nonce);
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
#[derive(Default, Serialize, Deserialize)]
pub struct TrustStore(pub BTreeMap<String, TrustedDevice>);
impl TrustStore {
    pub fn load(dir: &Path) -> Result<Self> {
        let path = dir.join("trusted_devices.json");
        if path.exists() {
            Ok(serde_json::from_slice(&fs::read(path)?).context("invalid trust store")?)
        } else {
            Ok(Self::default())
        }
    }
    pub fn save(&self, dir: &Path) -> Result<()> {
        let path = dir.join("trusted_devices.json");
        let temp = dir.join(format!(".trust-{}.tmp", uuid::Uuid::new_v4()));
        write_new(&temp, &serde_json::to_vec_pretty(self)?)?;
        fs::rename(&temp, &path)?;
        fs::File::open(dir)?.sync_all()?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
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
