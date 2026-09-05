import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("io.sentry.android.gradle")
}

// Several AndroidX artifacts' dependency metadata resolves kotlin-stdlib to
// whatever the latest published release is (currently newer than this
// project's pinned Kotlin plugin version above), which the older compiler
// can't read ("compiled with an incompatible version of Kotlin"). Force it
// back down to match so `./gradlew :app:compileDebugKotlin` builds at all —
// pre-existing/unrelated to any one feature, found while working on notes.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
    }
}

val localProps: Properties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.reader().use { load(it) }
}

val projectVersion: String by lazy {
    val manifestCandidates = listOf(
        File(rootProject.projectDir, "version.json"),
        File(rootProject.projectDir.parentFile, "version.json"),
    )
    val manifest = manifestCandidates.firstOrNull { it.exists() }
        ?: error("Could not locate version.json from ${rootProject.projectDir}")
    val match = Regex(""""version"\s*:\s*"([^"]+)"""").find(manifest.readText())
    match?.groupValues?.get(1) ?: error("Could not read version from version.json")
}

// Android refuses to install an update whose versionCode is not strictly greater
// than the installed one, so this encoding has to be strictly increasing in
// (major, minor, patch). The previous `major * 10000 + minor * 100 + patch` was
// not: 0.7.100 and 0.8.0 both encoded to 800. release.yml bumps the patch on
// every merge to master, so three-digit patch numbers are reachable rather than
// theoretical, and a collision would silently stop the in-app APK updater.
//
// Every component now owns its own decimal slot — <major><minor:3><patch:4> —
// so 0.7.2 -> 70_002 and 1.44.0 -> 10_440_000. The slot widths are asserted
// below because a component that overflows its slot collides with the next slot
// up, which is exactly the bug this replaces.
val versionCodePatchSlot = 10_000 // patch owns 4 decimal digits: 0..9_999
val versionCodeMinorSlot = 1_000 // minor owns 3 decimal digits: 0..999
val versionCodeMajorScale = versionCodePatchSlot * versionCodeMinorSlot // 10_000_000

// versionCode is a signed 32-bit int and Play caps it at 2_100_000_000. With the
// slots above that still leaves room for major versions up to 209.
val versionCodeCeiling = 2_100_000_000

// The highest versionCode this project ever produced is 14_400, from the legacy
// v1.44.0 tag under the old formula. Anything at or below that could not install
// over an already-shipped APK, so fail the build rather than ship a dud update.
val highestShippedVersionCode = 14_400

val projectVersionCode: Int by lazy {
    val parts = projectVersion.split(".")
    require(parts.size == 3) {
        "version.json version must be major.minor.patch, got '$projectVersion'"
    }
    val (major, minor, patch) = parts.map { it.toInt() }
    require(minor in 0 until versionCodeMinorSlot) {
        "version.json minor $minor overflows its versionCode slot " +
            "(max ${versionCodeMinorSlot - 1}); widen the slots in app/build.gradle.kts"
    }
    require(patch in 0 until versionCodePatchSlot) {
        "version.json patch $patch overflows its versionCode slot " +
            "(max ${versionCodePatchSlot - 1}); widen the slots in app/build.gradle.kts"
    }

    val code = major.toLong() * versionCodeMajorScale +
        minor.toLong() * versionCodePatchSlot +
        patch.toLong()
    require(code <= versionCodeCeiling) {
        "versionCode $code for $projectVersion exceeds Android's $versionCodeCeiling ceiling"
    }
    require(code > highestShippedVersionCode) {
        "versionCode $code for $projectVersion is not above the highest already-shipped " +
            "$highestShippedVersionCode; Android would reject the update"
    }
    code.toInt()
}

// One name for the release build type, its signing config and the variant filter below, so the
// three do not drift apart (and so the literal is not repeated — DeepSource KT-W1042).
val releaseBuildType = "release"

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
        buildConfigField(
            "String",
            "SENTRY_TRACES_SAMPLE_RATE",
            "\"${localProps.getProperty("sentryTracesSampleRate") ?: System.getenv("SENTRY_TRACES_SAMPLE_RATE") ?: ""}\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create(releaseBuildType) {
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
            signingConfig = signingConfigs.findByName(releaseBuildType)
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

// Export the Room schema so future version bumps can ship real Migration objects
// (the offline cache also stores unsynced pending mutations, which destructive
// migration would lose). Commit the generated app/schemas/*.json files.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":shared"))

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // On-device natural-language date parsing for the task-title field (offline,
    // no AI/network). Same engine the backend uses, so behaviour matches.
    implementation("com.joestelmach:natty:0.13")

    // HTML sanitizer for the notes rich-text encoding — allow-lists the same
    // small tag set as tday-web's DOMParser-based sanitizer (see richNotes.ts).
    implementation("org.jsoup:jsoup:1.23.1")

    implementation("androidx.core:core-ktx:1.15.0")
    // Per-app language override (AppCompatDelegate.setApplicationLocales); works
    // back to API 21 via the AppLocalesMetadataHolderService manifest hook.
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    // Backdrop blur for the bottom toast so it matches iOS's translucent
    // .ultraThinMaterial look (RenderEffect on API 31+, translucent fallback below).
    implementation("dev.chrisbanes.haze:haze:1.2.2")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")

    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-compiler:2.57.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // The offline cache holds task titles, notes and unsynced mutations, so it is encrypted at
    // rest with SQLCipher. androidx-compatible artifact (net.zetetic:sqlcipher-android) so it
    // plugs into Room's openHelperFactory directly. See DatabaseModule / DatabasePassphraseStore.
    implementation("net.zetetic:sqlcipher-android:4.17.0")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // Optional, opt-in app lock (BiometricPrompt with device-credential fallback).
    implementation("androidx.biometric:biometric:1.1.0")

    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

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

    // UnifiedPush: lets Server-Mode self-hosters receive server pushes through their
    // own distributor (e.g. ntfy) instead of FCM. Local reminders remain the default.
    implementation("org.unifiedpush.android:connector:2.5.0")

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

// ── Widget class identity (R8) ──────────────────────────────────────────────
//
// Glance looks a widget provider up by CLASS NAME — `GlanceAppWidgetManager` stores
// `provider:<receiver>` as `appWidget.javaClass.canonicalName`, `updateAll` reads it back with an
// unvalidated `providerNameToReceivers[canonicalName]`, and `GlanceAppWidget.update` keys its
// render session on the appWidgetId alone. So two widget classes sharing one runtime name is not a
// size regression, it is a widget rendering as the wrong kind.
//
// R8 did exactly that: without the keep rules in `proguard-rules.pro`, its horizontal class merger
// collapsed TodayTasksWidget, FloaterTasksWidget and ListTasksWidget into a single class. The keep
// rules prevent it, but a keep rule is easy to delete and the symptom only appears in a signed
// release build on a real home screen. This reads the R8 mapping back and fails the build instead.
//
// Release-only by construction — there is no mapping file without R8 — so it runs in
// `release.yml`'s `assembleRelease`, not in the PR unit-test job.
val widgetClassesRequiringDistinctNames = listOf(
    "com.ohmz.tday.compose.feature.widget.TodayTasksWidget",
    "com.ohmz.tday.compose.feature.widget.FloaterTasksWidget",
    "com.ohmz.tday.compose.feature.widget.ListTasksWidget",
    "com.ohmz.tday.compose.feature.widget.TodayTasksWidgetSmallReceiver",
    "com.ohmz.tday.compose.feature.widget.TodayTasksWidgetReceiver",
    "com.ohmz.tday.compose.feature.widget.TodayTasksWidgetLargeReceiver",
    "com.ohmz.tday.compose.feature.widget.FloaterTasksWidgetSmallReceiver",
    "com.ohmz.tday.compose.feature.widget.FloaterTasksWidgetReceiver",
    "com.ohmz.tday.compose.feature.widget.FloaterTasksWidgetLargeReceiver",
    "com.ohmz.tday.compose.feature.widget.ListTasksWidgetSmallReceiver",
    "com.ohmz.tday.compose.feature.widget.ListTasksWidgetReceiver",
    "com.ohmz.tday.compose.feature.widget.ListTasksWidgetLargeReceiver",
)

// The mapping comes from AGP's artifact API rather than a hardcoded
// `outputs/mapping/<variant>/mapping.txt`. With core library desugaring enabled that path is only
// finalised by `l8DexDesugarLibRelease`, several tasks AFTER `minifyReleaseWithR8` — reading the
// literal path just gets the PREVIOUS build's mapping, which is worse than no check at all. The
// artifact provider carries whichever task actually produces it.
androidComponents.onVariants { variant ->
    if (variant.buildType != releaseBuildType) return@onVariants

    val mappingFile = variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
    val expected = widgetClassesRequiringDistinctNames
    val variantName = variant.name.replaceFirstChar { it.uppercase() }

    val verifyWidgetClassIdentity = tasks.register("verify${variantName}WidgetClassIdentity") {
        group = "verification"
        description =
            "Fails if R8 merged two Glance widget classes or receivers into one runtime class."

        inputs.file(mappingFile).withPropertyName("r8Mapping")
        // Cheap, and a stale "pass" here would be a silently broken gate.
        outputs.upToDateWhen { false }

        doLast {
            val mapping = mappingFile.get().asFile
            if (!mapping.isFile) {
                throw GradleException(
                    "No R8 mapping at ${mapping.absolutePath}; cannot verify widget class identity.",
                )
            }

            // A class definition in mapping.txt is the only kind of line starting at column 0:
            // `<original> -> <obfuscated>:`. Member lines are all indented.
            val outputNameByClass = mutableMapOf<String, String>()
            mapping.useLines { lines ->
                for (line in lines) {
                    if (line.isEmpty() || line[0].isWhitespace() || line.startsWith("#")) continue
                    val arrow = line.indexOf(" -> ")
                    if (arrow < 0 || !line.endsWith(":")) continue
                    val original = line.substring(0, arrow)
                    if (original in expected) {
                        outputNameByClass[original] = line.substring(arrow + 4, line.length - 1)
                    }
                }
            }

            // A class R8 merged INTO another loses its own definition line entirely, which is how
            // TodayTasksWidget and ListTasksWidget vanished. Absence is the primary symptom.
            val missing = expected.filterNot { it in outputNameByClass }
            val collisions = outputNameByClass.entries
                .groupBy({ it.value }, { it.key })
                .filterValues { it.size > 1 }

            if (missing.isNotEmpty() || collisions.isNotEmpty()) {
                val detail = buildString {
                    appendLine("R8 collapsed Glance widget class identities in the release build.")
                    appendLine("Glance resolves providers by canonical class name and keys render")
                    appendLine("sessions on the appWidgetId alone, so this makes a widget render as")
                    appendLine("another kind until the process is replaced.")
                    if (missing.isNotEmpty()) {
                        appendLine("No mapping entry (merged away or shrunk out):")
                        missing.forEach { appendLine("  - $it") }
                    }
                    collisions.forEach { (output, originals) ->
                        appendLine("Share the output name '$output':")
                        originals.forEach { appendLine("  - $it") }
                    }
                    appendLine("Restore the Glance keep rules in app/proguard-rules.pro.")
                    append("Mapping: ${mapping.absolutePath}")
                }
                throw GradleException(detail)
            }

            logger.lifecycle(
                "verifyWidgetClassIdentity: ${expected.size} widget classes have distinct " +
                    "runtime names.",
            )
        }
    }

    // Hung off `assemble`/`bundle` rather than finalizing the R8 task: a finalizer also runs when
    // R8 itself failed, and would then report a missing mapping on top of the real error.
    tasks.matching { it.name == "assemble$variantName" || it.name == "bundle$variantName" }
        .configureEach { dependsOn(verifyWidgetClassIdentity) }
}
