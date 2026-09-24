pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "9.4.1"
        id("com.android.library") version "9.4.1"
        id("org.jetbrains.kotlin.android") version "2.4.20"
        id("org.jetbrains.kotlin.multiplatform") version "2.4.20"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
        id("com.google.devtools.ksp") version "2.3.12"
        id("com.google.dagger.hilt.android") version "2.60.1"
        id("io.sentry.android.gradle") version "5.7.0"
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

rootProject.name = "TdayCompose"
include(":app")
include(":shared")

project(":shared").projectDir = file("../shared")
