// NEUROX offline compile stubs - core-splashscreen.
package androidx.core.splashscreen

/**
 * androidx.core.splashscreen 1.0.1.
 *
 * REAL SHAPE: `installSplashScreen()` is a TOP-LEVEL extension function on
 * ComponentActivity declared in this package, not a member of SplashScreen's
 * companion. The previous stub put it on the companion, so the call site
 * `SplashScreen.installSplashScreen(this)` compiled perfectly offline and failed
 * under Gradle with "Unresolved reference 'installSplashScreen'".
 *
 * Call sites must therefore import both the class and the extension, and call it
 * as `installSplashScreen()` on the activity (or via the import on a qualified
 * receiver). See NeuroxActivity.
 */
class SplashScreen {
    /** Keeps the splash visible while [condition] returns true. */
    fun setKeepOnScreenCondition(condition: () -> Boolean) {}

    fun setOnExitAnimationListener(listener: (Any) -> Unit) {}
}

/**
 * The real signature: `fun ComponentActivity.installSplashScreen(): SplashScreen`.
 * Declared at package level so an `import androidx.core.splashscreen.*` finds it.
 */
fun androidx.activity.ComponentActivity.installSplashScreen(): SplashScreen = SplashScreen()
