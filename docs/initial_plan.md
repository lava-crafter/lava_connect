# Lava Connect 开发计划

## 1. 项目概述

**项目名称：** Lava Connect  
**Android 包名：** `com.lavacrafter.connect`  
**许可证：** GNU GPL-3.0  
**目标平台：**

- Android 8.0+，API 26+
- Linux
  - 第一优先级：
    - Arch Linux
    - KDE Plasma 6
    - Wayland
  - 第二优先级：
    - 其他 KDE Plasma 发行版
    - GNOME
    - X11 fallback

Lava Connect 的目标是构建一个统一的 Android ↔ Linux 跨设备互联系统。

它希望逐步替代以下工具的组合：

- KDE Connect
- LocalSend
- ClipCascade / LinuxDrop
- scrcpy
- 部分 Syncthing 使用场景
- 部分 Samsung Multi Control / Apple Continuity 类功能

最终目标不是简单将多个软件套在同一个 GUI 中，而是：

> 使用统一的设备身份、统一的配对机制、统一的连接协议和统一的用户界面，为 Android 和 Linux 提供完整的 Continuity 体验。

---

# 2. 核心设计原则

## 2.1 一个设备，只配对一次

设备之间只建立一次信任关系。

例如：

```text
Galaxy Phone
     │
     │ Pair once
     ▼
Linux PC
```

之后以下功能全部复用同一个设备身份和安全会话：

```text
Clipboard
Files
Notifications
Media
Remote Input
Screen Mirroring
Handoff
Second Screen
Camera
Audio
```

用户不应该分别：

```text
KDE Connect 配对一次
LocalSend 识别一次
ClipCascade 登录一次
scrcpy 再连接一次
```

---

## 2.2 一个协议，多种 Capability

所有设备通过统一 Capability 系统协商功能。

例如：

```text
Galaxy Sxx
────────────────────
Clipboard Text       ✓
Clipboard Image      ✓
Files                ✓
Notifications        ✓
Media                ✓
Remote Input         ✓
Screen Mirror        ✓
Handoff              ✓
Second Screen        —
```

不同版本、不同平台和不同权限状态均通过 Capability 反映。

---

## 2.3 UI 与核心逻辑彻底分离

Android：

```text
Jetpack Compose
       │
   ViewModel
       │
Platform Services
       │
      JNI
       │
  Shared Rust Core
```

Linux：

```text
Slint UI
   │
Controller
   │
Linux Platform Layer
   │
Shared Rust Core
```

Slint 和 Compose 都不得直接承担：

- 网络协议
- 加密
- 配对状态机
- QUIC
- 文件分块
- 重连算法

---

## 2.4 平台功能由平台原生层完成

共享 Rust Core 只负责跨平台逻辑。

系统级 API 分别实现。

Android：

```text
ClipboardManager
NotificationListenerService
ForegroundService
MediaSession
ContentResolver
Storage Access Framework
Shizuku
MediaProjection
Bluetooth
Wi-Fi / Network APIs
```

Linux：

```text
Wayland
wl-clipboard
D-Bus
PipeWire
MPRIS
xdg-desktop-portal
systemd --user
filesystem
BlueZ
NetworkManager
```

---

# 3. 技术栈

## 3.1 Android

语言：

```text
Kotlin
```

UI：

```text
Jetpack Compose
Material 3
```

Android SDK：

```text
minSdk     = 26
compileSdk = 37
targetSdk  = 37
```

包名：

```text
com.lavacrafter.connect
```

推荐 Android 架构：

```text
Compose UI
   │
ViewModel
   │
Repository
   │
Android Platform Services
   │
NativeCore JNI
   │
Rust Core
```

主要 Android 组件：

```text
MainActivity
ConnectService
NotificationListenerService
ClipboardService
FileShareService
MediaService
ShizukuBridge
NativeCore
```

---

# 4. Linux 技术栈

语言：

```text
Rust
```

GUI：

```text
Slint
```

运行架构：

```text
lava-connect
    GUI

lava-connectd
    background daemon
```

推荐长期将 GUI 和后台 daemon 分开。

```text
Slint GUI
   │
 IPC
   │
lava-connectd
   │
Shared Core
```

第一版可以暂时运行在同一个进程中。

