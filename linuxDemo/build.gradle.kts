plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    linuxArm64 {
        binaries {
            executable {
                entryPoint = "com.d10ng.serialport.cli.main"
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines)
        }
        linuxMain.dependencies {
            implementation(project(":DLSerialPortUtil"))
        }
    }
}