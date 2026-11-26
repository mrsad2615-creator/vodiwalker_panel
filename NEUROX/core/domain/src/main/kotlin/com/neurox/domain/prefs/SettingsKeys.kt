package com.neurox.domain.prefs

/**
 * Stable settings keys.
 *
 * CONTRACT: a key is written once and never renamed. A renamed key would silently
 * reset a user preference. Keys are grouped by owner, and each group documents its
 * default so a missing row is never ambiguous.
 */
object SettingsKeys {

    // Ownership of the settings surface. These are stored per profile.
    const val ONBOARDING_COMPLETE = "onboarding.complete"
    const val ONBOARDING_SEEN_INTRO = "onboarding.seen_intro"
    const val BASELINE_COMPLETE = "baseline.complete"
    const val BASELINE_SKIPPED = "baseline.skipped"

    // Adaptive engine.
    const val ADAPTIVE_ENABLED = "adaptive.enabled"
    const val ADAPTIVE_MIN_SAMPLES = "adaptive.min_samples"
    const val ADAPTIVE_PROMOTE_ACCURACY = "adaptive.promote_accuracy"
    const val ADAPTIVE_DEMOTE_ACCURACY = "adaptive.demote_accuracy"

    // Timing calibration.
    const val TIMING_INPUT_LATENCY_MS = "timing.input_latency_ms"
    const val TIMING_DISPLAY_LATENCY_MS = "timing.display_latency_ms"

    // Presentation.
    const val THEME_ID = "theme.id"
    const val MOTION_MODE = "presentation.motion_mode"
    const val REDUCED_MOTION = "presentation.reduced_motion"
    const val TEXT_SCALE = "presentation.text_scale"
    const val LANGUAGE_TAG = "language.tag"
    const val SHOW_NUMBERS_LOCALIZED = "presentation.localized_numbers"

    // Reading Studio.
    const val READING_WPM = "reading.wpm"
    const val READING_FONT_SIZE = "reading.font_size"
    const val READING_LINE_HEIGHT = "reading.line_height"
    const val READING_FOCUS_GUIDE = "reading.focus_guide"
    const val READING_RTL = "reading.rtl"

    // Typing.
    const val TYPING_LANGUAGE = "typing.language"
    const val TYPING_SHOW_KEYBOARD_HINTS = "typing.keyboard_hints"

    // Training planner.
    const val PLAN_ACTIVE_ID = "plan.active_id"
    const val PLAN_WEEKLY_GOAL_MINUTES = "plan.weekly_goal_minutes"

    // Vault.
    const val VAULT_LAST_EXPORT_AT = "vault.last_export_at"
    const val VAULT_SCHEDULED_BACKUP = "vault.scheduled_backup"
    const val VAULT_SCHEDULED_BACKUP_DAYS = "vault.scheduled_backup_days"

    // Privacy.
    const val PRIVACY_TELEMETRY_ACK = "privacy.telemetry_ack"

    /** Defaults used when a key is missing. Never invent a value elsewhere. */
    val defaults: Map<String, String> = mapOf(
        ONBOARDING_COMPLETE to "false",
        ONBOARDING_SEEN_INTRO to "false",
        BASELINE_COMPLETE to "false",
        BASELINE_SKIPPED to "false",
        ADAPTIVE_ENABLED to "true",
        ADAPTIVE_MIN_SAMPLES to "3",
        ADAPTIVE_PROMOTE_ACCURACY to "0.92",
        ADAPTIVE_DEMOTE_ACCURACY to "0.68",
        TIMING_INPUT_LATENCY_MS to "0",
        TIMING_DISPLAY_LATENCY_MS to "0",
        THEME_ID to "neurox.obsidian",
        MOTION_MODE to "BALANCED",
        REDUCED_MOTION to "false",
        TEXT_SCALE to "1.0",
        LANGUAGE_TAG to "fa",
        SHOW_NUMBERS_LOCALIZED to "true",
        READING_WPM to "240",
        READING_FONT_SIZE to "19",
        READING_LINE_HEIGHT to "1.6",
        READING_FOCUS_GUIDE to "NONE",
        READING_RTL to "false",
        TYPING_LANGUAGE to "EN",
        TYPING_SHOW_KEYBOARD_HINTS to "true",
        PLAN_ACTIVE_ID to "",
        PLAN_WEEKLY_GOAL_MINUTES to "40",
        VAULT_LAST_EXPORT_AT to "0",
        VAULT_SCHEDULED_BACKUP to "false",
        VAULT_SCHEDULED_BACKUP_DAYS to "7",
        PRIVACY_TELEMETRY_ACK to "false",
    )

