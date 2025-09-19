@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    id("maven-publish")
}

group = "com.github.D10NGYANG"
version = "0.1.0"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
        }
        publishLibraryVariants("release")
    }
    js {
        browser()
        binaries.library()
    }
    linuxX64()
    linuxArm64()

    sourceSets {
        commonMain.dependencies {
            // 协程
            implementation(libs.kotlinx.coroutines)
            // 通用计算库
            implementation(libs.dl.common)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            // startup
            implementation(libs.androidx.startup.runtime)
            // 协程 Android
            implementation(libs.kotlinx.coroutines.android)
            // APP通用工具
            implementation(libs.dl.app)
            // 机内串口通讯
            api(libs.serialport.android)
            // USB串口通讯
            api(libs.serialport.android.usb)
        }
    }
}

android {
    namespace = "com.d10ng.serialport"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

val bds100MavenUsername: String by project
val bds100MavenPassword: String by project

afterEvaluate {
    publishing {
        repositories {
            maven {
                url = uri("/Users/d10ng/project/kotlin/maven-repo/repository")
            }
            maven {
                credentials {
                    username = bds100MavenUsername
                    password = bds100MavenPassword
                }
                setUrl("https://nexus.bds100.com/repository/maven-releases/")
            }
        }
    }
}