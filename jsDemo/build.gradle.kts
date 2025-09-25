plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "bundle.js"
            }
        }
        binaries.executable()
    }
    sourceSets {
        commonMain.dependencies {
            // 协程
            implementation(libs.kotlinx.coroutines)
            // 日志库
            implementation(libs.dl.log)
        }
        jsMain.dependencies {
            // 串口通讯
            implementation(project(":DLSerialPortUtil"))
        }
    }
}