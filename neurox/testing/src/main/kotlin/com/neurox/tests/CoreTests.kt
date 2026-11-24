package com.neurox.tests

import com.neurox.domain.*
import com.neurox.crypto.BackupCipher
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.math.abs
import kotlin.random.Random

private var passed = 0
private var failed = 0
internal fun case(name: String, body: () -> Unit) {
    try { body(); passed++; println("PASS $name") }
    catch (error: Throwable) { failed++; println("FAIL $name: ${error::class.simpleName}: ${error.message}"); error.printStackTrace() }
}
internal fun eq(expected: Any?, actual: Any?) { check(expected == actual) { "Expected $expected, got $actual" } }
internal fun near(expected: Double, actual: Double, epsilon: Double = 0.00001) { check(abs(expected - actual) < epsilon) { "Expected $expected, got $actual" } }
internal inline fun rejects(body: () -> Unit) { var thrown = false; try { body() } catch (_: Exception) { thrown = true }; check(thrown) { "Expected rejection" } }
internal class Clock(var time: Long = 0) : MonotonicClock { override fun nowMs() = time; fun advance(ms: Long) { time += ms } }
internal fun engine(exercise: Exercise, clock: Clock, rounds: Int = 1, difficulty: Int = 1, baseline: Boolean = false) =
    TrainingEngine(TrainingConfig(exercise, Language.EN, difficulty, rounds, baseline = baseline), clock, Random(42)).also { it.resume() }
private fun success(difficulty: Int = 2, accuracy: Double = 0.95, valid: Boolean = true) = Performance(difficulty, accuracy, 300.0, 30.0, valid)

