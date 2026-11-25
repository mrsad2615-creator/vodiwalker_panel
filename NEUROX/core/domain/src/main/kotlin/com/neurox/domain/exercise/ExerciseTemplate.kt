package com.neurox.domain.exercise

import com.neurox.domain.model.DifficultyLevel
import com.neurox.domain.model.Module

/**
 * The NEUROX ORIGINAL catalog.
 *
 * Every exercise named by the product specification is here with a stable id.
 * Stable ids are used by sessions, records, achievements and backups; they must
 * never be renamed once released.
 */
enum class ExerciseTemplate(
    val id: String,
    val module: Module,
    val skills: List<Skill>,
    val defaultRounds: Int,
    val defaultDurationSeconds: Int,
    val baseResponseMillis: Double,
) {
    // ---- VISION (8) -------------------------------------------------------
    FOCUS_LOCK("vision.focus_lock", Module.VISION, listOf(Skill.SELECTIVE_ATTENTION), 16, 120, 1100.0),
    PERIPHERAL_SCAN("vision.peripheral_scan", Module.VISION, listOf(Skill.PERIPHERAL_VISION, Skill.DIVIDED_ATTENTION), 20, 120, 900.0),
    MULTI_TARGET_TRACKING("vision.multi_target_tracking", Module.VISION, listOf(Skill.DIVIDED_ATTENTION, Skill.WORKING_MEMORY), 12, 150, 1300.0),
    VISUAL_SEARCH("vision.visual_search", Module.VISION, listOf(Skill.SELECTIVE_ATTENTION), 14, 120, 1400.0),
    PATTERN_MATCH("vision.pattern_match", Module.VISION, listOf(Skill.VISUAL_MEMORY), 14, 120, 1200.0),
    VISUAL_MEMORY("vision.visual_memory", Module.VISION, listOf(Skill.VISUAL_MEMORY, Skill.SPATIAL_MEMORY), 10, 150, 1500.0),
    TARGET_IDENTIFICATION("vision.target_identification", Module.VISION, listOf(Skill.SELECTIVE_ATTENTION), 18, 120, 950.0),
    SPATIAL_ATTENTION("vision.spatial_attention", Module.VISION, listOf(Skill.SPATIAL_MEMORY, Skill.DIVIDED_ATTENTION), 14, 120, 1250.0),

    // ---- REFLEX (9) -------------------------------------------------------
    QUICK_TAP("reflex.quick_tap", Module.REFLEX, listOf(Skill.REACTION_SPEED), 12, 90, 420.0),
    COLOR_REACTION("reflex.color_reaction", Module.REFLEX, listOf(Skill.REACTION_SPEED, Skill.SELECTIVE_ATTENTION), 16, 90, 470.0),
    SHAPE_REACTION("reflex.shape_reaction", Module.REFLEX, listOf(Skill.REACTION_SPEED, Skill.SELECTIVE_ATTENTION), 16, 90, 500.0),
    DIRECTION_MATCH("reflex.direction_match", Module.REFLEX, listOf(Skill.REACTION_SPEED, Skill.WORKING_MEMORY), 16, 90, 620.0),
    DUAL_FOCUS("reflex.dual_focus", Module.REFLEX, listOf(Skill.DIVIDED_ATTENTION), 18, 120, 700.0),
    SEQUENCE_MEMORY("reflex.sequence_memory", Module.REFLEX, listOf(Skill.WORKING_MEMORY), 10, 150, 900.0),
    INHIBITION_TEST("reflex.inhibition_test", Module.REFLEX, listOf(Skill.INHIBITORY_CONTROL), 20, 120, 560.0),
    GO_NO_GO("reflex.go_no_go", Module.REFLEX, listOf(Skill.INHIBITORY_CONTROL, Skill.REACTION_SPEED), 24, 120, 500.0),
    MULTI_STIMULUS("reflex.multi_stimulus", Module.REFLEX, listOf(Skill.DIVIDED_ATTENTION, Skill.REACTION_SPEED), 20, 120, 720.0),

    // ---- READ (9) ---------------------------------------------------------
    WORD_FLASH("read.word_flash", Module.READ, listOf(Skill.READING_SPEED), 20, 120, 900.0),
    RSVP_READER("read.rsvp_reader", Module.READ, listOf(Skill.READING_SPEED), 1, 180, 900.0),
    PHRASE_READING("read.phrase_reading", Module.READ, listOf(Skill.READING_SPEED, Skill.READING_COMPREHENSION), 14, 150, 950.0),
    GUIDED_READING("read.guided_reading", Module.READ, listOf(Skill.READING_SPEED, Skill.PERIPHERAL_VISION), 1, 240, 900.0),
    VISUAL_SCANNING("read.visual_scanning", Module.READ, listOf(Skill.SELECTIVE_ATTENTION, Skill.PERIPHERAL_VISION), 16, 120, 1000.0),
    PERIPHERAL_READING("read.peripheral_reading", Module.READ, listOf(Skill.PERIPHERAL_VISION), 18, 120, 1100.0),
    READING_COMPREHENSION("read.reading_comprehension", Module.READ, listOf(Skill.READING_COMPREHENSION, Skill.WORKING_MEMORY), 6, 240, 1500.0),
    RECALL_CHALLENGE("read.recall_challenge", Module.READ, listOf(Skill.WORKING_MEMORY, Skill.READING_COMPREHENSION), 8, 180, 1400.0),
    FOCUS_READING("read.focus_reading", Module.READ, listOf(Skill.READING_SPEED, Skill.SELECTIVE_ATTENTION), 1, 300, 900.0),

    // ---- TYPE (8) ---------------------------------------------------------
    KEY_PRECISION("type.key_precision", Module.TYPE, listOf(Skill.MOTOR_PRECISION), 20, 120, 260.0),
    WORD_SPRINT("type.word_sprint", Module.TYPE, listOf(Skill.MOTOR_PRECISION, Skill.REACTION_SPEED), 1, 60, 240.0),
    SENTENCE_FLOW("type.sentence_flow", Module.TYPE, listOf(Skill.MOTOR_PRECISION, Skill.RHYTHM), 1, 120, 250.0),
    TIMED_CHALLENGE("type.timed_challenge", Module.TYPE, listOf(Skill.MOTOR_PRECISION), 1, 60, 240.0),
    WEAK_KEYS("type.weak_keys", Module.TYPE, listOf(Skill.MOTOR_PRECISION), 1, 90, 300.0),
    RHYTHM_TRAINING("type.rhythm_training", Module.TYPE, listOf(Skill.RHYTHM), 1, 90, 260.0),
    CODE_TYPING("type.code_typing", Module.TYPE, listOf(Skill.MOTOR_PRECISION), 1, 120, 300.0),
    CUSTOM_TEXT("type.custom_text", Module.TYPE, listOf(Skill.MOTOR_PRECISION), 1, 120, 260.0),
    ;

    val displayKey: String get() = "exercise.$id.name"
    val descriptionKey: String get() = "exercise.$id.description"

    /** Response-time reference in milliseconds at [difficulty]. */
    fun referenceMillis(difficulty: Int): Double {
        val factor = when (difficulty.coerceIn(DifficultyLevel.MIN, DifficultyLevel.MAX)) {
            1 -> 1.35
            2 -> 1.15
            3 -> 1.00
            4 -> 0.86
            else -> 0.72
        }
        return baseResponseMillis * factor
    }

    /** Default validated configuration for this template. */
    fun defaultConfig(): ExerciseConfig = when (this) {
        FOCUS_LOCK -> ExerciseConfig(targetCount = 1, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 700)
        PERIPHERAL_SCAN -> ExerciseConfig(targetCount = 3, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 500)
        MULTI_TARGET_TRACKING -> ExerciseConfig(targetCount = 3, targetSpeedDpPerSecond = 40, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds)
        VISUAL_SEARCH -> ExerciseConfig(targetCount = 6, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds)
        PATTERN_MATCH -> ExerciseConfig(targetCount = 4, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 1200)
        VISUAL_MEMORY -> ExerciseConfig(targetCount = 6, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 1500)
        TARGET_IDENTIFICATION -> ExerciseConfig(targetCount = 4, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 900)
        SPATIAL_ATTENTION -> ExerciseConfig(targetCount = 5, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 800)
        QUICK_TAP -> ExerciseConfig(rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 1000)
        COLOR_REACTION -> ExerciseConfig(targetCount = 4, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 900)
        SHAPE_REACTION -> ExerciseConfig(targetCount = 4, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 900)
        DIRECTION_MATCH -> ExerciseConfig(targetCount = 4, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 1000)
        DUAL_FOCUS -> ExerciseConfig(targetCount = 2, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 850)
        SEQUENCE_MEMORY -> ExerciseConfig(targetCount = 4, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 1200)
        INHIBITION_TEST -> ExerciseConfig(rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, errorAllowance = 4, displayDurationMillis = 700)
        GO_NO_GO -> ExerciseConfig(rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, errorAllowance = 4, displayDurationMillis = 650)
        MULTI_STIMULUS -> ExerciseConfig(targetCount = 3, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 800)
        WORD_FLASH -> ExerciseConfig(rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 600, showLabels = true)
        RSVP_READER -> ExerciseConfig(rounds = 1, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 800)
        PHRASE_READING -> ExerciseConfig(rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 900)
        GUIDED_READING -> ExerciseConfig(rounds = 1, sessionDurationSeconds = defaultDurationSeconds)
        VISUAL_SCANNING -> ExerciseConfig(targetCount = 8, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds)
        PERIPHERAL_READING -> ExerciseConfig(targetCount = 3, rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 700)
        READING_COMPREHENSION -> ExerciseConfig(rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, requireComprehension = true)
        RECALL_CHALLENGE -> ExerciseConfig(rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, displayDurationMillis = 1200, requireComprehension = true)
        FOCUS_READING -> ExerciseConfig(rounds = 1, sessionDurationSeconds = defaultDurationSeconds)
        KEY_PRECISION -> ExerciseConfig(rounds = defaultRounds, sessionDurationSeconds = defaultDurationSeconds, typingLanguage = TypingLanguage.EN)
        WORD_SPRINT -> ExerciseConfig(rounds = 1, sessionDurationSeconds = defaultDurationSeconds)
        SENTENCE_FLOW -> ExerciseConfig(rounds = 1, sessionDurationSeconds = defaultDurationSeconds)
        TIMED_CHALLENGE -> ExerciseConfig(rounds = 1, sessionDurationSeconds = defaultDurationSeconds)
        WEAK_KEYS -> ExerciseConfig(rounds = 1, sessionDurationSeconds = defaultDurationSeconds)
        RHYTHM_TRAINING -> ExerciseConfig(rounds = 1, sessionDurationSeconds = defaultDurationSeconds)
        CODE_TYPING -> ExerciseConfig(rounds = 1, sessionDurationSeconds = defaultDurationSeconds)
        CUSTOM_TEXT -> ExerciseConfig(rounds = 1, sessionDurationSeconds = defaultDurationSeconds)
    }

    companion object {
        fun fromId(id: String): ExerciseTemplate? = entries.firstOrNull { it.id == id }
        fun forModule(module: Module): List<ExerciseTemplate> = entries.filter { it.module == module }
    }
}
