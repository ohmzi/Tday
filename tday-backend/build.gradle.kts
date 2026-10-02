plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
    id("io.sentry.jvm.gradle") version "6.23.0"
    application
}

group = "com.ohmz"
version = projectVersion()

application {
    mainClass.set("com.ohmz.tday.ApplicationKt")
}

val exposedVersion = "1.5.0"

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

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    implementation("org.postgresql:postgresql:42.7.13")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("com.nimbusds:nimbus-jose-jwt:10.10")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.dmfs:lib-recur:0.17.1")
    implementation("com.joestelmach:natty:0.13")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("ch.qos.logback:logback-classic:1.6.5")

    implementation("io.sentry:sentry:8.59.0")
    implementation("io.sentry:sentry-logback:8.59.0")
    // SentryContext gives each request its own scope. The Sentry Gradle plugin also installs it
    // at build time, but a dependency the code compiles against should not hinge on that.
    implementation("io.sentry:sentry-kotlin-extensions:8.59.0")

    implementation("io.insert-koin:koin-ktor:4.2.2")
    implementation("io.insert-koin:koin-logger-slf4j:4.2.2")

    implementation("io.arrow-kt:arrow-core:2.2.3")

    implementation("io.konform:konform-jvm:0.11.1")

    implementation("io.ktor:ktor-server-websockets-jvm")

    implementation("org.flywaydb:flyway-core:13.8.1")
    implementation("org.flywaydb:flyway-database-postgresql:13.8.1")

    implementation("nl.martijndwars:web-push:5.1.2")
    // web-push 5.1.2 demotes its Apache HTTP client to a runtime dependency, but
    // WebPushService.send() still returns an org.apache.http.HttpResponse that
    // PushNotificationService reads, so the compile classpath needs httpcore.
    implementation("org.apache.httpcomponents:httpcore:4.4.16")

    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("io.ktor:ktor-client-websockets-jvm")
    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    // Real Postgres for the one property H2 cannot stand in for: partial
    // (filtered) unique indexes. See CompletedFloaterConcurrencyTest.
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.processResources {
    from(projectVersionManifest()) {
        rename { "tday-version.json" }
    }
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
    includeSourceContext = !System.getenv("SENTRY_AUTH_TOKEN").isNullOrBlank()
    org = "tday-kb"
    projectName = "tday-backend"
    authToken = System.getenv("SENTRY_AUTH_TOKEN")
}

fun projectVersion(): String {
    val manifest = projectVersionManifest()
    val match = Regex(""""version"\s*:\s*"([^"]+)"""").find(manifest.readText())
    return match?.groupValues?.get(1) ?: error("Could not read version from version.json")
}

fun projectVersionManifest(): File {
    return listOf(
        File(rootProject.projectDir, "version.json"),
        File(rootProject.projectDir.parentFile, "version.json"),
    ).firstOrNull { it.exists() }
        ?: error("Could not locate version.json from ${rootProject.projectDir}")
}
