// SPDX-License-Identifier: GPL-3.0-or-later
fn main() {
    let mut config = prost_build::Config::new();
    config.protoc_executable(protoc_bin_vendored::protoc_bin_path().expect("vendored protoc"));
    config
        .compile_protos(&["../../protocol/connect.proto"], &["../../protocol"])
        .expect("compile protocol");
    println!("cargo:rerun-if-changed=../../protocol/connect.proto");
}
