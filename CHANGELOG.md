# Changelog

## 0.7.1 - 2026-11-04

- 升级 Gradle 8.14.6、Kotlin 2.2.21、Coroutines 1.11.0、DLLogUtil 0.3.0、Compose 1.9.3 与 JetBrains Lifecycle 2.9.6。
- Web 工具链升级 webpack 5.112.2、dev-server 5.2.6、Mocha 12.0.3 和 Node 22.23.4；Wasm 复用受锁文件管理的 JS 工具，依赖检查插件升级 0.67.0。
- 修复 Web 打开/关闭竞争、取消后迟到句柄清理和读取错误恢复；修复 JVM/Android 打开取消泄漏，以及 POSIX fd 复用竞争和 EOF 空转。
- 修复 Demo 错误反馈、消息并发覆盖、订阅泄漏、无界历史、CLI Ctrl+C 和跨块 UTF-8 解码。
- 公共 Flow 和 Logger 依赖改为 api，支持仅声明本库的消费者；增加真实 PTY、浏览器和 Android UI 回归。
- 增加 [0.7.1 升级说明](MIGRATION_0.7.1.md) 与 [验证记录](UPGRADE_VERIFICATION.md)；开发服务器仅监听本机，并只提供编译资源。

## 0.7.0 - 2026-08-10

### Changed

- 全平台写入改为每端口串行并分配 `op`，不同端口不共享全局锁
- 普通读写失败、写超时和协程取消不再自动关闭已经打开的连接
- Android USB 改为应用显式请求运行时授权，库只做防御性检查
- Android demo 增加 30 秒 USB 授权超时，并在关闭或断开时取消端口状态与 RX 收集器
- Android 机内串口不再执行 `su` 或 `chmod`，设备节点权限由应用部署环境负责
- RX 在订阅建立前有限保留 64 个块，满时丢弃最旧块并记录日志
- 通讯日志统一为完整、连续、大写 HEX，并覆盖打开、配置、读循环、写入阶段、断开证据和清理
- POSIX 使用非阻塞 I/O 和 2 秒写入期限；JVM 使用 jSerialComm 底层写超时

### Dependencies

- `usb-serial-for-android` 3.9.0 -> 3.11.0
- `jSerialComm` 2.11.2 -> 2.11.4
- `DLLogUtil` 0.1.1 -> 0.2.1
- Android Navigation 2.9.5 -> 2.9.8
- Gradle 8.14.3 -> 8.14.5，Android Gradle Plugin 8.13.0 -> 8.13.2
- Android demo targetSdk 34 -> 36

### Documentation

- 新增 0.7.0 升级迁移、依赖审计和稳定版发布说明
- 补齐权限模型、日志数据安全、传输层职责和跨平台验证清单
