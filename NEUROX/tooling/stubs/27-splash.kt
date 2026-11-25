// NEUROX offline compile stubs - core-splashscreen.
package androidx.core.splashscreen

class SplashScreen {
    fun setKeepOnScreenCondition(condition: () -> Boolean) {}

    companion object {
        fun installSplashScreen(activity: androidx.activity.ComponentActivity): SplashScreen = SplashScreen()
    }
}
