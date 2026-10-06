// SPDX-License-Identifier: GPL-3.0-or-later
//! Self-signed identities: TLS proves key possession; application pairing pins the
//! complete certificate fingerprint. An untrusted TLS session may ONLY pair.
use crate::identity::Identity;
use anyhow::Result;
use rustls::{
    DigitallySignedStruct, DistinguishedName, Error, SignatureScheme,
    client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier},
    crypto::{CryptoProvider, verify_tls12_signature, verify_tls13_signature},
    pki_types::{CertificateDer, ServerName, UnixTime},
    server::danger::{ClientCertVerified, ClientCertVerifier},
};
use std::sync::Arc;

#[derive(Debug)]
struct IdentityVerifier {
    provider: Arc<CryptoProvider>,
}
impl ServerCertVerifier for IdentityVerifier {
    fn verify_server_cert(
        &self,
        _: &CertificateDer<'_>,
        _: &[CertificateDer<'_>],
        _: &ServerName<'_>,
        _: &[u8],
        _: UnixTime,
    ) -> Result<ServerCertVerified, Error> {
        Ok(ServerCertVerified::assertion())
    }
    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, Error> {
        verify_tls12_signature(
            message,
            cert,
            dss,
            &self.provider.signature_verification_algorithms,
        )
    }
    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, Error> {
        verify_tls13_signature(
            message,
            cert,
            dss,
            &self.provider.signature_verification_algorithms,
        )
    }
    fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
        self.provider
            .signature_verification_algorithms
            .supported_schemes()
    }
}
impl ClientCertVerifier for IdentityVerifier {
    fn root_hint_subjects(&self) -> &[DistinguishedName] {
        &[]
    }
    fn verify_client_cert(
        &self,
        _: &CertificateDer<'_>,
        _: &[CertificateDer<'_>],
        _: UnixTime,
    ) -> Result<ClientCertVerified, Error> {
        Ok(ClientCertVerified::assertion())
    }
    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, Error> {
        ServerCertVerifier::verify_tls12_signature(self, message, cert, dss)
    }
    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, Error> {
        ServerCertVerifier::verify_tls13_signature(self, message, cert, dss)
    }
    fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
        ServerCertVerifier::supported_verify_schemes(self)
    }
}

pub fn endpoint(identity: &Identity, port: u16) -> Result<quinn::Endpoint> {
    let provider = Arc::new(rustls::crypto::ring::default_provider());
    let verifier = Arc::new(IdentityVerifier {
        provider: provider.clone(),
    });
    let mut server = rustls::ServerConfig::builder_with_provider(provider.clone())
        .with_protocol_versions(&[&rustls::version::TLS13])?
        .with_client_cert_verifier(verifier.clone())
        .with_single_cert(vec![identity.certificate()], identity.private_key())?;
    server.alpn_protocols = vec![b"lava-connect/1".to_vec()];
    let mut server = quinn::ServerConfig::with_crypto(Arc::new(
        quinn::crypto::rustls::QuicServerConfig::try_from(server)?,
    ));
    let mut transport = quinn::TransportConfig::default();
    transport.keep_alive_interval(Some(std::time::Duration::from_secs(10)));
    transport.max_idle_timeout(Some(std::time::Duration::from_secs(30).try_into()?));
    transport.max_concurrent_bidi_streams(1u8.into());
    transport.max_concurrent_uni_streams(0u8.into());
    let transport = Arc::new(transport);
    server.transport_config(transport.clone());
    let mut client = rustls::ClientConfig::builder_with_provider(provider)
        .with_protocol_versions(&[&rustls::version::TLS13])?
        .dangerous()
        .with_custom_certificate_verifier(verifier)
        .with_client_auth_cert(vec![identity.certificate()], identity.private_key())?;
    client.alpn_protocols = vec![b"lava-connect/1".to_vec()];
    let mut client = quinn::ClientConfig::new(Arc::new(
        quinn::crypto::rustls::QuicClientConfig::try_from(client)?,
    ));
    client.transport_config(transport);
    let mut endpoint = quinn::Endpoint::server(server, ([0, 0, 0, 0], port).into())?;
    endpoint.set_default_client_config(client);
    Ok(endpoint)
}

pub fn pairing_code(connection: &quinn::Connection, local: &str, remote: &str) -> Result<String> {
    use sha2::{Digest, Sha256};
    let mut exporter = [0u8; 32];
    connection
        .export_keying_material(&mut exporter, b"lava-connect pairing v1", b"")
        .map_err(|_| anyhow::anyhow!("TLS exporter unavailable"))?;
    let mut ids = [local, remote];
    ids.sort();
    let mut digest = Sha256::new();
    digest.update(exporter);
    for id in ids {
        digest.update(id);
    }
    let hash = digest.finalize();
    let number = u32::from_be_bytes(hash[..4].try_into()?) % 1_000_000;
    Ok(format!("{:03} {:03}", number / 1000, number % 1000))
}
