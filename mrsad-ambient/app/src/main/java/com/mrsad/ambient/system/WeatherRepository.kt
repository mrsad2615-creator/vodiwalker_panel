package com.mrsad.ambient.system

import com.mrsad.ambient.domain.model.WeatherData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class WeatherRepository {

    private var cachedWeather: WeatherData? = null

    suspend fun fetchWeather(city: String, lat: Double, lon: Double): Result<WeatherData> = withContext(Dispatchers.IO) {
        try {
            val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min,uv_index_max,sunrise,sunset&timezone=auto"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }

            if (conn.responseCode != 200) {
                return@withContext cachedWeather?.let { Result.success(it.copy(isCached = true)) }
                    ?: Result.failure(IllegalStateException("HTTP ${conn.responseCode}"))
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")

            val code = current.optInt("weather_code", 0)
            val (fa, en) = mapWeatherCode(code)

            val data = WeatherData(
                cityName = city,
                temperatureC = current.optDouble("temperature_2m", 0.0),
                minTempC = daily.getJSONArray("temperature_2m_min").optDouble(0, 0.0),
                maxTempC = daily.getJSONArray("temperature_2m_max").optDouble(0, 0.0),
                conditionLabelFa = fa,
                conditionLabelEn = en,
                humidityPercent = current.optInt("relative_humidity_2m", 0),
                windKmh = current.optDouble("wind_speed_10m", 0.0),
                uvIndex = daily.getJSONArray("uv_index_max").optDouble(0, 0.0),
                sunrise = daily.getJSONArray("sunrise").optString(0, "--:--").takeLast(5),
                sunset = daily.getJSONArray("sunset").optString(0, "--:--").takeLast(5),
                lastUpdatedMillis = System.currentTimeMillis(),
                isCached = false
            )
            cachedWeather = data
            Result.success(data)
        } catch (e: Exception) {
            cachedWeather?.let { Result.success(it.copy(isCached = true)) } ?: Result.failure(e)
        }
    }

    private fun mapWeatherCode(code: Int): Pair<String, String> = when (code) {
        0 -> "صاف" to "Clear"
        1, 2, 3 -> "نیمه‌ابری" to "Partly Cloudy"
        45, 48 -> "مه‌آلود" to "Fog"
        51, 53, 55, 61, 63, 65 -> "بارانی" to "Rain"
        71, 73, 75 -> "برفی" to "Snow"
        95, 96, 99 -> "رعدوبرق" to "Thunderstorm"
        else -> "ابری" to "Cloudy"
    }
}