当核心功能稳定后，再拆分：

```text
lava-connect
lava-connectd
```

IPC 优先考虑：

```text
D-Bus
```

---

# 5. Shared Core

核心语言：

```text
Rust
```

核心负责：

```text
Device identity
Device discovery abstraction
Pairing
Authentication
Encryption
Capability negotiation
Connection lifecycle
QUIC transport
Protocol serialization
Reconnect
Heartbeat
Clipboard messages
File transfer protocol
Notification protocol
Media protocol
Handoff protocol
Remote control protocol
```

Android 和 Linux 必须共享同一套协议实现。

---

# 6. 网络协议

推荐架构：

```text
Discovery
   ↓
Pairing
   ↓
Authentication
   ↓
Encrypted QUIC Connection
   ↓
Multiplexed Services
```

## 6.1 Discovery

第一阶段：

```text
mDNS
```

第二阶段加入：

```text
Bluetooth LE
```

最终：

```text
mDNS
+
BLE
```

mDNS 用于：

- 同一 LAN 自动发现
- 主机名发现
- IP/端口发现

BLE 用于：

- 睡眠状态附近设备发现
- Proximity
- Wi-Fi 不可用时发现设备
- 辅助自动重连

---

# 7. 配对系统

设备首次配对：

```text
Device A
   │
   │ discovers
   ▼
Device B
```

之后：

```text
Pair request
     ↓
Fingerprint exchange
     ↓
User confirmation
     ↓
Identity saved
```

UI 示例：

```text
Pair with Lava Desktop?

Code

        827 419

[ Cancel ]    [ Pair ]
```

长期身份应基于：

```text
public/private key pair
```

每个设备第一次启动生成永久设备身份。

示例：

```text
DeviceIdentity {
    device_id
    public_key
    device_name
    platform
}
```

---

# 8. Transport

建议使用：

```text
QUIC
```

原因：

- TLS 1.3
- 多 stream
- 避免 head-of-line blocking
- 文件传输与控制消息可以共存
- 天然支持长连接
- 适合移动设备网络切换

建议 Rust 实现：

```text
quinn
```

逻辑：

```text
QUIC connection
│
├── control stream
├── clipboard stream
├── notification stream
├── file stream
├── media stream
├── input stream
└── video stream
```

---

# 9. Protocol

推荐：

```text
Protocol Buffers
```

协议单独维护：

```text
protocol/
```

建议：

```text
protocol/
├── common.proto
├── device.proto
├── pairing.proto
├── capabilities.proto
├── clipboard.proto
├── file.proto
├── notification.proto
├── media.proto
├── input.proto
├── screen.proto
├── handoff.proto
└── system.proto
```

---

# 10. Device Model

基本设备消息：

```protobuf
message DeviceInfo {
  string id = 1;
  string name = 2;
  Platform platform = 3;
  string version = 4;
  repeated Capability capabilities = 5;
}
```

平台：

```protobuf
enum Platform {
  PLATFORM_UNKNOWN = 0;
  PLATFORM_ANDROID = 1;
  PLATFORM_LINUX = 2;
}
```

Capability：

```protobuf
enum Capability {
  CAPABILITY_UNKNOWN = 0;

  CLIPBOARD_TEXT = 1;
  CLIPBOARD_IMAGE = 2;

  FILE_TRANSFER = 10;
  FOLDER_SYNC = 11;

  NOTIFICATIONS = 20;
  NOTIFICATION_REPLY = 21;

  MEDIA_CONTROL = 30;

  REMOTE_INPUT = 40;

  SCREEN_MIRROR = 50;
  APP_STREAMING = 51;

  HANDOFF = 60;

  CONTINUITY_CAMERA = 70;
  SECOND_SCREEN = 71;

  CALLS = 80;
  SMS = 81;

  PROXIMITY = 90;
}
```

---

# 11. Repository 结构

建议：