fun main() {
    case("config accepts safe limits") { check(TrainingConfig(Exercise.FOCUS_LOCK).validate().isEmpty()) }
    case("config rejects zero rounds") { check(TrainingConfig(Exercise.FOCUS_LOCK, rounds = 0).validate().isNotEmpty()) }
    case("config rejects excessive rounds") { rejects { TrainingEngine(TrainingConfig(Exercise.FOCUS_LOCK, rounds = 31), Clock()) } }
    case("config rejects invalid difficulty") { check(TrainingConfig(Exercise.FOCUS_LOCK, difficulty = 6).validate().isNotEmpty()) }
    case("config rejects short response window") { check(TrainingConfig(Exercise.FOCUS_LOCK, responseLimitMs = 100).validate().isNotEmpty()) }
    case("engine initially paused") { eq(Phase.PAUSED, TrainingEngine(TrainingConfig(Exercise.FOCUS_LOCK), Clock()).view().phase) }
    case("vision correct target recorded") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c); c.advance(1200); e.tap(e.view().targetCell); eq(Outcome.CORRECT, e.view().attempts.single().outcome); eq(Phase.COMPLETE, e.view().phase) }
    case("vision wrong target recorded") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c); c.advance(300); e.tap((e.view().targetCell + 1) % 16); eq(Outcome.INCORRECT, e.view().attempts.single().outcome) }
    case("vision rejects out of range cell") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c); e.tap(16); eq(0, e.view().attempts.size) }
    case("vision expires missing target") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c); c.advance(5000); e.tick(); eq(Outcome.MISSED, e.view().attempts.single().outcome) }
    case("expired tap does not hit next round") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c, 2); c.advance(5000); e.tap(0); eq(1, e.view().attempts.size) }
    case("higher visual difficulty reduces response window") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c, difficulty = 5); c.advance(2600); e.tick(); eq(Outcome.MISSED, e.view().attempts.single().outcome) }
    case("completed session ignores further taps") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c); c.advance(1000); e.tap(e.view().targetCell); e.tap(0); eq(1, e.view().attempts.size) }
    case("reaction waits before GO") { val c = Clock(); val e = engine(Exercise.QUICK_TAP, c); eq(Phase.WAITING, e.view().phase) }
    case("reaction early tap has no response latency") { val c = Clock(); val e = engine(Exercise.QUICK_TAP, c); c.advance(10); e.tap(); val a = e.view().attempts.single(); eq(Outcome.PREMATURE, a.outcome); eq(null, a.responseMs) }
    case("reaction uses actual publication time") { val c = Clock(); val e = engine(Exercise.QUICK_TAP, c); c.advance(3000); e.tick(); c.advance(247); e.tap(); eq(247L, e.view().attempts.single().responseMs) }
    case("reaction no stimulus time produces missed response") { val c = Clock(); val e = engine(Exercise.QUICK_TAP, c); c.advance(3000); e.tick(); c.advance(5000); e.tick(); eq(Outcome.MISSED, e.view().attempts.single().outcome) }
    case("pause excludes elapsed time") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c); c.advance(1000); e.pause(); c.advance(9000); eq(1000L, e.view().elapsedMs) }
    case("resume restarts unfinished stimulus") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c); c.advance(1000); e.pause(); c.advance(9000); e.resume(); c.advance(200); e.tap(e.view().targetCell); eq(200L, e.view().attempts.single().responseMs); eq(1200L, e.checkpoint().elapsedMs) }
    case("paused input ignored") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c); e.pause(); e.tap(0); eq(0, e.view().attempts.size) }
    case("abort never completes") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c); e.abort(); c.advance(1000); e.tap(0); eq(Phase.ABORTED, e.view().phase); check(!e.checkpoint().completed) }
    case("restored session stays paused") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c, 2); c.advance(1000); e.tap(e.view().targetCell); val cp = e.checkpoint(); val restored = TrainingEngine(cp.config, Clock(99999), checkpoint = cp); eq(Phase.PAUSED, restored.view().phase); eq(1, restored.view().attempts.size); eq(cp.elapsedMs, restored.view().elapsedMs) }
    case("restored complete session cannot resume") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c); c.advance(1000); e.tap(e.view().targetCell); val cp = e.checkpoint(); val restored = TrainingEngine(cp.config, Clock(), checkpoint = cp); restored.resume(); eq(Phase.COMPLETE, restored.view().phase) }
    case("checkpoint rejects impossible completion flag") { rejects { Checkpoint(TrainingConfig(Exercise.FOCUS_LOCK), emptyList(), 1000, true) } }
    case("checkpoint rejects full attempts without completion") { rejects { Checkpoint(TrainingConfig(Exercise.FOCUS_LOCK, rounds = 1), listOf(Attempt(Outcome.CORRECT, 1000)), 1000, false) } }
    case("checkpoint rejects negative duration") { rejects { Checkpoint(TrainingConfig(Exercise.FOCUS_LOCK), emptyList(), -1, false) } }
    case("checkpoint rejects excessive duration") { rejects { Checkpoint(TrainingConfig(Exercise.FOCUS_LOCK), emptyList(), 86_400_001, false) } }
    case("restore config must match") { val cp = Checkpoint(TrainingConfig(Exercise.FOCUS_LOCK), emptyList(), 0, false); rejects { TrainingEngine(TrainingConfig(Exercise.QUICK_TAP), Clock(), checkpoint = cp) } }
    case("negative reaction latency rejected") { rejects { Attempt(Outcome.CORRECT, -1) } }
    case("invalid weak key rejected") { rejects { Attempt(Outcome.INCORRECT, 100, weakKeys = mapOf("word" to 1)) } }
    case("negative character count rejected") { rejects { Attempt(Outcome.CORRECT, 100, correctCharacters = -1) } }
    case("reading requires reading interval") { val c = Clock(); val e = engine(Exercise.COMPREHENSION, c); e.showQuestion(); eq(Phase.ACTIVE, e.view().phase) }
    case("reading correct comprehension") { val c = Clock(); val e = engine(Exercise.COMPREHENSION, c); c.advance(12000); e.showQuestion(); c.advance(2000); e.answer(e.view().passage.correctOption); val a = e.view().attempts.single(); eq(Outcome.CORRECT, a.outcome); eq(12000L, a.readingMs); check(a.wordsRead > 10) }
    case("reading question time not included in WPM") { val c = Clock(); val e = engine(Exercise.COMPREHENSION, c); val words = wordCount(e.view().passage.text); c.advance(60000); e.showQuestion(); c.advance(30000); e.answer(e.view().passage.correctOption); near(words.toDouble(), Scoring.summarize(e.checkpoint()).wpm!!) }
    case("reading wrong answer lowers comprehension") { val c = Clock(); val e = engine(Exercise.COMPREHENSION, c); c.advance(1000); e.showQuestion(); e.answer((e.view().passage.correctOption + 1) % 3); near(0.0, Scoring.summarize(e.checkpoint()).accuracy) }
    case("reading rejects invalid answer index") { val c = Clock(); val e = engine(Exercise.COMPREHENSION, c); c.advance(1000); e.showQuestion(); e.answer(3); eq(0, e.view().attempts.size) }
    case("reading cannot answer before question") { val c = Clock(); val e = engine(Exercise.COMPREHENSION, c); e.answer(0); eq(0, e.view().attempts.size) }
    case("typing English accuracy and WPM") { val m = Typing.measure("hello world", "hello world", 60000); eq(11, m.correct); eq(0, m.incorrect); near(2.2, m.wpm); near(1.0, m.accuracy) }
    case("typing Persian shaping does not reverse strings") { val m = Typing.measure("سلام دنیا", "سلام دنیا", 60000); eq(9, m.correct); eq(0, m.incorrect) }
    case("typing NFC canonical equivalence") { eq(0, Typing.measure("é", "e\u0301", 1000).incorrect) }
    case("typing supplementary code point counts once") { eq(3, Typing.measure("A😀B", "A😀B", 1000).correct) }
    case("typing substitutions identify expected weak key") { val m = Typing.measure("cat", "cut", 1000); eq(2, m.correct); eq(1, m.incorrect); eq(mapOf("a" to 1), m.weakKeys) }
    case("typing missing suffix counts as errors") { val m = Typing.measure("hello", "hel", 1000); eq(3, m.correct); eq(2, m.incorrect) }
    case("typing extra characters count as errors") { val m = Typing.measure("hello", "hello!", 1000); eq(5, m.correct); eq(1, m.incorrect) }
    case("typing zero duration never produces infinite WPM") { near(0.0, Typing.measure("a", "a", 0).wpm) }
    case("typing empty input does not get perfect accuracy") { near(0.0, Typing.measure("abc", "", 1000).accuracy) }
    case("typing oversized input rejected") { rejects { Typing.measure("a", "a".repeat(1001), 1000) } }
    case("typing blank target rejected") { rejects { Typing.measure(" ", " ", 1000) } }
    case("typing negative duration rejected") { rejects { Typing.measure("a", "a", -1) } }
    case("typing engine records correct character count") { val c = Clock(); val e = engine(Exercise.KEY_PRECISION, c); val target = e.view().typingTarget; c.advance(60000); e.submitTyping(target); eq(target.length, e.view().attempts.single().correctCharacters) }
    case("typing difficulty lengthens target") { val a = engine(Exercise.KEY_PRECISION, Clock()); val b = engine(Exercise.KEY_PRECISION, Clock(), difficulty = 3); check(b.view().typingTarget.length > a.view().typingTarget.length) }
    case("typing input never persisted in checkpoint") { val c = Clock(); val e = engine(Exercise.KEY_PRECISION, c); c.advance(1000); e.submitTyping("my private text"); check(!e.checkpoint().toString().contains("my private text")) }
    case("valid completion earns separate XP") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c); c.advance(1000); e.tap(e.view().targetCell); val s = Scoring.summarize(e.checkpoint()); check(s.valid); eq(30, s.xp) }
    case("incomplete session earns no XP") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c, 2); c.advance(1000); e.tap(e.view().targetCell); eq(0, Scoring.summarize(e.checkpoint()).xp) }
    case("baseline completion earns no XP") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c, baseline = true); c.advance(1000); e.tap(e.view().targetCell); eq(0, Scoring.summarize(e.checkpoint()).xp) }
    case("all premature session earns no XP") { val c = Clock(); val e = engine(Exercise.QUICK_TAP, c); c.advance(1000); e.tap(); eq(0, Scoring.summarize(e.checkpoint()).xp) }
    case("too short completion earns no XP") { val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c); c.advance(1); e.tap(e.view().targetCell); check(!Scoring.summarize(e.checkpoint()).valid) }
    case("missed responses excluded from mean latency") { val cp = Checkpoint(TrainingConfig(Exercise.FOCUS_LOCK, rounds = 2), listOf(Attempt(Outcome.CORRECT, 200), Attempt(Outcome.MISSED, null)), 6000, true); val s = Scoring.summarize(cp); near(200.0, s.meanResponseMs!!); near(0.5, s.accuracy) }
    case("latency deviation calculated") { val cp = Checkpoint(TrainingConfig(Exercise.FOCUS_LOCK, rounds = 2), listOf(Attempt(Outcome.CORRECT, 200), Attempt(Outcome.CORRECT, 400)), 1000, true); near(100.0, Scoring.summarize(cp).responseDeviationMs!!) }
    case("adaptive disabled respects selection") { eq(AdaptationReason.DISABLED, Adaptive.recommend(2, List(5) { success() }, false).reason) }
    case("one good response never raises difficulty") { eq(2, Adaptive.recommend(2, listOf(success())).difficulty) }
    case("five stable sessions raise one level") { eq(3, Adaptive.recommend(2, List(5) { success() }).difficulty) }
    case("three repeated error sessions lower one level") { val r = Adaptive.recommend(2, List(3) { success(accuracy = 0.5) }); eq(1, r.difficulty); eq(3, r.rounds) }
    case("isolated error does not lower difficulty") { eq(2, Adaptive.recommend(2, listOf(success(accuracy = 0.1), success(), success())).difficulty) }
    case("inconsistent fast sessions do not raise difficulty") { eq(2, Adaptive.recommend(2, List(5) { success().copy(deviationMs = 200.0) }).difficulty) }
    case("invalid sessions do not influence adaptation") { eq(AdaptationReason.INSUFFICIENT_HISTORY, Adaptive.recommend(2, List(5) { success(valid = false) }).reason) }
    case("baselines do not influence training adaptation") { eq(AdaptationReason.INSUFFICIENT_HISTORY, Adaptive.recommend(2, List(5) { success().copy(baseline = true) }).reason) }
    case("different difficulty history ignored") { eq(AdaptationReason.INSUFFICIENT_HISTORY, Adaptive.recommend(2, List(5) { success(difficulty = 1) }).reason) }
    case("adaptive invalid accuracy rejected from history") { eq(AdaptationReason.INSUFFICIENT_HISTORY, Adaptive.recommend(2, List(5) { success(accuracy = Double.NaN) }).reason) }
    case("adaptive level capped at five") { eq(5, Adaptive.recommend(5, List(5) { success(difficulty = 5) }).difficulty) }
    case("adaptive level floor is one") { eq(1, Adaptive.recommend(1, List(3) { success(difficulty = 1, accuracy = 0.1) }).difficulty) }
    case("plan detects overlapping rest periods") { val a = PlanSlot(setOf(DayOfWeek.MONDAY), LocalTime.of(10, 0), 20, 10); val b = a.copy(start = LocalTime.of(10, 25)); check(a.overlaps(b)) }
    case("plan permits adjacent sessions") { val a = PlanSlot(setOf(DayOfWeek.MONDAY), LocalTime.of(10, 0), 20, 10); check(!a.overlaps(a.copy(start = LocalTime.of(10, 30)))) }
    case("plan permits different weekdays") { val a = PlanSlot(setOf(DayOfWeek.MONDAY), LocalTime.NOON, 20, 10); check(!a.overlaps(a.copy(days = setOf(DayOfWeek.TUESDAY)))) }
    case("plan rejects cross-midnight schedule") { check(!PlanSlot(setOf(DayOfWeek.MONDAY), LocalTime.of(23, 50), 20, 10).validate()) }
    case("plan rejects empty weekdays") { check(!PlanSlot(emptySet(), LocalTime.NOON, 20, 10).validate()) }
    case("progression is deterministic") { eq(1L, Progression.level(0)); eq(2L, Progression.level(250)); near(0.5, Progression.progressInLevel(125).toDouble()) }
    case("progression rejects negative XP") { rejects { Progression.level(-1) } }
    case("hex parser accepts six digits") { eq(0xFFD4B982, ColorMath.parseHex("#D4b982")) }
    case("hex parser rejects malformed colors") { eq(null, ColorMath.parseHex("GG0000")); eq(null, ColorMath.parseHex("#fff")); eq(null, ColorMath.parseHex("FF000000")) }
    case("contrast black and white is 21") { near(21.0, ColorMath.contrast(0xFF000000, 0xFFFFFFFF)) }
    case("dark accent corrected to readable contrast") { check(ColorMath.contrast(ColorMath.accessibleAccent(0xFF171717, 0xFF181B20, true), 0xFF181B20) >= 4.5) }
    case("light accent corrected to readable contrast") { check(ColorMath.contrast(ColorMath.accessibleAccent(0xFFFFFFEE, 0xFFEDE6DA, false), 0xFFEDE6DA) >= 4.5) }
    case("valid accent not changed") { eq(0xFFD4B982, ColorMath.accessibleAccent(0xFFD4B982, 0xFF181B20, true)) }
    case("all catalogue exercises have instructions and both languages") { eq(Exercise.entries.toSet(), Catalogue.runnable.map { it.exercise }.toSet()); eq(14, Catalogue.runnable.size); Catalogue.runnable.forEach { check(it.fa.isNotBlank() && it.en.isNotBlank() && it.instructionFa.isNotBlank() && it.instructionEn.isNotBlank()) } }
    case("reading material has valid answer schemas") { Language.entries.flatMap { Content.passages(it) }.forEach { check(it.options.size == 3 && it.correctOption in it.options.indices && wordCount(it.text) > 10) } }
    case("word count handles Unicode whitespace") { eq(3, wordCount("  one\ttwo\nthree ")); eq(0, wordCount("   ")) }
    val password = "a long offline password".toCharArray()
    val plaintext = "{\"version\":1,\"profile\":\"local\"}".toByteArray()
    var encrypted = ByteArray(0)
    case("vault round trip authenticates plaintext") { encrypted = BackupCipher.encrypt(plaintext, password); check(plaintext.contentEquals(BackupCipher.decrypt(encrypted, password))) }
    case("vault uses fresh salt and nonce") { check(!encrypted.contentEquals(BackupCipher.encrypt(plaintext, password))) }
    case("vault ciphertext does not contain plaintext") { check(!String(encrypted, Charsets.ISO_8859_1).contains("profile")) }
    case("vault wrong password rejected") { rejects { BackupCipher.decrypt(encrypted, "another long password".toCharArray()) } }
    case("vault corrupted ciphertext rejected") { val bad = encrypted.copyOf(); bad[bad.lastIndex] = (bad.last().toInt() xor 1).toByte(); rejects { BackupCipher.decrypt(bad, password) } }
    case("vault authenticated header tampering rejected") { val bad = encrypted.copyOf(); bad[12] = (bad[12].toInt() xor 1).toByte(); rejects { BackupCipher.decrypt(bad, password) } }
    case("vault truncated file rejected") { rejects { BackupCipher.decrypt(encrypted.copyOf(12), password) } }
    case("vault unsupported version rejected") { val bad = encrypted.copyOf(); bad[4] = 99; rejects { BackupCipher.decrypt(bad, password) } }
    case("vault excessive KDF cost rejected before derivation") { val bad = encrypted.copyOf(); for (i in 5..8) bad[i] = 127; rejects { BackupCipher.decrypt(bad, password) } }
    case("vault invalid magic rejected") { val bad = encrypted.copyOf(); bad[0] = 0; rejects { BackupCipher.decrypt(bad, password) } }
    case("vault empty plaintext rejected") { rejects { BackupCipher.encrypt(ByteArray(0), password) } }
    case("vault oversized plaintext rejected") { rejects { BackupCipher.encrypt(ByteArray(BackupCipher.MAX_PLAINTEXT + 1), password) } }
    case("vault weak password rejected") { rejects { BackupCipher.encrypt(plaintext, "short".toCharArray()) } }
    case("vault does not mutate caller password") { eq("a long offline password", String(password)) }
    exerciseExpansionTests()
    builderAndMatchingTests()
    colorAndDirectionTests()
    println("\nRESULT: $passed passed, $failed failed")
    check(failed == 0) { "$failed test cases failed" }
}
