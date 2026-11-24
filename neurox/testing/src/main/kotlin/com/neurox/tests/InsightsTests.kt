package com.neurox.tests

import com.neurox.domain.*
import java.time.Instant
import java.time.ZoneId

internal fun insightsTests() {
    val now = Instant.parse("2026-11-24T12:00:00Z").toEpochMilli()
    val zone = ZoneId.of("UTC")
    fun fact(id: String = "a", date: String = "2026-11-24T10:00:00Z", exercise: Exercise = Exercise.FOCUS_LOCK) =
        TrainingFact(id, exercise, Instant.parse(date).toEpochMilli(), 60_000, 0.9, true, true)
    case("insights empty history contains honest zero days and no accuracy") {
        val s = TrainingInsights.summarize(emptyList(), now, zone)
        eq(0, s.sessions); eq(0L, s.activeMs); eq(null, s.meanSessionAccuracy); eq(0, s.streak)
        eq(7, s.days.size); check(s.days.all { it.sessions == 0 && it.activeMs == 0L })
    }
    case("insights counts real active time and mean session accuracy") {
        val s = TrainingInsights.summarize(listOf(fact(), fact("b").copy(accuracy = 0.7, durationMs = 120_000)), now, zone)
        eq(2, s.sessions); eq(180_000L, s.activeMs); near(0.8, s.meanSessionAccuracy!!)
    }
    case("insights excludes practice-invalid, baseline, active, aborted and future data") {
        val f = fact()
        val s = TrainingInsights.summarize(listOf(f.copy(valid = false), f.copy(baseline = true), f.copy(completed = false), f.copy(startedAt = now + 1)), now, zone)
        eq(0, s.sessions)
    }
    case("insights can explicitly chart baselines without granting milestone progress") {
        val records = listOf(fact().copy(baseline = true))
        eq(1, TrainingInsights.summarize(records, now, zone, includeBaselines = true).sessions)
        check(TrainingInsights.milestones(records, now, zone).none { it.unlocked })
    }
    case("insights rejects nonfinite out-of-range and impossible stored aggregates") {
        val f = fact()
        val s = TrainingInsights.summarize(listOf(f.copy(accuracy = Double.NaN), f.copy(accuracy = 1.1), f.copy(durationMs = -1), f.copy(durationMs = Long.MAX_VALUE)), now, zone)
        eq(0, s.sessions)
    }
    case("insights duplicate session identifiers cannot inflate rewards or counts") {
        val f = fact()
        eq(1, TrainingInsights.summarize(List(20) { f }, now, zone).sessions)
        eq(1L, TrainingInsights.milestones(List(20) { f }, now, zone).first().current)
    }
    case("insights filters modules without comparing unrelated speed units") {
        val s = TrainingInsights.summarize(listOf(fact(), fact("b", exercise = Exercise.CODE_TYPING)), now, zone, module = Module.VISION)
        eq(1, s.sessions); eq(60_000L, s.activeMs)
    }
    case("insights streak continues from yesterday") {
        val s = TrainingInsights.summarize(listOf(fact(date = "2026-11-23T10:00:00Z"), fact("b", "2026-11-22T10:00:00Z")), now, zone)
        eq(2, s.streak)
    }
    case("insights streak has no debt after a missed day") {
        eq(0, TrainingInsights.summarize(listOf(fact(date = "2026-11-22T10:00:00Z")), now, zone).streak)
    }
    case("insights session range does not truncate all-history streak") {
        val values = (0..12).map { i -> fact("$i").copy(startedAt = now - i * 86_400_000L) }
        val s = TrainingInsights.summarize(values, now, zone, days = 7)
        eq(7, s.sessions); eq(13, s.streak)
    }
    case("insights calendar buckets use supplied timezone") {
        val midnight = Instant.parse("2026-11-24T01:00:00Z").toEpochMilli()
        val values = listOf(fact(date = "2026-11-23T23:30:00Z"))
        eq(1, TrainingInsights.summarize(values, midnight, ZoneId.of("Europe/Berlin")).days.last().sessions)
        eq(0, TrainingInsights.summarize(values, midnight, zone).days.last().sessions)
    }
    case("insights DST day boundaries do not assume 24 hour calendar days") {
        val end = Instant.parse("2026-10-26T12:00:00Z").toEpochMilli()
        val s = TrainingInsights.summarize(listOf(fact(date = "2026-10-24T22:30:00Z")), end, ZoneId.of("Europe/Berlin"), days = 2)
        eq(1, s.sessions); eq("2026-10-25", s.days.first().date.toString())
    }
    case("insights validates range and time bounds") {
        rejects { TrainingInsights.summarize(emptyList(), -1, zone) }
        rejects { TrainingInsights.summarize(emptyList(), now, zone, days = 0) }
        rejects { TrainingInsights.summarize(emptyList(), now, zone, days = 3661) }
    }
    case("milestones start locked with zero progress") {
        val values = TrainingInsights.milestones(emptyList(), now, zone)
        eq(6, values.size); check(values.all { !it.unlocked && it.fraction == 0f })
    }
    case("milestone date is the earliest actual qualifying session") {
        val a = fact(date = "2026-11-23T10:00:00Z")
        val b = fact("b")
        eq(a.startedAt, TrainingInsights.milestones(listOf(b, a), now, zone).first().earnedAt)
    }
    case("four-system milestone needs four distinct measured modules") {
        val records = Catalogue.primary.mapIndexed { i, d -> fact("$i", exercise = d.exercise) }
        check(TrainingInsights.milestones(records, now, zone).first { it.id == "four_systems" }.unlocked)
        check(!TrainingInsights.milestones(List(4) { fact("$it") }, now, zone).first { it.id == "four_systems" }.unlocked)
    }
    case("seven-day milestone uses distinct local days not session count") {
        check(!TrainingInsights.milestones(List(7) { fact("$it") }, now, zone).first { it.id == "seven_days" }.unlocked)
        val records = (0..6).map { fact("$it").copy(startedAt = now - it * 86_400_000L) }
        check(TrainingInsights.milestones(records, now, zone).first { it.id == "seven_days" }.unlocked)
    }
    case("careful-practice milestone counts only real high-accuracy sessions") {
        val records = (0..4).map { fact("$it").copy(accuracy = 0.95) }
        check(TrainingInsights.milestones(records, now, zone).last().unlocked)
        check(!TrainingInsights.milestones(records.map { it.copy(valid = false) }, now, zone).last().unlocked)
    }
    case("one-hour milestone uses active milliseconds and progress clamps at one") {
        val values = TrainingInsights.milestones(listOf(fact().copy(durationMs = 3_700_000)), now, zone)
        val hour = values.first { it.id == "one_hour" }
        check(hour.unlocked); eq(1f, hour.fraction)
    }
    case("next exercise is deterministic with no history for every system") {
        Module.entries.forEach { module ->
            eq(Catalogue.primary.first { it.exercise.module == module }.exercise, TrainingInsights.nextExercise(emptyList(), now, module))
        }
    }
    case("next exercise balances valid task counts without changing difficulty") {
        val records = listOf(fact())
        eq(Exercise.VISUAL_SEARCH, TrainingInsights.nextExercise(records, now, Module.VISION))
        eq(Exercise.FOCUS_LOCK, TrainingInsights.nextExercise(records.map { it.copy(valid = false) }, now, Module.VISION))
    }
}
