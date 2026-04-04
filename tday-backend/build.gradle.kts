plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
    id("io.sentry.jvm.gradle")
    application
}

group = "com.ohmz"
version = "0.0.1"

application {
    mainClass.set("com.ohmz.tday.ApplicationKt")
}

val exposedVersion = "0.57.0"

dependencies {
    implementation(project(":shared"))

    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-default-headers-jvm")
    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-client-core-jvm")
    implementation("io.ktor:ktor-client-cio-jvm")
    implementation("io.ktor:ktor-client-content-negotiation-jvm")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.nimbusds:nimbus-jose-jwt:10.0.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
    implementation("org.dmfs:lib-recur:0.16.0")
    implementation("com.joestelmach:natty:0.13")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    implementation("io.sentry:sentry:8.13.0")
    implementation("io.sentry:sentry-logback:8.13.0")

    implementation("io.insert-koin:koin-ktor:4.0.4")
    implementation("io.insert-koin:koin-logger-slf4j:4.0.4")

    implementation("io.arrow-kt:arrow-core:2.1.2")
    implementation("io.arrow-kt:arrow-fx-coroutines:2.1.2")

    implementation("io.konform:konform-jvm:0.11.1")

    implementation("io.ktor:ktor-server-websockets-jvm")

    implementation("org.flywaydb:flyway-core:10.22.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.22.0")

    testImplementation("io.ktor:ktor-client-websockets-jvm")
    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

ktor {
    fatJar {
        archiveFileName.set("tday-backend-all.jar")
    }
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
}

sentry {
    includeSourceContext = true
    org = "tday-kb"
    projectName = "tday-backend"
    authToken = System.getenv("SENTRY_AUTH_TOKEN")
}
