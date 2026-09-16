import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(17)
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    val iosX64Target = iosX64()
    val iosArm64Target = iosArm64()
    val iosSimulatorArm64Target = iosSimulatorArm64()

    listOf(
        iosX64Target,
        iosArm64Target,
        iosSimulatorArm64Target,
    ).forEach { target ->
        target.binaries.framework {
            baseName = "TdayShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
    }
}

android {
    namespace = "com.ohmz.tday.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// ── Guide content codegen ────────────────────────────────────────────────
// Generates the committed guide artifacts (web structure JSON, Android strings,
// iOS per-locale JSON, search fixtures) from the shared GuideCatalog and the web
// locale files. `verifyGuideContent` (--check) is the CI drift gate — the Gradle
// twin of `node scripts/version.mjs check`.
run {
    val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")
    val exporterMain = "com.ohmz.tday.shared.guide.export.GuideContentExporterKt"
    val exporterClasspath =
        jvmMainCompilation.output.allOutputs + requireNotNull(jvmMainCompilation.runtimeDependencyFiles)

    tasks.register<JavaExec>("exportGuideContent") {
        group = "guide"
        description = "Generate committed guide artifacts from the catalog + locale files."
        dependsOn(jvmMainCompilation.compileTaskProvider)
        classpath = exporterClasspath
        mainClass.set(exporterMain)
        args(rootProject.rootDir.absolutePath)
    }

    tasks.register<JavaExec>("verifyGuideContent") {
        group = "guide"
        description = "Fail if the committed guide artifacts are stale (CI drift gate)."
        dependsOn(jvmMainCompilation.compileTaskProvider)
        classpath = exporterClasspath
        mainClass.set(exporterMain)
        args(rootProject.rootDir.absolutePath, "--check")
    }
}

// ── List icon table codegen ──────────────────────────────────────────────
// The third codegen, same shape as the two below it: generates the committed iOS
// copy of the shared list-icon keyword table. iOS links no Kotlin (the pbxproj has
// no TdayShared reference), so a committed artifact is the only way the table can
// reach it without a second hand-maintained word list. `verifyListIconTable`
// (--check) is the CI drift gate. See docs/ICONS.md.
run {
    val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")
    val exporterMain = "com.ohmz.tday.shared.listicon.export.ListIconTableExporterKt"
    val exporterClasspath =
        jvmMainCompilation.output.allOutputs + requireNotNull(jvmMainCompilation.runtimeDependencyFiles)

    tasks.register<JavaExec>("exportListIconTable") {
        group = "listicon"
        description = "Generate the committed iOS copy of the shared list icon keyword table."
        dependsOn(jvmMainCompilation.compileTaskProvider)
        classpath = exporterClasspath
        mainClass.set(exporterMain)
        args(rootProject.rootDir.absolutePath)
    }

    tasks.register<JavaExec>("verifyListIconTable") {
        group = "listicon"
        description = "Fail if the committed list icon table artifact is stale (CI drift gate)."
        dependsOn(jvmMainCompilation.compileTaskProvider)
        classpath = exporterClasspath
        mainClass.set(exporterMain)
        args(rootProject.rootDir.absolutePath, "--check")
    }
}

// ── Motion token codegen ─────────────────────────────────────────────────
// The repo's second cross-platform Gradle codegen, modelled on the guide one
// above: generates the committed Android/iOS/web motion artifacts from the
// shared MotionTokens source of truth. `verifyMotionTokens` (--check) is the CI
// drift gate. See docs/motion.md.
run {
    val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")
    val exporterMain = "com.ohmz.tday.shared.motion.export.MotionTokenExporterKt"
    val exporterClasspath =
        jvmMainCompilation.output.allOutputs + requireNotNull(jvmMainCompilation.runtimeDependencyFiles)

    tasks.register<JavaExec>("exportMotionTokens") {
        group = "motion"
        description = "Generate the committed motion token artifacts for all three clients."
        dependsOn(jvmMainCompilation.compileTaskProvider)
        classpath = exporterClasspath
        mainClass.set(exporterMain)
        args(rootProject.rootDir.absolutePath)
    }

    tasks.register<JavaExec>("verifyMotionTokens") {
        group = "motion"
        description = "Fail if the committed motion token artifacts are stale (CI drift gate)."
        dependsOn(jvmMainCompilation.compileTaskProvider)
        classpath = exporterClasspath
        mainClass.set(exporterMain)
        args(rootProject.rootDir.absolutePath, "--check")
    }
}
