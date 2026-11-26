package com.neurox.presentation

import com.neurox.common.result.DomainError
import com.neurox.common.result.Outcome
import com.neurox.domain.model.ProfileSummary
import com.neurox.domain.prefs.SettingsKeys
import com.neurox.domain.repository.ProfileRepository
import com.neurox.domain.repository.SettingsRepository
import com.neurox.domain.repository.Stream
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.OnboardingFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Onboarding, as behaviour.
 *
 * These tests exist because the shipped 1.0.0 build could not get past onboarding
 * AT ALL, and every gate in the repository reported green. The flow lived inside a
 * `@Composable`, so nothing could exercise it; the run that finally showed the
 * defect was a person tapping a phone.
 *
 * The three behaviours pinned here are the three that were broken:
 *   1. a guest profile can be created at all;
 *   2. finishing writes the completion flag and does not silently no-op;
 *   3. advancing happens after the profile exists, never before.
 */
class OnboardingFlowTest {

    private class FakeProfiles : ProfileRepository {
        val created = mutableListOf<ProfileSummary>()
        val patches = mutableListOf<Triple<String, String?, String?>>()
        var nextId = 1
        var failWith: DomainError? = null
        var active: ProfileSummary? = null

        override fun observeProfiles(): Stream<List<ProfileSummary>> =
            Stream { collector -> collector(created.toList()) }

        override fun observeActiveProfile(): Stream<ProfileSummary?> =
            Stream { collector -> collector(active) }

        override suspend fun getActiveProfile(): ProfileSummary? = active
        override suspend fun getProfile(id: String): ProfileSummary? = created.firstOrNull { it.id == id }

        override suspend fun createProfile(displayName: String, isGuest: Boolean): Outcome<ProfileSummary> {
            failWith?.let { return Outcome.Err(it) }
            // Mirrors the real repository: a blank name is rejected.
            if (displayName.isBlank()) return Outcome.Err(DomainError.ProfileNameBlank)
            val profile = ProfileSummary(
                id = "p${nextId++}",
                displayName = displayName,
                isGuest = isGuest,
                avatarId = 0,
                createdAtMillis = 0L,
                hasPin = false,
                themeId = "neurox.obsidian",
                languageTag = "fa",
                active = true,
            )
            created += profile
            active = profile
            return Outcome.Ok(profile)
        }

        override suspend fun renameProfile(id: String, newName: String) = Outcome.Err(DomainError.ProfileNotFound)
        override suspend fun updateAvatar(id: String, avatarId: Int) = Outcome.Err(DomainError.ProfileNotFound)
        override suspend fun updateSettingsPatch(id: String, themeId: String?, languageTag: String?): Outcome<ProfileSummary> {
            patches += Triple(id, themeId, languageTag)
            return created.firstOrNull { it.id == id }?.let { Outcome.Ok(it) }
                ?: Outcome.Err(DomainError.ProfileNotFound)
        }
        override suspend fun switchProfile(id: String, pin: String?) = Outcome.Err(DomainError.ProfileNotFound)
        override suspend fun setPin(id: String, pin: String?) = Outcome.Err(DomainError.ProfileNotFound)
        override suspend fun verifyPin(id: String, pin: String) = false
        override suspend fun deleteProfile(id: String) = Outcome.Err(DomainError.ProfileNotFound)
        override suspend fun countProfiles(): Int = created.size
    }

    private class FakeSettings : SettingsRepository {
        val written = mutableMapOf<String, String>()

        override fun observeSettings(profileId: String): Stream<Map<String, String>> =
            Stream { collector -> collector(written.toMap()) }

        override suspend fun get(profileId: String, key: String): String? = written[key]

        override suspend fun put(profileId: String, key: String, value: String) {
            written[key] = value
        }

        override suspend fun putAll(profileId: String, values: Map<String, String>) {
            written.putAll(values)
        }
    }

    private fun flow() = OnboardingFlow(FakeProfiles(), FakeSettings())

    @Test
    fun aGuestCanBeCreatedAndIsNotRejectedForHavingNoName() = runBlocking {
        val profiles = FakeProfiles()
        val flow = OnboardingFlow(profiles, FakeSettings())
        flow.setGuest(true)

        val outcome = flow.createProfile(OnboardingFlow.GUEST_LABEL_FA)

        assertTrue("guest creation must succeed, got $outcome", outcome is Outcome.Ok)
        assertEquals(1, profiles.created.size)
        val profile = profiles.created.single()
        assertEquals("a guest is created with a label, not an empty name",
            OnboardingFlow.GUEST_LABEL_FA, profile.displayName)
        assertTrue("the guest flag is what distinguishes it", profile.isGuest)
        assertTrue(flow.snapshot().profileCreated)
    }

