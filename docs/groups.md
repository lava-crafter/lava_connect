# 同步组

一台设备最多配置 16 个同步组，包括不可删除的 `default`（nil UUID）。每台设备同一时刻只有一个 active group。用户可创建或用共享的小写 UUID 加入组；组名仅本机显示，不参与协议。新建自定义组成员为空，用户须在每台设备上手动勾选其他 trusted devices。组 UUID 是标识而非 secret 或共享密钥；无云服务或 relay。

组不会建立信任。设备仍须逐对完成认证配对；组码不替代证书指纹、信任或文字/图片 capability。想同步的每一对设备都必须互相加入对方的组成员列表，并各自将该 UUID 设为 active，且 capability 允许。系统不转发：需要全互通的 n 台设备须配置每对互配，最多 16 个连接 session/设备。

默认组迁移既有全部 trusted devices 并保留原 capability；新配对默认进入 default。重新配对不会恢复已关闭的成员或 capability。default 对 legacy peer 保持兼容；默认组就绪不表示旧客户端支持自定义组。协议 v1 additive 协商 Ready 字段 `clipboard_groups_supported` / `clipboard_group_ids`；只公布当前组中已将该 peer 设为成员的组。ClipboardPayload `group_id` 空值表示 default；legacy peer 仅能使用 default，禁止向其发送自定义组内容。

组创建、切换、成员开关和删除与设备信任保存在 `trusted_devices.json` version 2（`devices`、`groups`、`active_group_id`）。旧 bare device map 自动加载迁移，并在首次写入时保存新格式。升级前建议备份数据目录；旧 binary 不能读取新格式，不支持降级。升级不引入外部依赖。任何 trust/group 持久化错误都会使核心断开或停止（fail closed）；修复存储后重启，不应通过删除/重置数据绕过错误。

平台剪贴板事件携带 group_id。切换或退出组时丢弃旧组排队事件及 Android 待重试内容；不自动重发当前剪贴板或历史，离线内容不补发。多个对端内容同时到达时，各自成为本机最后接收内容；不提供分布式全序。Android 仍可后台写入剪贴板，读取仍限前台/手动操作。

## 使用步骤

1. 升级各设备应用并启动连接服务。已有配对自动保留在默认组；新设备仍先在需要直连的每一对设备上核对验证码并配对。
2. Linux 在组设置中输入组名后点「创建并切换」；Android 在「Clipboard groups → Manage」中创建。记下生成的 UUID 组码。
3. 其他设备输入同一组码和本地显示名称后加入。同名不同组码不是同一组。
4. 各设备均选中该组，并对每个需要同步的已配对对端开启「当前组成员」/「Sync in group」。双方都同意后，对端组就绪状态变为是。
5. 复制新的文字/PNG，或点击发送；只发往本机有直接连接的授权组员。只配对 A↔B、B↔C 时，A 的内容不会经 B 到达 C。
6. 删除当前自定义组会切回默认组；默认组仍可能有其他已授权设备。切组不清空系统剪贴板，需敏感隔离时请同时管理本机剪贴板内容。
