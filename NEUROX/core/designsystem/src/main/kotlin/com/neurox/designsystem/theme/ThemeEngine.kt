package com.neurox.designsystem.theme

/**
 * Color Studio logic: create, preview, rename, delete, restore, and validate themes.
 *
 * This object contains no Compose code, so every rule is unit-testable and the
 * persistable format is independent of the UI that edits it.
 *
 * INVARIANTS
 * - Built-in themes can be reset but never deleted.
 * - A custom theme always has a unique id and a non-empty name within its limit.
 * - An accent color must keep readable contrast against its surface; if it does not,
 *   the engine reports a violation instead of silently applying it.
 * - Editing a theme changes only colors. Exercise configuration, layout and timing
 *   are never part of a theme (see docs/DECISIONS.md, DEC-0007).
 */
object ThemeEngine {

    const val MAX_NAME_LENGTH = 40
    const val MAX_CUSTOM_THEMES = 24
    const val MIN_ACCENT_CONTRAST = 3.0

    /** The persisted theme library of one profile. */
    data class ThemeLibrary(
        val themes: List<NxThemeSpec>,
        val activeThemeId: String,
    ) {
        fun active(): NxThemeSpec = themes.firstOrNull { it.id == activeThemeId }
            ?: NxThemeSpec.OBSIDIAN

        val custom: List<NxThemeSpec> get() = themes.filterNot { it.isBuiltIn }
        val builtIn: List<NxThemeSpec> get() = themes.filter { it.isBuiltIn }
    }

    sealed interface Result {
        data class Ok(val library: ThemeLibrary) : Result
        data class Invalid(val reason: String) : Result
    }

    fun emptyLibrary(): ThemeLibrary = ThemeLibrary(
        themes = NxThemeSpec.builtIns,
        activeThemeId = NxThemeSpec.OBSIDIAN.id,
    )

    /**
     * Adds a new custom theme with a generated id.
     *
     * [idSeed] keeps the id deterministic in tests; production passes a random or
     * time-based seed.
     */
    fun create(
        library: ThemeLibrary,
        name: String,
        accentArgb: Long,
        secondaryArgb: Long,
        lightSurfaces: Boolean,
        idSeed: String,
    ): Result {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.Invalid("theme.name.empty")
        if (trimmed.length > MAX_NAME_LENGTH) return Result.Invalid("theme.name.too_long")
        if (library.custom.size >= MAX_CUSTOM_THEMES) return Result.Invalid("theme.limit_reached")
        val id = "custom.$idSeed"
        if (library.themes.any { it.id == id }) return Result.Invalid("theme.id.duplicate")
        val candidate = NxThemeSpec(
            id = id,
            name = trimmed,
            isBuiltIn = false,
            accentArgb = accentArgb,
            secondaryArgb = secondaryArgb,
            lightSurfaces = lightSurfaces,
        )
        val contrast = contrastViolation(candidate)
        if (contrast != null) return Result.Invalid(contrast)
        return Result.Ok(library.copy(themes = library.themes + candidate))
    }

    /**
     * Updates an existing custom theme. Built-in themes keep their colors but can be
     * restored, which is [reset].
     */
    fun update(library: ThemeLibrary, theme: NxThemeSpec): Result {
        val existing = library.themes.firstOrNull { it.id == theme.id }
            ?: return Result.Invalid("theme.not_found")
        if (existing.isBuiltIn) return Result.Invalid("theme.builtin_locked")
        val trimmed = theme.name.trim()
        if (trimmed.isEmpty()) return Result.Invalid("theme.name.empty")
        if (trimmed.length > MAX_NAME_LENGTH) return Result.Invalid("theme.name.too_long")
        val contrast = contrastViolation(theme)
        if (contrast != null) return Result.Invalid(contrast)
        val updated = theme.copy(name = trimmed, isBuiltIn = false)
        return Result.Ok(library.copy(themes = library.themes.map { if (it.id == theme.id) updated else it }))
    }

