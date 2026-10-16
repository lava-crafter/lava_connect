# v0.1.0 真机验收记录（部分完成）

下文保留初轮验收记录。后续同一 LAN 下新增的后台写入实测结果见[后台写入真机验收](background-write-acceptance.md)，不再受初轮网络条件阻塞。

本轮会话日期：2026-10-15；设备日志显示当地时间 2026-10-16。按 [testing.md](testing.md) 执行，不将自动测试或 USB 调试连接当作 LAN 验收。

## 环境与边界

- Samsung SM-X730，Android 16 / API 36，ARM64，ADB 已授权。
- Linux KDE Plasma 6.8.0 / Wayland，`wl-copy` / `wl-paste`、D-Bus 托盘宿主可用。
- 安装 APK：`android/app/build/outputs/apk/debug/app-debug.apk`，SHA-256：`63b68792299ec19896acba11160a23fc2ff9f56aef4d7ab8997e9bde57e4f59e`。
- Linux 地址位于 `192.168.31.0/24`，Android 位于 `10.106.0.0/20`；用户确认暂时无法接入同一 LAN，因此跨端用例未执行，不能判为通过或产品失败。
- 未使用端口转发或模拟器代替 LAN；未读取或覆盖两端剪贴板，未修改既有 Linux 身份/信任数据。

## 已确认

| 检查 | 观察与结果 |
|---|---|
| APK 安装与 UI | ADB 安装成功，MainActivity 正常启动，无崩溃。 |
| 真机 JNI | 点击 Test Rust，显示 `Hello from Lava Connect Core`；应用日志明确记录 `liblava_connect.so` 加载成功。 |
| 权限与前台服务 | 通过系统通知权限弹窗允许通知；启动后 UI 显示 Running 与 64 位设备 ID，`dumpsys activity services` 显示 `isForeground=true`。API 36 不覆盖 API 37 的本地网络权限流程。 |
| 短时后台/熄屏 | 返回桌面并熄屏 60 秒；PID 未变，前台服务仍在。此时设备正在充电；仅证明这一条件下短时存活，不证明 Doze、长期省电或后台剪贴板接收。 |
| 停止服务 | 点击 Stop，UI 显示 Stopped；等待 5 秒后系统服务列表为 `(nothing)`。 |
| 重启后身份持久化 | 停止后 force-stop 并重新启动应用/服务；新进程设备 ID 与原值一致，`identity.bin` SHA-256 未变，未发生解密错误。仅验证应用重启，不包含设备重启或系统升级。 |
| 本地存储 | `identity.bin`、包装密钥文件为私有文件；仅检查文件元数据及身份文件摘要，未导出私钥或包装密钥。 |
| 错误日志 | 两次应用进程日志未发现 FATAL EXCEPTION、UnsatisfiedLinkError、SecurityException 或核心加载失败。 |

## 未完成

- Linux GUI 真机联调、真实 LAN mDNS 发现、单端确认隔离和双端验证码配对。
- 双向文字/PNG 在目标应用实际粘贴，图片 URI 消费，防回环、能力关闭及持久化。
- 跨端自动重连、断网恢复、撤销信任、Linux 托盘关闭/恢复。
- Android 后台接收并回前台应用剪贴板；未充电长期熄屏、Doze、OEM 省电策略。
- API 37 本地网络权限、密钥失效/设备重启、异常输入真机拒绝用例。

## 结束状态与证据

验收结束时 Android APK 保留安装，通知权限为已允许，连接服务已停止（UI 为 Stopped）；未清除应用身份数据。未启动 Linux 测试进程。

本机运行证据位于 `/tmp/opencode/lava-connect-acceptance/`（应用 UI XML、应用进程日志、系统服务检查和身份文件摘要）；这些是临时本机证据，不提交原始屏幕/通知或敏感密钥。

**结论：Android 本机启动、JNI、前台服务、短时熄屏及应用重启身份持久化已验证；完整 v0.1 真机端到端验收仍被同一 LAN 前置条件阻塞。**

## 后续用户反馈与后台写入变更

- 用户随后确认剪贴板已可正确跨端传输；这是用户实测反馈，不等同于上述全部逐项验收通过。
- 本次新增 Linux → Android 后台写入：文字/PNG 收到后不再等待 Activity 前台回调；写入抛出异常时保留最新内容，在回前台时重试。Android 后台自动读取仍不支持。
- 新版后台文字/图片粘贴及 OEM / 锁屏行为的验证结果需单独记录，不能以旧版前台传输成功替代。
- 新版 APK 的 ktlint、7 项 Android 单元测试、lint 和构建通过，签名/ZIP 对齐及 Rust 核心库打包检查通过；用户选择仅构建、稍后自行测试，本次未更新设备或操作剪贴板。
- 用户再次授权真机测试后，已覆盖安装新版并完成后台文字、PNG 跨应用消费和短时熄屏发送测试，均通过；临时测试应用已卸载。方法、摘要与限制见[独立记录](background-write-acceptance.md)。
