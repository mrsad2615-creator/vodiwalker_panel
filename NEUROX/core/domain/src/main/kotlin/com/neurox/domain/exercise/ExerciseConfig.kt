package com.neurox.domain.exercise

import com.neurox.common.json.JsonSerializable

/**
 * Validated configuration schema for one exercise instance.
 *
 * The Exercise Builder writes this structure, the engines read it, and the
 * backup format serializes it. There is no executable code in the schema, so a
 * custom exercise can never run arbitrary logic.
 *
 * Validation invariant: every numeric field is inside its allowed band. An
 * invalid configuration can be constructed only through [validate].
 */
@JsonSerializable
data class ExerciseConfig(
    val targetCount: Int = 1,
    val targetSizeDp: Int = 48,
    val targetSpeedDpPerSecond: Int = 60,
    val displayDurationMillis: Int = 800,
    val rounds: Int = 12,
    val sessionDurationSeconds: Int = 120,
    val errorAllowance: Int = 3,
    val movementDirection: MovementDirection = MovementDirection.ANY,
    val stimulusMode: StimulusMode = StimulusMode.VISUAL,
    val showLabels: Boolean = true,
    val readingTextId: String? = null,
    val typingTextId: String? = null,
    val typingLanguage: TypingLanguage = TypingLanguage.EN,
    val timerVisible: Boolean = true,
    val requireComprehension: Boolean = false,
) {
    /** Returns a list of violations. An empty list means valid. */
    fun validate(): List<ConfigViolation> {
        val out = mutableListOf<ConfigViolation>()
        if (targetCount !in 1..24) out += ConfigViolation("targetCount", 1, 24)
        if (targetSizeDp !in 16..160) out += ConfigViolation("targetSizeDp", 16, 160)
        if (targetSpeedDpPerSecond !in 0..600) out += ConfigViolation("targetSpeedDpPerSecond", 0, 600)
        if (displayDurationMillis !in 100..10_000) {
            out += ConfigViolation("displayDurationMillis", 100, 10_000)
        }
        if (rounds !in 1..99) out += ConfigViolation("rounds", 1, 99)
        if (sessionDurationSeconds !in 20..1_800) {
            out += ConfigViolation("sessionDurationSeconds", 20, 1_800)
        }
        if (errorAllowance !in 0..20) out += ConfigViolation("errorAllowance", 0, 20)
        return out
    }

    fun isValid(): Boolean = validate().isEmpty()
}

/** One boundary violation with a machine key and the allowed band. */
data class ConfigViolation(val field: String, val min: Int, val max: Int)

@JsonSerializable
enum class MovementDirection { ANY, HORIZONTAL, VERTICAL, DIAGONAL, STATIC }

@JsonSerializable
enum class StimulusMode { VISUAL, AUDIO, MIXED }

@JsonSerializable
enum class TypingLanguage { EN, FA }

/** Skill tags power Library filters and weak-skill detection. */
@JsonSerializable
enum class Skill(val id: String) {
    SELECTIVE_ATTENTION("selective_attention"),
    DIVIDED_ATTENTION("divided_attention"),
    SPATIAL_MEMORY("spatial_memory"),
    VISUAL_MEMORY("visual_memory"),
    REACTION_SPEED("reaction_speed"),
    INHIBITORY_CONTROL("inhibitory_control"),
    WORKING_MEMORY("working_memory"),
    READING_SPEED("reading_speed"),
    READING_COMPREHENSION("reading_comprehension"),
    PERIPHERAL_VISION("peripheral_vision"),
    MOTOR_PRECISION("motor_precision"),
    RHYTHM("rhythm"),
    LANGUAGE_FLEXIBILITY("language_flexibility"),
}
