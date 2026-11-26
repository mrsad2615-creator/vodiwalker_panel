package com.neurox.presentation.screen

import com.neurox.common.result.DomainError
import com.neurox.common.result.Outcome
import com.neurox.domain.model.ProfileSummary
import com.neurox.domain.prefs.SettingsKeys
import com.neurox.domain.repository.ProfileRepository
import com.neurox.domain.repository.SettingsRepository
import com.neurox.presentation.i18n.Strings

/**
 * The onboarding flow as logic, not as Compose.
 *
 * WHY THIS EXISTS
 * The original [com.neurox.app] wiring did this inside a composable:
 *
 *     Step.PROFILE -> createProfileIfNeeded()   // launched, not awaited
 *     step = nextStep                           // advanced immediately
 *
 * Two defects followed from that, and both were invisible to every test in the
 * project because the logic lived in a `@Composable` that no test could run:
 *
 *  1. The profile was created in the background while the UI advanced regardless,
 *     so the finish handler could run against a null id and write nothing.
 *  2. A guest profile passed `displayName = ""` to a repository that rejects blank
 *     names, so guest mode failed 100% of the time - and the failure was discarded
 *     by `if (outcome is Outcome.Ok)`, leaving no profile and no message.
 *
 * The flow is now a plain class: it is driven step by step, every write is awaited,
 * every failure is returned, and the whole thing is tested without Compose.
 */
class OnboardingFlow(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
) {

    enum class Step { WELCOME, LANGUAGE, PROFILE, BASELINE, PRIVACY, ACCESSIBILITY, DONE }

    /** What the screen must show. Never a reason to guess. */
    data class Snapshot(
        val step: Step,
        val languageTag: String,
        val profileName: String,
        val isGuest: Boolean,
        val profileCreated: Boolean,
        val busy: Boolean = false,
        /** Set when the last action failed; the screen shows it and stays put. */
        val error: DomainError? = null,
    ) {
        val isLast: Boolean get() = step == Step.DONE

        /**
         * Whether the primary button may advance.
         *
         * The PROFILE step needs a usable name OR guest mode, and guest mode now
         * supplies its own label, so this matches what [OnboardingFlow.createProfile]
         * will accept rather than hoping.
         */
        val canContinue: Boolean
            get() = when (step) {
                Step.PROFILE -> profileCreated || isGuest || profileName.isNotBlank()
                Step.DONE -> profileCreated
                else -> true
            }
    }

    var step: Step = Step.WELCOME
        private set
    var languageTag: String = DEFAULT_LANGUAGE_TAG
        private set
    var profileName: String = ""
        private set
    var isGuest: Boolean = false
        private set
    var profileCreated: Boolean = false
        private set
    var busy: Boolean = false
        private set
    var error: DomainError? = null
        private set

    private var createdId: String? = null

    fun snapshot(): Snapshot = Snapshot(
        step = step,
        languageTag = languageTag,
        profileName = profileName,
        isGuest = isGuest,
        profileCreated = profileCreated,
        busy = busy,
        error = error,
    )

    fun setLanguage(tag: String) {
        languageTag = tag
    }

    fun setName(value: String) {
        profileName = value
        error = null
    }

    fun setGuest(value: Boolean) {
        isGuest = value
        // A guest needs no name, and a name typed before switching must not be sent
        // for a guest row: the guest label replaces it at creation time.
        error = null
    }

    /** Moves forward, creating the profile when the PROFILE step completes. */
    fun next(): Snapshot {
        if (busy) return snapshot()
        if (step == Step.PROFILE) {
            // Handled by createProfile + advanceAfterProfile so the caller can await.
            return snapshot()
        }
        advance()
        return snapshot()
    }

    fun back(): Snapshot {
        if (busy) return snapshot()
        val previous = Step.entries.getOrNull(step.ordinal - 1) ?: Step.WELCOME
        step = previous
        error = null
        return snapshot()
    }

    /**
     * Creates the profile. Returns the created id, or null with [error] set.
     *
     * GUEST LABEL: a guest profile is created with a real, non-blank label, because
     * the repository rejects blank names - and rightly so: a blank name would render
     * as an empty title everywhere a profile is shown. "Guest" is the label; the
     * distinguishing property is `isGuest`, not an empty string.
     */
    suspend fun createProfile(guestLabel: String): Outcome<String> {
        createdId?.let { return Outcome.Ok(it) }
        busy = true
        error = null
        val label = if (isGuest) guestLabel else profileName.trim()
        val created = profiles.createProfile(displayName = label, isGuest = isGuest)
        busy = false
        return when (created) {
            is Outcome.Ok -> {
                val id = created.value.id
                createdId = id
                profileCreated = true
                // Language belongs to this profile: two people on one phone may want
                // different ones, so it is written here and never globally.
                profiles.updateSettingsPatch(id, themeId = null, languageTag = languageTag)
                settings.put(id, SettingsKeys.LANGUAGE_TAG, languageTag)
                settings.put(id, SettingsKeys.ONBOARDING_SEEN_INTRO, "true")
                Outcome.Ok(id)
            }
            is Outcome.Err -> {
                error = created.error
                Outcome.Err(created.error)
            }
        }
    }

    /** Advances past PROFILE once a profile exists. */
    fun advanceAfterProfile() {
        if (profileCreated) advance()
    }

    /**
     * Marks the flow complete.
     *
     * Fails loudly when there is no profile: the old code returned silently, so a
     * user could "finish" onboarding having written nothing at all and be returned
     * to the first screen with no explanation.
     */
    suspend fun complete(baselineRequested: Boolean): Outcome<Unit> {
        val id = createdId
            ?: return Outcome.Err(DomainError.ProfileNotFound).also { error = DomainError.ProfileNotFound }
        busy = true
        settings.put(id, SettingsKeys.ONBOARDING_COMPLETE, "true")
        settings.put(id, SettingsKeys.ONBOARDING_SEEN_INTRO, "true")
        if (baselineRequested) {
            settings.put(id, SettingsKeys.BASELINE_COMPLETE, "false")
        } else {
            settings.put(id, SettingsKeys.BASELINE_SKIPPED, "true")
        }
        busy = false
        return Outcome.Ok(Unit)
    }

    /** The created profile, for a host that needs to navigate on it. */
    fun createdProfileId(): String? = createdId

    fun skipBaseline(): Snapshot {
        step = Step.PRIVACY
        return snapshot()
    }

    private fun advance() {
        step = Step.entries.getOrElse(step.ordinal + 1) { Step.DONE }
        error = null
    }

    companion object {
        /** The label a guest profile is created with. Tests pin this exact string. */
        /** Persian is the fallback language everywhere in this app. */
        const val DEFAULT_LANGUAGE_TAG = "fa"

        const val GUEST_LABEL_EN = "Guest"
        const val GUEST_LABEL_FA = "مهمان"

        /** The label for the current language; the catalogue owns the wording. */
        fun guestLabel(strings: Strings): String =
            if (strings.languageTag.startsWith("fa")) GUEST_LABEL_FA else GUEST_LABEL_EN
    }
}
