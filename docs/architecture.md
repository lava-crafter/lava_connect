# v0.1 架构

同步组及其持久化、成员管理和切组事件语义见[同步组说明](groups.md)。组配置与设备信任共存于 `trusted_devices.json` version 2；旧 bare device map 在加载时迁移，首次写入保存新格式。任何信任/组持久化错误均 fail closed：核心断开或停止，修复存储后重启。

```text
Compose → ViewModel → Repository → ConnectService → JNI ┐
                                                       ├→ lava-core → QUIC / mDNS
Slint → controller → Linux clipboard platform layer ───┘
```

- `protocol/connect.proto` 是唯一线上协议定义；`crates/protocol` 构建生成 Prost 类型。
- `crates/core` 拥有身份、信任库、能力授权、配对状态机、连接、发现、重连和防回环。
- `CoreHandle` 启动专用 Rust 线程 / Tokio runtime，命令和网络消息都有有界队列；快照供 UI 使用，剪贴板事件供平台层消费。stop 关闭端点并 join 工作线程。
- 身份目录独占文件锁避免 GUI/headless 或重复 Android 初始化创建两套竞争会话。信任库临时文件 + fsync + rename 更新；存储失败不开放新信任。
- Linux UI 不执行网络和阻塞剪贴板命令。controller 在线程中调用 `wl-copy` / `wl-paste`，限制数据大小和执行时间。ksni 托盘负责显示/隐藏 UI；关闭窗口不停止共享核心，显式退出才停止。
- 两端 UI 各自实现、共享一套设计语言（来源 lava_note 的 MD3 Expressive 设计包），令牌、组件对照和平台差异见[界面设计](ui-design.md)。
- Android 生命周期由 Kotlin 服务控制，Rust 不启动/停止 Android 组件。JNI 只做类型转换和安全句柄注册。平台监听不能绕过 Android 剪贴板权限模型。
- JNI 的 JSON bytes 使用 Base64（不是整数数组），避免图像在 Java 堆上膨胀为数百万个装箱对象；线上 protobuf 仍使用原始 bytes。
- 文字和图片在 v0.1 共用一个有界双向 control stream。独立 capability stream、文件和视频 multiplexing 是后续扩展，不创建空模块伪装实现。

首次配对仅交换认证证书、Hello、配对请求与用户确认。双方确认后写入受信任指纹并发送 Ready；只有本地信任 + 远端 Ready 的会话才可传输授权能力。重连复用固定的身份和授权，不再次显示验证码。
