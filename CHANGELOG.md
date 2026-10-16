# Changelog

## 0.1.0 — development preview

- Rust workspace、v1 protobuf、共享 QUIC/TLS Core 和核心事件模型。
- mDNS LAN 定位、会话绑定双端验证码、持久化信任、重连、撤销。
- 双向 UTF-8 / PNG 剪贴板协议、大小限制、防回环、单设备能力授权。
- Slint Linux UI、Wayland clipboard、系统托盘、headless 与 systemd 模板。
- Compose Android UI、JNI 和前台连接服务，普通模式剪贴板。
- Linux → Android 接收剪贴板直接尝试后台写入文字/PNG；异常时保留最新内容并回前台重试，后台读取仍受限。
- 核心自动测试、构建脚本、CI 和手动验收说明。
- 最多 16 个本地同步组、逐设备成员及组隔离；旧信任库迁移和旧协议 peer 默认组兼容（本轮最终验证及多机真机组验收待完成，见 [状态](docs/release-status.md)）。

真实设备验收和独立安全审计未完成，不是稳定版发布声明。
