// NEUROX offline compile stubs - android.database.Cursor (smallest real surface).
package android.database

/** On Android `Cursor` extends `Closeable`, so the stdlib `use` applies. */
interface Cursor : java.io.Closeable {
    fun moveToFirst(): Boolean
    fun getColumnIndex(name: String): Int
    fun getString(index: Int): String?
    fun getLong(index: Int): Long
    fun getInt(index: Int): Int
    override fun close()
}
