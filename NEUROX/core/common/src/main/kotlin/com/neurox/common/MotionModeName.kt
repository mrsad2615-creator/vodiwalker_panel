package com.neurox.common

/**
 * Motion intensity, shared by settings, presentation and persistence.
 *
 * The name is stored in the settings table, so it is a persisted contract.
 */
enum class MotionModeName {
    CINEMATIC,
    BALANCED,
    PERFORMANCE,
}
