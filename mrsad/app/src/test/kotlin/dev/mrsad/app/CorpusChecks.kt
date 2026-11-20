package dev.mrsad.app

import dev.mrsad.app.feature.read.ReadTexts
import dev.mrsad.app.feature.type.TypeTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integrity checks for the local training corpora.
 *
 * Both corpora are shipped inside the APK, so a broken entry is a defect the
 * user meets immediately: a reading text whose correct answer is out of range
 * silently scores a right answer as wrong, and a duplicate id makes the
 * setup screen select the wrong passage. These checks fail the build instead.
 */
class CorpusChecks {

    @Test
    fun readingCorpusIsWellFormed() {
        assertTrue("the reading corpus must not be empty", ReadTexts.all.isNotEmpty())

        val ids = ReadTexts.all.map { it.id }
        assertEquals("reading text ids must be unique", ids.size, ids.toSet().size)
        ids.forEach { assertTrue("reading text id must not be blank", it.isNotBlank()) }

        ReadTexts.all.forEach { text ->
            assertTrue(
                "reading text '${text.id}' must have a level in 1..3",
                text.level in 1..3,
            )
            assertTrue("reading text '${text.id}' must have a body", text.body.isNotBlank())
            assertTrue("reading text '${text.id}' must have a title", text.title.isNotBlank())
            assertTrue("reading text '${text.id}' must have a category", text.category.isNotBlank())
            assertTrue(
                "reading text '${text.id}' must be long enough to read",
                text.wordCount >= 40,
            )

            assertEquals(
                "reading text '${text.id}' must ask exactly four questions",
                4,
                text.questions.size,
            )
            val prompts = text.questions.map { it.prompt }
            assertEquals(
                "reading text '${text.id}' must not repeat a question",
                prompts.size,
                prompts.toSet().size,
            )
            text.questions.forEach { question ->
                assertTrue(
                    "reading text '${text.id}' must have three options",
                    question.options.size == 3,
                )
                assertTrue(
                    "reading text '${text.id}' has an out-of-range correct answer",
                    question.correctIndex in question.options.indices,
                )
                assertTrue(
                    "reading text '${text.id}' must not repeat an option",
                    question.options.size == question.options.toSet().size,
                )
                question.options.forEach { option ->
                    assertTrue("an option must not be blank", option.isNotBlank())
                }
            }
        }
    }

    @Test
    fun readingCorpusCoversEveryCategoryAtEveryTier() {
        // A progression only means something if each subject has a passage at
        // each difficulty tier.
        ReadTexts.categories.forEach { category ->
            (1..3).forEach { level ->
                val matches = ReadTexts.all.filter { it.category == category && it.level == level }
                assertTrue(
                    "category '$category' has no level $level text",
                    matches.isNotEmpty(),
                )
            }
        }
        assertEquals("the corpus must cover exactly three subjects", 3, ReadTexts.categories.size)
    }

    @Test
    fun readingLookupFallsBackSafely() {
        assertEquals("unknown ids must fall back to the first text", ReadTexts.all.first().id, ReadTexts.byId("no-such-id").id)
        assertEquals("every tier must be reachable", 3, (1..3).count { ReadTexts.byLevel(it).isNotEmpty() })
    }

    @Test
    fun typingCorpusIsWellFormed() {
        listOf(true, false).forEach { persian ->
            val texts = TypeTexts.forLanguage(persian)
            assertTrue("the corpus must not be empty", texts.isNotEmpty())
            val ids = texts.map { it.id }
            assertEquals("typing text ids must be unique", ids.size, ids.toSet().size)
            texts.forEach { text ->
                assertTrue("typing text '${text.id}' must have a level in 1..3", text.level in 1..3)
                assertTrue("typing text '${text.id}' must have a body", text.body.isNotBlank())
                assertTrue("typing text '${text.id}' must have a title", text.title.isNotBlank())
            }
            (1..3).forEach { level ->
                val matches = texts.filter { it.level == level }
                assertTrue(
                    "each tier needs at least two passages (persian=$persian, level=$level)",
                    matches.size >= 2,
                )
            }
        }
    }

    @Test
    fun typingDailyPickIsStableAndHonoursTheTier() {
        repeat(20) { index ->
            val pick = TypeTexts.dailyPick(usePersian = true, dateIso = "2026-11-$index", level = 2)
            assertEquals("the daily pick must honour the requested tier", 2, pick.level)
            val again = TypeTexts.dailyPick(usePersian = true, dateIso = "2026-11-$index", level = 2)
            assertEquals("the daily pick must be stable", pick.id, again.id)
        }
        // An impossible tier must still return a usable text rather than crash.
        val fallback = TypeTexts.dailyPick(usePersian = false, dateIso = "2026-11-19", level = 99)
        assertTrue("the fallback pick must exist", fallback.id.isNotBlank())
    }
}
