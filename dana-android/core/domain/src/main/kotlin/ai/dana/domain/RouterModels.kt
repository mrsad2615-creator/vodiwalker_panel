package ai.dana.domain

internal fun isRouterFreeId(id: String): Boolean = id == OPENROUTER_FREE_MODEL ||
    id.length <= ProviderConfig.MODEL_LIMIT && FREE_VARIANT.matches(id)

private val FREE_VARIANT = Regex("[a-z0-9][a-z0-9._-]*/[a-z0-9][a-z0-9._-]*:free")

/** Exact, free-only routing; no unverified model string can become a paid request. */
class RouterFreeModel private constructor(val id: String, val name: String) {
    companion object {
        val Automatic = RouterFreeModel(OPENROUTER_FREE_MODEL, "OpenRouter Free")

        fun fromCatalog(id: String, name: String): RouterFreeModel? {
            if (id == OPENROUTER_FREE_MODEL || !isRouterFreeId(id)) return null
            val clean = name.trim()
            if (clean.length !in 1..120 || clean.any { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }) return null
            return RouterFreeModel(id, clean)
        }
    }
}
