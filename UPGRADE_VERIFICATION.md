# 更新与验证记录

日期：2026-11-04。本文记录 0.7.1 的依赖更新、正确性修复和发布验证范围。安装与迁移见 [MIGRATION_0.7.1.md](MIGRATION_0.7.1.md)。

## 已完成的更新

| 项目 | 原版本 | 当前 |
|---|---|---|
| Gradle wrapper | 8.14.5 | 8.14.6，wrapper 任务重新生成，并固定官方 distributionSha256Sum |
| Kotlin / Compose compiler | 2.2.20 | 2.2.21 |
| Coroutines core/android/swing/test | 1.10.2 | 1.11.0 |
| DLLogUtil | 0.2.1 | 0.3.0 |
| Compose Multiplatform | 1.9.0 | 1.9.3 |
| JetBrains AndroidX Lifecycle | 2.9.4 | 2.9.6 |
| dependencyUpdates 插件 | 0.52.0 | io.github.ben-manes.versions 0.67.0 |
| webpack | 5.100.2 | 5.112.2 |
| webpack-dev-server | 5.2.2 | 5.2.6 |
| Mocha | 11.7.1 | 12.0.3 |
| KGP 使用的 Node | 22.0.0 | 22.23.4 |

Mocha 11 最新维护版仍依赖 serialize-javascript 6 和 diff 7，不能消除对应安全问题。因此实际升级了父依赖 Mocha 12，并使用 Node 22 同线补丁满足其 engine 要求；已重跑 JS/Wasm 浏览器测试，没有跨 major 强制覆盖传递依赖。

KGP 的独立 Wasm 工具目录自带旧锁文件，单改应用的 Wasm yarn.lock 不会更新它。现通过官方 `WasmNpmTooling.installationDir` 配置复用项目 `build/js` 工具目录，并使 Wasm tooling setup 依赖 `kotlinNpmInstall`。两套工具统一受 `kotlin-js-store/yarn.lock` 管理。Wasm 应用自己的 `kotlin-js-store/wasm/yarn.lock` 也通过 Gradle 重新生成，内容仍是原来的 @js-joda/core 3.2.0，故无 Git 差异。没有手改生成锁文件或全局缓存。

AGP 8.13.2、三套串口驱动、AndroidX 新主线、Hot Reload 和 datetime 保持方案中的后置/保留决定。未进行 AGP9/Kotlin2.4/CMP1.12 迁移。Kotlin2.2.21 与 AGP8.13.2 超出官方 fully-supported AGP 上限，当前项目与独立 Android/R8 消费者的实际构建均通过，不据此声称官方完整认证。

## 正确性修复

- F01：Web open/close 共享生命周期锁；并发 open 等待真实安装完成，关闭过程中拒绝新写入。浏览器 open Promise 不可取消，等待其完成后检测取消并释放迟到句柄。
- F02：Web 可恢复读错误后释放旧 reader，并为新的 readable 获取 reader；明确 EOF/断开才关闭。
- F03：JVM/Android 的取消清理移到覆盖整个 withContext 的外层，修复 dispatcher 返回时抛取消而绕过内部清理的问题。
- F04/F05：POSIX 校验 fd/generation 与非阻塞 read 在同一生命周期锁内完成；零返回退避，poll 使用 POLLIN 并识别 HUP/ERR，避免挂断后空转。
- F06：Android 导航层统一 Snackbar 展示错误；发送成功才清空输入，失败可重试；JS/Compose 也不清除发送期间新输入的内容。
- F07：Android 消息使用 StateFlow.update 原子追加；发送结果检查连接归属。
- F08：JS 每次连接的观察 Job 在断开/替换/掉线时取消，旧连接不更新新连接状态。
- F09：Android、Compose、JS 历史最多保留 200 条；Compose UI scope 回到 UI dispatcher。
- F10：CLI 选择阶段处理 Ctrl+C，通过 finally 恢复终端；通讯退出也在 finally 中释放连接。
- F11：五套 Demo 复用轻量增量 UTF-8 解码器；任意 read 边界不会破坏多字节字符。
- 消费者验证额外发现并修复：公开 Flow/Logger 类型的依赖由 implementation 改为 api。否则只依赖本库的 JVM 消费者不能访问 `openStateFlow.value` 或 collect。

## 自动化与行为验证

| 检查 | 结果 |
|---|---|
| JVM 测试 | 24 通过，0 失败、0 跳过；含真实 PTY + 受控 dispatcher 的打开取消清理回归 |
| macOS arm64 测试 | 25 通过；含真实 PTY 空闲、RX、对端挂断以及 20 次关闭/重开 |
| JS Chromium 浏览器测试 | 27 通过；含并发 open、open/close、取消后迟到成功、reader 错误恢复 |
| Wasm Chromium 浏览器测试 | 27 通过；同一组 Web 生命周期场景 |
| Android 真机 UI 测试 | 1 通过；GOA-AL80U / Android 12，验证打开不存在设备时错误可见且仍在配置页 |
| Android Demo | debug 编译、release assemble、lintDebug 通过 |
| Compose Demo | JVM 编译、JS/Wasm production distribution 通过 |
| JS Demo | production distribution 通过 |
| Native Demo / 库 | macOS arm64 CLI 链接、Linux arm64 Demo 编译、Linux x64 与 macOS x64 库编译通过；所有 publication 成功生成 |
| 独立 JVM Maven 消费者 | 仅声明本库依赖，即可编译 Flow/Logger API |
| 独立 Android Maven 消费者 | 仅声明本库依赖，release/R8 构建通过；minSdk26、Java8 字节码 |
| dependencyUpdates | 新插件任务成功；第二次明确显示 Reusing configuration cache，无原先 198 项缓存失效问题 |
| Compose Web HMR | 移除 KT-80582 workaround 后，一次 Kotlin 源修改仅触发一次页面刷新，无重复刷新；临时文本已恢复。 |
| CLI 行为 | 独立 PTY 中在设备选择页 Ctrl+C，进程返回 0，终端设置恢复；比较时忽略内核维护的 PENDIN 瞬态标志 |
| Web Demo 实际页面 | 使用模拟 Web Serial 硬件、真实按钮：选择→连接→发送 AT/CRLF、拆分中文、读错误恢复、230 次 RX 后保留 200 条、发送失败保留输入、6 次成对打开/关闭，全部通过 |