    @Test
    fun theGuestLabelFollowsTheLanguage() {
        assertEquals(OnboardingFlow.GUEST_LABEL_FA, OnboardingFlow.guestLabel(Strings.of("fa")))
        assertEquals(OnboardingFlow.GUEST_LABEL_EN, OnboardingFlow.guestLabel(Strings.of("en")))
    }

    @Test
    fun aFailedCreationIsReportedInsteadOfDiscarded() = runBlocking {
        val profiles = FakeProfiles().apply { failWith = DomainError.ProfileLimitReached }
        val flow = OnboardingFlow(profiles, FakeSettings())
        flow.setName("Ali")

        val outcome = flow.createProfile(OnboardingFlow.GUEST_LABEL_EN)

        assertTrue("the failure must surface", outcome is Outcome.Err)
        assertEquals(DomainError.ProfileLimitReached, flow.snapshot().error)
        assertFalse("no profile means the step is not complete", flow.snapshot().profileCreated)
        assertNull(flow.createdProfileId())
    }

    @Test
    fun finishingWritesTheCompletionFlag() = runBlocking {
        val settings = FakeSettings()
        val flow = OnboardingFlow(FakeProfiles(), settings)
        flow.setName("Sara")
        flow.createProfile(OnboardingFlow.GUEST_LABEL_EN)

        val outcome = flow.complete(baselineRequested = false)

        assertTrue("completion must not silently no-op", outcome is Outcome.Ok)
        assertEquals("true", settings.written[SettingsKeys.ONBOARDING_COMPLETE])
        assertEquals("true", settings.written[SettingsKeys.ONBOARDING_SEEN_INTRO])
        assertEquals("a skipped baseline is recorded as skipped", "true", settings.written[SettingsKeys.BASELINE_SKIPPED])
    }

    @Test
    fun finishingWithoutAProfileFailsLoudly() = runBlocking {
        val flow = flow()

        val outcome = flow.complete(baselineRequested = false)

        assertTrue("completing with no profile is a real failure", outcome is Outcome.Err)
        assertEquals(DomainError.ProfileNotFound, flow.snapshot().error)
    }

    @Test
    fun theProfileStepCompletesOnlyAfterTheProfileExists() = runBlocking {
        val flow = flow()
        flow.setName("Reza")
        repeat(2) { flow.next() } // WELCOME -> LANGUAGE -> PROFILE

        assertEquals(OnboardingFlow.Step.PROFILE, flow.snapshot().step)

        // next() alone must not skip past PROFILE; that was the original bug.
        flow.next()
        assertEquals("advancing past PROFILE without a profile must not happen",
            OnboardingFlow.Step.PROFILE, flow.snapshot().step)

        flow.createProfile(OnboardingFlow.GUEST_LABEL_EN)
        flow.advanceAfterProfile()
        assertEquals(OnboardingFlow.Step.BASELINE, flow.snapshot().step)
    }

    @Test
    fun theDoneStepCannotBeReachedWithoutAProfile() {
        val flow = flow()
        repeat(9) { flow.next() }
        assertTrue("no profile means the button stays disabled", !flow.snapshot().canContinue)
    }

    @Test
    fun theLanguageChosenInOnboardingIsWrittenToTheNewProfile() = runBlocking {
        val profiles = FakeProfiles()
        val settings = FakeSettings()
        val flow = OnboardingFlow(profiles, settings)
        flow.setLanguage("en")
        flow.setName("Nima")

        val id = (flow.createProfile(OnboardingFlow.GUEST_LABEL_EN) as Outcome.Ok).value

        assertEquals("en", settings.written[SettingsKeys.LANGUAGE_TAG])
        assertTrue("the patch must carry the language",
            profiles.patches.any { it.first == id && it.third == "en" })
    }

    @Test
    fun creatingTwiceIsIdempotent() = runBlocking {
        val profiles = FakeProfiles()
        val flow = OnboardingFlow(profiles, FakeSettings())
        flow.setName("Ali")

        val first = flow.createProfile(OnboardingFlow.GUEST_LABEL_EN)
        val second = flow.createProfile(OnboardingFlow.GUEST_LABEL_EN)

        assertEquals("a second tap must not create a second profile", 1, profiles.created.size)
        assertEquals((first as Outcome.Ok).value, (second as Outcome.Ok).value)
    }

    @Test
    fun aGuestTypingANameStillCreatesAGuest() = runBlocking {
        val profiles = FakeProfiles()
        val flow = OnboardingFlow(profiles, FakeSettings())
        flow.setName("Typed Before Toggling")
        flow.setGuest(true)

        flow.createProfile(OnboardingFlow.GUEST_LABEL_FA)

        val profile = profiles.created.single()
        assertTrue(profile.isGuest)
        assertEquals("the guest label wins, otherwise the row looks like a named profile",
            OnboardingFlow.GUEST_LABEL_FA, profile.displayName)
    }
}
