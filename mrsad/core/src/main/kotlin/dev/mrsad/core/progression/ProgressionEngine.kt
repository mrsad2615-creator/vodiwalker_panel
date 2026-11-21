package dev.mrsad.core.progression

import dev.mrsad.core.analytics.AnalyticsMath
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure domain engine for user XP, leveling, achievements, streak calendar,
 * and weekly challenges (Master Prompt §6 & §12).
 *
 * Every number is derived deterministically from real database telemetry —
 * never from fabricated counters.
 */
object ProgressionEngine {

    data class TelemetryInput(
        val totalMinutes: Int = 0,
        val totalSessions: Int = 0,
        val todayMinutes: Int = 0,
        val todaySessions: Int = 0,
        val currentStreak: Int = 0,
        val longestStreak: Int = 0,
        val bestWpm: Int = 0,
        val bestAccuracy: Double = 0.0,
        val bestReadingWpm: Int = 0,
        val eyeSessions: Int = 0,
        val mindSessions: Int = 0,
        val focusSessions: Int = 0,
        val activeDates: Set<String> = emptySet(),
        val todayIso: String = "2026-11-21",
        val dailyGoalMinutes: Int = 25,
    )

    data class LevelStatus(
        val level: Int,
        val titleFa: String,
        val totalXp: Int,
        val currentLevelXp: Int,
        val nextLevelSpanXp: Int,
        val progress: Float,
    ) {
        val progressFraction: Float get() = progress
    }

    data class AchievementBadge(
        val id: String,
        val titleFa: String,
        val descriptionFa: String,
        val categoryCode: String,
        val xpReward: Int,
        val unlocked: Boolean,
        val progress: Float,
        val progressLabelFa: String,
    )

    data class CalendarDayCell(
        val dateIso: String,
        val active: Boolean,
        val isToday: Boolean,
    )

