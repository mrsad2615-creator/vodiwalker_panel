plugins {
    // Version-catalog aliases are declared once here and applied by module convention plugins.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    // detekt / ktlint are declared in libs.versions.toml for local use but are NOT applied here:
    // the enforced release gate is tools/audit_android_standards.py (Android 14/15-specific,
    // tree-sitter AST based) plus tools/run_kotlin_tests.sh with kotlinc -Werror.
}
