package com.neurox.feature.read

import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.Module

/**
 * Module entry point for Read.
 *
 * The browsing screen itself is shared (feature:vision owns the single
 * implementation) so the four modules cannot drift apart in behaviour or layout.
 * This file states which module this package is responsible for, and is the only
 * module-specific code a module browser needs.
 */
object ReadModule {

    val module: Module = Module.READ

    /** Every exercise of this module, in declaration order. */
    fun templates(): List<ExerciseTemplate> = ExerciseTemplate.forModule(module)

    fun isEmpty(): Boolean = templates().isEmpty()
}
