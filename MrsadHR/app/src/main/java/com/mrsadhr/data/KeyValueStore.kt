package com.mrsadhr.data

/**
 * Minimal persistence contract. The device implementation is SharedPreferences; tests and the
 * preview harness use an in-memory map.
 */
interface KeyValueStore {
    fun getString(key: String, fallback: String): String
    fun putString(key: String, value: String)
    fun getLong(key: String, fallback: Long): Long
    fun putLong(key: String, value: Long)
    fun getFloat(key: String, fallback: Float): Float
    fun putFloat(key: String, value: Float)
    fun getBool(key: String, fallback: Boolean): Boolean
    fun putBool(key: String, value: Boolean)
    fun remove(key: String)
}

class MemoryStore : KeyValueStore {
    private val map = HashMap<String, Any?>()
    override fun getString(key: String, fallback: String): String = map[key] as? String ?: fallback
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getLong(key: String, fallback: Long): Long = map[key] as? Long ?: fallback
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getFloat(key: String, fallback: Float): Float = map[key] as? Float ?: fallback
    override fun putFloat(key: String, value: Float) { map[key] = value }
    override fun getBool(key: String, fallback: Boolean): Boolean = map[key] as? Boolean ?: fallback
    override fun putBool(key: String, value: Boolean) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}