    /** Renames a theme. Names are user-facing and have no other effect. */
    fun rename(library: ThemeLibrary, themeId: String, newName: String): Result {
        val theme = library.themes.firstOrNull { it.id == themeId }
            ?: return Result.Invalid("theme.not_found")
        if (theme.isBuiltIn) return Result.Invalid("theme.builtin_locked")
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return Result.Invalid("theme.name.empty")
        if (trimmed.length > MAX_NAME_LENGTH) return Result.Invalid("theme.name.too_long")
        return Result.Ok(
            library.copy(
                themes = library.themes.map { if (it.id == themeId) it.copy(name = trimmed) else it },
            ),
        )
    }

    /** Deletes a custom theme. The active theme falls back to the signature theme. */
    fun delete(library: ThemeLibrary, themeId: String): Result {
        val theme = library.themes.firstOrNull { it.id == themeId }
            ?: return Result.Invalid("theme.not_found")
        if (theme.isBuiltIn) return Result.Invalid("theme.builtin_locked")
        val remaining = library.themes.filterNot { it.id == themeId }
        val active = if (library.activeThemeId == themeId) NxThemeSpec.OBSIDIAN.id else library.activeThemeId
        return Result.Ok(ThemeLibrary(remaining, active))
    }

    /** Restores a built-in theme to its original values. */
    fun reset(library: ThemeLibrary, themeId: String): Result {
        val original = NxThemeSpec.builtIns.firstOrNull { it.id == themeId }
            ?: return Result.Invalid("theme.not_builtin")
        return Result.Ok(
            library.copy(themes = library.themes.map { if (it.id == themeId) original else it }),
        )
    }

    /** Activates a theme. Unknown ids are refused, so a stored id cannot break the UI. */
    fun activate(library: ThemeLibrary, themeId: String): Result {
        if (library.themes.none { it.id == themeId }) return Result.Invalid("theme.not_found")
        return Result.Ok(library.copy(activeThemeId = themeId))
    }

    /**
     * Checks contrast for the accent against the surface it will sit on.
     *
     * The accent is used for text and icons on the surface, so it must reach the
     * large-text threshold, and it must also be distinguishable from the surface.
     */
    fun contrastViolation(theme: NxThemeSpec): String? {
        val colors = NxColorResolver.resolve(theme)
        val accentContrast = NxColorResolver.contrastRatio(theme.accentArgb, colors.surface)
        if (accentContrast < MIN_ACCENT_CONTRAST) return "theme.contrast.accent_low"
        val onAccent = NxColorResolver.contrastRatio(theme.secondaryArgb, colors.onAccent)
        if (onAccent < MIN_ACCENT_CONTRAST) return "theme.contrast.on_accent_low"
        return null
    }

    private fun NxColorResolver.contrastRatio(accentArgb: Long, surface: androidx.compose.ui.graphics.Color): Double =
        NxColorResolver.contrastRatio(androidx.compose.ui.graphics.Color(accentArgb), surface)
}

/**
 * Reduce-motion and motion-mode decisions are user settings. This helper turns them
 * into concrete numbers so the UI stays declarative.
 */
object MotionPolicy {

    fun duration(
        baseMillis: Int,
        mode: com.neurox.designsystem.tokens.NxMotion.MotionMode,
        systemAnimationsDisabled: Boolean,
    ): Int {
        if (systemAnimationsDisabled) return 0
        return com.neurox.designsystem.tokens.NxMotion.durationMillis(baseMillis, mode)
    }

    /** Only the cinematic mode plays the launch sequence, and only once. */
    fun playsIntro(
        mode: com.neurox.designsystem.tokens.NxMotion.MotionMode,
        seenIntro: Boolean,
        systemAnimationsDisabled: Boolean,
    ): Boolean = mode == com.neurox.designsystem.tokens.NxMotion.MotionMode.CINEMATIC &&
        !seenIntro &&
        !systemAnimationsDisabled
}
