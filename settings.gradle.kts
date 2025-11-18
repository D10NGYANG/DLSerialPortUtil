rootProject.name = "DLSerialPortUtil-Project"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io") {
            mavenContent {
                includeGroupAndSubgroups("com.github.mik3y")
            }
        }
        maven("https://raw.githubusercontent.com/D10NGYANG/maven-repo/main/repository") {
            mavenContent {
                includeGroupAndSubgroups("com.github.D10NGYANG")
            }
        }
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidDemo", ":composeDemo", ":jsDemo", ":macosDemo", ":linuxDemo", ":library")
project(":library").name = "DLSerialPortUtil"
 