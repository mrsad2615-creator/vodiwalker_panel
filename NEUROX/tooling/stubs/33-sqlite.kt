// NEUROX offline compile stubs - androidx.sqlite.db.
package androidx.sqlite.db

interface SupportSQLiteDatabase {
    fun execSQL(sql: String)
    fun query(query: String): SupportSQLiteCursor
}

interface SupportSQLiteCursor {
    fun moveToFirst(): Boolean
    fun moveToNext(): Boolean
    fun getInt(columnIndex: Int): Int
    fun getLong(columnIndex: Int): Long
    fun getString(columnIndex: Int): String?
    fun close()
}