```text
lava-connect/
│
├── README.md
├── PLAN.md
├── CONTRIBUTING.md
├── CHANGELOG.md
├── LICENSE
│
├── Cargo.toml
│
├── crates/
│   │
│   ├── core/
│   │   ├── Cargo.toml
│   │   └── src/
│   │       ├── lib.rs
│   │       ├── identity/
│   │       ├── pairing/
│   │       ├── devices/
│   │       ├── capabilities/
│   │       ├── transport/
│   │       ├── connection/
│   │       ├── discovery/
│   │       └── crypto/
│   │
│   ├── protocol/
│   │   ├── Cargo.toml
│   │   ├── build.rs
│   │   ├── src/
│   │   └── proto/
│   │
│   ├── clipboard/
│   ├── file-transfer/
│   ├── notifications/
│   ├── media/
│   ├── input/
│   ├── handoff/
│   └── screen/
│
├── android/
│   │
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   │
│   └── app/
│       ├── build.gradle.kts
│       └── src/
│           └── main/
│               ├── AndroidManifest.xml
│               └── java/
│                   └── com/
│                       └── lavacrafter/
│                           └── connect/
│                               │
│                               ├── MainActivity.kt
│                               ├── LavaConnectApplication.kt
│                               │
│                               ├── ui/
│                               ├── viewmodel/
│                               ├── repository/
│                               │
│                               ├── service/
│                               │   ├── ConnectService.kt
│                               │   └── NotificationService.kt
│                               │
│                               ├── clipboard/
│                               ├── notifications/
│                               ├── files/
│                               ├── media/
│                               ├── shizuku/
│                               │
│                               └── native/
│                                   └── NativeCore.kt
│
├── bindings/
│   └── android-jni/
│       ├── Cargo.toml
│       └── src/
│           └── lib.rs
│
├── linux/
│   │
│   ├── app/
│   │   ├── Cargo.toml
│   │   ├── build.rs
│   │   │
│   │   ├── src/
│   │   │   ├── main.rs
│   │   │   ├── controller.rs
│   │   │   └── models/
│   │   │
│   │   └── ui/
│   │       ├── main.slint
│   │       ├── components/
│   │       └── pages/
│   │           ├── devices.slint
│   │           ├── device.slint
│   │           ├── transfers.slint
│   │           └── settings.slint
│   │
│   ├── daemon/
│   │   ├── Cargo.toml
│   │   └── src/
│   │
│   ├── platform/
│   │   ├── clipboard/
│   │   ├── notification/
│   │   ├── dbus/
│   │   ├── wayland/
│   │   ├── pipewire/
│   │   ├── media/
│   │   └── filesystem/
│   │
│   └── packaging/
│       ├── arch/
│       ├── flatpak/
│       ├── desktop/
│       └── systemd/
│
├── third_party/
│   └── README.md
│
├── scripts/
│   ├── build-android.sh
│   ├── build-linux.sh
│   └── generate-protocol.sh
│
└── docs/
    ├── architecture.md
    ├── protocol.md
    ├── security.md
    ├── android.md
    ├── linux.md
    └── roadmap.md
```

---

# 12. Android Gradle 配置

Android：

```kotlin
android {
    namespace = "com.lavacrafter.connect"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lavacrafter.connect"

        minSdk = 26
        targetSdk = 37

        versionCode = 1
        versionName = "0.1.0"
    }
}
```

语言：

```text
Kotlin
```

UI：

```text
Jetpack Compose
Material 3
```

建议模块第一版先保持单 `app` module。

不要过早拆：

```text
feature-device
feature-settings
feature-transfer
...
```

等代码规模真正扩大以后再拆。

---

# 13. Android Native Core

Android 加载 Rust 动态库：

```kotlin
package com.lavacrafter.connect.native

object NativeCore {

    init {
        System.loadLibrary("lava_connect")
    }

    external fun initialize(
        deviceName: String,
    )

    external fun start()

    external fun stop()

    external fun getDeviceId(): String

    external fun sendClipboardText(
        deviceId: String,
        text: String,
    )

    external fun sendClipboardImage(
        deviceId: String,
        image: ByteArray,
    )
}
```

Rust：

```text
bindings/android-jni
```

负责 JNI 映射。

JNI 层应该保持极薄：

```text
Kotlin
  ↓
JNI
  ↓
Rust Core API
```

禁止把核心逻辑直接写在 JNI binding 内。

---

# 14. Android 生命周期

Rust 不得控制 Android 生命周期。

Android：

```text
ConnectService
    │
    ▼
NativeCore.start()
```

Android 系统要求停止：

```text
ConnectService
    │
    ▼
NativeCore.stop()
```

