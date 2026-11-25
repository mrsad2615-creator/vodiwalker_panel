// NEUROX offline compile stubs - android.app.Activity.
package android.app

import android.content.Context
import android.os.Bundle

open class Activity : Context() {
    open val application: Application get() = Application()
    open fun onCreate(savedInstanceState: Bundle?) {}
    open fun onStart() {}
    open fun onResume() {}
    open fun onPause() {}
    open fun onStop() {}
    open fun onDestroy() {}
    open fun finish() {}
}
