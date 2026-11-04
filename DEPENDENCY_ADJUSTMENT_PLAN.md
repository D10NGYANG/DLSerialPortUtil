# 0.7.1 依赖维护决策

日期：2026-11-04。实施版本与测试结果见 [验证记录](UPGRADE_VERIFICATION.md)，消费者迁移见 [升级说明](MIGRATION_0.7.1.md)。

## 已执行

- Gradle 8.14.5 → 8.14.6，修复已公布安全问题并固定发行包校验和。
- Kotlin 2.2.20 → 2.2.21、Coroutines 1.10.2 → 1.11.0、DLLogUtil 0.2.1 → 0.3.0，成组验证 JS/Wasm 互操作与公共依赖。
- Compose Multiplatform 1.9.0 → 1.9.3、JetBrains Lifecycle 2.9.4 → 2.9.6。
- dependencyUpdates 插件升级 0.67.0，配置缓存复用通过。
- webpack 5.112.2、webpack-dev-server 5.2.6、Mocha 12.0.3、Node 22.23.4，重新生成锁文件并复扫。Wasm 复用受项目锁文件管理的 JS 工具链。

## 保留边界

- jSerialComm 2.11.4、usb-serial-for-android 3.11.0、android-serialport 2.1.5 未发现需要替换的更新稳定版，继续使用。
- AGP 8.13.2、Android minSdk 26、Java 8 库字节码保持不变。
- AGP9/Kotlin2.4/Compose1.12 属于单独迁移，不混入本轮修复。AndroidX 新线、Hot Reload 和 datetime 后续按实际需求评估。
- 不通过跨 major 强制覆盖传递依赖来追求扫描零告警。剩余 glob、minimatch、uuid 命中及实际调用条件在验证记录中逐项列明；用法变化时需要重新评估。

## 主要依据

- [Gradle 8.14.6](https://docs.gradle.org/8.14.6/release-notes.html)
- [Kotlin 2.2.21](https://github.com/JetBrains/kotlin/releases/tag/v2.2.21)
- [Coroutines 1.11.0](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0)
- [DLLogUtil 0.3.0](https://github.com/D10NGYANG/DLLogUtil/releases/tag/0.3.0)
- [Compose Multiplatform 1.9.3](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.9.3)
- [Kotlin Gradle Plugin 兼容矩阵](https://kotlinlang.org/docs/gradle-configure-project.html)
