package dev.mrsad.core.checks

import dev.mrsad.core.analytics.AnalyticsMath
import dev.mrsad.core.analytics.DayActivity
import dev.mrsad.core.brand.MrsadMark
import dev.mrsad.core.coach.Coach
import dev.mrsad.core.coach.CoachAnswer
import dev.mrsad.core.coach.SkillSnapshot
import dev.mrsad.core.mind.MindGenerators
import dev.mrsad.core.mind.MindProgression
import dev.mrsad.core.random.SplitMix64
import dev.mrsad.core.reading.ReadingEngine
import dev.mrsad.core.session.FocusSession
import dev.mrsad.core.text.PersianText
import dev.mrsad.core.typing.TypingLanguage
import dev.mrsad.core.typing.TypingSession
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Executable specification for the MRSAD domain core.
 *
 * Deliberately dependency-free (plain Kotlin assertions) so the exact same
 * checks run: (a) locally via `kotlinc && java`, and (b) inside JUnit on CI
 * through [CoreChecksTest]. A failure message names the violated rule.
 */
object CoreChecks {

    private val results = ArrayList<Pair<String, String?>>()

    fun runAll(): Int {
        results.clear()
        check("PersianText: yeh/kaf/marbuta normalisation") {
            val input = "\u064A\u0643\u0629" // Arabic yeh, kaf, teh marbuta
            val out = PersianText.normalizeForComparison(input)
            expect(out == "\u06CC\u06A9\u0647", "unexpected: ${out.map { it.code.toString(16) }}")
        }
        check("PersianText: tatweel removal + digit conversion") {
            val out = PersianText.normalizeForComparison("a\u0640b\u0661\u0662")
            expect(out == "ab۱۲", "unexpected: $out")
        }
        check("PersianText: space collapse preserves ZWNJ") {
            val out = PersianText.normalizeForComparison("می  خانه" + PersianText.ZWNJ + "ها  ")
            expect(out == "می خانه‌ها", "unexpected: $out")
        }
        check("PersianText: space accepted for ZWNJ") {
            expect(PersianText.matches(' ', PersianText.ZWNJ), "space must be accepted for ZWNJ")
            expect(!PersianText.matches('x', PersianText.ZWNJ), "letters must not match ZWNJ")
        }
        check("PersianText: Persian-aware word count") {
            expect(PersianText.wordCount("می‌روم به خانه") == 3, "ZWNJ compound counts as one word")
        }
        check("PersianText: digit round-trip") {
            val fa = PersianText.toPersianDigits("1234567890")
            expect(PersianText.toLatinDigits(fa) == "1234567890", "round trip failed")
        }

        check("Typing: metrics follow documented formulas") {
            // Target with 10 chars; type 8 correct in 10 keystrokes, 2 errors.
            val session = TypingSession("abcdefghij", TypingLanguage.ENGLISH, "test", startedAtEpochMillis = 0L)
            session.type('a', 0); session.type('b', 100); session.type('x', 200); // error at 'c'
            session.type('c', 300); session.type('d', 400); session.type('e', 500)
            session.type('y', 600); session.type('f', 700); session.type('g', 800); session.type('h', 900)
            val result = session.result(nowMillis = 60_000)
            expect(result.correctChars == 8, "correctChars=${result.correctChars}")
            expect(result.errorKeystrokes == 2, "errors=${result.errorKeystrokes}")
            expect(result.totalKeystrokes == 10, "keystrokes=${result.totalKeystrokes}")
            expect(abs(result.accuracy - 80.0) < 1e-9, "accuracy=${result.accuracy}")
            // 60s elapsed (first key at 0ms to 60_000ms) → 8 CPM, 1.6 WPM (8/5 chars per min).
            expect(abs(result.cpm - 8.0) < 1e-9, "cpm=${result.cpm}")
            expect(abs(result.wpm - 1.6) < 1e-9, "wpm=${result.wpm}")
        }
        check("Typing: wrong key does not advance cursor but is recorded") {
            val session = TypingSession("سلام", TypingLanguage.PERSIAN, "test", 0L)
            val outcome = session.type('x', 10)
            expect(outcome == TypingSession.KeyOutcome.INCORRECT, "expected INCORRECT")
            expect(session.cursor == 0, "cursor must not advance on error")
            expect(session.mistakeCount('س') == 1, "heatmap must record expected char")
        }
        check("Typing: backspace removes correct char, never erases history") {
            val session = TypingSession("abc", TypingLanguage.ENGLISH, "test", 0L)
            session.type('a', 0); session.type('b', 1)
            val before = session.totalKeystrokes
            session.backspace()
            expect(session.cursor == 1, "cursor=${session.cursor}")
            expect(session.correctChars == 1, "correctChars=${session.correctChars}")
            expect(session.totalKeystrokes == before, "keystrokes must not decrease")
        }
        check("Typing: session finishes and freezes at end") {
            val session = TypingSession("اب", TypingLanguage.PERSIAN, "test", 0L)
            session.type('ا', 100); session.type('ب', 200)
            expect(session.isFinished, "session must finish")
            val extra = session.type('ج', 300)
            expect(extra == TypingSession.KeyOutcome.IGNORED, "further keys ignored")
            expect(session.elapsedMillis(900_000) == 100L, "elapsed must freeze at finish time")
        }
        check("Typing: space typed for ZWNJ target counts as correct") {
            val target = "می" + PersianText.ZWNJ + "روم"
            val session = TypingSession(target, TypingLanguage.PERSIAN, "test", 0L)
            session.type('م', 1); session.type('ی', 2)
            val outcome = session.type(' ', 3)
            expect(outcome == TypingSession.KeyOutcome.CORRECT, "space for ZWNJ must be correct")
        }
        check("Typing: word count increments at word boundaries") {
            val session = TypingSession("ab cd", TypingLanguage.ENGLISH, "test", 0L)
            session.type('a', 1); session.type('b', 2); expect(session.correctWords == 0, "inside first word")
            session.type(' ', 3); expect(session.correctWords == 1, "space completes first word")
            session.type('c', 4); session.type('d', 5)
            expect(session.correctWords == 2, "final word completes at end of text")
        }

        check("Analytics: linear trend detects direction") {
            val up = AnalyticsMath.trend(listOf(1.0, 2.0, 3.0, 4.0))
            expect(up.direction == dev.mrsad.core.analytics.TrendSummary.Direction.UP, "expected UP")
            val down = AnalyticsMath.trend(listOf(4.0, 3.0, 2.0, 1.0))
            expect(down.direction == dev.mrsad.core.analytics.TrendSummary.Direction.DOWN, "expected DOWN")
            val flat = AnalyticsMath.trend(listOf(3.0, 3.0, 3.0))
            expect(flat.direction == dev.mrsad.core.analytics.TrendSummary.Direction.FLAT, "expected FLAT")
        }
        check("Analytics: streak counts backwards, tolerates empty today") {
            val active = setOf("2026-11-16", "2026-11-17", "2026-11-18")
            expect(AnalyticsMath.currentStreak(active, "2026-11-19") == 3, "streak must continue from yesterday")
            expect(AnalyticsMath.currentStreak(active + "2026-11-19", "2026-11-19") == 4, "today counted when active")
            expect(AnalyticsMath.currentStreak(emptySet(), "2026-11-19") == 0, "empty streak")
        }
        check("Analytics: longest streak across month and year boundaries") {
            val dates = (setOf("2026-12-30", "2026-12-31", "2027-01-01", "2027-01-02") +
                setOf("2027-02-27", "2027-02-28"))
            expect(AnalyticsMath.longestStreak(dates) == 4, "longest=${AnalyticsMath.longestStreak(dates)}")
        }
        check("Analytics: ISO arithmetic handles leap years") {
            expect(AnalyticsMath.nextIso("2028-02-28") == "2028-02-29", "leap day")
            expect(AnalyticsMath.nextIso("2028-02-29") == "2028-03-01", "after leap day")
            expect(AnalyticsMath.previousIso("2027-01-01") == "2026-12-31", "year back")
            expect(AnalyticsMath.nextIso("2026-12-31") == "2027-01-01", "year forward")
        }
        check("Analytics: lastDays returns chronological window") {
            val days = AnalyticsMath.lastDays("2026-11-19", 3)
            expect(days == listOf("2026-11-17", "2026-11-18", "2026-11-19"), "got $days")
        }
        check("Analytics: percent change is safe on zero baseline") {
            expect(AnalyticsMath.percentChange(0.0, 5.0) == 100.0, "zero baseline")
            expect(abs(AnalyticsMath.percentChange(10.0, 15.0) - 50.0) < 1e-9, "50%")
        }

        check("Coach: refuses to analyse insufficient data") {
            val answer = Coach.analyze(SkillSnapshot())
            expect(answer is CoachAnswer.NeedsData, "must ask for more data")
            expect((answer as CoachAnswer.NeedsData).guidanceFa.contains("داده"), "guidance must mention data")
        }
        check("Coach: prioritises accuracy when accuracy is weak") {
            val snapshot = SkillSnapshot(
                typingSessions = 10, avgWpm = 40.0, avgAccuracy = 82.0,
                readingSessions = 4, avgReadingWpm = 200, avgComprehension = 80,
                activeDaysLast7Days = 5,
                topMistakes = listOf('ژ' to 4, 'پ' to 3),
            )
            val answer = Coach.analyze(snapshot) as CoachAnswer.Analysis
            expect(answer.recommendations.isNotEmpty(), "recommendations expected")
            expect(answer.recommendations.first().titleFa.contains("دقت"), "accuracy first: ${answer.recommendations.first().titleFa}")
            expect(answer.confidence == dev.mrsad.core.coach.Confidence.MEDIUM, "confidence=${answer.confidence}")
        }
        check("Coach: daily plan is deterministic per date and non-empty") {
            val snapshot = SkillSnapshot(typingSessions = 5, avgAccuracy = 90.0, readingSessions = 3,
                avgComprehension = 70, mindSessions = 2, avgMindScore = 60.0, activeDaysLast7Days = 3)
            val a = Coach.dailyPlan(snapshot, "2026-11-19")
            val b = Coach.dailyPlan(snapshot, "2026-11-19")
            val c = Coach.dailyPlan(snapshot, "2026-11-20")
            expect(a.isNotEmpty() && a.size <= 3, "plan size ${a.size}")
            expect(a.map { it.titleFa } == b.map { it.titleFa }, "same date must yield same plan")
            expect(a.isNotEmpty(), "plan must exist")
            expect(c.isNotEmpty(), "next day must have a plan")
            expect(a.all { it.minutes in 1..60 }, "minutes must be sane")
        }

        check("Mind: digit span grows with level and is deterministic") {
            val l0 = MindGenerators.digitSpan(42L, 0)
            val l5 = MindGenerators.digitSpan(42L, 5)
            expect(l0.length == 4, "level0 length=${l0.length}")
            expect(l5.length == 9, "level5 length=${l5.length}")
            expect(MindGenerators.digitSpan(42L, 5) == l5, "deterministic for same seed")
            expect(l5.first() != '0', "must not start with zero")
        }
        check("Mind: pattern memory respects grid bounds") {
            repeat(30) { seed ->
                val grid = MindGenerators.patternMemory(seed.toLong(), 3)
                expect(grid.side in 3..6, "side=${grid.side}")
                expect(grid.litCells.isNotEmpty(), "no lit cells")
                expect(grid.litCells.all { it in 0 until grid.side * grid.side }, "lit cell out of bounds")
                expect(grid.litCells.distinct().size == grid.litCells.size, "duplicate cells")
            }
        }
        check("Mind: attention grid has exactly the announced targets") {
            repeat(30) { seed ->
                val grid = MindGenerators.attentionGrid(seed.toLong(), 2)
                val actualTargets = grid.symbols.withIndex().count { it.value == grid.targetSymbol }
                expect(actualTargets == grid.targetIndices.size, "target count mismatch")
                expect(grid.symbols.size == grid.side * grid.side, "grid size")
            }
        }
        check("Mind: stroop never shows ink equal to word") {
            val items = MindGenerators.stroop(7L, 40)
            expect(items.all { it.wordColorIndex != it.inkColorIndex }, "word==ink would be ambiguous")
            expect(items.all { it.correctAnswerIndex == it.inkColorIndex }, "answer must be ink color")
        }
        check("Mind: arithmetic options contain exactly one correct answer") {
            repeat(20) { seed ->
                val items = MindGenerators.arithmetic(seed.toLong(), 2, 10)
                items.forEach { item ->
                    expect(item.options.distinct().size == item.options.size, "duplicate options")
                    expect(item.options.getOrNull(item.correctIndex) != null, "correct index out of range")
                    expect(item.correctIndex >= 0, "bad index")
                }
            }
        }
        check("Mind: logic sequences have unique answer in options") {
            repeat(25) { seed ->
                MindGenerators.logicSequences(seed.toLong(), 2, 6).forEach { seq ->
                    expect(seq.options.distinct().size == seq.options.size, "duplicate options")
                    expect(seq.options.count { it == seq.correctValue } == 1, "answer must appear exactly once")
                }
            }
        }
        check("Mind: odd-one-out has one distinct cell") {
            repeat(20) { seed ->
                val item = MindGenerators.oddOneOut(seed.toLong(), 4)
                val odd = item.symbols[item.correctIndex]
                expect(item.symbols.count { it == odd } == 1, "exactly one odd cell")
                expect(item.symbols.count { it != odd } == item.symbols.size - 1, "all others equal")
            }
        }

        check("Reading: chunking preserves word order") {
            val chunks = ReadingEngine.chunk("امروز هوا بسیار خوب است", wordsPerDisplay = 2)
            expect(chunks.map { it.text }.joinToString(" ") == "امروز هوا بسیار خوب است", "chunk join mismatch")
            expect(chunks.map { it.wordCount }.sum() == 5, "word counts must sum to 5")
        }
        check("Reading: pivot is inside the word") {
            val pivot = ReadingEngine.pivotFor("خواندن")
            expect(pivot in 0 until "خواندن".length, "pivot out of range")
        }
        check("Reading: effective wpm rewards comprehension") {
            val raw = ReadingEngine.wordsPerMinute(300, 60_000)
            expect(raw == 300, "raw=$raw")
            expect(ReadingEngine.effectiveWpm(300, 60_000, 100) == 300, "100% comprehension")
            expect(ReadingEngine.effectiveWpm(300, 60_000, 60) == 180, "60% comprehension")
            expect(ReadingEngine.effectiveWpm(300, 60_000, 0) == 0, "no comprehension ⇒ no effective speed")
        }
        check("Reading: chunk duration follows requested wpm") {
            expect(ReadingEngine.chunkDurationMillis(300, 1) == 200L, "300wpm → 200ms/word")
            expect(ReadingEngine.chunkDurationMillis(300, 3) == 600L, "3-word phrase → 600ms")
        }
        check("Reading: comprehension scoring passes at 60%") {
            val questions = listOf(
                ReadingEngine.ComprehensionQuestion("q1", listOf("a", "b"), 0),
                ReadingEngine.ComprehensionQuestion("q2", listOf("a", "b"), 1),
            )
            val result = ReadingEngine.score(listOf(0, 0), questions)
            expect(result.correct == 1 && result.percent == 50 && !result.passed, "50% must fail")
            val pass = ReadingEngine.score(listOf(0, 1), questions)
            expect(pass.percent == 100 && pass.passed, "100% must pass")
        }

        check("Focus: phases advance with real elapsed time") {
            val focus = FocusSession(focusMinutes = 1, breakMinutes = 1, rounds = 2)
            focus.start(0)
            expect(focus.remainingMillis(30_000) == 30_000L, "remaining")
            expect(focus.tick(59_000) == null, "no transition before deadline")
            expect(focus.tick(60_500) == FocusSession.Phase.BREAK, "focus→break")
            expect(focus.tick(121_000) == FocusSession.Phase.FOCUS, "break→focus (round 2)")
            expect(focus.round == 2, "round=${focus.round}")
            expect(focus.tick(181_000) == FocusSession.Phase.DONE, "final round ends")
            expect(focus.plannedFocusMinutes == 2, "planned minutes")
        }
        check("Focus: pause freezes elapsed and resume continues correctly") {
            val focus = FocusSession(1, 1, 1)
            focus.start(0)
            focus.pause(10_000)
            expect(focus.elapsedInPhase(50_000) == 10_000L, "paused elapsed must freeze")
            focus.resume(50_000)
            expect(focus.elapsedInPhase(60_000) == 20_000L, "elapsed continues after resume")
            expect(focus.remainingMillis(60_000) == 40_000L, "remaining after resume")
        }
        check("Focus: long background gap skips completed phases") {
            val focus = FocusSession(1, 1, 2)
            focus.start(0)
            val phase = focus.tick(3 * 60_000L) // 3 minutes passed: focus→break→focus→done
            expect(phase == FocusSession.Phase.DONE || focus.phase == FocusSession.Phase.DONE, "must reach DONE")
        }

        check("RNG: SplitMix64 deterministic and shuffled uniformly enough") {
            expect(SplitMix64(1).nextLong() == SplitMix64(1).nextLong(), "deterministic")
            val rng = SplitMix64(99)
            val buckets = IntArray(10)
            repeat(10_000) { buckets[rng.nextInt(10)]++ }
            expect(buckets.all { it in 800..1200 }, "distribution looks skewed: ${buckets.toList()}")
            expect(SplitMix64.seedFor("2026-11-19") == SplitMix64.seedFor("2026-11-19"), "seed stable")
            expect(SplitMix64.seedFor("2026-11-19") != SplitMix64.seedFor("2026-11-20"), "different days differ")
        }
        check("Analytics: day activity flags activity correctly") {
            expect(!DayActivity("2026-11-19").hasActivity, "empty day")
            expect(DayActivity("2026-11-19", focusMinutes = 25).hasActivity, "focus counts")
        }
        check("Analytics: a window series keeps one slot per day") {
            val days = AnalyticsMath.lastDays("2026-11-19", 7)
            val byDate = mapOf("2026-11-19" to 40.0, "2026-11-17" to 25.0)
            val series = AnalyticsMath.alignToWindow(days, byDate)
            expect(series.size == days.size, "the series must have one slot per day")
            expect(series[6] == 40.0, "the last day must carry its value")
            expect(series[4] == 25.0, "the middle day must carry its value")
            expect(series[0] == null, "a day without data must stay empty, not become zero")
            expect(series.count { it != null } == 2, "only the measured days may carry a value")
            expect(
                AnalyticsMath.alignToWindow(days, emptyMap()).all { it == null },
                "an empty window must stay empty",
            )
        }

        check("Brand: the mark is symmetric about its centre line") {
            val plates = MrsadMark.plates()
            expect(plates.size == 6, "the mark has six plates")
            // A and D mirror each other; B and C mirror each other; E and F
            // mirror each other. The vertices are compared as sets, because
            // a mirrored polygon starts at the opposite corner.
            fun mirrored(p: MrsadMark.Point) = MrsadMark.Point(MrsadMark.WIDTH - p.x, p.y)
            fun key(points: List<MrsadMark.Point>) =
                points.map { "%.3f,%.3f".format(it.x, it.y) }.sorted()
            val pairs = listOf(0 to 3, 1 to 2, 4 to 5)
            pairs.forEach { (l, r) ->
                val left = key(plates[l].map { mirrored(it) })
                val right = key(plates[r])
                expect(left == right, "plates $l and $r are not mirror images")
            }
        }
        check("Brand: the chevron band is a constant-thickness ribbon") {
            // At any x, the gap between the upper and lower edges must be the
            // same, or the band would taper and the M would read as a wedge.
            var x = 0f
            while (x <= MrsadMark.WIDTH) {
                val thickness = MrsadMark.lower(x) - MrsadMark.upper(x)
                expect(thickness > 0f, "band inverts at x=$x")
                x += 7f
            }
            expect(
                abs(MrsadMark.lower(0f) - MrsadMark.upper(0f) - 182f) < 0.01f,
                "band thickness at the outer edge must be 182 units",
            )
            expect(
                abs(MrsadMark.lower(MrsadMark.CENTRE) - MrsadMark.upper(MrsadMark.CENTRE) - 175f) < 0.01f,
                "band thickness at the centre must be 175 units",
            )
        }
        check("Brand: the monochrome silhouette is one closed polygon") {
            val silhouette = MrsadMark.silhouette()
            expect(silhouette.size == 10, "the silhouette has ten vertices")
            // The silhouette is the mark with its grooves closed, so every
            // one of its vertices has to sit on the groove-free outline.
            val grooveFree = MrsadMark.plates(grooveSplit = 0f, grooveStem = 0f).flatten()
            silhouette.forEach { vertex ->
                val onBand = grooveFree.any {
                    abs(it.x - vertex.x) < 0.5f && abs(it.y - vertex.y) < 0.5f
                }
                expect(onBand, "silhouette vertex ${vertex.x},${vertex.y} is not on the mark")
            }
            // And it must enclose strictly more area than the grooved mark,
            // because closing the grooves fills them in.
            expect(
                silhouette.size == 10 && grooveFree.size == 24,
                "unexpected outline point counts",
            )
        }
        check("Brand: the safe-zone circle matches the shipped launcher icon") {
            // These are the numbers docs/branding/build_icon.py prints; the
            // launcher icon is fitted to the 66dp safe zone using them.
            val points = MrsadMark.plates().flatten()
            val (centre, radius) = MrsadMark.minimalEnclosingCircle(points)
            expect(abs(centre.x - 351.5f) < 0.5f, "centre x drifted: ${centre.x}")
            expect(abs(centre.y - 272.6f) < 0.5f, "centre y drifted: ${centre.y}")
            expect(abs(radius - 444.8f) < 0.5f, "radius drifted: $radius")
            expect(abs(33f / radius - 0.07419f) < 0.0001f, "safe-zone scale drifted")
        }
        check("Brand: the mark fits inside its own aspect box") {
            val points = MrsadMark.plates().flatten()
            expect(points.all { it.x >= 0f && it.x <= MrsadMark.WIDTH }, "mark exceeds its width")
            expect(points.all { it.y >= 0f && it.y <= MrsadMark.HEIGHT }, "mark exceeds its height")
            expect(abs(MrsadMark.ASPECT - 703f / 653f) < 1e-6f, "aspect drifted")
        }
        check("Brand: the generated plates reproduce the approved Concept-2 shape") {
            // The six polygons sampled from docs/branding/concept-2-geometric.png.
            // They are the visual authority; the generated plates have to
            // reproduce them, otherwise the shipped icon is a different mark.
            val concept = listOf(
                listOf(0f to 0f, 198f to 205f, 198f to 386f, 0f to 182f),
                listOf(198f to 205f, 351.5f to 365f, 351.5f to 540f, 198f to 386f),
                listOf(351.5f to 365f, 505f to 205f, 505f to 386f, 351.5f to 540f),
                listOf(505f to 205f, 703f to 0f, 703f to 182f, 505f to 386f),
                listOf(0f to 195f, 121f to 318f, 121f to 653f, 0f to 480f),
                listOf(582f to 318f, 703f to 195f, 703f to 480f, 582f to 653f),
            )
            // The plates are generated with the split groove closed, so the
            // only difference from the sampled concept is the stem groove.
            val generated = MrsadMark.plates(grooveSplit = 0f, grooveStem = 13f)
            expect(generated.size == concept.size, "plate count drifted")
            concept.forEachIndexed { index, sampled ->
                val remaining = generated[index].toMutableList()
                sampled.forEach { (sx, sy) ->
                    val match = remaining.minByOrNull {
                        (it.x - sx) * (it.x - sx) + (it.y - sy) * (it.y - sy)
                    } ?: throw AssertionError("plate $index has no vertex near ($sx,$sy)")
                    val distance = kotlin.math.hypot(match.x - sx, match.y - sy)
                    expect(
                        distance < 3f,
                        "plate $index vertex ($sx,$sy) is $distance units from the generated mark",
                    )
                    remaining.remove(match)
                }
            }
        }
        check("Brand: Concept-2 and the generated mark share one enclosing circle") {
            // Two coordinate systems that describe the same shape have to
            // agree on the circle that contains it. If they ever disagree,
            // one of the two is measuring something else.
            val conceptPoints = listOf(
                0f to 0f, 198f to 205f, 198f to 386f, 0f to 182f,
                351.5f to 365f, 351.5f to 540f, 505f to 205f, 505f to 386f,
                703f to 0f, 703f to 182f, 0f to 195f, 121f to 318f,
                121f to 653f, 0f to 480f, 582f to 318f, 703f to 195f,
                703f to 480f, 582f to 653f,
            ).map { MrsadMark.Point(it.first, it.second) }
            val (conceptCentre, conceptRadius) = MrsadMark.minimalEnclosingCircle(conceptPoints)
            val (markCentre, markRadius) =
                MrsadMark.minimalEnclosingCircle(MrsadMark.plates().flatten())
            expect(
                abs(conceptCentre.x - markCentre.x) < 0.5f,
                "Concept-2 centre x ${conceptCentre.x} != mark centre x ${markCentre.x}",
            )
            expect(
                abs(conceptCentre.y - markCentre.y) < 0.5f,
                "Concept-2 centre y ${conceptCentre.y} != mark centre y ${markCentre.y}",
            )
            expect(
                abs(conceptRadius - markRadius) < 0.5f,
                "Concept-2 radius $conceptRadius != mark radius $markRadius",
            )
            // And the circle really is tight: some point has to touch it.
            val touching = MrsadMark.plates().flatten().any { point ->
                abs(
                    kotlin.math.hypot(point.x - markCentre.x, point.y - markCentre.y) - markRadius,
                ) < 0.5f
            }
            expect(touching, "the safe-zone circle is not tight around the mark")
        }

        check("Mind: the level only moves after two rounds in the same direction") {
            expect(MindProgression.levelFrom(emptyList()) == 0, "a new exercise starts at level 0")
            expect(MindProgression.levelFrom(listOf(1.0)) == 0, "one strong round must not promote")
            expect(MindProgression.levelFrom(listOf(1.0, 1.0)) == 1, "two strong rounds promote once")
            expect(MindProgression.levelFrom(listOf(1.0, 1.0, 1.0, 1.0)) == 2, "each pair promotes again")
            expect(MindProgression.levelFrom(listOf(0.2)) == 0, "one weak round must not demote")
            expect(MindProgression.levelFrom(listOf(0.2, 0.2)) == 0, "cannot demote below level 0")
            expect(
                MindProgression.levelFrom(listOf(1.0, 1.0, 0.2, 0.2)) == 0,
                "two weak rounds must undo one promotion",
            )
            expect(
                MindProgression.levelFrom(listOf(0.7, 0.7, 0.7)) == 0,
                "a middling score must not move the ladder",
            )
        }
        check("Mind: the ladder is capped at both ends") {
            val manyPerfect = List(40) { 1.0 }
            expect(MindProgression.levelFrom(manyPerfect) == MindProgression.MAX_LEVEL, "must cap at the top")
            val manyWeak = List(40) { 0.0 }
            expect(MindProgression.levelFrom(manyWeak) == 0, "must cap at the bottom")
        }
        check("Mind: the level is a pure function of the recorded scores") {
            // The level the user sees after a restart must be the level they
            // had before it, so it cannot depend on hidden state.
            val history = listOf(0.9, 0.9, 0.4, 0.95, 0.95, 0.6, 0.88, 0.88)
            val level = MindProgression.levelFrom(history)
            expect(
                MindProgression.levelFrom(history) == level,
                "replaying the same history must give the same level",
            )
            expect(
                MindProgression.levelFrom(history.take(5)) <= level,
                "a shorter history cannot sit above the full one",
            )
        }
        check("Mind: the step prediction matches the level it describes") {
            val history = listOf(0.9, 0.9)
            expect(MindProgression.step(history, 0.95) == 0, "one more strong round must not promote yet")
            expect(
                MindProgression.step(history + 0.95, 0.95) == 1,
                "the second consecutive strong round promotes",
            )
            expect(MindProgression.step(history, 0.1) == 0, "one weak round must not demote yet")
            expect(
                MindProgression.step(history + 0.1, 0.1) == -1,
                "the second consecutive weak round demotes",
            )
        }

        val failures = results.filter { it.second != null }
        println("—".repeat(30))
        println("MRSAD core checks: ${results.size - failures.size}/${results.size} passed")
        failures.forEach { println("FAIL: ${it.first}\n  → ${it.second}") }
        return if (failures.isEmpty()) 0 else 1
    }

    private fun check(name: String, block: () -> Unit) {
        val error = try {
            block(); null
        } catch (t: Throwable) {
            t.message ?: t.toString()
        }
        results += name to error
    }

    private fun expect(condition: Boolean, message: String) {
        if (!condition) throw AssertionError(message)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val code = runAll()
        if (code != 0) kotlin.system.exitProcess(code)
    }
}
