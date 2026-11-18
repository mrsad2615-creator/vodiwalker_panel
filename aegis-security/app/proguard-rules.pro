-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault

-keep class ir.aegis.core.model.** { *; }
-keep class ir.aegis.core.maps.** { *; }
-keep class ir.aegis.core.ai.** { *; }

# Keep kotlinx.serialization serializers & companions under AGP 9 R8 full mode
-keep,includedescriptorclasses class ir.aegis.core.model.**$$serializer { *; }
-keep,includedescriptorclasses class ir.aegis.core.maps.**$$serializer { *; }
-keep,includedescriptorclasses class ir.aegis.core.ai.**$$serializer { *; }
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class ir.aegis.core.maps.** {
    *** Companion;
}
-keepclassmembers class ir.aegis.core.model.** {
    *** Companion;
}

-dontwarn kotlinx.serialization.**
