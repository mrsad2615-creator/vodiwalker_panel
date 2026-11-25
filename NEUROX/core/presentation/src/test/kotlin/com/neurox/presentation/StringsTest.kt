package com.neurox.presentation

import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.progression.Achievement
import com.neurox.domain.model.Module
import com.neurox.domain.progression.Quests
import com.neurox.presentation.i18n.LayoutDirection
import com.neurox.presentation.i18n.Strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The localization contract is only worth having if it is enforced. These tests
 * are the enforcement: they fail the build if a key exists in one language and not
 * the other, if a translation is blank, if a placeholder is missing, or if any
 * user-visible domain label has no translation at all.
 */
class StringsTest {

    private val english = Strings.of("en")
    private val persian = Strings.of("fa")

    @Test
    fun bothLanguagesDefineExactlyTheSameKeys() {
        val en = Strings.englishKeys()
        val fa = Strings.persianKeys()
        val missingInPersian = en - fa
        val missingInEnglish = fa - en
        assertEquals("keys missing from Persian: $missingInPersian", emptySet<String>(), missingInPersian)
        assertEquals("keys missing from English: $missingInEnglish", emptySet<String>(), missingInEnglish)
    }

    @Test
    fun noTranslationIsBlank() {
        for (strings in listOf(english, persian)) {
            val blank = Strings.englishKeys().filter { strings.raw(it).isBlank() }
            assertEquals("blank values in ${strings.languageTag}: $blank", emptyList<String>(), blank)
        }
    }

    @Test
    fun placeholdersMatchAcrossLanguages() {
        // A translation that drops {0} would show a sentence with no number in it.
        val mismatched = Strings.englishKeys().filter { key ->
            val en = placeholders(english.raw(key))
            val fa = placeholders(persian.raw(key))
            en != fa
        }
        assertEquals("placeholder mismatch: $mismatched", emptyList<String>(), mismatched)
    }

    @Test
    fun everyExerciseHasANameAndDescriptionInBothLanguages() {
        for (template in ExerciseTemplate.entries) {
            for (strings in listOf(english, persian)) {
                val name = strings.raw(template.displayKey)
                val description = strings.raw(template.descriptionKey)
                assertFalse("missing name for ${template.id} (${strings.languageTag})", name == template.displayKey)
                assertFalse(
                    "missing description for ${template.id} (${strings.languageTag})",
                    description == template.descriptionKey,
                )
                assertTrue(name.isNotBlank())
                assertTrue(description.isNotBlank())
            }
        }
    }

    @Test
    fun everyModuleHasALabelAndASkillSummary() {
        for (module in Module.entries) {
            for (strings in listOf(english, persian)) {
                val label = strings.raw("module.${module.id}")
                val skill = strings.raw("module.${module.id}.skill")
                assertFalse("missing module label ${module.id}", label == "module.${module.id}")
                assertFalse("missing module summary ${module.id}", skill == "module.${module.id}.skill")
            }
        }
    }

    @Test
    fun everyAchievementIsTranslated() {
        for (achievement in Achievement.entries) {
            for (strings in listOf(english, persian)) {
                assertFalse(
                    "missing title ${achievement.titleKey} (${strings.languageTag})",
                    strings.raw(achievement.titleKey) == achievement.titleKey,
                )
                assertFalse(
                    "missing description ${achievement.descriptionKey} (${strings.languageTag})",
                    strings.raw(achievement.descriptionKey) == achievement.descriptionKey,
                )
            }
        }
    }

    @Test
    fun everyQuestTitleIsTranslated() {
        val missions = Quests.dailyMissions(dayKey = 20_000L)
        assertTrue(missions.isNotEmpty())
        for (mission in missions) {
            for (strings in listOf(english, persian)) {
                assertFalse(
                    "missing quest title ${mission.titleKey} (${strings.languageTag})",
                    strings.raw(mission.titleKey) == mission.titleKey,
                )
            }
        }
    }

    @Test
    fun everyDomainErrorCodeHasAMessage() {
        val codes = listOf(
            "profile_not_found", "profile_inactive", "profile_name_blank", "profile_name_too_long",
            "profile_limit_reached", "pin_required", "pin_invalid", "session_not_running",
            "session_already_finished", "exercise_not_found", "invalid_configuration",
            "configuration_field_invalid", "backup_format_unsupported", "backup_version_unsupported",
            "backup_corrupted", "backup_password_wrong", "backup_content_invalid",
            "restore_verify_failed", "plan_conflict", "plan_invalid", "storage_failure", "unknown",
        )
        for (code in codes) {
            for (strings in listOf(english, persian)) {
                val key = "error.$code"
                assertFalse("missing error message for $key", strings.raw(key) == key)
            }
        }
    }

    @Test
    fun persianIsRightToLeftAndEnglishIsNot() {
        assertEquals(LayoutDirection.RTL, persian.layoutDirection)
        assertEquals(LayoutDirection.LTR, english.layoutDirection)
        assertTrue(persian.isRightToLeft)
        assertFalse(english.isRightToLeft)
    }

    @Test
    fun persianUsesPersianDigitsForSocialNumbers() {
        assertEquals("۱۲۳", persian.decimal(123))
        assertEquals("123", english.decimal(123))
        // Negative numbers keep the sign in ASCII.
        assertEquals("-۹", persian.decimal(-9))
    }

    @Test
    fun measurementsKeepWesternDigitsInPersian() {
        // A stopwatch reading must be unambiguous, so milliseconds are not localised.
        val text = persian.millis(412.0)
        assertTrue("expected ASCII digits in: $text", text.contains("412"))
    }

    @Test
    fun percentageDropsTheDecimalAtOneHundred() {
        assertEquals("100%", english.percent(1.0))
        // 99.9% must not be displayed as 100%: that would claim a perfect session.
        assertEquals("99.9%", english.percent(0.999))
        assertEquals("87.5%", english.percent(0.875))
        assertEquals("0.0%", english.percent(0.0))
        // Out-of-range input is clamped instead of printing 340%.
        assertEquals("100%", english.percent(3.4))
    }

    @Test
    fun formatSubstitutesEveryPlaceholder() {
        val text = english.format("session.round", 3, 12)
        assertEquals("Round 3 of 12", text)
        val persianText = persian.format("session.round", 3, 12)
        assertEquals("دور ۳ از ۱۲", persianText)
    }

    @Test
    fun missingKeyReturnsTheKeyInsteadOfThrowing() {
        // QA builds must show the key so a missing string is obvious, and a missing
        // translation must never crash a screen.
        assertEquals("no.such.key", english.raw("no.such.key"))
        assertFalse(english.has("no.such.key"))
    }

    @Test
    fun unknownOrNullLanguageFallsBackToPersian() {
        assertEquals("fa", Strings.of(null).languageTag)
        assertEquals("fa", Strings.of("de").languageTag)
        assertEquals("fa", Strings.of("fa-IR").languageTag)
        assertEquals("en", Strings.of("en-GB").languageTag)
    }

    @Test
    fun allKeysAreNamespaced() {
        // A key without a namespace is a key nobody can find later.
        for (key in Strings.englishKeys()) {
            assertNotNull(key)
            assertTrue("key '$key' has no namespace", key.contains('.'))
            assertFalse("key '$key' contains a space", key.contains(' '))
        }
    }

    private fun placeholders(text: String): List<String> {
        val found = Regex("\\{(\\d+)\\}").findAll(text).map { it.groupValues[1] }.toList()
        return found.sorted()
    }
}