后台连接由 Android Service 管理。

第一版使用：

```text
Foreground Service
```

保持稳定长连接。

通知：

```text
Lava Connect
Connected to Lava Desktop
```

---

# 15. Android 权限策略

原则：

> 没有必要的权限绝不请求。

基础功能：

```text
INTERNET
ACCESS_NETWORK_STATE
ACCESS_WIFI_STATE
POST_NOTIFICATIONS
```

文件：

```text
Storage Access Framework
```

避免传统广泛 storage 权限。

通知同步：

```text
NotificationListenerService
```

高级 Clipboard：

```text
Shizuku
```

屏幕镜像：

```text
MediaProjection
```

Bluetooth：

根据 API level 使用现代 Bluetooth runtime permission。

---

# 16. Clipboard

Clipboard 是第一优先级功能。

必须支持：

```text
Text
Image
```

以后扩展：

```text
HTML
URI
Files
Rich text
```

---

# 17. Clipboard 数据模型

例如：

```protobuf
message ClipboardPayload {

  string id = 1;

  ClipboardType type = 2;

  bytes data = 3;

  string mime_type = 4;

  string source_device = 5;

  int64 timestamp = 6;
}
```

类型：

```protobuf
enum ClipboardType {
  CLIPBOARD_UNKNOWN = 0;
  CLIPBOARD_TEXT = 1;
  CLIPBOARD_IMAGE = 2;
  CLIPBOARD_HTML = 3;
  CLIPBOARD_FILE = 4;
}
```

---

# 18. Clipboard Loop 防护

这是必须第一天设计好的功能。

场景：

```text
Android copy
    ↓
Linux receives
    ↓
Linux clipboard changes
    ↓
Linux sends
    ↓
Android receives
    ↓
无限循环
```

解决方法：

每一个 Clipboard Item 带：

```text
clipboard_id
origin_device_id
content_hash
```

收到远端 Clipboard 后：

```text
mark as remote
```

本地 clipboard watcher 检测时：

```text
if known remote hash:
    do not resend
```

---

# 19. Linux Clipboard

Wayland 第一优先。

实现优先顺序：

```text
Wayland native / wl-clipboard
```

第一版允许调用：

```text
wl-copy
wl-paste
```

稳定以后再考虑减少外部依赖。

必须支持 MIME：

```text
text/plain
image/png
```

第一版图片统一转成：

```text
PNG
```

减少格式复杂度。

---

# 20. Android Clipboard

普通模式：

```text
用户主动触发
```

增强模式：

```text
Shizuku
```

实现完整自动 clipboard sync。

设置 UI：

```text
Clipboard synchronization

[✓] Text
[✓] Images

Automatic background clipboard
[ Requires Shizuku ]

Status:
Connected
```

---

# 21. File Transfer

文件传输优先实现：

```text
Send one file
Send multiple files
Android Share Sheet
Linux drag-and-drop
```

Android：

```text
ACTION_SEND
ACTION_SEND_MULTIPLE
```

Linux：

```text
Drag files onto device
```

协议：

```text
metadata
  ↓
accept/reject
  ↓
chunked transfer
  ↓
hash validation
```

---

# 22. File Metadata

```protobuf
message FileMetadata {
  string id = 1;
  string name = 2;
  uint64 size = 3;
  string mime_type = 4;
  bytes sha256 = 5;
}
```

文件流使用 QUIC 独立 stream。

---

# 23. Notifications

第一版：

```text
Android → Linux
```

Android：

```text
NotificationListenerService
```

同步：

```text
App name
Title
Text
Icon
Timestamp
```

第二阶段：

```text
Dismiss
Inline Reply
Actions
```

---

# 24. Linux Notification

第一版使用：

```text
org.freedesktop.Notifications
```

也就是标准 Linux Desktop Notifications。

这样 KDE 和 GNOME 都可支持。

---

# 25. Media Control

第二阶段实现。

Android：

```text
MediaSession
```

Linux：

```text
MPRIS
```

功能：

```text
Play
Pause
Next
Previous
Seek
Volume
Metadata
Album art
```

长期目标：

```text
Android media
    ↕
Linux MPRIS
```

---

# 26. Remote Input

目标类似：

