// NEUROX offline compile stubs - the Android framework surface this app uses.
//
// These mirror android.jar well enough to type-check. Nothing here ever runs.
package android.content

open class Context {
    open fun getSystemService(name: String): Any? = null
    open val applicationContext: Context get() = this
    open val packageName: String get() = "com.neurox.app"
    open fun getString(resId: Int): String = ""
    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = SharedPreferences()

    companion object {
        const val MODE_PRIVATE = 0
        const val VIBRATOR_SERVICE = "vibrator"
        const val AUDIO_SERVICE = "audio"
        const val VIBRATOR_MANAGER_SERVICE = "vibrator_manager"
    }
}

class SharedPreferences {
    fun getAll(): Map<String, Any?> = emptyMap()
    fun getString(key: String, defValue: String?): String? = defValue
    fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
    fun getInt(key: String, defValue: Int): Int = defValue
    fun getLong(key: String, defValue: Long): Long = defValue
    fun edit(): Editor = Editor()

    class Editor {
        fun putString(key: String, value: String?): Editor = this
        fun putBoolean(key: String, value: Boolean): Editor = this
        fun putInt(key: String, value: Int): Editor = this
        fun putLong(key: String, value: Long): Editor = this
        fun remove(key: String): Editor = this
        fun apply() {}
        fun commit(): Boolean = true
    }
}

