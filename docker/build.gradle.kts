plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
    id("io.ktor.plugin") version "3.0.3" apply false
    id("io.sentry.jvm.gradle") version "5.7.0" apply false
}

allprojects {
    group = "com.ohmz.tday"
}