```text
Samsung Multi Control
Apple Universal Control
```

Linux 鼠标移动到屏幕边缘：

```text
Linux
    │
    │ edge crossing
    ▼
Android
```

Android 显示远程 cursor。

输入：

```text
Mouse
Keyboard
Scroll
Clipboard
Drag/drop later
```

Linux 优先使用：

```text
xdg-desktop-portal
RemoteDesktop
InputCapture
```

尽量避免直接依赖 compositor 私有协议。

---

# 27. Screen Mirroring

后续版本实现。

优先策略：

> 复用 scrcpy 思路，而不是重新发明 Android video capture。

功能：

```text
phone screen
    ↓
H.264 / H.265
    ↓
Linux
    ↓
render
```

输入：

```text
Linux mouse/keyboard
    ↓
Android
```

---

# 28. Handoff

Handoff 必须作为通用协议，而不是硬编码浏览器。

数据：

```protobuf
message HandoffActivity {
  string app_id = 1;
  string activity_type = 2;

  string title = 3;

  string url = 4;

  bytes state = 5;
}
```

第一版 Handoff：

```text
Browser URL
```

例如：

```text
Android Chrome
example.com/page
       ↓
Linux
Continue browsing
       ↓
Firefox
```

后续开放：

```text
plugin/application integration
```

---

# 29. Second Screen

这是后期功能。

目标：

```text
Linux virtual display
       ↓
H.264 / H.265
       ↓
Android tablet
```

Android 返回：

```text
touch
mouse
keyboard
stylus
pressure
tilt
```

Linux：

```text
Wayland virtual monitor
PipeWire capture
```

KDE Plasma 优先研究：

```text
xdg-desktop-portal
KWin
krfb / PipeWire
```

---

# 30. Continuity Camera

后期：

```text
Android Camera
     ↓
video stream
     ↓
Linux
     ↓
v4l2loopback
```

Linux 应用看到：

```text
Lava Connect Camera
```

因此：

```text
OBS
Discord
Firefox
Zoom
```

都可以直接使用。

---

# 31. Audio Continuity

长期功能。

Android microphone：

```text
Android Mic
   ↓
Linux virtual microphone
```

Android speaker：

```text
Linux audio
   ↓
Android speaker
```

Linux：

```text
PipeWire virtual node
```

---

# 32. GUI 设计

## Linux

Slint。

首页：

```text
┌────────────────────────────────────────────┐
│ Lava Connect                         ⚙     │
├───────────────┬────────────────────────────┤
│ Devices       │ Galaxy Phone               │
│               │ ● Connected                │
│ ● Galaxy      │                            │
│ ● Tablet      │ Clipboard          ON      │
│               │ Notifications      ON      │
│               │ File sharing       ON      │
│               │ Handoff            ON      │
│               │                            │
│               │ [ Send File ]              │
│               │ [ Mirror Phone ]           │
└───────────────┴────────────────────────────┘
```

---

# 33. Android GUI

首页：

```text
Lava Connect

Lava Desktop
● Connected

Clipboard              ON
Notifications          ON
Files                   ON

Send File

Recent Transfers
────────────────────
photo.png
→ Lava Desktop
Complete
```

---

# 34. Settings

设置分类：

```text
Connection
Clipboard
Notifications
Files
Media
Privacy
Advanced
About
```

---

# 35. 系统托盘

Linux 必须支持 system tray。

菜单：

```text
Lava Connect

Galaxy Sxx       Connected
Tablet           Offline

Send File
Open Lava Connect

Pause Sync
Quit
```

关闭主窗口：

```text
daemon continues
```

---

# 36. 后台 daemon

最终：

```text
lava-connectd
```

systemd：

```text
~/.config/systemd/user/lava-connect.service
```

登录自动启动。

职责：

```text
Discovery
Connections
Clipboard
Notifications
Files
Media
Remote control
```

GUI 可以随时退出。

---

# 37. 数据目录

Linux：

```text
~/.config/lava-connect/
~/.local/share/lava-connect/
~/.cache/lava-connect/
```

例如：

```text
~/.config/lava-connect/
├── config.toml
└── trusted_devices.toml
```

敏感 private key 不应以明文普通 config 形式随意存储。

尽量使用系统 keyring。

---

# 38. Android Storage

