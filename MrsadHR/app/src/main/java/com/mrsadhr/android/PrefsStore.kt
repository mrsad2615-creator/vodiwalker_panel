package com.mrsadhr.android

import android.content.Context
import com.mrsadhr.data.KeyValueStore

/**
 * SharedPreferences backed persistence. Writes are applied asynchronously; the app additionally
 * flushes on pause so a process kill right after a change cannot lose the last action.
 */
class PrefsStore(context: Context) : KeyValueStore {

    private val prefs = context.getSharedPreferences("mrsadhr.state", Context.MODE_PRIVATE)

    override fun getString(key: String, fallback: String): String = prefs.getString(key, fallback) ?: fallback
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }

    override fun getLong(key: String, fallback: Long): Long = prefs.getLong(key, fallback)
    override fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }

    override fun getFloat(key: String, fallback: Float): Float = prefs.getFloat(key, fallback)
    override fun putFloat(key: String, value: Float) { prefs.edit().putFloat(key, value).apply() }

    override fun getBool(key: String, fallback: Boolean): Boolean = prefs.getBoolean(key, fallback)
    override fun putBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }

    override fun remove(key: String) { prefs.edit().remove(key).apply() }

    fun flush() { prefs.edit().commit() }
}
