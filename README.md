# Lava Connect

Android ↔ Linux 局域网互联。首版 **0.1.0 开发预览**专注安全配对、自动重连、双向文字与 PNG 剪贴板。

Kotlin / Jetpack Compose + Slint，共享 Rust Core、Protocol Buffers、QUIC / TLS 1.3。
无账户、无云服务、无遥测。许可证：**GPL-3.0-or-later**。

## Linux

推荐 Arch Linux / KDE Plasma 6 / Wayland。安装 Rust、C/C++ 构建工具、`wl-clipboard`、`libxkbcommon`、Wayland、D-Bus 和字体。

```sh
cargo build --locked -p lava-connect
cargo run --locked -p lava-connect -- --name "Lava Desktop"
# 或：bash scripts/build-linux.sh
```

`cargo build` 的可执行文件位于 `target/debug/lava-connect`；Linux 构建脚本生成 `target/release/lava-connect`。

两端连接同一个 LAN；选择设备「配对」，核对**两端相同的六位代码和目标设备**，两端都确认。
剪贴板同步支持 UTF-8 文字 / PNG（每条最多 8 MiB，PNG 最多 1600 万像素）。Linux 自动监测剪贴板，也可手动发送。
每个受信任设备的文字、图片能力均可单独关闭；「撤销信任」立即断开并删除本地授权。

支持最多 16 个本地同步组（含不可删除的默认组）；组用于控制同步范围，不替代逐设备配对信任和能力授权。创建/加入及手动成员配置、旧版兼容和存储升级说明见[同步组](docs/groups.md)。

关闭 Linux 窗口后继续驻留托盘；托盘可打开窗口、发送、暂停同步或退出。托盘不可用时，关闭窗口停止服务。
后台模式：`lava-connect --headless`。可安装 `linux/packaging/systemd/lava-connect.service`。
**先在 GUI 配对，退出 GUI 后再启用 headless 服务**；同一身份不允许被多个进程同时使用。当前尚未拆分 daemon / IPC。

默认数据目录：`${XDG_DATA_HOME:-$HOME/.local/share}/lava-connect`。
命令选项：`--name`、`--data-dir`、`--port`、`--no-discovery`、`--headless`。mDNS 使用 UDP 5353；QUIC 默认随机 UDP 端口，可用 `--port` 固定端口供防火墙放行。

## Android

Release 包名 `com.lavacrafter.connect`，debug 包名 `com.lavacrafter.connect.debug`，可共存安装且应用数据相互独立；Android 8.0+（minSdk 26，compileSdk / targetSdk 37），首要 ABI `arm64-v8a`。原先无后缀的 debug 安装不会自动迁移到新包名，新 debug 需要重新配对。
安装 JDK 17、Android SDK 37 / NDK、Rust Android target、`cargo-ndk`，然后：

```sh
# 已安装 rustup 和 Android SDK command-line tools 时，补齐构建依赖：
source "$HOME/.cargo/env"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  'platforms;android-37.0' 'build-tools;37.0.0' 'ndk;30.0.16248370'
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/30.0.16248370"
rustup target add aarch64-linux-android
cargo install cargo-ndk --version 4.1.2 --locked
bash scripts/build-android.sh
```

该脚本先编译并打包 `liblava_connect.so`，并检查 APK 中确实包含该库。首版 APK 仅支持 `arm64-v8a`，输出为 `android/app/build/outputs/apk/debug/app-debug.apk`（开发调试签名）；原生库输出为 `android/app/src/main/jniLibs/arm64-v8a/liblava_connect.so`。

Android Release 使用外部 `release-signing.properties` 和 keystore。默认位置为 `~/.android/release-signing.properties` 与 `~/.android/my-release-key.jks`；可用 Gradle 属性 `-PlavaConnectSigningProperties=...`、`-PlavaConnectKeystore=...` 或环境变量 `LAVA_CONNECT_SIGNING_PROPERTIES`、`LAVA_CONNECT_KEYSTORE` 覆盖路径。签名密码只放在本机配置中，不要提交到仓库。
仅运行 Gradle 而未提供 native 库时，最多只能验证 Kotlin/UI 构建，产出的 APK **不能用于连接功能**。

打开应用并启动连接服务，按上面的流程配对。复制文字或图片后切回应用，点击发送剪贴板。
前台服务保持连接；**Android 10+ 普通应用不能任意读取后台剪贴板**，前台监听和手动发送是首版支持的模式。
后台收到的文字和图片会直接尝试写入 Android 剪贴板，不需要打开应用或 Shizuku；若系统写入抛出异常，保留最新一条内容并在回到前台时重试。后台读取仍受系统限制；写入接口无成功回执，OEM / 策略静默拒绝及锁屏行为仍需真机确认。图片通过受限 FileProvider URI 提供，接收应用需要支持粘贴图片。
Android 17 / API 37 需要用户授予「本地网络」权限；通知权限仅在启动服务时按需请求。
Shizuku 全自动后台剪贴板不属于本预览版，不会假装已经实现。

## 开发与验证

```sh
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
cargo build --workspace
bash scripts/test-jni-host.sh # JDK 17：实际 JVM 调用 JNI（不替代 Android 验收）
```

Protobuf 由 `crates/protocol/build.rs` 使用 vendored protoc 生成，不要求系统安装 protoc。
Kotlin 检查：`cd android && ./gradlew ktlintCheck :app:lintDebug`；格式化：`./gradlew ktlintFormat`。
构建使用 Gradle 9.8.1、AGP 9.4.1 内置 Kotlin 和 Compose compiler 2.4.21；不再应用旧的 Kotlin Android 插件。依赖版本与升级范围见 [dependencies.md](docs/dependencies.md)。
核心集成测试使用真实的 loopback QUIC 连接，覆盖两端配对确认、身份固定、重启重连、文字/图片消息、回环抑制、能力开关及撤销信任。
真实 LAN 发现、桌面剪贴板、Android 权限和后台行为仍需[手动验收](docs/testing.md)，不能用编译成功替代。

## 当前边界

- v0.1 不实现文件、通知、媒体、屏幕镜像、Handoff、远程输入、BLE。
- 当前传输只使用 IPv4；无公网连接、X11 fallback、剪贴板历史或多设备转发。
- Linux 身份文件使用私有目录 0700 / 文件 0600；尚未集成系统 keyring。Android 身份由 Keystore 包装的随机密钥加密。
- 验证码配对必须由用户在两端核对，未经过独立安全审计；不宣称已经达到稳定发行质量。

原始计划：[docs/initial_plan.md](docs/initial_plan.md)。实现说明：[架构](docs/architecture.md)、[协议](docs/protocol.md)、[安全](docs/security.md)。
本次验证与阻塞：[docs/release-status.md](docs/release-status.md)。
