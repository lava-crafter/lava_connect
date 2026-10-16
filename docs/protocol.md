# 协议 v1

组扩展为 v1 additive：Ready 可协商 `clipboard_groups_supported` 与 `clipboard_group_ids`；只公布当前组中将该 peer 列为成员的组。ClipboardPayload 的 `group_id` 为空表示默认组。旧 peer 只能使用默认组，禁止向其发送自定义组内容。详细成员/兼容规则见[同步组说明](groups.md)。

发现服务：`_lava-connect._udp.local.`，TXT：`id`、`name`、`platform`、`version=1`。广告不可信，仅用于定位。
连接后，广告 `id` 必须等于 TLS 对端叶证书 DER 的 SHA-256（64 位小写十六进制），Hello 中的 id 也必须一致。

QUIC 使用 TLS 1.3、ALPN `lava-connect/1`，两端都必须提供证书并证明私钥持有。仅允许一个双向流，无单向流、无 0-RTT。
帧 = 网络字节序 u32 长度 + protobuf Envelope；长度上限 8 MiB + 4096。长度先验证后分配。

1. 首帧 Hello，协商协议版本与 text/image capabilities。v1 不接受其他版本。
2. PairRequest 展示会话绑定六位码；PairConfirm 代表该端用户确认。只确认一端不保存身份、不发送剪贴板。
   UI 确认命令必须携带用户看到的 pairing_code，核心再次比较当前会话，拒绝失效弹窗确认。
3. 两端确认后持久化信任，互发 Ready（允许的 capabilities）。已信任重连直接交换 Ready。
4. Clipboard 包含 UUID、origin_device_id、MIME、bytes、毫秒时间戳、SHA-256(MIME + NUL + bytes)。接收时校验授权、来源、大小、类型、哈希，拒绝未授权消息并关闭连接。
5. Ping 保持应用流活性；QUIC 自带保活和空闲超时。未授权/未完成配对会话 120 秒后断开。

剪贴板类型：UTF-8 `text/plain`、`image/png`，最大 8 MiB。
PNG 必须有完整 IHDR/IEND，尺寸限制为 1600 万像素；核心不解码图像像素，平台层负责实际解码。
最近 256 个 ID 和 hash 用于去重；30 秒内相同 hash 不从本地 watcher 重发。此窗口也会抑制相同内容的重复手动发送。
平台实际写入前提交 MarkClipboardApplied 刷新该 hash 的抑制窗口；Android 收到后直接尝试后台写入，抛出异常则保留最新内容，回前台重试时再次刷新窗口。已有 PNG 不重新编码，保留同一 hash。
远端内容不被转发给第三台设备。payload 不会进入日志。

配对码：TLS exporter（label `lava-connect pairing v1`）与排序后的双方证书指纹一起 SHA-256，前 32 位模 1,000,000，格式 `123 456`。与当前 TLS 会话绑定，可阻止中间人向两端显示同一码（用户仍需真实比较）。
