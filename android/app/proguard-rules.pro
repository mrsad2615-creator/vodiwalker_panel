# R8 Full-Mode Optimization & Obfuscation Rules for VodiGesture (Android 14/15)
-keepattributes *Annotation*,SourceFile,LineNumberTable

# Preserve AccessibilityService entry point referenced by Android System Server
-keep public class com.vodiwalker.gesture.service.accessibility.VodiGestureAccessibilityService { *; }

# Room payload codecs are hand-written Kotlin (no reflection-based serializers to preserve):
# only the DAO/entity JVM names used in generated queries must survive obfuscation.
-keepnames class com.vodiwalker.gesture.core.database.**Entity

# Google AI Edge LiteRT native JNI bridge
-keep class com.google.ai.edge.litert.** { *; }
