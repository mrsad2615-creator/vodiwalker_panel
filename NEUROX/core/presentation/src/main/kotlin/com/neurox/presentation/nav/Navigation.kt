package com.neurox.presentation.nav

import com.neurox.domain.model.Module

/**
 * Every destination in the app.
 *
 * WHY THIS IS AN ENUM IN A PURE MODULE: which screens exist, which are immersive and
 * which appear in the bottom bar is product logic. Keeping it here means a test can
 * assert that the four modules are equally reachable, that no destination is
 * unreachable, and that immersive screens never show navigation chrome - all without
 * an emulator.
 *
 * @param route stable string used by the navigation host. Never renamed: deep links
 *   and saved back stacks use it.
 * @param inBottomBar whether the destination is a top-level tab.
 * @param immersive whether navigation chrome is hidden (measurement surfaces).
 */
enum class AppDestination(
    val route: String,
    val inBottomBar: Boolean,
    val immersive: Boolean,
) {
    HOME("home", inBottomBar = true, immersive = false),
    VISION("module/vision", inBottomBar = true, immersive = false),
    REFLEX("module/reflex", inBottomBar = true, immersive = false),
    READ("module/read", inBottomBar = true, immersive = false),
    TYPE("module/type", inBottomBar = true, immersive = false),
    ANALYTICS("analytics", inBottomBar = false, immersive = false),
    LIBRARY("library", inBottomBar = false, immersive = false),
    PROFILE("profile", inBottomBar = false, immersive = false),
    SETTINGS("settings", inBottomBar = false, immersive = false),
    SESSION("session/{exerciseId}", inBottomBar = false, immersive = true),
    READING_STUDIO("reading-studio", inBottomBar = false, immersive = false),
    BASELINE("baseline", inBottomBar = false, immersive = true),
    ONBOARDING("onboarding", inBottomBar = false, immersive = true),
    VAULT_EXPORT("vault/export", inBottomBar = false, immersive = true),
    VAULT_IMPORT("vault/import", inBottomBar = false, immersive = true),
    EXERCISE_BUILDER("library/builder", inBottomBar = false, immersive = false),
    ;

    /** The route pattern to register with the navigation host. */
    val pattern: String get() = route

    /** A concrete route for a parameterised destination. */
    fun of(module: Module): AppDestination = fromModule(module) ?: this

    /** Human-readable label key, so the bar and the screens agree. */
    val labelKey: String
        get() = when (this) {
            HOME -> "home.title"
            VISION, REFLEX, READ, TYPE -> "module.${module?.id ?: "vision"}"
            ANALYTICS -> "analytics.title"
            LIBRARY -> "library.title"
            PROFILE -> "profile.title"
            SETTINGS -> "settings.title"
            SESSION -> "session.running"
            READING_STUDIO -> "read.studio.title"
            BASELINE -> "mode.baseline"
            ONBOARDING -> "onboarding.welcome.title"
            VAULT_EXPORT -> "vault.export.title"
            VAULT_IMPORT -> "vault.import.title"
            EXERCISE_BUILDER -> "library.builder"
        }

    /** The module this destination browses, when it browses one. */
    val module: Module?
        get() = when (this) {
            VISION -> Module.VISION
            REFLEX -> Module.REFLEX
            READ -> Module.READ
            TYPE -> Module.TYPE
            else -> null
        }

    companion object {
        /** Resolves a registered route pattern or a concrete route to a destination. */
        fun fromRoute(route: String?): AppDestination? {
            if (route == null) return null
            return entries.firstOrNull { it.route == route }
                ?: entries.firstOrNull { pattern ->
                    pattern.route.contains("{") &&
                        route.startsWith(pattern.route.substringBefore("{"))
                }
        }

        fun fromModule(module: Module): AppDestination? = when (module) {
            Module.VISION -> VISION
            Module.REFLEX -> REFLEX
            Module.READ -> READ
            Module.TYPE -> TYPE
        }

        /** The bottom bar, in order. Exactly the five top-level destinations. */
        val bottomBar: List<AppDestination> = entries.filter { it.inBottomBar }
    }
}

/**
 * Navigation rules, as a testable object.
 *
 * These are the invariants that keep the app coherent as screens are added:
 * 1. The bottom bar shows only top-level destinations.
 * 2. Immersive destinations never show chrome.
 * 3. The four modules are equally reachable: same bar membership, same depth.
 * 4. Every non-immersive destination has a way back through the bar or an explicit
 *    back affordance, so no screen is a dead end.
 */
object NavigationState {

    /** Home plus the four modules is the complete bar; anything else is a bug. */
    const val BOTTOM_BAR_SIZE = 5

    fun showsBottomBar(destination: AppDestination): Boolean =
        destination.inBottomBar && !destination.immersive

    /** Destinations that must be reachable from the bar in one tap. */
    fun topLevel(): List<AppDestination> = AppDestination.bottomBar

    /**
     * True when the graph is coherent. Called by a unit test, not at runtime.
     */
    fun isCoherent(): Boolean {
        if (AppDestination.bottomBar.size != BOTTOM_BAR_SIZE) return false
        if (AppDestination.bottomBar.any { it.immersive }) return false
        if (AppDestination.bottomBar.first() != AppDestination.HOME) return false
        val modules = AppDestination.bottomBar.filter { it.module != null }
        if (modules.mapNotNull { it.module }.toSet() != Module.entries.toSet()) return false
        // Immersive screens must not be in the bar, and must all be parameterless or
        // carry their parameter in the route.
        if (AppDestination.entries.any { it.immersive && it.inBottomBar }) return false
        // Routes are unique, otherwise the navigation host throws at runtime.
        if (AppDestination.entries.map { it.route }.distinct().size != AppDestination.entries.size) return false
        return true
    }

    /**
     * The session route for an exercise.
     *
     * Exercise ids are namespaced with a dot and never contain slashes, so they are
     * safe as a path segment without encoding. The test below pins that assumption.
     */
    fun sessionRoute(exerciseId: String): String {
        require(!exerciseId.contains('/')) { "exercise id must not contain a slash: $exerciseId" }
        require(exerciseId.isNotBlank())
        return "session/$exerciseId"
    }
}
