plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("io.sentry.android.gradle")
}

val localProps: java.util.Properties by lazy {
    java.util.Properties().also { props ->
        val f = rootProject.file("local.properties")
        if (f.exists()) f.reader().use { props.load(it) }
    }
}

val projectVersion: String by lazy {
    val packageJsonCandidates = listOf(
        File(rootProject.projectDir, "tday-web/package.json"),
        File(rootProject.projectDir.parentFile, "tday-web/package.json"),
        File(rootProject.projectDir, "package.json"),
        File(rootProject.projectDir.parentFile, "package.json"),
    )
    val packageJson = packageJsonCandidates.firstOrNull { it.exists() }
        ?: error("Could not locate package.json from ${rootProject.projectDir}")
    val match = Regex(""""version"\s*:\s*"([^"]+)"""").find(packageJson.readText())
    match?.groupValues?.get(1) ?: error("Could not read version from package.json")
}

val projectVersionCode: Int by lazy {
    val (major, minor, patch) = projectVersion.split(".").map { it.toInt() }
    major * 10000 + minor * 100 + patch
}

val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseKeystoreFile = releaseKeystorePath
    ?.takeIf(String::isNotBlank)
    ?.let(::File)
val hasReleaseSigning = releaseKeystoreFile?.exists() == true &&
    !System.getenv("RELEASE_KEYSTORE_PASSWORD").isNullOrBlank() &&
    !System.getenv("RELEASE_KEY_ALIAS").isNullOrBlank() &&
    !System.getenv("RELEASE_KEY_PASSWORD").isNullOrBlank()
val allowDebugSignedRelease = providers.gradleProperty("allowDebugSignedRelease").orNull == "true"
val isReleaseTaskRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("Release", ignoreCase = true)
}

if (isReleaseTaskRequested && !hasReleaseSigning && !allowDebugSignedRelease) {
    error(
        "Release builds require the release keystore so APK signatures stay stable for updates. " +
            "Set RELEASE_KEYSTORE_PATH, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS, and " +
            "RELEASE_KEY_PASSWORD, or rerun with -PallowDebugSignedRelease=true for a local-only build.",
    )
}

android {
    namespace = "com.ohmz.tday.compose"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ohmz.tday.compose"
        minSdk = 26
        targetSdk = 35
        versionCode = projectVersionCode
        versionName = projectVersion
        manifestPlaceholders["usesCleartextTraffic"] = "false"

        buildConfigField(
            "String",
            "PROBE_ENCRYPTION_KEY",
            "\"${localProps.getProperty("probeEncryptionKey") ?: System.getenv("TDAY_PROBE_ENCRYPTION_KEY") ?: ""}\"",
        )
        buildConfigField(
            "String",
            "SENTRY_DSN",
            "\"${localProps.getProperty("sentryDsn") ?: System.getenv("SENTRY_DSN") ?: ""}\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }

        release {
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":shared"))

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("com.google.android.material:material:1.12.0")

    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-compiler:2.57.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("io.sentry:sentry-okhttp:8.13.0")
    implementation("io.sentry:sentry-android-navigation:8.13.0")

    implementation("androidx.security:security-crypto:1.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.2.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.6")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.6")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.6")
}

val hasSentryAuth = !System.getenv("SENTRY_AUTH_TOKEN").isNullOrBlank()

sentry {
    includeSourceContext = hasSentryAuth
    includeProguardMapping = true
    autoUploadProguardMapping = hasSentryAuth
    org = "tday-kb"
    projectName = "tday-android"
    authToken = System.getenv("SENTRY_AUTH_TOKEN")
    tracingInstrumentation {
        enabled = true
    }
    autoInstallation {
        enabled = true
        sentryVersion = "8.13.0"
    }
}
