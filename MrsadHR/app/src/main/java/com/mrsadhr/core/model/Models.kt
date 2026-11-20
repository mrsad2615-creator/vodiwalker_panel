package com.mrsadhr.core.model

import com.mrsadhr.themes.ThemeId

enum class Mode { Timer, Stopwatch }

enum class Language(val key: String) {
    Persian("fa"), English("en");

    companion object {
        fun fromKey(k: String?): Language = if (k == "fa") Persian else English
    }
}

enum class TimePart { Hours, Minutes, Seconds }

enum class SoundCategory(val key: String) {
    Minimal("minimal"), Digital("digital"), Cinematic("cinematic"), Ambient("ambient")
}

data class Preset(val id: String, val seconds: Long)

data class Settings(
    val theme: ThemeId = ThemeId.Obsidian,
    val language: Language = Language.English,
    val haptics: Boolean = true,
    val soundEnabled: Boolean = true,
    val soundVolume: Float = 0.85f,
    val soundId: String = "digital-chime",
    val focusMode: Boolean = true,
    val reducedMotion: Boolean = false,
    val keepScreenOn: Boolean = true,
    val notifications: Boolean = true,
    val presets: List<Preset> = listOf(
        Preset("p1", 60L),
        Preset("p2", 5 * 60L),
        Preset("p3", 10 * 60L),
        Preset("p4", 30 * 60L),
    ),
)

data class TimerSnapshot(
    val durationMs: Long,
    val remainingMs: Long,
    val running: Boolean,
    val completed: Boolean,
) {
    val progress: Float
        get() = if (durationMs <= 0L) 1f else (1f - (remainingMs.toFloat() / durationMs)).coerceIn(0f, 1f)

    val isIdle: Boolean get() = !running && !completed && remainingMs == durationMs
}

data class Lap(val index: Int, val totalMs: Long, val splitMs: Long)

data class StopwatchSnapshot(
    val elapsedMs: Long,
    val running: Boolean,
    val laps: List<Lap>,
)

data class CompletionState(
    val atMonoMs: Long,
    val durationMs: Long,
)

data class AppState(
    val settings: Settings = Settings(),
    val mode: Mode = Mode.Timer,
    val timer: TimerSnapshot = TimerSnapshot(5 * 60_000L, 5 * 60_000L, false, false),
    val stopwatch: StopwatchSnapshot = StopwatchSnapshot(0L, false, emptyList()),
    val completion: CompletionState? = null,
)
