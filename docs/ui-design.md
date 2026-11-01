# 界面设计

Android（Jetpack Compose）和 Linux（Slint）各自实现界面，但共享同一套设计语言，来源是 lava_note 的 `ui/design` 设计包（Material 3 Expressive，配色由强调色生成，扁平、带色调，没有阴影和水波纹）。两端看起来是同一个产品，交互各自符合平台习惯：Android 面向触屏，Linux 面向键鼠和可缩放的窗口。

| | Android | Linux |
|---|---|---|
| 设计包 | `android/app/src/main/java/com/lavacrafter/connect/ui/design/`（从 lava_note 拷贝，Apache-2.0） | `linux/app/ui/theme.slint` + `linux/app/ui/components/` |
| 配色计算 | `LavaColorScheme.kt`（Compose `lerp`，Oklab） | `linux/app/src/theme.rs`（同一算法的 Rust 移植） |
| 页面 | `ui/devices`、`ui/groups`、`ui/settings` | `ui/pages/*.slint` |
| 文案 | `res/values/strings.xml`、`res/values-zh/strings.xml` | `@tr()` + `linux/app/i18n/zh_CN/LC_MESSAGES/lava-connect.po` |
| 外观设置存储 | SharedPreferences `appearance` | `$XDG_CONFIG_HOME/lava-connect/ui.json` |

## 设计令牌

**配色**：所有颜色角色由一个强调色生成，大部分容器和表面色是固定中性色与强调色在 Oklab 空间的混合（`accentOverlay`）。强调色预设：`#1976D2`（默认）、`#42A5F5`、`#3F51B5`、`#0097A7`、`#0D47A1`。深色模式可跟随系统或手动选择。

`design/palette-fixture.json` 记录默认强调色下浅色、深色全部角色的期望值。Android 单测 `LavaColorSchemeTest` 和 Rust 单测 `theme::tests::palette_matches_fixture` 都对照它检查，每个通道允许 ±1 误差。修改配色公式后，用 `LAVA_WRITE_PALETTE_FIXTURE=1 cargo test -p lava-connect palette` 重新生成，再跑 Android 单测确认两端一致。

**其余令牌**（两端数值相同）：

| 类别 | 值 |
|---|---|
| 圆角 Expressive | 8 / 12 / 16 / 24 / 32（xs / sm / md / lg / xl）；Square 全部为 0 |
| 间距 | 4 / 8 / 12 / 16 / 24 |
| 字号 | Material 3 字号表；标题用 SemiBold |
| 动效 | 120 / 200 / 320 ms |
| 状态层 | 悬停 8%、按下 12%、选中 16%；选中边框为主色 72% |

圆角用法：按钮、提示框用 sm，卡片用 md，列表行和导航项用 lg（药丸形），对话框用 xl。

## 组件对照

| Compose（`ui/design/component`） | Slint（`ui/components`） | 说明 |
|---|---|---|
| `LavaInteractiveSurface` | `Interactive` | 无水波纹；悬停/按下用状态层；键盘焦点显示 2px 主色焦点环 |
| `Button` / `FilledTonalButton` / `OutlinedButton` | `Button { kind }` | filled、tonal、outlined、text、danger、danger-outlined |
| `LavaIconButton` + `LavaTooltip` | `IconButton` | 图标按钮都带悬停提示 |
| `LavaCard` | `Card` / `Panel` | surface-container-low，md 圆角；选中为 primary-container + 主色细边框 |
| `LavaSegmentedSelector` | `Segmented` | |
| `LavaDialog` | `Dialog` | 危险操作的确认按钮用 error 配色 |
| `LavaContextActions`（⋮ 和长按） | `ContextMenuArea`（右键） | 同一组操作 |
| `LavaFabMenu` | 顶部工具栏按钮 | 桌面端不用 FAB |
| `SettingsGroup` / `ListItem` | `SectionHeader` / `ListRow` | |

## 页面结构

三个页面：**设备**、**剪贴板组**、**设置**。

- **设备**：本机信息、设备列表（平台图标、名称、状态标签、主操作）、设备详情（指纹、配对代码、文字/图片/组成员开关、撤销信任）。
- **剪贴板组**：组列表（点击即切换当前组）、当前组详情（组码、成员开关、删除）。创建和加入用对话框。
- **设置**：连接、后台读取（仅 Android）、外观（主题、强调色、圆角风格）、关于。

## 平台差异

| | Android | Linux |
|---|---|---|
| 导航 | 宽度 < 600dp 用底部导航栏；600–840dp 用导航栏（NavigationRail）；≥ 840dp 导航栏 + 设备页列表/详情双栏 | 左侧固定侧栏（248px，顶部是本机信息，底部是暂停/恢复）；窗口宽度 < 760px 时收成只显示图标的窄栏 |
| 设备详情 | 窄屏进入详情页，宽屏双栏 | 始终是主从双栏 |
| 发送 | 设备页的 FAB 菜单：发送剪贴板 / 图片 / 文字 | 工具栏：发送文字、发送剪贴板 |
| 更多操作 | ⋮ 按钮或长按 | 右键菜单 |
| 控件尺寸 | 48dp 触控目标 | 控件高 36px，列表行高 56px |
| 状态和错误 | Snackbar | 底部状态栏（同步状态、已连接数量、最近事件） |
| 关闭 | 系统返回 | 关闭窗口隐藏到托盘；退出走托盘菜单或 Ctrl+Q |

Linux 键盘快捷键：

| 按键 | 作用 |
|---|---|
| Ctrl+1 / 2 / 3 | 切换到设备 / 剪贴板组 / 设置 |
| Ctrl+Shift+V | 发送剪贴板 |
| Ctrl+T | 发送文字 |
| Ctrl+F | 筛选设备 |
| ↑ / ↓ | 在设备列表中移动选中项 |
| Enter | 对选中设备执行主操作（配对或核对代码）；对话框中为确认 |
| Esc | 关闭对话框；否则清空筛选或取消选中 |
| Ctrl+W | 隐藏到托盘 |
| Ctrl+Q | 退出 |
| Tab | 按顺序切换焦点 |

## 文案与术语

界面源语言为英文，中文跟随系统语言。两端各自维护文案文件，改动时按下表统一用词，并同时更新 Android 的 `values-zh/strings.xml` 和 Linux 的 `.po`。Rust 侧产生的少量文字（托盘菜单、剪贴板错误）用 `main.rs` 的 `t(en, zh)` 选择。

| English | 中文 |
|---|---|
| Pair / Pairing code | 配对 / 配对代码 |
| Unpair | 撤销信任 |
| Trusted | 已信任 |
| Clipboard group / Group code | 剪贴板组 / 组码 |
| Default (group) | 默认 |
| Sync in active group | 在当前组中同步 |
| Device ID / fingerprint | 设备 ID / 指纹 |
| Background reading | 后台读取 |
| Pause / Resume sync | 暂停同步 / 恢复同步 |

默认组的 ID `00000000-0000-0000-0000-000000000000` 以 `crates/core` 的 `DEFAULT_GROUP_ID` 为准，两端 UI 各有一份常量；核心里的组名固定为 "Default"，界面显示时按语言替换。

## 图标

Android 使用 Material Icons Extended。Linux 使用 Material Symbols（Outlined）SVG，放在 `linux/app/ui/icons/`，许可证为 Apache-2.0。
