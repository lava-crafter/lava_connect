# v0.1 手动验收

准备：Linux KDE Plasma 6 / Wayland，安装 `wl-clipboard`；Android 8+ ARM64 真机；同一 LAN，允许 multicast UDP 5353 及 QUIC 端口，无 AP client isolation。
以下未实际执行前只能标记 **待验证**，不能标记通过。

| 阶段 | 操作 | 通过条件 | 失败条件 |
|---|---|---|---|
| 启动/JNI | Linux 打开 GUI；Android 启动服务，使用 Rust 测试入口（如提供） | 两端本机指纹可见，无 native 加载错误 | 崩溃、native 库丢失、错误身份 |
| 发现 | 等待 LAN 发现 | 两端显示真实对端名称/可配对 | 虚构已连接、30 秒无对端 |
| 配对隔离 | 仅一端点确认，复制并发送 | 仍未连接，无剪贴板数据传输 | 单端确认后授权 |
| 配对 | 核对两端指纹和六位码，再双方确认 | 两端 Connected | 码不同、未确认就连接 |
| 文字 A→L | Android 复制 `Hello Linux`，回到应用并发送；Linux 粘贴 | 完全一致 | 空白、乱码、不更新 |
| 文字 L→A | Linux 复制 `Hello Android`；Android 应用前台接收，再在其他应用粘贴 | 完全一致 | 空白、乱码、不更新 |
| 图片 A→L | Android 复制或选择截图作为剪贴板并手动发送；Linux 图像应用粘贴 | 可见正确 PNG | 文本 URI、损坏、无法粘贴 |
| 图片 L→A | Linux 复制 PNG；Android 前台接收，在支持图片粘贴的应用粘贴 | 可见正确图片 | URI 权限异常、损坏 |
| 回环 | 双向复制后静置 30 秒 | 无反复变化/高 CPU/持续消息 | 反复收到相同剪贴板 |
| 能力 | 关闭文字或图片并发送该类型，再重启 | 关闭类型不传，设置保留，另一类型仍可用 | 关闭未生效 |
| 重连 | 重启两端、短暂关闭 Wi-Fi 后恢复 | 身份未变，无需再次配对，自动恢复 | 身份改变、需确认、永久离线 |
| 撤销 | 单端撤销信任 | 立即断开，重连不再传数据，需人工配对 | 自动重新授权 |
| Linux 后台 | 关闭主窗口后 Android 发送；再通过托盘打开 | 后台收到，UI 可恢复；Quit 真正退出 | 同步停止、隐藏后打不开 |
| Android 后台 | 应用切后台/熄屏，Linux 发送，再打开 Android | 服务存在；回前台后应用收到内容，不宣称系统限制被绕过 | 服务崩溃、错误声称后台已写入 |
| 拒绝异常 | 超过 8 MiB / 非 PNG / 不匹配指纹 | 明确错误，无崩溃，无泄露 | 无限制读取、内存耗尽、未授权事件 |

自动测试只覆盖共享核心的协议/安全状态与 loopback 传输，不覆盖 Android Keystore、OEM 后台策略、mDNS multicast、wl-clipboard 或目标应用的图片 URI 消费。
另有 Linux MIME 选择单元测试、Android 原始 PNG bytes 保留单元测试，避免 PNG 重新编码导致远端 hash 改变后被 watcher 回传。LAN multicast 测试需显式运行 ignored 测试。