    /** Typed reader with the documented default, so call sites stay one line. */
    class Reader(private val values: Map<String, String>) {
        fun string(key: String): String = values[key] ?: defaults[key] ?: ""
        fun boolean(key: String): Boolean = string(key).toBooleanStrictOrNull() ?: false
        fun int(key: String, fallback: Int = 0): Int = string(key).toIntOrNull() ?: fallback
        fun long(key: String, fallback: Long = 0L): Long = string(key).toLongOrNull() ?: fallback
        fun float(key: String, fallback: Float = 0f): Float = string(key).toFloatOrNull() ?: fallback
    }
}

/**
 * The full set of preferences the presentation and domain layers need, in one
 * immutable object. Loading this is a single database read per profile.
 */
data class NxPreferences(
    val onboardingComplete: Boolean = false,
    val seenIntro: Boolean = false,
    val baselineComplete: Boolean = false,
    val baselineSkipped: Boolean = false,
    val adaptiveEnabled: Boolean = true,
    val adaptiveMinSamples: Int = 3,
    val themeId: String = "neurox.obsidian",
    val motionMode: com.neurox.common.MotionModeName = com.neurox.common.MotionModeName.BALANCED,
    val reducedMotion: Boolean = false,
    val textScale: Float = 1f,
    val languageTag: String = "fa",
    val inputLatencyMillis: Float = 0f,
    val displayLatencyMillis: Float = 0f,
    val readingWpm: Int = 240,
    val readingFontSize: Int = 19,
    val readingLineHeight: Float = 1.6f,
    val readingFocusGuide: String = "NONE",
    val typingLanguage: String = "EN",
    val weeklyGoalMinutes: Int = 40,
    val scheduledBackup: Boolean = false,
    val scheduledBackupDays: Int = 7,
    val lastExportAtMillis: Long = 0L,
) {
    companion object {
        fun from(values: Map<String, String>): NxPreferences {
            val reader = SettingsKeys.Reader(values)
            return NxPreferences(
                onboardingComplete = reader.boolean(SettingsKeys.ONBOARDING_COMPLETE),
                seenIntro = reader.boolean(SettingsKeys.ONBOARDING_SEEN_INTRO),
                baselineComplete = reader.boolean(SettingsKeys.BASELINE_COMPLETE),
                baselineSkipped = reader.boolean(SettingsKeys.BASELINE_SKIPPED),
                adaptiveEnabled = reader.boolean(SettingsKeys.ADAPTIVE_ENABLED),
                adaptiveMinSamples = reader.int(SettingsKeys.ADAPTIVE_MIN_SAMPLES, 3).coerceIn(1, 10),
                themeId = reader.string(SettingsKeys.THEME_ID),
                motionMode = runCatching {
                    com.neurox.common.MotionModeName.valueOf(reader.string(SettingsKeys.MOTION_MODE))
                }.getOrDefault(com.neurox.common.MotionModeName.BALANCED),
                reducedMotion = reader.boolean(SettingsKeys.REDUCED_MOTION),
                textScale = reader.float(SettingsKeys.TEXT_SCALE, 1f).coerceIn(0.8f, 2f),
                languageTag = reader.string(SettingsKeys.LANGUAGE_TAG).takeIf { it in setOf("fa", "en") } ?: "fa",
                inputLatencyMillis = reader.float(SettingsKeys.TIMING_INPUT_LATENCY_MS).coerceIn(0f, 100f),
                displayLatencyMillis = reader.float(SettingsKeys.TIMING_DISPLAY_LATENCY_MS).coerceIn(0f, 100f),
                readingWpm = reader.int(SettingsKeys.READING_WPM, 240).coerceIn(60, 900),
                readingFontSize = reader.int(SettingsKeys.READING_FONT_SIZE, 19).coerceIn(12, 40),
                readingLineHeight = reader.float(SettingsKeys.READING_LINE_HEIGHT, 1.6f).coerceIn(1f, 2.6f),
                readingFocusGuide = reader.string(SettingsKeys.READING_FOCUS_GUIDE),
                typingLanguage = reader.string(SettingsKeys.TYPING_LANGUAGE),
                weeklyGoalMinutes = reader.int(SettingsKeys.PLAN_WEEKLY_GOAL_MINUTES, 40).coerceIn(0, 1_200),
                scheduledBackup = reader.boolean(SettingsKeys.VAULT_SCHEDULED_BACKUP),
                scheduledBackupDays = reader.int(SettingsKeys.VAULT_SCHEDULED_BACKUP_DAYS, 7).coerceIn(1, 30),
                lastExportAtMillis = reader.long(SettingsKeys.VAULT_LAST_EXPORT_AT),
            )
        }
    }
}
