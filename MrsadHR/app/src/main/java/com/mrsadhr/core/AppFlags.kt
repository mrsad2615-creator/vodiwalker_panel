package com.mrsadhr.core

/**
 * Global flags that must be readable from pure drawing code without threading a design object
 * through every leaf function (currently only reduced motion for micro animations).
 */
object AppFlags {
    @Volatile
    var reducedMotion: Boolean = false
}
