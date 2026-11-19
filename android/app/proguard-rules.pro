# R8 Full-Mode Optimization & Obfuscation Rules for VodiGesture (Android 14/15)
-keepattributes *Annotation*,SourceFile,LineNumberTable

# Preserve AccessibilityService entry point referenced by Android System Server
-keep public class com.vodiwalker.gesture.service.accessibility.VodiGestureAccessibilityService { *; }

# Room payload codecs are hand-written Kotlin (no reflection-based serializers to preserve):
# only the DAO/entity JVM names used in generated queries must survive obfuscation.
-keepnames class com.vodiwalker.gesture.core.database.**Entity

# Google AI Edge LiteRT native JNI bridge
-keep class com.google.ai.edge.litert.** { *; }

# Keep the deobfuscation mapping self-describing for Play Console / crash triage.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room: database + DAO generated implementations are looked up by name at runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Hilt / Dagger generated component code
-keep,allowobfuscation @interface dagger.hilt.android.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-dontwarn dagger.hilt.**

# Kotlin metadata (needed for kotlin-reflect and coroutine intrinsics)
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# WorkManager instantiates the Hilt worker factory reflectively.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class androidx.work.WorkManagerInitializer { *; }

# Compose keeps its own rules; silence the optional-desugaring warnings.
-dontwarn androidx.compose.**