应用数据：

```text
Context.filesDir
```

配置：

```text
DataStore
```

密钥：

```text
Android Keystore
```

---

# 39. Security

必须遵守：

```text
Zero trust before pairing
```

任何未配对设备不能：

```text
read clipboard
send files automatically
read notifications
control input
mirror screen
```

---

# 40. Transport Security

要求：

```text
Encrypted by default
Authenticated devices only
No plaintext LAN protocol
```

用户必须能看到：

```text
Trusted Devices
```

并可以：

```text
Unpair
Revoke key
```

---

# 41. Capability Permission

每项能力都应可单独关闭：

```text
Galaxy Sxx

Clipboard
[ ON ]

Notifications
[ ON ]

File access
[ ON ]

Remote control
[ OFF ]
```

高风险能力默认更保守：

```text
Remote input
Screen control
Camera
Microphone
```

---

# 42. 外部项目使用策略

Lava Connect 可以学习、复用或移植：

## KDE Connect

参考：

```text
device model
notification design
media control
UX
```

GPL 代码允许在 GPL-3.0 项目中使用，但必须保留许可证和归属信息。

---

## Sefirah

重点参考：

```text
Android clipboard
Shizuku
notifications
scrcpy integration
device synchronization
```

---

## LinuxDrop

重点参考：

```text
Android ↔ Linux image clipboard
Shizuku clipboard
Wayland clipboard
```

---

## LocalSend

重点参考：

```text
file transfer UX
LAN discovery
transfer workflow
```

---

## scrcpy

重点参考或集成：

```text
Android screen capture
codec
input injection
virtual display
audio forwarding
```

---

## Vortex / Linux Link

重点参考：

```text
Continuity architecture
QUIC
Handoff
Universal Control
Second Screen
Linux integration
```

---

# 43. Third-party 原则

不要：

```text
复制整个项目
然后修改名字
```

应该：

```text
阅读实现
理解架构
抽取需要的模块
保留许可证
重构进入 Lava Connect architecture
```

所有复制或修改代码必须记录来源。

例如：

```text
third_party/
README.md
```

记录：

```text
Source
Original license
Files reused
Modifications
Copyright
```

---

# 44. License

整个项目：

```text
GPL-3.0-or-later
```

根目录：

```text
LICENSE
```

源文件推荐使用 SPDX：

```text
SPDX-License-Identifier: GPL-3.0-or-later
```

对于 Apache/MIT/BSD 来源代码：

保留：

```text
copyright
LICENSE
NOTICE
```

---

# 45. Versioning

使用：

```text
Semantic Versioning
```

例如：

```text
0.1.0
0.2.0
0.3.0
...
1.0.0
```

---

# 46. 第一版 v0.1.0

目标：

> 完成 Android 和 Linux 之间的基本安全连接与双向 Clipboard。

必须实现：

```text
Android app starts
Linux app starts
        ↓
LAN discovery
        ↓
pair
        ↓
encrypted connection
        ↓
text clipboard
        ↓
image clipboard
```

v0.1.0 不做：

```text
notifications
files
media
scrcpy
handoff
second screen
```

---

# 47. v0.1.0 Milestone

成功标准：

## Test 1

Android：

```text
Copy:
Hello Linux
```

Linux：

```text
Ctrl + V
```

得到：

```text
Hello Linux
```

---

## Test 2

Linux：

```text
Copy:
Hello Android
```

Android：

```text
Paste
```

得到：

```text
Hello Android
```

---

## Test 3

Android：

```text
Screenshot
Copy image
```

Linux：

```text
Ctrl + V
```

可以粘贴图片。

---

## Test 4

Linux：

```text
Copy image
```

Android：

```text
Paste
```

得到图片。

---

# 48. v0.2.0

加入：

```text
File transfer
```

支持：

```text
Android Share Sheet
Linux drag-and-drop
multi-file
transfer progress
cancel
SHA-256 verify
```

---

# 49. v0.3.0

加入：

```text
Android notifications
```

支持：

```text
Receive
Display
Dismiss
Reply
```

Linux 使用：

```text
org.freedesktop.Notifications
```

---

# 50. v0.4.0

加入：

```text
Media Control
```

Android：

```text
MediaSession
```

Linux：

```text
MPRIS
```

