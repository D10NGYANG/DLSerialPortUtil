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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
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

rootProject.name = "DLSerialPortUtil"
include(":app")
include(":library")
 