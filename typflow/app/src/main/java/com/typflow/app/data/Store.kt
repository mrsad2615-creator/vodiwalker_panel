package com.typflow.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HistoryEntry(val type: String, val wpm: Int, val acc: Int, val date: String)

/**
 * Local, offline-only storage for stats, records, streaks, settings and
 * per-key error telemetry. Backed by SharedPreferences.
 */
class TypflowStore(context: Context) {
    private val prefs = context.getSharedPreferences("typflow_prefs", Context.MODE_PRIVATE)

    // ---- settings ----
    var themeId by mutableStateOf(prefs.getString("theme_id", "void") ?: "void")
    var lang by mutableStateOf(prefs.getString("lang", "fa") ?: "fa")
    var dailyGoal by mutableStateOf(prefs.getInt("daily_goal", 10))
    var soundOn by mutableStateOf(prefs.getBoolean("sound", false))
    var vibrationOn by mutableStateOf(prefs.getBoolean("vibration", true))
    var textScale by mutableStateOf(prefs.getFloat("text_scale", 1f))
    var chunkSize by mutableStateOf(prefs.getInt("chunk", 1))
    var rampOn by mutableStateOf(prefs.getBoolean("ramp", false))

    // ---- records & totals ----
    var bestWpm by mutableStateOf(prefs.getInt("best_wpm", 0))
    var bestAccuracy by mutableStateOf(prefs.getInt("best_acc", 0))
    var bestReadingWpm by mutableStateOf(prefs.getInt("best_read_wpm", 0))
    var bestComprehension by mutableStateOf(prefs.getInt("best_comp", 0))
    var totalSessions by mutableStateOf(prefs.getInt("total_sessions", 0))
    var totalMinutes by mutableStateOf(prefs.getInt("total_minutes", 0))
    var totalErrors by mutableStateOf(prefs.getInt("total_errors", 0))
    var streak by mutableStateOf(prefs.getInt("streak", 0))
    var bestStreak by mutableStateOf(prefs.getInt("best_streak", 0))
    var xp by mutableStateOf(prefs.getInt("xp", 0))
    var lastDay by mutableStateOf(prefs.getString("last_day", "") ?: "")

    var history by mutableStateOf(loadHistory())
    var keyErrors by mutableStateOf(loadKeyErrors())

