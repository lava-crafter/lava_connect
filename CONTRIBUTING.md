# Contributing

先阅读 `docs/architecture.md`、`docs/security.md` 和 `docs/initial_plan.md` 中 v0.1 范围。
UI 不实现网络/加密/配对；平台 API 不进入共享协议层；JNI 保持薄包装。

提交前执行 README 的 format / clippy / tests / build，以及 Android 构建。
协议或安全逻辑修改必须附回归测试。平台行为变更按 `docs/testing.md` 记录通过/失败和设备环境，不用假 UI 数据代替真实设备事件。
不要记录剪贴板内容或私钥；不要提交身份目录、Keystore、签名密钥、local.properties 或产物。

源文件使用 `SPDX-License-Identifier: GPL-3.0-or-later`。
若复制第三方实现，先确认兼容许可证，并在 `third_party/README.md` 记录来源、修改、版权和需要保留的 NOTICE。
