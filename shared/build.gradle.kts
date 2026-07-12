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