    val level: Int get() = (xp / 250) + 1
    val levelProgress: Float get() = (xp % 250) / 250f

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun yesterday(): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }

    // ---------- persistence ----------

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

    private fun loadKeyErrors(): Map<Char, Int> {
        val raw = prefs.getString("key_errors", "") ?: ""
        if (raw.isBlank()) return emptyMap()
        val out = LinkedHashMap<Char, Int>()
        raw.split("|").forEach { part ->
            val p = part.split(":")
            if (p.size == 2 && p[0].length == 1) {
                p[1].toIntOrNull()?.let { if (it > 0) out[p[0][0]] = it }
            }
        }
        return out
    }

    private fun saveKeyErrors() {
        val raw = keyErrors.entries
            .filter { it.key != '|' && it.key != ':' && !it.key.isWhitespace() }
            .joinToString("|") { "${it.key}:${it.value}" }
        prefs.edit().putString("key_errors", raw).apply()
    }

    // ---------- setters ----------

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

    fun updateTextScale(s: Float) {
        textScale = s
        prefs.edit().putFloat("text_scale", s).apply()
    }

    fun updateChunk(c: Int) {
        chunkSize = c
        prefs.edit().putInt("chunk", c).apply()
    }

    fun updateRamp(on: Boolean) {
        rampOn = on
        prefs.edit().putBoolean("ramp", on).apply()
    }

    fun setSound(on: Boolean) {
        soundOn = on
        prefs.edit().putBoolean("sound", on).apply()
    }

    fun setVibration(on: Boolean) {
        vibrationOn = on
        prefs.edit().putBoolean("vibration", on).apply()
    }

    // ---------- activity ----------

    private fun touchStreak() {
        if (lastDay == today()) return
        streak = if (lastDay == yesterday()) streak + 1 else 1
        lastDay = today()
        if (streak > bestStreak) bestStreak = streak
        prefs.edit()
            .putInt("streak", streak)
            .putInt("best_streak", bestStreak)
            .putString("last_day", lastDay)
            .apply()
    }

    private fun addXp(amount: Int) {
        xp += amount
        prefs.edit().putInt("xp", xp).apply()
    }

    fun recordTyping(
        wpm: Int,
        acc: Int,
        minutes: Int,
        errors: Int,
        sessionKeyErrors: Map<Char, Int>
    ) {
        var changed = false
        if (wpm > bestWpm) {
            bestWpm = wpm
            prefs.edit().putInt("best_wpm", wpm).apply()
        }
        if (acc > bestAccuracy) {
            bestAccuracy = acc
            prefs.edit().putInt("best_acc", acc).apply()
        }
        totalSessions += 1
        totalMinutes += minutes.coerceAtLeast(1)
        totalErrors += errors
        prefs.edit()
            .putInt("total_sessions", totalSessions)
            .putInt("total_minutes", totalMinutes)
            .putInt("total_errors", totalErrors)
            .apply()
        history = (listOf(HistoryEntry("typing", wpm, acc, today())) + history).take(120)
        saveHistory()
        if (sessionKeyErrors.isNotEmpty()) {
            val merged = LinkedHashMap(keyErrors)
            sessionKeyErrors.forEach { (ch, n) -> merged[ch] = (merged[ch] ?: 0) + n }
            keyErrors = merged.entries.sortedByDescending { it.value }.take(24)
                .associate { it.key to it.value }
            saveKeyErrors()
            changed = true
        }
        if (!changed) Unit
        touchStreak()
        addXp(12 + wpm / 4 + acc / 10)
    }

    fun recordReading(wpm: Int, score: Int) {
        if (wpm > bestReadingWpm) {
            bestReadingWpm = wpm
            prefs.edit().putInt("best_read_wpm", wpm).apply()
        }
        if (score > bestComprehension) {
            bestComprehension = score
            prefs.edit().putInt("best_comp", score).apply()
        }
        totalSessions += 1
        totalMinutes += 2
        prefs.edit()
            .putInt("total_sessions", totalSessions)
            .putInt("total_minutes", totalMinutes)
            .apply()
        history = (listOf(HistoryEntry("reading", wpm, score, today())) + history).take(120)
        saveHistory()
        touchStreak()
        addXp(10 + wpm / 50 + score / 10)
    }

    // ---------- derived ----------

    fun minutesToday(): Int = history.count { it.date == today() } * 2

    fun sessionsToday(): Int = history.count { it.date == today() }

    fun goalFraction(): Float =
        (sessionsToday().toFloat() / dailyGoal.coerceAtLeast(1)).coerceIn(0f, 1f)

    fun typingSeries(limit: Int = 20): List<Int> =
        history.filter { it.type == "typing" }.take(limit).map { it.wpm }.reversed()

    fun topProblemKeys(limit: Int = 8): List<Pair<Char, Int>> =
        keyErrors.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }

    fun dailyCounts(days: Int = 7): List<Pair<String, Int>> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFmt = SimpleDateFormat("EEE", Locale.US)
        val out = ArrayList<Pair<String, Int>>()
        for (offset in days - 1 downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -offset)
            val key = fmt.format(c.time)
            out.add(dayFmt.format(c.time) to history.count { it.date == key })
        }
        return out
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        themeId = "void"
        lang = "fa"
        dailyGoal = 10
        soundOn = false
        vibrationOn = true
        textScale = 1f
        chunkSize = 1
        rampOn = false
        bestWpm = 0
        bestAccuracy = 0
        bestReadingWpm = 0
        bestComprehension = 0
        totalSessions = 0
        totalMinutes = 0
        totalErrors = 0
        streak = 0
        bestStreak = 0
        xp = 0
        lastDay = ""
        history = emptyList()
        keyErrors = emptyMap()
    }
}
