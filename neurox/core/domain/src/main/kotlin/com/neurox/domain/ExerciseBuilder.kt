package com.neurox.domain

/** A finite template schema. No scripts, expressions or executable user content. */
data class ExerciseBlueprint(
    val name: String,
    val template: Exercise,
    val difficulty: Int = 1,
    val rounds: Int = 5,
    val responseLimitMs: Long = 5_000,
    val targetAccuracyPercent: Int = 80,
    val errorAllowance: Int = 30,
    val schemaVersion: Int = 1,
) {
    val module: Module get() = template.module
    fun validate(): List<String> = buildList {
        if (schemaVersion != 1) add("unsupported_schema")
        if (name.trim().length !in 1..60 || name.any { it.isISOControl() || it in unsafeDirectionControls }) add("invalid_name")
        addAll(configuration(Language.EN, 0).validate())
    }
    fun configuration(language: Language, seed: Long, sourceId: String? = null, practice: Boolean = false): TrainingConfig = TrainingConfig(
        template, language, difficulty, rounds, responseLimitMs, stimulusSeed = seed,
        targetAccuracyPercent = targetAccuracyPercent, errorAllowance = errorAllowance,
        practice = practice, customExerciseId = sourceId, customExerciseName = if (sourceId != null) name.trim() else null,
    )
    companion object {
        private val unsafeDirectionControls = ('\u202A'..'\u202E') + ('\u2066'..'\u2069')
    }
}
