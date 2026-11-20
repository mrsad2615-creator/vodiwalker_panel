package com.mrsad.ambient.domain.model

data class WeatherData(
    val cityName: String,
    val temperatureC: Double,
    val minTempC: Double,
    val maxTempC: Double,
    val conditionLabelFa: String,
    val conditionLabelEn: String,
    val humidityPercent: Int,
    val windKmh: Double,
    val uvIndex: Double,
    val sunrise: String,
    val sunset: String,
    val lastUpdatedMillis: Long,
    val isCached: Boolean = false
)

data class BatteryState(
    val percentage: Int,
    val isCharging: Boolean,
    val chargingSource: String,
    val estimatedMinutesToFull: Int? = null,
    val isLowBattery: Boolean = false
)

data class MediaState(
    val isActive: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val packageName: String = ""
)

data class NotificationItem(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val category: String
)

data class CallState(
    val hasIncomingOrMissed: Boolean = false,
    val callerName: String? = null,
    val phoneNumber: String? = null,
    val isRinging: Boolean = false,
    val missedCount: Int = 0
)

data class ActivityStats(
    val stepsToday: Int,
    val dailyGoal: Int = 8000,
    val estimatedDistanceKm: Double,
    val estimatedCaloriesKcal: Int,
    val sensorAvailable: Boolean
)

data class ConnectivityState(
    val wifiConnected: Boolean,
    val internetAvailable: Boolean,
    val bluetoothEnabled: Boolean
)

data class SensorStatus(
    val hasProximity: Boolean,
    val hasAccelerometer: Boolean,
    val hasLightSensor: Boolean,
    val hasStepCounter: Boolean,
    val isPocketCovered: Boolean = false,
    val ambientLux: Float? = null
)

data class DiagnosticEvent(
    val id: Long = 0,
    val timestamp: Long,
    val category: String,
    val message: String,
    val isEstimate: Boolean = false
)
