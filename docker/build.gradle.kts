plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.20" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
    id("io.ktor.plugin") version "3.6.0" apply false
    id("io.sentry.jvm.gradle") version "6.23.0" apply false
}

allprojects {
    group = "com.ohmz.tday"
}
