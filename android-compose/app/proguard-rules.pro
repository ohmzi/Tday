# ── kotlinx.serialization ────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.ohmz.tday.compose.core.model.**$$serializer { *; }
-keepclassmembers class com.ohmz.tday.compose.core.model.** {
    *** Companion;
    *** serializer(...);
}

-keep,includedescriptorclasses class com.ohmz.tday.compose.core.data.**$$serializer { *; }
-keepclassmembers class com.ohmz.tday.compose.core.data.** {
    *** Companion;
    *** serializer(...);
}

# Keep @Serializable data classes themselves
-keep @kotlinx.serialization.Serializable class com.ohmz.tday.compose.** { *; }

# ── Retrofit / OkHttp ───────────────────────────────────────────────
-keepattributes Signature, Exceptions
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation interface retrofit2.Callback
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Retrofit service interface
-keep interface com.ohmz.tday.compose.core.network.TdayApiService { *; }

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Hilt / Dagger ───────────────────────────────────────────────────
-dontwarn dagger.hilt.android.internal.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Compose ─────────────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ── Sentry ──────────────────────────────────────────────────────────
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**

# ── Natty (on-device NLP date parsing) + ANTLR runtime ──────────────
# Natty loads its generated ANTLR grammar/lexer/parser reflectively, so the
# whole package and the ANTLR runtime must survive R8 shrinking/obfuscation.
-keep class com.joestelmach.natty.** { *; }
-keep class org.antlr.** { *; }
-dontwarn com.joestelmach.natty.**
-dontwarn org.antlr.**
-dontwarn org.apache.commons.**
# ANTLR's old concurrent backport (edu.emory.mathcs) and its optional SLF4J
# logging binding, plus the JDK-internal classes it probes for a perf
# counter, are never actually present/used at runtime on Android — only
# referenced defensively — so R8 just needs to stop treating them as errors.
-dontwarn edu.emory.mathcs.backport.**
-dontwarn org.slf4j.**
-dontwarn sun.misc.Perf
-dontwarn sun.misc.Unsafe

# ── SQLCipher (encrypted offline cache) ─────────────────────────────
# The native layer looks these classes and their members up by name through JNI, so R8 renaming
# any of them turns into an UnsatisfiedLinkError at the first query — in release builds only.
-keep class net.zetetic.database.** { *; }
-keep interface net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# ── General ─────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
