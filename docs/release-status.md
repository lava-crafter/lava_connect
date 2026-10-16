# v0.1.0 开发预览验收状态

代码已建立，不代表已完成稳定发行或真机端到端验收。2026-10-15 补齐构建环境，并按[依赖版本基线](dependencies.md)升级稳定版依赖；随后开始[真机验收](device-acceptance.md)，目前仅完成 Android 本机部分，跨端验收受网络前置条件阻塞。

## 已确认

- 本轮 `cargo fmt --all -- --check`、严格 clippy、workspace 构建、协议生成检查和 `scripts/build-linux.sh` release 构建通过；Linux x86_64 动态链接依赖均可解析。
- 本轮 Rust 默认自动测试 9 项通过、0 失败，1 项 LAN multicast 测试忽略；新增固定 AES-GCM 密文验证 LC01 身份文件兼容性。此前本机两节点 mDNS multicast 测试曾通过，本轮未重跑。
- 真实 loopback QUIC 测试覆盖双端确认、失效验证码拒绝、双向文字/PNG、回环抑制、权限持久化、重新配对不恢复已关闭能力、重启重连、撤销信任。
- 未受信任对端伪造 Ready 后发送剪贴板被拒绝；超长帧被拒绝；延迟应用剪贴板会刷新回环抑制窗口。
- `scripts/test-jni-host.sh` 通过：JDK 17 实际加载 host JNI，验证 greeting、句柄、快照、事件接口、停止及加密身份重载；JNI 0.22 迁移后另验证 null / 非法 JSON / 非法 Base64 仍抛出 `IllegalStateException`，NUL / emoji / 中文字符串可往返。
- Android 原生工具链已补齐并升级：Rust 1.99.1、`aarch64-linux-android` target、cargo-ndk 4.1.2、NDK 30.0.16248370、SDK / build-tools 37、JDK 17；Gradle 9.8.1、AGP 9.4.1、Kotlin / Compose compiler 2.4.21、ktlint plugin 14.2.0 / CLI 1.8.0。
- `scripts/build-android.sh` 已成功交叉编译 Rust Core 并生成 ARM64 debug APK，包内包含 ELF64 AArch64 `liblava_connect.so`，JNI 导出与 Kotlin 接口一致；APK 签名和 16 KiB ZIP/ELF 对齐检查通过。这些离线检查不替代 Android 实际加载与真机验收。
- Android `ktlintCheck`、`assembleDebug`、`testDebugUnitTest`（2 项）、`lintDebug` 通过；lint 仍有 7 项警告、无错误，不宣称零警告。
- 此前 Linux GUI 使用独立临时身份、关闭发现进行了短时启动检查，无启动错误；未操作用户剪贴板。本轮仅编译和离线验证，未启动 GUI。
- Samsung SM-X730（Android 16 / API 36）真机安装、UI 与 JNI 加载成功；前台服务启动、充电状态下熄屏 60 秒存活、停止服务及应用进程重启后身份保持均通过。详细范围与证据见[真机验收记录](device-acceptance.md)。

## 阻塞 / 未确认

- AGP 9.4 已支持 compileSdk 37，原兼容性警告消除；仍有上游 Gradle `Configuration.setVisible` 弃用提醒、既有 Kotlin `Unit` 表达式及 Android lint 警告，未隐藏这些提醒。
- 两端当前不在同一 LAN，用户暂无法调整，因此发现、配对、双端实际粘贴、URI 消费、跨端重连/撤销与托盘联调仍未验证。Android Keystore 身份重载和通知权限已在 API 36 验证；API 37 本地网络权限、密钥失效、OEM 长期后台/Doze 策略仍未验证。
- CI 配置已添加，但未在 GitHub Actions 上执行；aarch64 Linux 尚未构建。Android 原生 ARM64 构建已在本机通过。
- Linux 暂用 0600 私有身份文件而非 keyring；没有独立安全审计或长期断网/资源压力测试。

## 构建产物

- Android ARM64（debug 签名）：`android/app/build/outputs/apk/debug/app-debug.apk`。
- Android 原生库：`android/app/src/main/jniLibs/arm64-v8a/liblava_connect.so`。
- Linux x86_64 release：`target/release/lava-connect`。

后续在同一 LAN 下按 [testing.md](testing.md) 继续真实 Linux ↔ Android 四项剪贴板验收及后台/撤销/重连检查，全部满足后再决定发布。当前已完成部分 Android 真机检查，未执行模拟器、桌面剪贴板或跨端 LAN 验收。
