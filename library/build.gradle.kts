import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    id("maven-publish")
}

group = "com.github.D10NGYANG"
version = "0.7.0"

kotlin {
    jvmToolchain(8)
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
        }
        publishLibraryVariants("release")
    }
    jvm()
    js(IR) {
        browser()
        binaries.library()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.library()
    }
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            // 协程
            implementation(libs.kotlinx.coroutines)
            // 日志库
            implementation(libs.dl.log)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            // startup
            implementation(libs.androidx.startup.runtime)
            // 协程 Android
            implementation(libs.kotlinx.coroutines.android)
            // 机内串口通讯
            api(libs.serialport.android)
            // USB串口通讯
            api(libs.serialport.android.usb)
        }
        jvmMain.dependencies {
            // 串口通讯
            api(libs.serialport.jvm)
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

afterEvaluate {
    publishing {
        repositories {
            providers.gradleProperty("localMavenRepository").orNull
                ?.takeIf { it.isNotBlank() }
                ?.let { repositoryPath ->
                    maven {
                        name = "local"
                        url = uri(repositoryPath)
                    }
                }

            val username = providers.gradleProperty("bds100MavenUsername").orNull
                ?.takeIf { it.isNotBlank() }
            val password = providers.gradleProperty("bds100MavenPassword").orNull
                ?.takeIf { it.isNotBlank() }
            if (username != null && password != null) {
                maven {
                    name = "bds100"
                    credentials {
                        this.username = username
                        this.password = password
                    }
                    setUrl("https://nexus.bds100.com/repository/maven-releases/")
                }
            }
        }
    }
}
