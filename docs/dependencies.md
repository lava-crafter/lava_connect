# 依赖版本基线

2026-10-15 按官方仓库元数据升级至最新非预发布、非撤回的稳定版本。范围包括所有直接生产、开发、构建依赖，Gradle wrapper、Android NDK 和 CI actions；不更改 Android minSdk 26 / targetSdk 37、Java 字节码目标 17 或项目协议版本。

## Rust

各 `Cargo.toml` 使用已核实稳定版作为最低兼容版本，`Cargo.lock` 固定解析结果。`cargo update` 同时更新允许范围内的间接依赖；上游库要求的旧版间接依赖不会被强行覆盖到不兼容主版本。

| 依赖 | 版本 |
|---|---|
| anyhow / serde / serde_json | 1.0.104 / 1.0.229 / 1.0.154 |
| tokio / tracing / tracing-subscriber | 1.54.1 / 0.1.44 / 0.3.23 |
| quinn / rustls / rcgen | 0.11.12 / 0.23.46 / 0.14.10 |
| aes-gcm / sha2 / rand | 0.11.1 / 0.11.1 / 0.10.3 |
| hex / uuid / fs2 / tempfile | 0.4.3 / 1.29.0 / 0.4.3 / 3.27.0 |
| mdns-sd | 0.21.6 |
| prost / prost-build / protoc-bin-vendored | 0.14.4 / 0.14.4 / 3.3.0 |
| slint / slint-build / ksni / ctrlc | 1.18.1 / 1.18.1 / 0.3.7 / 3.5.2 |
| base64 / jni | 0.23.1 / 0.22.4 |

来源：[crates.io API](https://crates.io/api/v1/crates) 中各 crate 的非 yanked 稳定版本。Rust 使用 stable channel；本次工具链为 1.99.1。

## Android / 构建工具

| 依赖 | 版本 |
|---|---|
| Gradle / Android Gradle Plugin | 9.8.2 / 9.4.1 |
| Kotlin Compose compiler plugin | 2.4.21 |
| ktlint Gradle plugin / ktlint CLI | 14.2.0 / 1.8.0 |
| AndroidX core-ktx / activity-compose | 1.19.1 / 1.13.0 |
| AndroidX lifecycle（所有直接引用模块） | 2.11.0 |
| Compose BOM | 2026.09.00 |
| JUnit 4 | 4.13.2（该坐标已是最新版） |
| Shizuku API / provider | 13.1.5（2026-10-29 核对 Maven Central 最新稳定版） |
| NDK / SDK build-tools / cargo-ndk | 30.0.16248370 / 37.0.0 / 4.1.2 |

AGP 9 使用内置 Kotlin，移除 `org.jetbrains.kotlin.android` 和旧 task-level JVM target 配置，沿用 Java 17 目标；Compose 插件提供相应 Kotlin 工具链。NDK 版本在 Gradle、构建脚本和 CI 中一致。保留 ARM64 APK 和原有 JNI 导出/异常契约、LC01 身份文件格式。

来源：[Google Maven](https://dl.google.com/dl/android/maven2/index.html)、[Maven Central](https://repo.maven.apache.org/maven2/)、[Gradle Plugin Portal](https://plugins.gradle.org/)、[Gradle stable API](https://services.gradle.org/versions/current)、[Android SDK 仓库](https://dl.google.com/android/repository/repository2-1.xml)。[AGP 9.4 兼容表](https://developer.android.com/build/releases/agp-9-4-0-release-notes)要求 Gradle ≥9.6、JDK ≥17，支持 API 37。

## CI

`actions/checkout@v7.0.1`、`actions/setup-java@v6.1.0`、`android-actions/setup-android@v4.0.4`、`Swatinem/rust-cache@v2.9.2` 使用官方最新稳定 release；均使用 Node 24 runtime，当前 `ubuntu-latest` 托管 runner 满足要求。`dtolnay/rust-toolchain@stable` 保留其稳定工具链渠道，不切换到 nightly。

实际验证结果与仍未执行的真机验收见 [release-status.md](release-status.md)。
