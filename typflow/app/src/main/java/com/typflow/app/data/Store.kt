package com.typflow.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryEntry(val type: String, val wpm: Int, val acc: Int, val date: String)

/**
 * Local offline storage (SharedPreferences) — stats, records, history, settings.
 */
class TypflowStore(context: Context) {
    private val prefs = context.getSharedPreferences("typflow_prefs", Context.MODE_PRIVATE)

    var themeId by mutableStateOf(prefs.getString("theme_id", "cyber") ?: "cyber")
    var lang by mutableStateOf(prefs.getString("lang", "fa") ?: "fa")
    var dailyGoal by mutableStateOf(prefs.getInt("daily_goal", 10))
    var soundOn by mutableStateOf(prefs.getBoolean("sound", true))
    var vibrationOn by mutableStateOf(prefs.getBoolean("vibration", true))

    var bestWpm by mutableStateOf(prefs.getInt("best_wpm", 0))
    var bestAccuracy by mutableStateOf(prefs.getInt("best_acc", 0))
    var bestReadingWpm by mutableStateOf(prefs.getInt("best_read_wpm", 0))
    var totalSessions by mutableStateOf(prefs.getInt("total_sessions", 0))
    var totalMinutes by mutableStateOf(prefs.getInt("total_minutes", 0))
    var history by mutableStateOf(loadHistory())

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun loadHistory(): List<HistoryEntry> {
        val raw = prefs.getString("history", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { part ->
            val p = part.split(",")
            if (p.size == 4) {
                HistoryEntry(p[0], p[1].toIntOrNull() ?: 0, p[2].toIntOrNull() ?: 0, p[3])
            } else null
        }
    }

    private fun saveHistory() {
        val raw = history.joinToString(";") { "${it.type},${it.wpm},${it.acc},${it.date}" }
        prefs.edit().putString("history", raw).apply()
    }

    fun setTheme(id: String) {
        themeId = id
        prefs.edit().putString("theme_id", id).apply()
    }

    fun updateLang(l: String) {
        lang = l
        prefs.edit().putString("lang", l).apply()
    }

    fun updateDailyGoal(g: Int) {
        dailyGoal = g
        prefs.edit().putInt("daily_goal", g).apply()
    }

    fun setSound(on: Boolean) {
        soundOn = on
        prefs.edit().putBoolean("sound", on).apply()
    }

    fun setVibration(on: Boolean) {
        vibrationOn = on
        prefs.edit().putBoolean("vibration", on).apply()
    }

    fun recordTyping(wpm: Int, acc: Int, minutes: Int) {
        if (wpm > bestWpm) {
            bestWpm = wpm
            prefs.edit().putInt("best_wpm", wpm).apply()
        }
        if (acc > bestAccuracy) {
            bestAccuracy = acc
            prefs.edit().putInt("best_acc", acc).apply()
        }
        totalSessions += 1
        totalMinutes += minutes
        prefs.edit()
            .putInt("total_sessions", totalSessions)
            .putInt("total_minutes", totalMinutes)
            .apply()
        history = (listOf(HistoryEntry("typing", wpm, acc, today())) + history).take(100)
        saveHistory()
    }

    fun recordReading(wpm: Int, score: Int) {
        if (wpm > bestReadingWpm) {
            bestReadingWpm = wpm
            prefs.edit().putInt("best_read_wpm", wpm).apply()
        }
        totalSessions += 1
        totalMinutes += 2
        prefs.edit()
            .putInt("total_sessions", totalSessions)
            .putInt("total_minutes", totalMinutes)
            .apply()
        history = (listOf(HistoryEntry("reading", wpm, score, today())) + history).take(100)
        saveHistory()
    }

    fun minutesToday(): Int = history.count { it.date == today() } * 2

    fun resetAll() {
        prefs.edit().clear().apply()
        themeId = "cyber"
        lang = "fa"
        dailyGoal = 10
        soundOn = true
        vibrationOn = true
        bestWpm = 0
        bestAccuracy = 0
        bestReadingWpm = 0
        totalSessions = 0
        totalMinutes = 0
        history = emptyList()
    }
}
