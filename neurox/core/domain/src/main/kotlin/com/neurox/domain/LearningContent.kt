package com.neurox.domain

import kotlin.random.Random

/** Educational practice content. This model must not be used to claim test norms. */
enum class ReadingSkill { EXPLICIT_INFORMATION, INFERENCE, MAIN_IDEA, EVIDENCE_EVALUATION, APPLICATION }
enum class EditorialStage { DRAFT, EDITORIALLY_REVIEWED }
data class AnswerChoice(val id: String, val text: String, val explanation: String)
data class LearningQuestion(
    val id: String,
    val skill: ReadingSkill,
    val prompt: String,
    val choices: List<AnswerChoice>,
    val correctChoiceId: String,
    val evidence: String,
) {
    fun validate(passage: String): List<String> = buildList {
        if (!id.matches(Regex("[a-z][a-z0-9-]{2,79}"))) add("invalid question id")
        if (prompt.trim().length !in 15..500) add("invalid question prompt")
        if (choices.size != 4) add("four choices required")
        if (choices.map { it.id }.distinct().size != choices.size) add("duplicate choice id")
        if (choices.map { it.text.trim() }.distinct().size != choices.size) add("duplicate choice text")
        if (choices.any { !it.id.matches(Regex("[a-z][a-z0-9-]{0,39}")) || it.text.trim().length !in 5..400 || it.explanation.trim().length !in 20..1200 }) add("incomplete choice or rationale")
        if (choices.count { it.id == correctChoiceId } != 1) add("exactly one keyed answer required")
        if (evidence.trim().length < 15 || !passage.contains(evidence)) add("evidence must quote the passage")
        if ((listOf(prompt, evidence) + choices.flatMap { listOf(it.text, it.explanation) }).any(::unsafeEditorialText)) add("unsafe editorial text")
    }
    /** Return the selected choice rationale; the UI reveals it only after submission. */
    fun feedback(choiceId: String): AnswerFeedback {
        val choice = requireNotNull(choices.find { it.id == choiceId }) { "Unknown choice" }
        return AnswerFeedback(choiceId == correctChoiceId, choice.explanation, evidence)
    }
}
data class AnswerFeedback(val correct: Boolean, val explanation: String, val evidence: String)
data class LearningText(
    val id: String,
    val revision: Int,
    val level: Int,
    val title: String,
    val topic: String,
    val text: String,
    val questions: List<LearningQuestion>,
    val editorialStage: EditorialStage = EditorialStage.DRAFT,
) {
    val contentKey: String get() = "$id@$revision"
    fun validate(): List<String> = buildList {
        if (!id.matches(Regex("fa-adult-[a-z0-9-]{3,60}"))) add("invalid text id")
        if (revision !in 1..10_000 || level !in 1..3) add("invalid content version or level")
        if (title.trim().length !in 5..120 || topic.trim().length !in 3..100 || unsafeEditorialText(title) || unsafeEditorialText(topic)) add("invalid title or topic")
        if (text.length !in 300..8000 || unsafeEditorialText(text)) add("invalid passage")
        if (questions.size !in 3..8) add("question coverage required")
        if (questions.map { it.id }.distinct().size != questions.size) add("duplicate question id")
        if (questions.map { it.skill }.distinct().size < 3) add("at least three reading skills required")
        questions.forEach { addAll(it.validate(text)) }
    }
}
private fun unsafeEditorialText(value: String): Boolean = value.any {
    (it.isISOControl() && it != '\n') || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' || it == '\uFFFD'
}
data class ReadingSelection(val texts: List<LearningText>, val requested: Int) {
    init {
        require(requested in 1..30 && texts.size <= requested)
        require(texts.map { it.id }.distinct().size == texts.size)
    }
    val shortfall: Int get() = requested - texts.size
}
object ReadingSelectionPolicy {
    /** No silent repetition to fill a requested session. Review mode is explicit and unscored. */
    fun select(bank: List<LearningText>, level: Int, count: Int, seed: Long, seenTextIds: Set<String> = emptySet(), editorialPreview: Boolean = false): ReadingSelection {
        require(level in 1..3 && count in 1..30)
        require(bank.all { it.validate().isEmpty() }) { "Invalid content bank" }
        require(bank.map { it.id }.distinct().size == bank.size) { "Pin one revision per text in a bank" }
        require(bank.flatMap { it.questions }.map { it.id }.let { it.distinct().size == it.size }) { "Question IDs must be globally unique" }
        val eligible = bank.filter { it.level == level && it.id !in seenTextIds &&
            (editorialPreview || it.editorialStage == EditorialStage.EDITORIALLY_REVIEWED) }
        return ReadingSelection(eligible.sortedBy { it.id }.shuffled(Random(seed)).take(count), count)
    }
}

/** Evidence requirements are missing by default. A local boolean is not proof of permission. */
enum class AssessmentRequirement { SOURCE_AND_VERSION, REUSE_PERMISSION, PERSIAN_VALIDATION, TARGET_POPULATION, ADMINISTRATION_PROTOCOL, QUALIFIED_REVIEW, OFFLINE_DISTRIBUTION_RIGHTS }
data class AssessmentReadiness(val missing: Set<AssessmentRequirement>, val decision: AssessmentDecision)
enum class AssessmentDecision { BLOCKED, ELIGIBLE_FOR_RELEASE_REVIEW }
object AssessmentReleasePolicy {
    fun evaluate(documented: Set<AssessmentRequirement>): AssessmentReadiness {
        val missing = AssessmentRequirement.entries.toSet() - documented
        return AssessmentReadiness(missing, if (missing.isEmpty()) AssessmentDecision.ELIGIBLE_FOR_RELEASE_REVIEW else AssessmentDecision.BLOCKED)
    }
    // Intentionally no "validated", "licensed" or runnable outcome. Human review of the
    // evidence and mode of administration remains necessary even when a checklist is full.
}