浏览器使用 Playwright 自带 Chromium headless shell，未以 headless 运行用户安装的 Chrome。Web Serial 硬件由测试 fixture 模拟，浏览器 Stream 和页面操作是真实执行。

主要验证命令（浏览器测试须将 CHROMIUM_BIN 指向独立 Chromium）：

```bash
./gradlew :DLSerialPortUtil:jvmTest :DLSerialPortUtil:macosArm64Test \
  :DLSerialPortUtil:jsBrowserTest :DLSerialPortUtil:wasmJsBrowserTest
./gradlew :androidDemo:connectedDebugAndroidTest :androidDemo:assembleRelease :androidDemo:lintDebug
./gradlew :composeDemo:compileKotlinJvm :composeDemo:jsBrowserDistribution \
  :composeDemo:wasmJsBrowserDistribution :jsDemo:jsBrowserDistribution \
  :macosDemo:linkDebugExecutableMacosArm64 :linuxDemo:compileKotlinLinuxArm64
./gradlew :DLSerialPortUtil:publishAllPublicationsToLocalRepository \
  -PlocalMavenRepository=build/verification-maven
```

测试源码位于 `library/src/{commonTest,jvmTest,macosTest,jsTest,wasmJsTest}` 和 `androidDemo/src/androidTest`。JVM PTY 回归需要 macOS/Linux 和 python3；Windows 用 JUnit assumption 明确跳过。

## 安全复扫与残留条目

最终两个项目锁文件及显式 Maven 版本共扫描 446 个去重包版本；重新解析的 195 个 JVM/Android/Compose runtime 坐标未命中 OSV。Wasm 实际使用的工具目录已改为项目 `build/js`，而非旧全局 tooling 缓存。

残留 3 条公告、5 个版本条目，不宣称“零漏洞”：

| 公告 / 版本 | 具体调用证据与处置 |
|---|---|
| GHSA-3g3m-2fjr-6pw4 / glob 13.0.6 | 来自 Mocha；`mocha/lib/cli/lookup-files.js` 用本地配置的 glob.sync，没有 AbortSignal。公告要求攻击者影响目录/模式并用 signal 作为遍历预算，当前浏览器测试路径不具备这些条件。修复线为 glob14，不为此跨 major 强制覆盖父依赖。 |
| GHSA-g3hv-75wg-m249 / minimatch 10.2.6、9.0.9、3.1.5 | 来自 Mocha/Karma/karma-webpack 的本地文件模式。公告涉及不可信模式与 optimizationLevel≥2 放大；本项目模式来自受控构建配置，串口数据和 HTTP 请求不进入这些匹配调用。3.x 的实现也没有公告描述的新预处理函数，但保留数据库命中记录。后续父工具兼容升级再消除。 |
| GHSA-w5hq-g745-h8pq / uuid 8.3.2 | SockJS `lib/transport.js` 仅调用不带 buffer 的 uuid.v4()；公告针对 v3/v5/v6 外部输出缓冲越界，当前调用不可达。等待 SockJS 父依赖更新，不强制跨 major 替换。 |

若以后允许远端提交文件匹配模式、启用新的 Node 测试模式或修改 UUID 用法，必须重新评估以上可达性。未压制扫描结果、未修改公告数据、未使用 blanket resolutions。

安全公告：[glob](https://github.com/advisories/GHSA-3g3m-2fjr-6pw4)、[minimatch](https://github.com/advisories/GHSA-g3hv-75wg-m249)、[uuid](https://github.com/advisories/GHSA-w5hq-g745-h8pq)。Gradle 三条 High 公告通过 8.14.6 修复；serialize-javascript/diff 等命中通过父依赖升级消除。

## 开发服务器

JS 与 Compose Web 的开发服务器仅监听 `127.0.0.1`，静态目录限于编译产物与 Demo 资源，不提供仓库根目录，且不自动打开系统浏览器。开发时手动访问控制台打印的本地地址即可。

## 未覆盖与保留风险

- 未进行真实 USB/机内串口首次授权、拔插、多芯片、控制线及持续吞吐验证；Android 真机验证的是 Demo UI，PTY 不能代替 USB 驱动硬件。
- Linux/Windows/macOS Intel 没有运行时硬件验收；相应 Native 目标验证以编译/制品为限。
- 编译仍有 Kotlin 生成 JS 的 import.meta 警告、Compose bundle 体积提示、SDK XML 工具版本提示和 Gradle9 迁移弃用提示；不影响本轮成功构建，不声称已经完成 Gradle9 迁移。
- DLLogUtil 0.3.0 的日志采集缓冲变为 1024 条，慢消费者会丢旧诊断日志；业务 RX 数据流仍遵循自己的 64 块 replay/背压语义。
- 本次没有做完整原生二进制供应链、所有 Gradle 插件 classpath 的漏洞扫描。
