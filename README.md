# DLSerialPortUtil
[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-blueviolet?logo=kotlin&logoColor=white)](#)
[![Android](https://img.shields.io/badge/Android-supported-brightgreen?logo=android&logoColor=white)](#)
[![JVM](https://img.shields.io/badge/JVM-supported-blue)](#)
[![Linux](https://img.shields.io/badge/Linux-supported-lightgrey?logo=linux&logoColor=white)](#)
[![macOS](https://img.shields.io/badge/macOS-supported-black?logo=apple&logoColor=white)](#)
[![Web Serial](https://img.shields.io/badge/Web%20Serial-supported-orange?logo=google-chrome&logoColor=white)](#)
[![Latest](https://img.shields.io/badge/version-0.2.0-blue)](#)

## 特性
- Kotlin Multiplatform：在 `commonMain` 使用统一 API，平台差异由库内部适配
- 统一的串口管理器与串口对象：列出设备、打开、读写、关闭
- 串口配置统一：波特率、数据位、校验位、停止位等使用枚举类型确保安全
- 基于协程的异步数据流：提供 `outputDataFlow` 与 `openStateFlow`
- Android：支持机内串口（/dev/tty*）与 USB 串口（USB CDC 等），内置 AndroidX Startup 自动初始化、USB 权限申请与拔出监听
- JVM：基于 jSerialComm，支持 Windows / Linux / macOS 桌面环境
- Linux & macOS：基于 POSIX 接口实现
- Web（浏览器）：基于 Web Serial API，支持通过浏览器访问串口设备

## 支持平台
- Android（机内串口与 USB 串口）
- JVM（Windows / Linux / macOS）
- Linux（linuxX64 / linuxArm64）
- macOS（macosX64 / macosArm64）
- JavaScript（Browser，Web Serial API）

### 平台支持矩阵

| 平台                       | KMP Target           | 串口类型/实现                | 主要特性/说明                                                                                                                   |
|--------------------------|----------------------|------------------------|---------------------------------------------------------------------------------------------------------------------------|
| Android                  | android              | 机内串口（/dev/tty*）、USB 串口 | 自动初始化（AndroidX Startup），USB 权限申请与拔出监听；默认 `getPlatformSerialPortManager()` 返回机内串口管理器；USB 请使用 `AndroidUsbSerialPortManager` |
| JVM（Windows/Linux/macOS） | jvm                  | jSerialComm            | 标准串口名与流式读写；跨桌面系统可用                                                                                                        |
| Linux                    | linuxX64, linuxArm64 | POSIX                  | 典型设备：`/dev/ttyS0`、`/dev/ttyUSB0` 等                                                                                        |
| macOS                    | macosX64, macosArm64 | POSIX                  | 典型设备：`/dev/tty.*`、`/dev/cu.*` 等                                                                                           |
| Web（Browser）             | js（browser）          | Web Serial API         | 需 HTTPS（或 localhost）与用户手势触发；可用 `WebSerialPortManager.isSupported()` 检查支持                                                  |

## 安装与集成

在你的 KMP 项目中添加仓库与依赖（Kotlin DSL）：

```kotlin
// settings.gradle.kts 或 build.gradle.kts 中的仓库（推荐）
dependencyResolutionManagement {
    repositories {
        maven("https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository")
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
                implementation("com.github.D10NGYANG:DLSerialPortUtil:0.2.0")
            }
        }
    }
}
```

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

    // 使用完毕关闭
    sp.close()
}
```

### API 概览
- 串口管理器：`getPlatformSerialPortManager()` 获取当前平台实现
  - `suspend fun listPorts(): List<SerialPortInfo>` 列出串口设备
  - `suspend fun open(portInfo: SerialPortInfo, config: SerialPortConfig): BaseSerialPort` 打开串口
- 串口对象：`BaseSerialPort`
  - `val outputDataFlow: MutableSharedFlow<ByteArray>` 输出数据流（异步）
  - `val openStateFlow: MutableStateFlow<Boolean>` 串口是否处于开启状态
  - `suspend fun write(data: ByteArray): Boolean` 写数据
  - `fun close()` 关闭串口
- 串口配置：`SerialPortConfig`
  - `baudRate: BaudRate`（如 `V9600`, `V115200` 等）
  - `dataBits: DataBits`（如 `V7`, `V8`）
  - `parity: Parity`（`NONE`, `EVEN`, `ODD`）
  - `stopBits: StopBits`（`V1`, `V2`）
- 设备信息：`SerialPortInfo`
  - `id: String` 平台唯一标识（如 `/dev/ttyS0`、`USB Device ID` 或 Web 的对象标识）
  - `description: String?` 可读描述
  - `obj: Any?` 平台层对象（供内部使用）

## 各平台使用说明与注意事项

### Android
- 机内串口（/dev/tty*）：库会在打开前尝试 `chmod 777` 以提升设备权限。不同设备权限策略可能不同，部分设备可能需要 root 或厂商授权；请根据实际情况评估
- USB 串口：库内置 AndroidX Startup（Manifest Provider）自动初始化并注册 USB 权限与拔出广播；在首次访问设备时会自动弹出权限申请对话框，无需手动在 Manifest 配置接收器
- 依赖：库已在内部依赖 `androidx.startup:startup-runtime` 以及常用 USB 串口驱动库，无需单独引入

### Web（浏览器）
- 基于 Web Serial API：
  - 需要 HTTPS（或 `localhost`）环境
  - 需要用户手势触发设备选择（例如点击按钮后调用）
- `listPorts()` 在 Web 平台会触发设备选择弹窗并返回用户授权的设备；
- 可通过 `WebSerialPortManager.isSupported()` 检查浏览器是否支持；

### JVM 桌面 / Linux / macOS
- JVM 桌面通过 jSerialComm 访问系统串口，通常端口名如下：
  - Windows 示例：`COM1`、`COM3`
  - Linux 示例：`/dev/ttyS0`、`/dev/ttyUSB0`
  - macOS 示例：`/dev/tty.*`、`/dev/cu.*`
- Linux 与 macOS 的 `linuxX64/linuxArm64/macosX64/macosArm64` 目标使用 POSIX 接口实现，行为与系统串口一致

## Demo 示例
本仓库提供多平台示例以帮助你快速集成：
- `androidDemo/`：Android App 示例
- `desktopDemo/`：Compose Desktop 示例（JVM）
- `jsDemo/`：浏览器端示例
- `macosDemo/`：macOS 示例

你可以参考这些模块的 `build.gradle.kts` 与源码，了解如何在不同平台中添加依赖并调用库 API。
