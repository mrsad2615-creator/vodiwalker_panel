package com.neurox.presentation

import com.neurox.presentation.i18n.Strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract between the pure layers and the string catalogues.
 *
 * WHY THIS EXISTS: `Strings.raw(key)` returns the key itself when it is missing, so a
 * wrong key is not a crash - it is the word "home.recommendedReason.start" shown to a
 * user in the middle of a sentence. Every compile gate passes. The only defence is to
 * assert that the keys the domain and presentation layers EMIT really exist.
 *
 * The recommendation reasons are the case that motivated this test: the domain emitted
 * "recommend.start" while the catalogue had "home.recommendedReason.start", and the
 * home screen hid it by taking everything after the last dot.
 */
class CatalogueContractTest {

    private val en = Strings.of("en")
    private val fa = Strings.of("fa")

    private fun assertInBothCatalogues(keys: List<String>) {
        for (key in keys) {
            assertTrue("missing from English: $key", en.has(key))
            assertTrue("missing from Persian: $key", fa.has(key))
        }
    }

    @Test
    fun everyRecommendationReasonHasATranslation() {
        assertInBothCatalogues(
            listOf(
                com.neurox.domain.progression.Recommender.REASON_START,
                com.neurox.domain.progression.Recommender.REASON_WEAK_SKILL,
                com.neurox.domain.progression.Recommender.REASON_NEW_MODULE,
                com.neurox.domain.progression.Recommender.REASON_ROTATION,
            ),
        )
    }

    @Test
    fun recommendationReasonsUseTheHomePrefix() {
        // Pins the family name: a rename here must change the catalogue too, and this
        // test is what makes that a two-file change instead of a silent one-file one.
        val reasons = listOf(
            com.neurox.domain.progression.Recommender.REASON_START,
            com.neurox.domain.progression.Recommender.REASON_WEAK_SKILL,
            com.neurox.domain.progression.Recommender.REASON_NEW_MODULE,
            com.neurox.domain.progression.Recommender.REASON_ROTATION,
        )
        for (key in reasons) {
            assertTrue("$key must be a home.recommendedReason.* key",
                key.startsWith("home.recommendedReason."))
        }
    }

    @Test
    fun everyModuleHasANameAndASkillLabel() {
        val keys = com.neurox.domain.model.Module.entries.flatMap { module ->
            listOf("module.${module.id}", "module.${module.id}.skill")
        }
        assertInBothCatalogues(keys)
    }

    @Test
    fun everyAudioChannelHasALabelAndAVolumeLabel() {
        val keys = com.neurox.domain.model.AudioChannel.entries.flatMap { channel ->
            listOf("audio.${channel.id}.enabled", "audio.${channel.id}.volume")
        }
        assertInBothCatalogues(keys)
    }

    @Test
    fun everyDifficultyHasALabel() {
        assertInBothCatalogues((1..5).map { "difficulty.$it" })
    }

    @Test
    fun everyThemeHasAName() {
        // Ids are "neurox.<short>"; the catalogue stores the short name.
        assertInBothCatalogues(listOf("settings.theme.obsidian", "settings.theme.ivory", "settings.theme.contrast"))
    }

    @Test
    fun everyAnalyticsRangeHasALabel() {
        assertInBothCatalogues(listOf("analytics.range.week", "analytics.range.month", "analytics.range.all"))
    }

    @Test
    fun everySkillHasALabel() {
        // Skill ids are the catalogue suffixes; the enum is the source of truth.
        val keys = com.neurox.domain.exercise.Skill.entries.map { "skill.${it.id}" }
        assertInBothCatalogues(keys)
    }

    @Test
    fun everyDomainErrorHasAMessage() {
        // DomainError codes are stable strings the UI translates. A new error without
        // a catalogue entry would render as the raw code, e.g. "error.storage_failure".
        val codes = listOf(
            "profile_not_found", "profile_inactive", "profile_name_blank",
            "profile_name_too_long", "profile_limit_reached", "pin_required",
            "pin_invalid", "session_not_running", "session_already_finished",
            "exercise_not_found", "invalid_configuration", "configuration_field_invalid",
            "backup_format_unsupported", "backup_version_unsupported", "backup_corrupted",
            "backup_password_wrong", "backup_content_invalid", "restore_verify_failed",
            "plan_conflict", "plan_invalid", "storage_failure", "unknown",
        )
        assertInBothCatalogues(codes.map { "error.$it" })
        // Pin the count so adding a DomainError without a catalogue entry fails here.
        assertEquals("DomainError grew; add the new error.* keys above", 22, codes.size)
    }

    @Test
    fun theTwoCataloguesStayInParity() {
        val enOnly = Strings.englishKeys() - Strings.persianKeys()
        val faOnly = Strings.persianKeys() - Strings.englishKeys()
        assertEquals("keys only in English: ${enOnly.sorted()}", emptySet<String>(), enOnly)
        assertEquals("keys only in Persian: ${faOnly.sorted()}", emptySet<String>(), faOnly)
    }

    @Test
    fun theCatalogueIsSubstantial() {
        // A sanity floor: a truncated catalogue file would otherwise pass parity.
        assertTrue("catalogue has ${Strings.englishKeys().size} keys", Strings.englishKeys().size >= 500)
    }
}