---

# 51. v0.5.0

加入：

```text
screen mirroring
```

功能：

```text
Android screen
Linux window
mouse
keyboard
audio
```

基于 scrcpy 设计。

---

# 52. v0.6.0

加入：

```text
Universal Control
```

目标：

```text
Linux mouse
      ↓
Android
```

支持：

```text
mouse
keyboard
scroll
```

---

# 53. v0.7.0

加入：

```text
Handoff
```

第一阶段：

```text
Browser URLs
```

第二阶段：

```text
generic activity system
```

---

# 54. v0.8.0

加入：

```text
Calls
SMS
Android storage browsing
```

---

# 55. v0.9.0

加入：

```text
Continuity Camera
Android microphone
Android speaker
```

---

# 56. v1.0.0

达到：

```text
Stable protocol
Stable pairing
Stable upgrade path
Stable clipboard
Stable files
Stable notifications
Stable media
Stable screen mirroring
Stable remote input
Stable handoff
```

支持：

```text
Android 8.0+
Linux Wayland
KDE Plasma 6
```

---

# 57. Post 1.0

计划：

```text
Tablet Second Screen
Stylus input
Multi-device control
Clipboard history
Folder sync
Proximity lock
BLE discovery
Automatic hotspot
Audio handoff
App streaming
Remote filesystem mount
```

---

# 58. 第一阶段实际开发顺序

严格按照下面顺序，不要跳。

## Step 1

创建仓库：

```bash
mkdir lava-connect
cd lava-connect

git init
```

---

## Step 2

建立 Rust workspace：

```text
Cargo.toml
crates/core
crates/protocol
bindings/android-jni
linux/app
```

目标：

```text
cargo build
```

成功。

---

## Step 3

建立 Linux Slint Hello World。

目标：

```text
cargo run
```

出现：

```text
Lava Connect
```

窗口。

---

## Step 4

建立 Android 项目。

包名：

```text
com.lavacrafter.connect
```

配置：

```text
minSdk 26
compileSdk 37
targetSdk 37
```

运行：

```text
Hello Lava Connect
```

---

# 59. Step 5 — JNI

编译：

```text
Rust
↓
liblava_connect.so
↓
Android
```

Android 点击：

```text
Test Rust
```

Rust 返回：

```text
Hello from Lava Connect Core
```

显示到 Compose UI。

这是第一个关键 milestone。

---

# 60. Step 6 — Core Event System

Rust Core 定义：

```text
CoreEvent
```

例如：

```rust
enum CoreEvent {
    DeviceDiscovered(...),
    DeviceConnected(...),
    DeviceDisconnected(...),
    ClipboardReceived(...),
    TransferProgress(...),
}
```

Android 和 Linux UI 都监听同样的事件模型。

---

# 61. Step 7 — Discovery

实现：

```text
mDNS
```

Android：

```text
Lava Desktop
Available
```

Linux：

```text
Galaxy Sxx
Available
```

---

# 62. Step 8 — Pairing

实现：

```text
Identity
Fingerprint
Pair Request
Verification Code
Trust store
```

配对以后：

```text
Connected
```

---

# 63. Step 9 — Persistent Reconnect

应用重启：

```text
Android
Linux
```

能够自动识别：

```text
already trusted
```

并重新建立连接。

无需重新配对。

---

# 64. Step 10 — Text Clipboard

先实现：

```text
manual send
```

例如 Android：

```text
Send Clipboard
```

确认协议正确。

然后才加入：

```text
automatic watcher
```

---

# 65. Step 11 — Image Clipboard

统一网络格式：

```text
PNG
```

Android：

```text
Bitmap
↓
PNG
↓
Rust
```

Linux：

```text
Rust
↓
image/png
↓
Wayland clipboard
```

---

# 66. Step 12 — Background

Android：

```text
ForegroundService
```

Linux：

```text
systemd --user
```

做到：

```text
GUI closed
clipboard still works
```

---

# 67. v0.1 Release

达到这一点：

```text
Pair once
Auto reconnect
Text clipboard
Image clipboard
Background service
```

发布：

```text
v0.1.0
```

---

# 68. Build Targets

Rust Linux：

```text
x86_64-unknown-linux-gnu
aarch64-unknown-linux-gnu
```

