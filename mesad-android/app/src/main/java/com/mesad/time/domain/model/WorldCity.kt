package com.mesad.time.domain.model

/**
 * Domain model for a World Clock city.
 */
data class WorldCity(
    val id: Long = 0,
    val name: String,        // "Tehran"
    val zoneId: String,      // "Asia/Tehran" (IANA)
    val isLocal: Boolean = false,
    val sortOrder: Int = 0
) {
    init {
        require(name.isNotBlank()) { "City name cannot be blank" }
        require(zoneId.isNotBlank()) { "ZoneId cannot be blank" }
    }
}
