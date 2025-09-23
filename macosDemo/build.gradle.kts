plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    macosArm64 {
        binaries {
            executable {
                entryPoint = "com.d10ng.serialport.cli.main"
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            // 协程
            implementation(libs.kotlinx.coroutines)
        }
        macosMain.dependencies {
            // 串口通讯
            implementation(project(":library"))
        }
    }
}