    data class WeeklyChallengeStatus(
        val titleFa: String,
        val subtitleFa: String,
        val current: Int,
        val target: Int,
        val xpReward: Int,
        val completed: Boolean,
    ) {
        val rewardXp: Int get() = xpReward
        val progress: Float
            get() = (current.toFloat() / target.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        val progressFraction: Float get() = progress
    }

    data class ProgressionSummary(
        val levelStatus: LevelStatus,
        val achievements: List<AchievementBadge>,
        val calendar28Days: List<CalendarDayCell>,
        val weeklyChallenges: List<WeeklyChallengeStatus>,
        val dailyGoalProgress: Float,
    ) {
        val levelInfo: LevelStatus get() = levelStatus
        val calendarDays: List<CalendarDayCell> get() = calendar28Days
        val unlockedCount: Int get() = achievements.count { it.unlocked }
    }

    /**
     * Computes the full progression state from [input].
     */
    fun compute(input: TelemetryInput): ProgressionSummary {
        val achievements = evaluateAchievements(input)
        val badgeBonusXp = achievements.filter { it.unlocked }.sumOf { it.xpReward }
        val rawXp = (input.totalMinutes.coerceAtLeast(0) * 12) +
            (input.totalSessions.coerceAtLeast(0) * 25) +
            (input.currentStreak.coerceAtLeast(0) * 20) +
            badgeBonusXp

        val levelStatus = computeLevel(rawXp)
        val calendar = buildCalendar28Days(input.activeDates, input.todayIso)
        val challenges = buildWeeklyChallenges(input)
        val goalProgress = (input.todayMinutes.toFloat() / input.dailyGoalMinutes.coerceAtLeast(1).toFloat())
            .coerceIn(0f, 1f)

        return ProgressionSummary(
            levelStatus = levelStatus,
            achievements = achievements,
            calendar28Days = calendar,
            weeklyChallenges = challenges,
            dailyGoalProgress = goalProgress,
        )
    }

    /**
     * Progressive XP curve:
     * Level 1 requires 0 XP; each level `L` requires `150 + (L - 1) * 75` XP to advance.
     */
    fun computeLevel(totalXp: Int): LevelStatus {
        val safeXp = totalXp.coerceAtLeast(0)
        var level = 1
        var remaining = safeXp
        while (level < 50) {
            val needed = xpForNextLevel(level)
            if (remaining < needed) break
            remaining -= needed
            level++
        }
        val span = xpForNextLevel(level).coerceAtLeast(1)
        val progress = (remaining.toFloat() / span.toFloat()).coerceIn(0f, 1f)
        return LevelStatus(
            level = level,
            titleFa = rankTitleFa(level),
            totalXp = safeXp,
            currentLevelXp = remaining,
            nextLevelSpanXp = span,
            progress = progress,
        )
    }

    fun xpForNextLevel(level: Int): Int = 150 + (level.coerceAtLeast(1) - 1) * 75

    fun rankTitleFa(level: Int): String = when {
        level <= 2 -> "آغازگر شناختی"
        level <= 5 -> "پویشگر متمرکز"
        level <= 9 -> "تیزبین دیجیتال"
        level <= 14 -> "تحلیل‌گر ذهنی"
        level <= 20 -> "استاد سرعت و دقت"
        level <= 30 -> "فرمانده شناختی"
        else -> "معمار ارشد ذهن"
    }

    private fun evaluateAchievements(input: TelemetryInput): List<AchievementBadge> {
        val list = ArrayList<AchievementBadge>(8)

        // 1. First session
        val firstDone = input.totalSessions >= 1
        list += AchievementBadge(
            id = "first_step",
            titleFa = "گام نخست",
            descriptionFa = "تکمیل اولین جلسهٔ واقعی تمرین در مرصاد",
            categoryCode = "CORE",
            xpReward = 50,
            unlocked = firstDone,
            progress = if (firstDone) 1f else 0f,
            progressLabelFa = "${input.totalSessions.coerceAtMost(1)} / ۱ جلسه",
        )

        // 2. 3-day streak
        val bestStreak = max(input.currentStreak, input.longestStreak)
        val streak3 = bestStreak >= 3
        list += AchievementBadge(
            id = "streak_3",
            titleFa = "آغاز پیوستگی",
            descriptionFa = "حداقل ۳ روز تمرین پیوسته",
            categoryCode = "STREAK",
            xpReward = 90,
            unlocked = streak3,
            progress = (bestStreak / 3f).coerceIn(0f, 1f),
            progressLabelFa = "${bestStreak.coerceAtMost(3)} / ۳ روز",
        )

        // 3. 7-day streak
        val streak7 = bestStreak >= 7
        list += AchievementBadge(
            id = "streak_7",
            titleFa = "ثبات آهنین",
            descriptionFa = "۷ روز تمرین متوالی بدون وقفه",
            categoryCode = "STREAK",
            xpReward = 200,
            unlocked = streak7,
            progress = (bestStreak / 7f).coerceIn(0f, 1f),
            progressLabelFa = "${bestStreak.coerceAtMost(7)} / ۷ روز",
        )

        // 4. Typing speed >= 50 WPM
        val speed50 = input.bestWpm >= 50
        list += AchievementBadge(
            id = "type_50",
            titleFa = "انگشتان تندرو",
            descriptionFa = "ثبت سرعت تایپ ۵۰ کلمه در دقیقه یا بیشتر",
            categoryCode = "TYPE",
            xpReward = 140,
            unlocked = speed50,
            progress = (input.bestWpm / 50f).coerceIn(0f, 1f),
            progressLabelFa = "${input.bestWpm.coerceAtMost(50)} / ۵۰ WPM",
        )

        // 5. Typing accuracy >= 96%
        val accInt = input.bestAccuracy.roundToInt()
        val acc96 = input.bestAccuracy >= 96.0
        list += AchievementBadge(
            id = "accuracy_96",
            titleFa = "دقت الماسی",
            descriptionFa = "کسب دقت ۹۶٪ یا بالاتر در یک جلسهٔ تایپ",
            categoryCode = "TYPE",
            xpReward = 150,
            unlocked = acc96,
            progress = (input.bestAccuracy.toFloat() / 96f).coerceIn(0f, 1f),
            progressLabelFa = "${accInt.coerceAtMost(96)}٪ / ۹۶٪",
        )

        // 6. Effective speed reading >= 260 WPM
        val read260 = input.bestReadingWpm >= 260
        list += AchievementBadge(
            id = "read_260",
            titleFa = "مطالعه‌گر تیزخوان",
            descriptionFa = "سرعت مطالعهٔ مؤثر ۲۶۰ کلمه بر دقیقه همراه با درک مطلب",
            categoryCode = "READ",
            xpReward = 130,
            unlocked = read260,
            progress = (input.bestReadingWpm / 260f).coerceIn(0f, 1f),
            progressLabelFa = "${input.bestReadingWpm.coerceAtMost(260)} / ۲۶۰ WPM",
        )

        // 7. Eye skill training >= 3 sessions
        val eye3 = input.eyeSessions >= 3
        list += AchievementBadge(
            id = "eye_3",
            titleFa = "نگاه متمرکز",
            descriptionFa = "تکمیل ۳ جلسه تمرین مهارت‌های دیداری و توجه چشمی",
            categoryCode = "EYE",
            xpReward = 110,
            unlocked = eye3,
            progress = (input.eyeSessions / 3f).coerceIn(0f, 1f),
            progressLabelFa = "${input.eyeSessions.coerceAtMost(3)} / ۳ جلسه",
        )

        // 8. Brain cognitive training >= 5 sessions
        val mind5 = input.mindSessions >= 5
        list += AchievementBadge(
            id = "mind_5",
            titleFa = "معمار شناخت",
            descriptionFa = "تکمیل ۵ جلسه تمرین شناختی و مغزی",
            categoryCode = "MIND",
            xpReward = 160,
            unlocked = mind5,
            progress = (input.mindSessions / 5f).coerceIn(0f, 1f),
            progressLabelFa = "${input.mindSessions.coerceAtMost(5)} / ۵ جلسه",
        )

        return list
    }

    fun buildStreakCalendar(
        todayIso: String,
        activeDates: Set<String>,
        windowDays: Int = 28,
    ): List<CalendarDayCell> {
        val window = AnalyticsMath.lastDays(todayIso, windowDays)
        return window.map { iso ->
            CalendarDayCell(
                dateIso = iso,
                active = iso in activeDates,
                isToday = iso == todayIso,
            )
        }
    }

    private fun buildCalendar28Days(activeDates: Set<String>, todayIso: String): List<CalendarDayCell> =
        buildStreakCalendar(todayIso, activeDates, 28)

    private fun buildWeeklyChallenges(input: TelemetryInput): List<WeeklyChallengeStatus> {
        val last7 = AnalyticsMath.lastDays(input.todayIso, 7)
        val activeLast7 = last7.count { it in input.activeDates }
        return listOf(
            WeeklyChallengeStatus(
                titleFa = "چالش ۵ روز فعال در هفته",
                subtitleFa = "در ۵ روز از ۷ روز اخیر حداقل یک تمرین ثبت کنید",
                current = activeLast7,
                target = 5,
                xpReward = 150,
                completed = activeLast7 >= 5,
            ),
            WeeklyChallengeStatus(
                titleFa = "تکمیل هدف روزانه (۲۵ دقیقه)",
                subtitleFa = "امروز مجموعاً ۲۵ دقیقه تمرین ثبت کنید",
                current = input.todayMinutes,
                target = input.dailyGoalMinutes,
                xpReward = 100,
                completed = input.todayMinutes >= input.dailyGoalMinutes,
            ),
            WeeklyChallengeStatus(
                titleFa = "تنوع چندبعدی مهارت‌ها",
                subtitleFa = "انجام حداقل ۳ جلسه در بخش‌های چشم و ذهن",
                current = input.eyeSessions + input.mindSessions,
                target = 3,
                xpReward = 120,
                completed = (input.eyeSessions + input.mindSessions) >= 3,
            ),
        )
    }
}
