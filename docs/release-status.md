# v0.1.0 开发预览验收状态

代码已建立，不代表已完成稳定发行或全部真机端到端验收。2026-10-15 补齐构建环境，并按[依赖版本基线](dependencies.md)升级稳定版依赖；已完成部分 Android 本机验收及[Linux → Android 后台写入真机测试](background-write-acceptance.md)。

## 已确认

- 本轮 `cargo fmt --all -- --check`、严格 clippy、workspace 构建、协议生成检查和 `scripts/build-linux.sh` release 构建通过；Linux x86_64 动态链接依赖均可解析。
- 本轮 Rust 默认自动测试 16 项通过、0 失败，1 项 LAN multicast 测试忽略；包含 LC01 兼容、信任库迁移/损坏拒绝、旧客户端组兼容和四节点组测试。此前本机两节点 mDNS multicast 测试曾通过，本轮未重跑。
- 真实 loopback QUIC 测试覆盖双端确认、失效验证码拒绝、双向文字/PNG、回环抑制、权限持久化、重新配对不恢复已关闭能力、重启重连、撤销信任。
- 未受信任对端伪造 Ready 后发送剪贴板被拒绝；超长帧被拒绝；延迟应用剪贴板会刷新回环抑制窗口。
- `scripts/test-jni-host.sh` 通过：JDK 17 实际加载 host JNI，验证 greeting、句柄、快照、事件接口、停止及加密身份重载；JNI 0.22 迁移后另验证 null / 非法 JSON / 非法 Base64 仍抛出 `IllegalStateException`，NUL / emoji / 中文字符串可往返。
- Android 原生工具链已补齐并升级：Rust 1.99.1、`aarch64-linux-android` target、cargo-ndk 4.1.2、NDK 30.0.16248370、SDK / build-tools 37、JDK 17；Gradle 9.8.1、AGP 9.4.1、Kotlin / Compose compiler 2.4.21、ktlint plugin 14.2.0 / CLI 1.8.0。
- `scripts/build-android.sh` 已成功交叉编译 Rust Core 并生成 ARM64 debug APK，包内包含 ELF64 AArch64 `liblava_connect.so`，JNI 导出与 Kotlin 接口一致；APK 签名和 16 KiB ZIP/ELF 对齐检查通过。这些离线检查不替代 Android 实际加载与真机验收。
- Android `ktlintCheck`、`assembleDebug`、`testDebugUnitTest`（8 项）、`lintDebug` 通过；接收剪贴板测试覆盖写入顺序、失败保留/重试、防回环标记刷新、最新值替换和切组清除待重试内容。仍有非阻塞警告，不宣称零警告。
- 此前 Linux GUI 使用独立临时身份、关闭发现进行了短时启动检查，无启动错误；未操作用户剪贴板。本轮仅编译和离线验证，未启动 GUI。
- Samsung SM-X730（Android 16 / API 36）真机安装、UI 与 JNI 加载成功；前台服务启动、充电状态下熄屏 60 秒存活、停止服务及应用进程重启后身份保持均通过。详细范围与证据见[真机验收记录](device-acceptance.md)。
- 用户反馈跨端剪贴板可正确传输；新增 Linux → Android 后台文字/PNG 写入已在同一 LAN 的 SM-X730 真机通过。独立普通应用在 Lava Connect 留在后台时读到完全一致的文字和 PNG，FileProvider URI 可跨 UID 访问；短时熄屏发送、解锁后不回 Lava Connect 即可读取也通过。写入异常保留/前台重试由单元测试覆盖；后台读取限制未改变。
- 多本地同步组及 Linux / Android 组管理界面已建立；四节点 loopback QUIC（两 Linux、两 Android 模拟平台）覆盖文字/PNG 多发、无中继、防回环、组隔离、单端授权阻断、能力开关、重启持久化、删除和撤销，4 项组测试额外连续重跑 3 次均通过。组码不能授权未配对设备，存储失败停止核心，默认组兼容旧客户端。两端构建产物已更新，APK 原生库、签名和 ZIP 对齐检查通过。

## 阻塞 / 未确认

- Android 后台自动读取（KDE Connect 方式 + Shizuku 授权）已实现：本轮 `ktlintCheck`、`assembleDebug`、`testDebugUnitTest`（14 项，新增日志匹配、debug/release 包名前缀、节流和本机写入抑制）、`lintDebug` 通过。**未做真机验收**，以下需在设备上确认：API 36 logcat 过滤写法、Android 13+ 日志访问确认框、后台启动 Activity 是否被悬浮窗权限豁免、OEM（如 MIUI）对 `pm grant` 的限制、图片 URI 在透明 Activity 结束后仍可读取。

- 本轮未安装设备或操作用户剪贴板；新组功能未做多机真机验收，不以模拟平台的 loopback 测试代替真实组 UI/网络验收。此前后台写入真机记录是前一版本结果，并非新版组功能的真机结论。组使用、迁移和降级限制见[groups.md](groups.md)。

- AGP 9.4 已支持 compileSdk 37，原兼容性警告消除；仍有上游 Gradle `Configuration.setVisible` 弃用提醒、既有 Kotlin `Unit` 表达式及 Android lint 警告，未隐藏这些提醒。
- 初轮不同 LAN 的阻塞已解除，本轮验证已有配对重连与 Linux → Android 后台接收及图片 URI 消费。首次配对隔离、Android → Linux 独立逐项验收、撤销/断网恢复、托盘联调仍未完成。Android Keystore 身份重载和通知权限已在 API 36 验证；API 37 本地网络权限、密钥失效、OEM 长期后台/Doze 策略仍未验证。
- 发现非阻塞显示问题：重连成功后 UI 可能仍残留 `peer is busy or disconnected` 旧错误文字；本轮连接和实际后台传输成功，未在验收任务中修改此行为。
- CI 配置已添加，但未在 GitHub Actions 上执行；aarch64 Linux 尚未构建。Android 原生 ARM64 构建已在本机通过。
- Linux 暂用 0600 私有身份文件而非 keyring；没有独立安全审计或长期断网/资源压力测试。

## 构建产物

- Android ARM64（debug 签名）：`android/app/build/outputs/apk/debug/app-debug.apk`。
- Android 原生库：`android/app/src/main/jniLibs/arm64-v8a/liblava_connect.so`。
- Linux x86_64 release：`target/release/lava-connect`。

后续按 [testing.md](testing.md) 继续剩余双向剪贴板及撤销/断网重连/托盘检查，全部满足后再决定发布。当前已完成 Android 本机部分和 Linux → Android 后台文字/PNG、短时熄屏发送实测，未将其等同于全部 v0.1 验收通过。
