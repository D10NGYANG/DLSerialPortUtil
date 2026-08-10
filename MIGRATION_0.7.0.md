# 升级到 0.7.0

## 依赖升级

将库版本改为：

```kotlin
implementation("com.github.D10NGYANG:DLSerialPortUtil:0.7.0")
```

如应用直接配置库日志，使用 `com.github.D10NGYANG:DLLogUtil:0.2.1`。Android USB 的传递依赖仍来自 JitPack，仓库坐标和 publication 名称没有变化。

## 公共 API 兼容性

0.7.0 没有删除或重命名公共类、属性和方法。`ISerialPortManager`、`BaseSerialPort`、`SerialPortConfig` 和各平台管理器的调用方式保持兼容。

有一项可观察的流语义变化：`outputDataFlow` 现在 replay 最近 64 个底层读取块，用于避免端口打开后、收集器订阅前的早到数据丢失。新收集器可能先收到 replay 数据；上层协议应按自身序号和幂等规则处理收集器重建场景。无订阅者时超过 64 块会替换最旧块并记录 `[serial.rx.drop]`；有订阅者时读循环挂起施加背压。

## Android 权限和系统配置

- 库 Manifest 不声明普通权限、USB Host feature 或设备过滤规则。
- 应用如依赖 USB Host，应在自己的 Manifest 声明 `android.hardware.usb.host`。
- Android USB permission 必须由应用在运行时使用 `UsbManager.requestPermission()` 请求。应先注册结果 receiver，再发起请求，授权后调用 `AndroidUsbSerialPortManager.open()`。
- 未授权时库不会弹窗，`open()` 抛出包含端口、设备名和 VID/PID 的 `SecurityException`。
- `/dev/tty*` 权限由系统镜像、`ueventd.rc`、SELinux、厂商策略或部署环境负责。0.7.0 不再执行 `su`/`chmod`。
- Linux/JVM 桌面通常通过 `dialout` 等用户组、udev、ACL 或系统策略授予设备文件访问权。库不会修改系统权限。

完整 Android 授权代码见 `androidDemo/.../SerialPortViewModel.kt`。

## 打开和关闭行为

- `open()` 仍在底层资源和读循环安装成功后返回。
- `close()` 仍允许重复调用；需要确定性释放并立即重开时使用 `closeAndAwait()`。
- 单次读取失败、写入失败、写入超时或协程取消只结束或重试当前操作，不会擅自关闭已交付连接。
- 只有调用方明确关闭，或 USB 拔出、POSIX `EBADF/ENODEV/EIO`、JVM `isOpen=false`、Web readable stream 明确结束等证据才自动清理。
- 自动清理会记录 `[serial.disconnect] 端口 evidence=...`，随后记录资源释放状态。

## 应答、重试和写入超时

- `write(true)` 只表示底层完整接受 payload，不表示设备业务处理完成。
- 库不根据底层写回调关联请求，不生成业务序号，不自动重发 payload。
- Android USB、JVM 和 POSIX 的写入期限为 2 秒。超时/部分写入返回 `false`，连接保持打开。
- JS/Wasm 的 Promise 在调用协程取消后可能仍由浏览器完成；迟到完成不会更新业务状态或触发重发。日志用 `op` 关联该次底层操作。
- 协议应答、超时、重试、幂等、拆包、CRC 和请求序号均由上层协议实现。

## 通讯日志安全

DEBUG 日志原样包含端口路径、设备标识、VID/PID 和完整 TX/RX payload。HEX 为大写连续格式且不截断。payload 可能包含凭据、身份信息或业务数据；应用必须限制 DEBUG 日志的保存时长、上传目标和访问权限，生产环境不应默认长期收集。

## 跨平台升级验证清单

- Android 机内串口：无权限失败、系统授权后打开、持续 RX/TX、主动关闭和立即重开。
- Android USB：首次授权、拒绝、拔出、重新插入、多通道设备和 2 秒写超时。
- JVM Windows/Linux/macOS：枚举、打开、部分/超时写入、取消后继续写和物理拔出。
- Linux/macOS Native：设备文件权限、termios 配置、非阻塞大 payload、`EIO/ENODEV` 清理。
- JS/Wasm：HTTPS/localhost、用户选择、读 stream 结束、写 Promise 取消和重新授权。
- 所有平台：两个端口并发、同端口写入顺序、早到 RX、64 块边界、空 payload 和大 payload。
