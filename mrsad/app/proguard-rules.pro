# MRSAD release rules. Minification is disabled at this milestone; the file
# exists so enabling R8 later is a one-line change with documented keeps.
-keepattributes SourceFile,LineNumberTable
-keep class dev.mrsad.core.** { *; }
