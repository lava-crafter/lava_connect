# v0.1.0 开发预览验收状态

代码已建立，不代表已完成稳定发行或真机端到端验收。

## 已确认

- `cargo fmt --all -- --check`、严格 clippy、workspace 构建通过。
- Rust 默认自动测试 8 项通过；另显式执行本机两节点 mDNS multicast 测试，通过。
- 真实 loopback QUIC 测试覆盖双端确认、失效验证码拒绝、双向文字/PNG、回环抑制、权限持久化、重新配对不恢复已关闭能力、重启重连、撤销信任。
- 未受信任对端伪造 Ready 后发送剪贴板被拒绝；超长帧被拒绝；延迟应用剪贴板会刷新回环抑制窗口。
- `scripts/test-jni-host.sh` 通过：JDK 17 实际加载 host JNI，验证 greeting、句柄、快照、事件接口、停止及加密身份重载。
- Android `ktlintCheck`、`assembleDebug`、`testDebugUnitTest`（2 项）、`lintDebug` 通过。这只验证 Kotlin / Android 项目及 PNG bytes 保留逻辑。
- Linux GUI 使用独立临时身份、关闭发现进行了短时启动检查，无启动错误；未操作用户剪贴板。

## 阻塞 / 未确认

- 当前 `scripts/build-android.sh` 因缺少 cargo-ndk 停止。默认 SDK 目录没有 NDK，当前 Rust 安装没有 Android target。
- 当前 Gradle debug APK **不含 `liblava_connect.so`**，不能用来验证或使用连接功能；需要按 README 准备原生工具链后重新构建。
- AGP 8.10.1 对 compileSdk 37 发出兼容性警告（其测试上限为 36）；本机编译和 lint 成功，未隐藏该警告。
- 未验证真机 LAN 发现、Android Keystore / 权限、双端实际粘贴、URI 消费、托盘关闭/恢复、OEM 后台和熄屏策略。
- CI 配置已添加，但未在 GitHub Actions 上执行；aarch64 Linux 与 Android 原生 target 未在此环境构建。
- Linux 暂用 0600 私有身份文件而非 keyring；没有独立安全审计或长期断网/资源压力测试。

下一步按 [testing.md](testing.md) 执行真实 Linux ↔ Android 四项剪贴板验收及后台/撤销/重连检查，全部满足后再决定发布。
