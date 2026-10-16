# Linux → Android 后台写入真机验收

## 范围与环境

- 会话日期：2026-10-15。
- Linux：现有 `target/release/lava-connect` 进程，KDE Wayland，地址 `192.168.31.26`。
- Android：Samsung SM-X730，Android 16 / API 36，ARM64，地址 `192.168.31.37`，与 Linux 同一 LAN。
- APK：`android/app/build/outputs/apk/debug/app-debug.apk`，SHA-256 `c053af8576beb966478d494aef4130197f6c05f0ebc27d61682b13025886aa4c`。
- 覆盖安装新版 APK，保留应用数据及配对；安装前后 `identity.bin` 摘要一致。启动后自动恢复已有受信任会话，UI 显示 `linux · connected`，未重新配对。
- 经用户本次授权，用测试内容覆盖两端剪贴板。未改防火墙、未使用端口转发、root、Shizuku 或额外剪贴板特权。

## 方法

临时安装独立包 `com.lavacrafter.connect.testprobe`，仅包含普通 Activity，无权限、服务或 Provider。它只在点击 Paste 时通过普通 `ClipboardManager` 读取内容；图片由独立 UID 经 `ContentResolver` 打开 Lava Connect 的 FileProvider URI 并解码显示。校验结果写入测试应用私有文件，ADB 仅收集结果和操作 UI，不代替应用读取系统剪贴板。

文字与图片传输期间，系统 `topResumedActivity` 均为测试应用，Lava Connect 留在后台。Linux 使用 `wl-copy` 设置测试内容，等待 3 秒后在测试应用点击 Paste；期间未返回 Lava Connect。全部后台测试完成后才打开 Lava Connect 检查最终状态。

## 已确认结果

| 用例 | 结果 | 证据 |
|---|---|---|
| 后台文字写入 | **通过** | 78 字节 UTF-8，含换行、中文、emoji，接收文本逐字一致；SHA-256 为 `68144c859e66542e5335e68eebaa9a3ea3328aeb59f4a1f14b4a4e332cc262b4`，与 Linux 源内容一致。 |
| 后台 PNG 写入 / 跨应用 URI 消费 | **通过** | 独立应用成功打开 `content://com.lavacrafter.connect.files/…`，解码显示 128×96 彩色 PNG；2959 字节、SHA-256 `1db1876d5cdb089cd09ed988410686f5742ecdb150402bd7d1ff01f67577f098` 均与原文件一致。 |
| 熄屏发送，解锁后不返回 Lava Connect 直接读取 | **通过（短时）** | 设备进入 Dozing 后由 Linux 发送新文字，等待 20 秒，前台服务仍在；用户解锁后直接进入测试应用读取，91 字节内容及 SHA-256 `5801d364c10c2ac5b6bde6f90c79cee56f72d501b7a3af057ee5db35654d1466` 一致。 |
| 应用运行稳定性 | **本轮未见崩溃** | 同一服务进程存活，应用日志未发现 FATAL EXCEPTION、UnsatisfiedLinkError、SecurityException 或写入失败。 |

## 边界与观察

- 熄屏测试时设备在充电，只验证短时熄屏发送后的可用性；未测未充电长时间 Doze / OEM 省电，不断言收到事件和实际写入的精确时间点。
- 验证了独立普通应用的跨 UID 读取、图片 URI 权限和图像解码，不代表所有聊天软件/输入法均支持图片粘贴。
- 本轮未注入写入拒绝异常，失败保留和前台重试由 5 项单元测试覆盖，尚未做平台故障注入。
- APK 更新后的重连曾显示 `peer is busy or disconnected`；连接恢复并成功传输后该旧错误文字仍残留，但设备状态为 connected。此为额外观察，本轮未修改该显示行为。
- 本轮不扩展为首次配对隔离、撤销、能力开关、断网恢复或长期防回环的完整 v0.1 验收。

## 结束状态

- 临时测试应用已卸载；其 APK、源码和测试证据仅位于 `/tmp/opencode/lava-connect-background-device/`，不加入项目生产依赖。
- 新版 Lava Connect 保留安装并保持连接；Linux 原进程未重启、身份与配对数据未清除。
- 剪贴板保留本轮测试文字，不恢复或保存原私人内容。

**结论：Linux → Android 普通后台文字/PNG 写入、跨应用图片 URI 消费及本轮短时熄屏发送用例通过。**
