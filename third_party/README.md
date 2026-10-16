# Third-party sources

当前未复制 KDE Connect、Sefirah、LinuxDrop、LocalSend、scrcpy 等项目源代码。
Rust / Kotlin 依赖通过各自的包管理器引用，版权和许可证归原作者；发布二进制前需审查完整依赖树及许可证通知。

Linux UI 使用 Slint 的 GPL-3.0 许可路径，与本项目 GPL-3.0-or-later 整体发布方式相容（该组合使用 GPLv3）。
Android Gradle wrapper（若存在）来自 Gradle 官方发行版，Apache-2.0。
来源：https://github.com/gradle/gradle/tree/v9.8.1 。文件：`android/gradlew`、`android/gradlew.bat`、`android/gradle/wrapper/gradle-wrapper.jar`；由 Gradle wrapper 任务更新，脚本保留原版权头，wrapper JAR 内保留 `META-INF/LICENSE`。配置固定发行版本并添加官方下载 SHA-256 校验。
根 LICENSE 的 GPLv3 文本来源为 SPDX license-list / Free Software Foundation，未修改许可证正文。

以后每项复制代码须记录 Source、Original license、Files reused、Modifications、Copyright；不要整项目改名。
