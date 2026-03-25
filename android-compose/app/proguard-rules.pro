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

# ── General ─────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
