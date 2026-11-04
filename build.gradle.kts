// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.hotreload) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    id("io.github.ben-manes.versions") version "0.67.0"
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}
// KGP's pinned web tooling predates the security fixes; configure both web targets.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension> {
        versions.webpack.version = "5.112.2"
        versions.mocha.version = "12.0.3"
        versions.webpackDevServer.version = "5.2.6"
    }
}
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootExtension> {
        versions.webpack.version = "5.112.2"
        versions.mocha.version = "12.0.3"
        versions.webpackDevServer.version = "5.2.6"
    }
}

plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin> {
    // KGP's separate Wasm tooling ships an old lock; share the project's audited JS tools.
    extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNpmTooling> {
        installationDir.set(layout.buildDirectory.dir("js"))
    }
    tasks.named("kotlinWasmToolingSetup") { dependsOn("kotlinNpmInstall") }
}

allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        extensions.configure<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec> {
            version.set("22.23.4")
        }
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
        extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec> {
            version.set("22.23.4")
        }
    }
}
