pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Tday"

include(":shared")
include(":tday-backend")
include(":android-compose")
include(":android-compose:app")

project(":shared").projectDir = file("shared")
project(":tday-backend").projectDir = file("tday-backend")
project(":android-compose").projectDir = file("android-compose")
project(":android-compose:app").projectDir = file("android-compose/app")
