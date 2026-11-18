# Hilt, Room and AndroidX ship consumer rules. No Dana code relies on name-based reflection.
# Add targeted rules only for features that require them; do not keep whole packages.

# JNI resolves these implementation and callback names in the pinned Tesseract AAR.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
