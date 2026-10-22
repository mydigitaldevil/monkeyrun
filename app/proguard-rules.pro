# Jungle Run release rules. AdMob, UMP, Play Billing, Meta Audience Network and Firebase
# ship their own consumer ProGuard rules; these are extra safety nets.

# Keep line numbers for readable Crashlytics stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Meta Audience Network (loads parts of itself reflectively)
-keep class com.facebook.ads.** { *; }
-dontwarn com.facebook.infer.annotation.**

# AdMob mediation adapters are instantiated by class name
-keep class com.google.ads.mediation.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
