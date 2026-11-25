// NEUROX offline compile stubs - androidx.core:core-splashscreen 1.0.1.
package androidx.core.splashscreen

/**
 * Verified against the real library by checking how working projects import it:
 *
 *     import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
 *
 * `installSplashScreen()` is an extension on ComponentActivity declared INSIDE
 * SplashScreen's companion object. That detail matters for the import: it is
 *
 *     SplashScreen.Companion.installSplashScreen
 *
 * and not `androidx.core.splashscreen.installSplashScreen`, which is what a
 * top-level extension would be named. Declaring it top-level here made the
 * import fail under Gradle with an unresolved reference even after the previous
 * fix - two wrong shapes in a row, both of which compiled offline.
 */
class SplashScreen {
    /** Keeps the splash visible while [condition] returns true. */
    fun setKeepOnScreenCondition(condition: () -> Boolean) {}

    fun setOnExitAnimationListener(listener: (Any) -> Unit) {}

    companion object {
        /**
         * The real declaration:
         * `fun ComponentActivity.installSplashScreen(): SplashScreen`
         * as an extension inside this companion.
         */
        fun androidx.activity.ComponentActivity.installSplashScreen(): SplashScreen =
            SplashScreen()
    }
}
