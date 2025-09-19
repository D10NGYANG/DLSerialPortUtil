plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    js {
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
        }
        jsMain.dependencies {
            // 串口通讯
            implementation(project(":library"))
        }
    }
}