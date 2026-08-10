# 0.7.0 依赖审计

审计日期：2026-08-10。候选来自 `./gradlew dependencyUpdates` 和 Android Lint，升级决策以 Kotlin 2.2.20、AGP 8.13.x、Gradle 8.14.x 和 Compose 1.9.0 的兼容性及全目标构建结果为准。

| 依赖/工具 | 升级前 | 当前/推荐版本 | 决策 | 原因 |
| --- | --- | --- | --- | --- |
| Gradle | 8.14.3 | 8.14.5 | 升级 | 同一 8.14 线的缺陷修复；9.7 会跨入 Gradle 9，当前构建仍有第三方插件弃用提示 |
| Android Gradle Plugin | 8.13.0 | 8.13.2 | 升级 | 同一 8.13 线的缺陷修复；9.3.1 需要 AGP 9 KMP DSL/模块迁移 |
| Kotlin | 2.2.20 | 2.2.20 | 保留 | 2.4.10 会联动 Compose、Native 和 JS/Wasm 工具链升级 |
| Coroutines | 1.10.2 | 1.10.2 | 保留 | 1.11.0 是较大版本推进；现版本覆盖所有目标且测试通过 |
| Compose Multiplatform | 1.9.0 | 1.9.0 | 保留 | 1.11.1 需要与 Kotlin/Compose 工具链整体迁移 |
| Compose Hot Reload | 1.0.0-beta08 | 1.0.0-beta08 | 保留 | 仅 demo 开发工具；1.2.0 与新版 Compose 工具链联动 |
| Android Compose BOM | 2025.09.01 | 2025.09.01 | 保留 | 2026.06.01 跨越多个功能版本，不是稳定补丁升级 |
| Android Navigation | 2.9.5 | 2.9.8 | 升级 | 同一 2.9 线的缺陷修复，demo 编译和 Lint 验证 |
| Android demo targetSdk | 34 | 36 | 升级 | 与 compileSdk 36 对齐并消除旧目标兼容模式；库 minSdk 仍为 26 |
| usb-serial-for-android | 3.9.0 | 3.11.0 | 升级 | 串口驱动兼容与缺陷修复；Android library/demo 编译验证 |
| jSerialComm | 2.11.2 | 2.11.4 | 升级 | 同一 2.11 线的串口兼容/缺陷修复；JVM 编译测试验证 |
| DLLogUtil | 0.1.1 | 0.2.1 | 升级 | 日志输出依赖；公开 logger 配置接口编译兼容 |
| AndroidX Startup | 1.2.0 | 1.2.0 | 保留 | 已是报告中的最新稳定版；只用于进程级 USB 拔出 receiver 初始化 |
| AndroidX Core | 1.17.0 | 1.17.0 | 保留 | 1.19.0 与更高 AndroidX 工具链一并升级风险更低 |
| AndroidX Lifecycle | 2.9.4 | 2.9.4 | 保留 | 2.11.0 跨功能版本，demo 无必要能力需求 |
| kotlinx-datetime | 0.7.1 | 0.7.1 | 保留 | 仅 Compose demo 格式化时间；0.8.0 无本轮必要修复 |
| ben-manes versions plugin | 0.52.0 | 0.52.0 | 保留 | 0.61.0 与 Gradle 9 方向联动；当前报告任务成功 |

## 通用工具依赖使用范围

- `kotlinx-coroutines-core`：Flow、CoroutineScope、Mutex、取消、超时和平台读循环，是公共并发契约的核心依赖，保留。
- `DLLogUtil`：创建公共可配置 logger，供库输出跨平台诊断和完整通讯日志，保留。
- `androidx-core-ktx`：Android receiver 兼容注册；库仍需 `ContextCompat.registerReceiver`，保留。
- `androidx-startup-runtime`：进程启动时注册 USB 拔出 receiver，保留。库不再用它发起 USB 授权。
- `kotlinx-coroutines-android`：Android Main/IO dispatcher，保留。
- `usb-serial-for-android`、`android-serialport`、`jSerialComm`：分别提供 Android USB、Android 设备节点和 JVM 底层串口能力，均非可用少量平台 API 等价替代的通用工具库。
- `kotlinx-datetime` 仅被 Compose demo 使用，不进入 library publication。

本轮没有发现可安全移除且不会重复实现成熟串口驱动能力的 library 直接依赖。
