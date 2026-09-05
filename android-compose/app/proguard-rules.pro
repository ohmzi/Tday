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

# ── Glance app widgets ──────────────────────────────────────────────
# DO NOT DELETE. Nothing else in this build makes two widget classes distinct at runtime, and
# without these rules a release APK silently renders the wrong widget.
#
# Glance identifies a widget provider by CLASS NAME, never by class identity:
#   * `GlanceAppWidgetManager.updateReceiver` records `provider:<receiver>` as
#     `appWidget.javaClass.canonicalName`;
#   * `GlanceAppWidgetKt.updateAll` calls `getGlanceIds(this.javaClass)`, which is a raw
#     `providerNameToReceivers[canonicalName]` lookup with no validation; and
#   * `GlanceAppWidget.update` keys its render session on `createUniqueRemoteUiName(appWidgetId)`
#     — the appWidgetId ALONE, with no widget class in the key — and, when no session is running
#     for that id, constructs `AppWidgetSession` with whichever `GlanceAppWidget` instance called
#     `update`. A running session keeps the class it was constructed with until the process dies.
#
# `TodayTasksWidget`, `FloaterTasksWidget` and `ListTasksWidget` are structurally identical, so
# R8's horizontal class merger collapsed all three into ONE runtime class. Verified in the
# mapping.txt of a release build made without these rules: `FloaterTasksWidget -> ki1` carrying a
# synthesized `$r8$classId` discriminator and a `provideGlance` body inlined from all three, with
# no top-level mapping line at all for the other two classes. All nine receivers then registered
# under that single provider name, so every kind's `updateAll` enumerated every OTHER kind's
# appWidgetIds and a Today-flavoured instance could take ownership of a Floater instance's render
# session — the "my Floater widget turned into the Today widget until I reopened the app" report.
# Debug builds are unminified, which is why this is invisible outside a release APK.
#
# Keeping the classes pins their names, which is what excludes them from the merger. The proof
# that a keep rule is sufficient is in the same build: `CompleteTodayTaskAction` and
# `CompleteFloaterTaskAction` are identically-shaped siblings that R8 left unmerged and unrenamed,
# solely because glance-appwidget's own consumer rules keep `ActionCallback` subclasses. Glance
# ships no such rule for `GlanceAppWidget`, so it has to live here.
#
# `:app:verifyReleaseWidgetClassIdentity` (app/build.gradle.kts) re-reads mapping.txt after every
# R8 run and fails the release build unless each of these classes still appears under its OWN name
# — which catches a merge, a shrink and a rename alike.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
# The nine concrete receivers are already pinned by the manifest, but their canonical names are
# the other half of the same lookup, so pin them explicitly rather than by side effect.
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver

# ── SQLCipher (encrypted offline cache) ─────────────────────────────
# The native layer looks these classes and their members up by name through JNI, so R8 renaming
# any of them turns into an UnsatisfiedLinkError at the first query — in release builds only.
-keep class net.zetetic.database.** { *; }
-keep interface net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# ── General ─────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
