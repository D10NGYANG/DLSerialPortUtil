# 升级到 0.7.1

0.7.1 是针对 0.7.0 的稳定性与依赖维护版本，没有删除或重命名公共串口 API，也没有增加自动重连或业务重发。

## 安装

仓库地址保持不变：

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository") {
            mavenContent { includeGroupAndSubgroups("com.github.D10NGYANG") }
        }
        // Android USB 驱动的传递依赖
        maven("https://jitpack.io") {
            mavenContent { includeGroupAndSubgroups("com.github.mik3y") }
        }
        google()
        mavenCentral()
    }
}
```

```kotlin
// KMP commonMain，由 Gradle 选择目标平台
implementation("com.github.D10NGYANG:DLSerialPortUtil:0.7.1")
// 纯 JVM/Java 项目也可选择以下平台坐标，二者择一
implementation("com.github.D10NGYANG:DLSerialPortUtil-jvm:0.7.1")
// 仅 Android 项目也可选择以下平台坐标
implementation("com.github.D10NGYANG:DLSerialPortUtil-android:0.7.1")
```

提供 Android、JVM、JS、WasmJS、Linux x64/arm64、macOS x64/arm64 制品。Android minSdk 仍为 26，JVM/Android 库字节码仍兼容 Java 8。

## 消费者依赖与工具链

- 使用 Kotlin 2.2.21 编译，建议消费者使用该版本或兼容的新版本；Native/JS/Wasm 应按目标编译器兼容规则验证。
- Coroutines 统一为 1.11.0，DLLogUtil 升级至 0.3.0；后者引入 atomicfu 0.33.0，无需消费者应用 atomicfu 编译器插件。
- 公共 Flow 和 Logger 类型的依赖通过 `api` 暴露，只声明本库即可编译这些公共 API。
- 如项目强制锁定旧协程或日志版本，应先移除不兼容约束，再检查最终解析结果。强制降级不在本次验证范围内。
- Coroutines 1.11.0 调整了 Wasm Promise 泛型约束和 JS/Wasm 未传播异常处理，直接使用这些 API 的应用应核对 [官方发布说明](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0)。
- Gradle 8.14.6、Compose 1.9.3、Node 22.23.4 等是本项目源码/Demo 的构建配置，不要求串口库消费者机械复制全部版本。

## 串口行为修复

- **Web 打开/关闭**：同一对象的并发 `open()` 等待资源安装完成；打开期间关闭或取消时，迟到的浏览器句柄也会清理。浏览器 open Promise 无法取消，因此取消完成可能需要等底层 Promise 结束。
- **Web 读错误恢复**：可恢复错误后释放失效 reader，从新的 readable 获取 reader；不会永久重试同一个 errored stream。明确断开或 EOF 仍会关闭连接。
- **JVM/Android 取消**：打开资源已经成功、但调用方在 dispatcher 返回前取消时，会回收尚未交付的端口。已交付连接的普通写入取消不会因此变成自动关闭。
- **POSIX**：fd/generation 校验与 read 和关闭操作互斥，避免旧任务读取被复用的 fd；零长度读取退避，并识别明确挂断。
- `write(true)` 仍只表示完整提交给底层，不代表设备业务应答；不会自动重发。
- RX 仍按底层 read 块发布，保留 64 块 replay/背压规则，业务协议仍负责拆包、校验、超时与幂等。

需要确定关闭完成后再重连时，继续优先使用 `closeAndAwait()`。

## 日志升级注意事项

DLLogUtil 0.3.0 的公开 API 保持兼容，但以下行为需要确认：

- 日志采集最多缓冲 1024 条，慢消费者积压时丢弃最旧日志。它不是保证每条必达的业务或审计日志通道；此限制不改变串口 RX 数据流的容量。
- 全局日志等级也会影响之后创建的 Logger；对某个标签的覆盖应在全局设置之后执行。
- `getAll()` 返回注册表快照，不再随之后的注册变化自动更新。
- 修复 logger 注册表并发、等级可见性、Apple 时间戳和中文/百分号输出。

生产环境应管理完整 TX/RX DEBUG 日志的保存周期和访问权限。[日志库完整升级说明](https://github.com/D10NGYANG/DLLogUtil/blob/0.3.0/docs/upgrade-0.3.0.md)。

## Demo 变化

- Android 的打开/发送错误现在可见，发送失败保留输入；并发收发不会覆盖消息记录。
- JS 断开或重连时取消旧订阅，图形 Demo 历史最多保留 200 条。
- 五套 Demo 使用增量 UTF-8 解码，解决中文跨 read 块时乱码；串口库仍返回原始字节。
- CLI 在设备/参数选择阶段支持 Ctrl+C 并恢复终端设置。
- Web 开发服务器只监听本机且不自动打开浏览器，不提供仓库根目录。Kotlin 2.2.21 已修复重复刷新问题，移除了旧 webpack workaround。

## 升级验证建议

1. 更新依赖，检查 Kotlin、Coroutines、DLLogUtil 的最终解析版本。
2. 验证打开、持续收发、取消打开、关闭后立即重连。
3. Web 验证授权入口、可恢复读取异常和 JS/Wasm Promise 取消；Android USB 验证首次授权、拒绝及拔插。
4. 若采集日志，确认能够接受慢消费者丢旧诊断日志，以及全局等级的新行为。
5. 在实际设备上检查控制线、驱动超时和吞吐。

本版完成 JVM、macOS PTY、JS/Wasm Chromium 和 Android UI 自动化验证，并验证了独立 Maven 消费者与 Android release/R8；真实 USB/机内串口、Linux/Windows 运行时等未覆盖项见 [验证记录](UPGRADE_VERIFICATION.md)。
