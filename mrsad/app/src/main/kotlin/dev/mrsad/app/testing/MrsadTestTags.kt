package dev.mrsad.app.testing

/**
 * Stable, app-owned Compose test tags.
 *
 * Instrumented tests must never depend on user-visible strings: Persian
 * labels change with the product, several of them are duplicated across
 * screens, and numerals are sometimes rendered in Persian digits. Every tag
 * below is part of the app's own contract with its tests.
 */
object MrsadTestTags {

    // ---- Bottom navigation -------------------------------------------------
    const val NAV_BAR = "nav_bar"
    const val NAV_SETTINGS = "nav_settings"

    /** Tag of a bottom-navigation destination, e.g. `nav_type`. */
    fun navItem(route: String): String = "nav_$route"

    // ---- HOME ---------------------------------------------------------------
    const val HOME_BRAND = "home_brand"
    const val HOME_CORE = "home_core"
    const val HOME_MODULES = "home_modules"
    const val HOME_PRIMARY_CTA = "home_primary_cta"

    /** Tag of a HOME module card, e.g. `home_module_type`. */
    fun homeModule(route: String): String = "home_module_$route"

    // ---- TYPE ---------------------------------------------------------------
    const val TYPE_SETUP = "type_setup"
    const val TYPE_START = "type_start"
    const val TYPE_RUNNING = "type_running"
    const val TYPE_FINISH = "type_finish"
    const val TYPE_RESULTS = "type_results"
    const val TYPE_RETRY = "type_retry"
    const val TYPE_BACK_TO_SETUP = "type_back_to_setup"

    // ---- SETTINGS -----------------------------------------------------------
    const val SETTINGS_ROOT = "settings_root"
    const val SETTINGS_THEME_SECTION = "settings_theme_section"
    const val SETTINGS_QUALITY_SECTION = "settings_quality_section"
    const val SETTINGS_TOGGLES = "settings_toggles"
    const val SETTINGS_RESTORE_DEFAULTS = "settings_restore_defaults"
    const val SETTINGS_PERSIAN_NUMERALS = "settings_persian_numerals"
    const val SETTINGS_REDUCE_MOTION = "settings_reduce_motion"

    /** Tag of a theme swatch, e.g. `settings_theme_crystal`. */
    fun settingsTheme(variantId: String): String = "settings_theme_$variantId"

    /** Tag of a graphics-quality choice, e.g. `settings_quality_high`. */
    fun settingsQuality(qualityId: String): String = "settings_quality_$qualityId"

    // ---- FOCUS --------------------------------------------------------------
    const val FOCUS_ROOT = "focus_root"
    const val FOCUS_DIAL = "focus_dial"
    const val FOCUS_START = "focus_start"
    const val FOCUS_STOP = "focus_stop"
}