Android：

```text
arm64-v8a
```

第一优先级。

以后：

```text
x86_64
armeabi-v7a
```

可以按需求支持。

Android API 26 已经使 arm64 成为非常合理的第一目标。

---

# 69. Linux Package

优先：

```text
Arch PKGBUILD
```

之后：

```text
Flatpak
AppImage
```

不建议第一天就解决所有发行版打包问题。

---

# 70. CI

GitHub Actions：

```text
cargo fmt
cargo clippy
cargo test
Linux build
Android build
Protocol generation check
```

每个 PR 必须通过：

```text
format
lint
tests
build
```

---

# 71. Rust Code Quality

启用：

```text
rustfmt
clippy
```

尽量：

```text
#![deny(unsafe_op_in_unsafe_fn)]
```

核心协议层减少 unsafe。

JNI 可以存在必要 unsafe，但隔离在：

```text
bindings/android-jni
```

---

# 72. Kotlin Code Quality

使用：

```text
ktlint
```

Compose 遵循单向数据流：

```text
UI
↓ intent
ViewModel
↓
Repository
↓
Service/Core
```

状态：

```text
StateFlow
```

---

# 73. Logging

Rust：

```text
tracing
```

Android：

```text
Logcat
```

Release 默认不得记录：

```text
clipboard contents
notification contents
private keys
file data
```

---

# 74. Debug UI

开发版本可增加：

```text
Developer Options

Device ID
Connection state
Local IP
Remote IP
Capabilities
Protocol version
Ping
Reconnect
Logs
```

对开发网络协议非常重要。

---

# 75. Protocol Version

每个连接协商：

```text
protocol_version
```

例如：

```text
1
```

以后保持：

```text
Backward compatibility
```

不要让：

```text
Android update
```

直接导致：

```text
Linux old client completely unusable
```

---

# 76. Feature Flags

协议应该支持：

```text
capabilities
```

而不是依赖：

```text
version >= x
```

例如：

```text
if remote supports CLIPBOARD_IMAGE:
    enable image clipboard
```

---

# 77. Error Handling

UI 不应该直接显示：

```text
QUIC error code 0x14
```

应该映射：

```text
Connection lost

Trying to reconnect…
```

开发日志保留真实错误。

---

# 78. Offline-first 原则

所有核心互联功能默认：

```text
LAN only
```

不要求：

```text
Cloud account
Google account
Lava account
External server
```

未来如果增加远程互联网连接：

```text
optional
```

并且不能成为本地 LAN 功能的依赖。

---

# 79. Privacy 原则

默认：

```text
No telemetry
No account
No cloud
No advertising
```

如果以后加入 crash report：

```text
opt-in
```

---

# 80. 产品目标

Lava Connect 最终应该让用户产生这样的感觉：

```text
我的 Android 手机
我的 Android 平板
我的 Linux PC

不是三台独立机器。

而是三个屏幕、
三套硬件、
一个本地设备生态。
```

---

# 81. 最终架构总结

```text
                  Android
          Kotlin + Jetpack Compose
                    │
       Android Platform Services
                    │
                   JNI
                    │
              Shared Rust Core
                    │
       ┌────────────┼────────────┐
       │            │            │
   Protocol      Crypto        QUIC
       │            │            │
       └────────────┼────────────┘
                    │
              Local Network
                    │
              Shared Rust Core
                    │
         Linux Platform Layer
                    │
         ┌──────────┼──────────┐
         │          │          │
      Wayland     D-Bus     PipeWire
         │          │          │
         └──────────┼──────────┘
                    │
                  Slint
                    │
              Lava Connect
```

---

# 82. 第一阶段最终目标

第一阶段不要被后面的 Second Screen、scrcpy、Handoff 分散注意力。

唯一目标：

```text
Android
        ↕
Secure connection
        ↕
Linux
```

并可靠实现：

```text
Pairing
Auto reconnect
Text clipboard
Image clipboard
Background operation
```

如果这五件事做得足够稳定，Lava Connect 就已经不再是一个实验项目，而是一个真正可以每天使用的软件。

之后所有功能：

```text
Files
Notifications
Media
Screen
Input
Handoff
Camera
Second Screen
```

都只是在这个稳定 Core 上继续添加 Capability。
