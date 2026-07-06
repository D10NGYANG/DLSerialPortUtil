# DLSerialPortUtil

Kotlin Multiplatform 串口通讯库。

[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-blueviolet?logo=kotlin&logoColor=white)](#)
[![Latest](https://img.shields.io/badge/version-0.6.1-blue)](#)
[![GitHub stars](https://img.shields.io/github/stars/D10NGYANG/DLSerialPortUtil?logo=github)](https://github.com/D10NGYANG/DLSerialPortUtil/stargazers)

**在线demo测试：**[https://d10ngyang.github.io/DLSerialPortUtil/](https://d10ngyang.github.io/DLSerialPortUtil/)

## 特性
- Kotlin Multiplatform：在 `commonMain` 使用统一 API，平台差异由库内部适配
- 统一的串口管理器与串口对象：列出设备、打开、读写、关闭
- 支持 DTR（Data Terminal Ready）与 RTS（Request To Send）控制，并可在运行时查询当前串口是否支持
- 串口配置统一：波特率、数据位、校验位、停止位等使用枚举类型确保安全
- 基于协程的异步数据流：提供 `outputDataFlow` 与 `openStateFlow`
- Android：支持机内串口（/dev/tty*）与 USB 串口（USB CDC 等），内置 AndroidX Startup 自动初始化、USB 权限申请与拔出监听
- JVM：基于 jSerialComm，支持 Windows / Linux / macOS 桌面环境
- Linux & macOS：基于 POSIX 接口实现
- Web（浏览器）：基于 Web Serial API，支持通过浏览器访问串口设备

## 支持平台
- ![Android](https://img.shields.io/badge/Android%20机内串口%2FUSB-✅-black?logo=android)
- ![JVM](https://img.shields.io/badge/JVM%20Windows%2FLinux%2FmacOS-✅-black?logo=java)
- ![Linux x64/arm64](https://img.shields.io/badge/Linux%20x64%2Farm64-✅-black?logo=linux)
- ![macOS x64/arm64](https://img.shields.io/badge/macOS%20x64%2Farm64-✅-black?logo=apple)
- ![JavaScript/WasmJs](https://img.shields.io/badge/JavaScript%2FWasmJs%20Web%20Serial-✅-black?logo=google-chrome)

### 平台支持矩阵

| 平台                       | KMP Target                  | 串口类型/实现                | DTR / RTS | 主要特性/说明                                                                                                                   |
|--------------------------|-----------------------------|------------------------|-----------|---------------------------------------------------------------------------------------------------------------------------|
| Android                  | android                     | 机内串口（/dev/tty*）       | 不支持 | 默认 `getPlatformSerialPortManager()` 返回机内串口管理器；当前底层依赖未暴露 DTR/RTS 控制接口 |
| Android                  | android                     | USB 串口                 | 视设备而定 | 使用 `AndroidUsbSerialPortManager`；支持 USB 权限申请与拔出监听，可通过 `isDtrSupported`、`isRtsSupported` 查询具体驱动能力 |
| JVM（Windows/Linux/macOS） | jvm                         | jSerialComm            | 支持 | 标准串口名与流式读写；跨桌面系统可用 |
| Linux                    | linuxX64, linuxArm64        | POSIX                  | 支持 | 典型设备：`/dev/ttyS0`、`/dev/ttyUSB0` 等 |
| macOS                    | macosX64, macosArm64        | POSIX                  | 支持 | 典型设备：`/dev/tty.*`、`/dev/cu.*` 等 |
| Web（Browser）             | js（browser），wasmJs（browser） | Web Serial API         | 支持 | 需 HTTPS（或 localhost）与用户手势触发；实际操作结果取决于浏览器、系统和设备 |

## 安装与集成

在你的 KMP 项目中添加仓库与依赖（Kotlin DSL）：

```kotlin
// settings.gradle.kts 或 build.gradle.kts 中的仓库（推荐）
dependencyResolutionManagement {
    repositories {
        maven("https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository") {
          mavenContent {
            includeGroupAndSubgroups("com.github.D10NGYANG")
          }
        }
        // 为 Android USB 串口的传递依赖提供仓库
        maven("https://jitpack.io") {
          mavenContent {
            includeGroupAndSubgroups("com.github.mik3y")
          }
        }
        google()
        mavenCentral()
    }
}
```

然后在模块的 `build.gradle.kts` 中添加依赖（建议在 `commonMain`）：

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.github.D10NGYANG:DLSerialPortUtil:0.6.1")
            }
        }
    }
}
```

## 日志输出控制

本库内部使用 [DLLogUtil](https://github.com/D10NGYANG/DLLogUtil) 进行日志记录。若需要在你的项目中控制日志输出等级，或收集日志，请在你的 KMP 模块添加日志库依赖（建议在 `commonMain`）：

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                // 日志库（用于控制输出等级）
                implementation("com.github.D10NGYANG:DLLogUtil:0.1.1")
            }
        }
    }
}
```

设置日志输出等级（示例）：

```kotlin
import com.d10ng.serialport.SerialPortManagerLog
import com.d10ng.log.LogLevel

fun initLogging() {
    // 仅输出 WARN 及以上级别
    SerialPortManagerLog.miniLevel = LogLevel.WARN

    // 如需输出更详细的日志（包含所有等级）
    // SerialPortManagerLog.miniLevel = LogLevel.VERBOSE
  
    // 关闭日志输出
    // SerialPortManagerLog.miniLevel = LogLevel.NONE
}
```

- 常见日志等级：`VERBOSE`、`DEBUG`、`INFO`、`WARN`、`ERROR`、`NONE`（具体以 DLLogUtil 定义为准）
- 推荐在应用启动时设置，如：Android 的 `Application.onCreate`、JVM/桌面项目的 `main` 函数、Web 页面初始化等

## 快速上手

以下示例演示在 `commonMain` 使用统一 API：

```kotlin
import com.d10ng.serialport.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

val scope = CoroutineScope(Dispatchers.Default)

suspend fun demo() {
    // 获取平台串口管理器
    val manager = getPlatformSerialPortManager()
  
    // 判断当前环境是否支持串口通讯
    if (!manager.isSupported()) {
        println("Serial port not supported")
    }

    // 列出可用串口
    val ports = manager.listPorts()
    if (ports.isEmpty()) {
        println("No serial ports found")
        return
    }

    val portInfo = ports.first()
    val config = SerialPortConfig(
        baudRate = BaudRate.V115200,
        dataBits = DataBits.V8,
        parity = Parity.NONE,
        stopBits = StopBits.V1,
    )

    // 打开串口
    val sp = manager.open(portInfo, config)

    // 订阅数据输出
    scope.launch {
        sp.outputDataFlow.collect { data ->
            println("recv: ${data.joinToString(", ")}")
        }
    }

    // 写入数据
    sp.write("AT\r\n".encodeToByteArray())

    // 控制 DTR。应在串口打开后查询并调用
    if (sp.isDtrSupported) {
        val success = sp.setDtr(true)
        println("Set DTR: $success")
    }

    // 控制 RTS
    if (sp.isRtsSupported) {
        val success = sp.setRts(true)
        println("Set RTS: $success")
    }

    // 使用完毕关闭，并等待底层资源释放后再重连
    sp.closeAndAwait()
}
```

Web 首次授权必须由用户点击等手势直接触发：

```kotlin
scope.launch {
    val selected = manager.requestPort() // 用户取消时返回 null
    if (selected != null) {
        // requestPort() 返回的 SerialPortInfo 可直接用于 open()
        println("Selected: ${selected.description ?: selected.id}")
    }
}
```

### API 概览
- 串口管理器：`getPlatformSerialPortManager()` 获取当前平台实现
  - `fun isSupported(): Boolean` 判断当前平台环境是否支持串口通讯
  - `suspend fun listPorts(): List<SerialPortInfo>` 列出串口设备
  - `suspend fun requestPort(): SerialPortInfo?` 请求用户选择并授权设备；仅 Web 等需要显式授权的平台覆盖
  - `val portEventFlow: Flow<SerialPortEvent>` 监听设备接入和拔出；不支持的平台为空 Flow
  - `suspend fun open(portInfo: SerialPortInfo, config: SerialPortConfig): BaseSerialPort` 打开串口
- 串口对象：`BaseSerialPort`
  - `val outputDataFlow: MutableSharedFlow<ByteArray>` 输出数据流（异步）
  - `val openStateFlow: MutableStateFlow<Boolean>` 串口是否处于开启状态
  - `val isDtrSupported: Boolean` 当前串口实现或设备是否支持 DTR 控制
  - `suspend fun setDtr(enabled: Boolean): Boolean` 设置 DTR，成功返回 `true`
  - `val isRtsSupported: Boolean` 当前串口实现或设备是否支持 RTS 控制
  - `suspend fun setRts(enabled: Boolean): Boolean` 设置 RTS，成功返回 `true`
  - `suspend fun write(data: ByteArray): Boolean` 写数据
  - `fun close()` 触发关闭串口；Web 端异步执行
  - `suspend fun closeAndAwait()` 关闭串口并等待底层资源释放；需要立即重连时优先使用
- 串口配置：`SerialPortConfig`
  - `baudRate: BaudRate`（如 `V9600`, `V115200` 等）
  - `dataBits: DataBits`（如 `V7`, `V8`）
  - `parity: Parity`（`NONE`, `EVEN`, `ODD`）
  - `stopBits: StopBits`（`V1`, `V2`）
- 设备信息：`SerialPortInfo`
  - `id: String` 平台唯一标识；Web ID 只在当前页面生命周期内有效，设备重新接入后会变化
  - `description: String?` 可读描述
  - `obj: Any?` 平台层对象（供内部使用）

## 各平台使用说明与注意事项

### Android
- 机内串口（/dev/tty*）：库会在打开前尝试 `chmod 777` 以提升设备权限。不同设备权限策略可能不同，部分设备可能需要 root 或厂商授权；请根据实际情况评估
- 机内串口当前不支持 DTR/RTS，`isDtrSupported` 和 `isRtsSupported` 均为 `false`，调用对应设置方法返回 `false`
- USB 串口：库内置 AndroidX Startup（Manifest Provider）自动初始化并注册 USB 权限与拔出广播；在首次访问设备时会自动弹出权限申请对话框，无需手动在 Manifest 配置接收器
- USB 串口的 DTR/RTS 支持取决于 USB 转串口芯片及其驱动；打开串口后可通过 `isDtrSupported`、`isRtsSupported` 检查
- 依赖：库已在内部依赖 `androidx.startup:startup-runtime` 以及常用 USB 串口驱动库，无需单独引入

### Web（浏览器）
- 基于 Web Serial API：
  - 需要 HTTPS（或 `localhost`）环境
  - 需要用户手势触发设备选择（例如点击按钮后调用）
- `listPorts()` 只调用 `navigator.serial.getPorts()` 查询当前站点已经授权的设备，不显示浏览器选择器
- `requestPort()` 显示浏览器设备选择器，必须由用户手势直接触发；用户取消时返回 `null`，其他异常继续抛出
- 设备拔出后旧 `SerialPortInfo` 不应复用；监听 `portEventFlow` 或重新调用 `listPorts()` 获取最新对象
- 库会按端口连接状态过滤浏览器或驱动重复派发的 `connect`/`disconnect`，一次状态变化只进入事件流一次
- `close()` 保留非挂起兼容接口；需要关闭后立即重连时调用 `closeAndAwait()`
- 可通过 `WebSerialPortManager.isSupported()` 检查浏览器是否支持；
- DTR/RTS 基于 Web Serial API 的 `setSignals()` 实现；即使 API 可用，浏览器、操作系统或设备拒绝操作时，对应设置方法仍会返回 `false`

#### 从 0.6.0 升级到 0.6.1

- 将“选择设备”操作从 `listPorts()` 改为 `requestPort()`；刷新列表仍调用 `listPorts()`
- 用户取消选择时 `requestPort()` 返回 `null`，权限策略或浏览器错误仍需捕获
- 收到拔出事件后清除当前选中项；重新接入后使用新列表中的 `SerialPortInfo`
- 断开后立即重连前调用 `closeAndAwait()`，不要复用拔出前保存的平台对象

### JVM 桌面 / Linux / macOS
- JVM 桌面通过 jSerialComm 访问系统串口，通常端口名如下：
  - Windows 示例：`COM1`、`COM3`
  - Linux 示例：`/dev/ttyS0`、`/dev/ttyUSB0`
  - macOS 示例：`/dev/tty.*`、`/dev/cu.*`
- Linux 与 macOS 的 `linuxX64/linuxArm64/macosX64/macosArm64` 目标使用 POSIX 接口实现，行为与系统串口一致
- JVM、Linux 与 macOS 支持 DTR/RTS 控制；底层驱动不支持或串口未打开时，对应设置方法返回 `false`

## Demo 示例
本仓库提供多平台示例以帮助你快速集成：
- `androidDemo/`：Android App 示例
- `composeDemo/`：Compose Multiplatform 示例： Desktop（JVM）+ Web（js/wasmJs）
- `jsDemo/`：浏览器端H5示例
- `macosDemo/`：macOS 终端命令行程序示例
- `linuxDemo/`：Linux 终端命令行程序示例

你可以参考这些模块的 `build.gradle.kts` 与源码，了解如何在不同平台中添加依赖并调用库 API。
