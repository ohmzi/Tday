# Keep all androidbrowserhelper classes (TWA runtime)
-keep class com.google.androidbrowserhelper.** { *; }
-keep class * extends com.google.androidbrowserhelper.** { *; }

# Keep generated DelegationProvider
-keep class **$$DelegationProvider { *; }

# Keep AndroidX classes used by the TWA
-keep class androidx.browser.** { *; }
-keep class androidx.core.** { *; }
