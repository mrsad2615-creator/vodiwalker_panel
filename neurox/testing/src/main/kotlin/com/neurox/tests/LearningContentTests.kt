package com.neurox.tests

import com.neurox.domain.*
import java.io.File

internal fun learningContentTests() {
    val bank = AdultReadingBank.texts
    case("adult reading bank has six versioned original drafts across three levels") {
        eq(6, bank.size); eq(18, bank.sumOf { it.questions.size })
        eq(setOf(1, 2, 3), bank.map { it.level }.toSet())
        check(bank.all { it.editorialStage == EditorialStage.DRAFT && it.validate().isEmpty() })
        eq(6, bank.map { it.contentKey }.distinct().size)
    }
    case("adult questions cover five distinct reading skills with four explained choices") {
        eq(ReadingSkill.entries.toSet(), bank.flatMap { it.questions }.map { it.skill }.toSet())
        check(bank.flatMap { it.questions }.all { it.choices.size == 4 && it.choices.all { c -> c.explanation.isNotBlank() } })
    }
    case("draft content is blocked from the ordinary practice selection") {
        for (level in 1..3) {
            val selection = ReadingSelectionPolicy.select(bank, level, 5, 7)
            eq(emptyList<LearningText>(), selection.texts); eq(5, selection.shortfall)
        }
    }
    case("editorial preview must be explicit and never fabricates enough texts") {
        val selection = ReadingSelectionPolicy.select(bank, 1, 5, 7, editorialPreview = true)
        eq(2, selection.texts.size); eq(3, selection.shortfall)
    }
    case("selection is stable across source list order with the same seed") {
        for (seed in listOf(0L, Long.MIN_VALUE, Long.MAX_VALUE, 71L)) {
            eq(ReadingSelectionPolicy.select(bank, 2, 2, seed, editorialPreview = true),
                ReadingSelectionPolicy.select(bank.reversed(), 2, 2, seed, editorialPreview = true))
        }
    }
    case("seen text is excluded across question variants and content revisions") {
        val item = bank.first()
        val selection = ReadingSelectionPolicy.select(bank.map { if (it.id == item.id) it.copy(revision = 2) else it }, 1, 5, 9, setOf(item.id), editorialPreview = true)
        eq(1, selection.texts.size); check(selection.texts.none { it.id == item.id })
    }
    case("selection rejects mixed revisions rather than overweighting a text") {
        rejects { ReadingSelectionPolicy.select(bank + bank.first().copy(revision = 2), 1, 3, 7, editorialPreview = true) }
    }
    case("selection rejects duplicate global question ids") {
        val item = bank[1].copy(questions = bank[1].questions.mapIndexed { i, q -> if (i == 0) q.copy(id = bank[0].questions[0].id) else q })
        rejects { ReadingSelectionPolicy.select(listOf(bank[0], item), 1, 1, 0, editorialPreview = true) }
    }
    case("question validation rejects duplicate labels and unknown answer keys") {
        val item = bank.first(); val q = item.questions.first()
        check(q.copy(choices = q.choices.toMutableList().also { it[1] = it[0] }).validate(item.text).isNotEmpty())
        check(q.copy(correctChoiceId = "absent").validate(item.text).isNotEmpty())
    }
    case("question evidence must actually appear in the original text") {
        val item = bank.first()
        check(item.questions.first().copy(evidence = "این عبارت در متن اصلی وجود ندارد.").validate(item.text).isNotEmpty())
    }
    case("editorial validation rejects control and bidi override characters but retains ZWNJ") {
        val item = bank.first()
        check(item.copy(text = item.text + "\u202E").validate().isNotEmpty())
        check(item.copy(text = item.text + "\u0000").validate().isNotEmpty())
        check(item.copy(title = item.title + "\u202E").validate().isNotEmpty())
        check(item.copy(topic = item.topic + "\u0000").validate().isNotEmpty())
        check(item.copy(text = item.text + "\nمی‌توان").validate().isEmpty())
    }
    case("every choice gives specific feedback and only one is keyed correct") {
        for (q in bank.flatMap { it.questions }) {
            eq(1, q.choices.count { q.feedback(it.id).correct })
            check(q.choices.all { q.feedback(it.id).explanation == it.explanation })
            rejects { q.feedback("unknown") }
        }
    }
    case("reading selection enforces requested range and level bounds") {
        for (level in listOf(0, 4)) rejects { ReadingSelectionPolicy.select(bank, level, 1, 0) }
        for (count in listOf(0, 31)) rejects { ReadingSelectionPolicy.select(bank, 1, count, 0) }
    }
    case("editorial review does not transform practice content into a validated assessment") {
        val reviewed = bank.map { it.copy(editorialStage = EditorialStage.EDITORIALLY_REVIEWED) }
        eq(2, ReadingSelectionPolicy.select(reviewed, 1, 2, 8).texts.size)
        eq(AssessmentDecision.BLOCKED, AssessmentReleasePolicy.evaluate(emptySet()).decision)
    }
    case("each missing assessment evidence requirement blocks eligibility") {
        val all = AssessmentRequirement.entries.toSet()
        for (missing in all) {
            val result = AssessmentReleasePolicy.evaluate(all - missing)
            eq(setOf(missing), result.missing); eq(AssessmentDecision.BLOCKED, result.decision)
        }
    }
    case("reading selection cannot claim a negative shortage or duplicate texts") {
        rejects { ReadingSelection(bank.take(2), 1) }
        rejects { ReadingSelection(listOf(bank.first(), bank.first()), 2) }
        rejects { ReadingSelection(emptyList(), 0) }
    }
    case("a complete assessment checklist still requires release review") {
        val result = AssessmentReleasePolicy.evaluate(AssessmentRequirement.entries.toSet())
        eq(emptySet<AssessmentRequirement>(), result.missing)
        eq(AssessmentDecision.ELIGIBLE_FOR_RELEASE_REVIEW, result.decision)
    }
    // Review artifacts are optional, generated from production models, and never user data.
    System.getenv("NEUROX_CONTENT_REVIEW_OUT")?.let { path ->
        require(bank.all { it.validate().isEmpty() }) { "Do not export an invalid bank" }
        File(path).also { it.parentFile?.mkdirs() }.writeText(learningContentJson())
    }
}

private fun json(value: String): String = buildString {
    append('"')
    for (ch in value) when (ch) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (ch.code < 32) append("\\u%04x".format(ch.code)) else append(ch)
    }
    append('"')
}
private fun learningContentJson(): String = """{"bankRevision":${AdultReadingBank.revision},"status":"editorial-draft-not-a-validated-test","texts":[""" +
    AdultReadingBank.texts.joinToString(",") { t ->
        """{"id":${json(t.id)},"revision":${t.revision},"level":${t.level},"title":${json(t.title)},"topic":${json(t.topic)},"text":${json(t.text)},"questions":[""" +
            t.questions.joinToString(",") { q ->
                """{"id":${json(q.id)},"skill":${json(q.skill.name)},"prompt":${json(q.prompt)},"correctChoiceId":${json(q.correctChoiceId)},"evidence":${json(q.evidence)},"choices":[""" +
                    q.choices.joinToString(",") { c -> """{"id":${json(c.id)},"text":${json(c.text)},"explanation":${json(c.explanation)}}""" } + "]}"
            } + "]}"
    } + "]}\n"
