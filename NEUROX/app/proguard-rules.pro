# NEUROX release rules.
# kotlinx.serialization: keep generated serializers for @Serializable models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
# Room generated implementations are referenced reflectively by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn org.jetbrains.annotations.**
