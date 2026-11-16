import type {
  FocusSession,
  Habit,
  HabitLog,
  Task,
  UserSettings,
} from '@/types/domain';
import {
  addDays,
  dateKeyToJalaali,
  diffDays,
  formatJalaaliShort,
  getDateKeysInJalaaliMonth,
  getDateKeysInJalaaliYear,
  getJalaaliMonthName,
  getPersianWeekdayIndex,
  getTodayKey,
  PERSIAN_WEEKDAYS_FULL,
  shiftJalaaliMonth,
} from './date';
import { CATEGORY_META } from './format';

export type HeatmapCellState =
  | 'future'
  | 'unlogged'
  | 'missed'
  | 'level1'
  | 'level2'
  | 'level3'
  | 'level4';

export interface HeatmapDayCell {
  dateKey: string;
  jy: number;
  jm: number;
  jd: number;
  weekdayIdx: number; // 0 = Sat .. 6 = Fri
  state: HeatmapCellState;
  score: number; // 0..100
  habitsScheduled: number;
  habitsCompleted: number;
  habitsPartial: number;
  habitsMissed: number;
  habitsUnlogged: number;
  tasksCompleted: number;
  focusMinutes: number;
}

export interface HabitMonthlyPoint {
  jy: number;
  jm: number;
  label: string;
  adherence: number;
  completedDays: number;
  missedDays: number;
  unloggedDays: number;
}

export interface HabitStats {
  habitId: string;
  completedDays: number;
  partialDays: number;
  missedDays: number;
  unloggedDays: number;
  scheduledDaysCount: number;
  loggedDaysCount: number;
  /** Adherence calculated strictly over logged days (unlogged kept separate!) */
  adherencePercent: number;
  currentStreak: number;
  bestStreak: number;
  monthlyComparison: HabitMonthlyPoint[];
  last14Days: { dateKey: string; ratio: number; state: 'completed' | 'partial' | 'missed' | 'unlogged' }[];
}

export function isHabitScheduledOnDate(habit: Habit, dateKey: string): boolean {
  if (dateKey < habit.startDate) return false;
  if (habit.scheduleType === 'specific_days') {
    const weekday = getPersianWeekdayIndex(dateKey);
    return habit.scheduledDays.includes(weekday);
  }
  return true;
}

export function computeHabitStats(
  habit: Habit,
  allLogs: HabitLog[],
  todayKey: string = getTodayKey()
): HabitStats {
  const logsForHabit = allLogs.filter((l) => l.habitId === habit.id);
  const logByDate = new Map<string, HabitLog>();
  logsForHabit.forEach((l) => logByDate.set(l.dateKey, l));

  // Walk from habit.startDate (capped at 365 days back) up to todayKey
  const minStart = addDays(todayKey, -365);
  const effectiveStart = habit.startDate > minStart ? habit.startDate : minStart;
  const totalSpan = Math.max(0, diffDays(effectiveStart, todayKey));

  let completedDays = 0;
  let partialDays = 0;
  let missedDays = 0;
  let unloggedDays = 0;
  let scheduledDaysCount = 0;

  let bestStreak = 0;
  let runningStreak = 0;
  const scheduledDates: string[] = [];

  for (let i = 0; i <= totalSpan; i++) {
    const dk = addDays(effectiveStart, i);
    if (!isHabitScheduledOnDate(habit, dk)) continue;
    scheduledDaysCount++;
    scheduledDates.push(dk);

    const log = logByDate.get(dk);
    if (!log) {
      unloggedDays++;
      // An unlogged past day breaks consecutive streak, except today which may still be in progress
      if (dk !== todayKey) {
        runningStreak = 0;
      }
    } else if (log.status === 'completed') {
      completedDays++;
      runningStreak++;
      if (runningStreak > bestStreak) bestStreak = runningStreak;
    } else if (log.status === 'partial') {
      partialDays++;
      runningStreak = 0;
    } else {
      missedDays++;
      runningStreak = 0;
    }
  }

  // Compute current streak backwards from today (if today is unlogged, allow streak ending yesterday!)
  let currentStreak = 0;
  for (let idx = scheduledDates.length - 1; idx >= 0; idx--) {
    const dk = scheduledDates[idx];
    const log = logByDate.get(dk);
    if (idx === scheduledDates.length - 1 && dk === todayKey && !log) {
      continue;
    }
    if (log && log.status === 'completed') {
      currentStreak++;
    } else {
      break;
    }
  }

  const loggedDaysCount = completedDays + partialDays + missedDays;
  const weightedSuccess = completedDays + partialDays * 0.5;
  const adherencePercent =
    loggedDaysCount > 0 ? Math.round((weightedSuccess / loggedDaysCount) * 100) : 0;

  // Monthly comparison over the last 6 Jalali months
  const todayJ = dateKeyToJalaali(todayKey);
  const monthlyComparison: HabitMonthlyPoint[] = [];
  for (let mOff = -5; mOff <= 0; mOff++) {
    const { jy, jm } = shiftJalaaliMonth(todayJ.jy, todayJ.jm, mOff);
    const keys = getDateKeysInJalaaliMonth(jy, jm).filter((k) => k <= todayKey);
    let mComp = 0;
    let mPart = 0;
    let mMiss = 0;
    let mUnlog = 0;
    for (const k of keys) {
      if (!isHabitScheduledOnDate(habit, k)) continue;
      const l = logByDate.get(k);
      if (!l) mUnlog++;
      else if (l.status === 'completed') mComp++;
      else if (l.status === 'partial') mPart++;
      else mMiss++;
    }
    const mLogged = mComp + mPart + mMiss;
    const mAdh = mLogged > 0 ? Math.round(((mComp + mPart * 0.5) / mLogged) * 100) : 0;
    monthlyComparison.push({
      jy,
      jm,
      label: getJalaaliMonthName(jm),
      adherence: mAdh,
      completedDays: mComp,
      missedDays: mMiss,
      unloggedDays: mUnlog,
    });
  }

  // Last 14 days trend
  const last14Days = Array.from({ length: 14 }, (_, idx) => {
    const dk = addDays(todayKey, idx - 13);
    const l = logByDate.get(dk);
    if (!l) return { dateKey: dk, ratio: 0, state: 'unlogged' as const };
    if (l.status === 'completed') return { dateKey: dk, ratio: 100, state: 'completed' as const };
    if (l.status === 'partial') {
      const r =
        habit.targetValue > 0
          ? Math.min(95, Math.max(15, Math.round((l.value / habit.targetValue) * 100)))
          : 50;
      return { dateKey: dk, ratio: r, state: 'partial' as const };
    }
    return { dateKey: dk, ratio: 0, state: 'missed' as const };
  });

  return {
    habitId: habit.id,
    completedDays,
    partialDays,
    missedDays,
    unloggedDays,
    scheduledDaysCount,
    loggedDaysCount,
    adherencePercent,
    currentStreak,
    bestStreak,
    monthlyComparison,
    last14Days,
  };
}

/**
 * Generates an accurate 365/366-day Jalali year Heatmap for either:
 * - mode = 'habits': all active habits (or a single habit if habitId provided)
 * - mode = 'overall': combined activity (habits + completed tasks + focus sessions)
 */
export function computeAnnualHeatmap(params: {
  jalaaliYear: number;
  habits: Habit[];
  habitLogs: HabitLog[];
  tasks: Task[];
  focusSessions: FocusSession[];
  todayKey?: string;
  mode?: 'habits' | 'overall';
  habitId?: string;
}): {
  cells: HeatmapDayCell[];
  activeDays: number;
  perfectDays: number;
  missedOnlyDays: number;
  unloggedDays: number;
  totalHabitCompletions: number;
} {
  const {
    jalaaliYear,
    habits,
    habitLogs,
    tasks,
    focusSessions,
    todayKey = getTodayKey(),
    mode = 'habits',
    habitId,
  } = params;

  const targetHabits = habitId
    ? habits.filter((h) => h.id === habitId)
    : habits.filter((h) => !h.archived);

  // Index logs by dateKey
  const logsByDate = new Map<string, HabitLog[]>();
  for (const log of habitLogs) {
    if (habitId && log.habitId !== habitId) continue;
    const arr = logsByDate.get(log.dateKey);
    if (arr) arr.push(log);
    else logsByDate.set(log.dateKey, [log]);
  }

  // Index completed tasks by completedDate
  const tasksDoneByDate = new Map<string, number>();
  for (const t of tasks) {
    if (t.status === 'completed' && t.completedDate) {
      tasksDoneByDate.set(t.completedDate, (tasksDoneByDate.get(t.completedDate) ?? 0) + 1);
    }
  }

  // Index focus minutes by dateKey
  const focusByDate = new Map<string, number>();
  for (const s of focusSessions) {
    focusByDate.set(s.dateKey, (focusByDate.get(s.dateKey) ?? 0) + s.durationMinutes);
  }

  const allKeys = getDateKeysInJalaaliYear(jalaaliYear);
  const cells: HeatmapDayCell[] = [];
  let activeDays = 0;
  let perfectDays = 0;
  let missedOnlyDays = 0;
  let unloggedDays = 0;
  let totalHabitCompletions = 0;

  for (const dk of allKeys) {
    const { jy, jm, jd } = dateKeyToJalaali(dk);
    const weekdayIdx = getPersianWeekdayIndex(dk);
    const isFuture = dk > todayKey;

    const scheduledHabits = targetHabits.filter((h) => isHabitScheduledOnDate(h, dk));
    const dayLogs = logsByDate.get(dk) ?? [];
    const logMap = new Map(dayLogs.map((l) => [l.habitId, l]));

    let hComp = 0;
    let hPart = 0;
    let hMiss = 0;
    let hUnlog = 0;

    for (const h of scheduledHabits) {
      const l = logMap.get(h.id);
      if (!l) hUnlog++;
      else if (l.status === 'completed') hComp++;
      else if (l.status === 'partial') hPart++;
      else hMiss++;
    }

    const tasksCompleted = tasksDoneByDate.get(dk) ?? 0;
    const focusMinutes = Math.round(focusByDate.get(dk) ?? 0);

    let state: HeatmapCellState = 'unlogged';
    let score = 0;

    if (isFuture) {
      state = 'future';
    } else if (mode === 'habits') {
      const loggedCount = hComp + hPart + hMiss;
      if (loggedCount === 0) {
        state = 'unlogged';
        if (scheduledHabits.length > 0) unloggedDays++;
      } else {
        const denom = Math.max(scheduledHabits.length, loggedCount);
        score = Math.round(((hComp + hPart * 0.5) / denom) * 100);
        if (score === 0) {
          state = 'missed';
          missedOnlyDays++;
        } else {
          activeDays++;
          if (score >= 100) {
            state = 'level4';
            perfectDays++;
          } else if (score >= 67) {
            state = 'level3';
          } else if (score >= 34) {
            state = 'level2';
          } else {
            state = 'level1';
          }
        }
      }
      totalHabitCompletions += hComp;
    } else {
      // Overall mode: habits + tasks + focus
      const hasAnyRecord = hComp + hPart + hMiss > 0 || tasksCompleted > 0 || focusMinutes > 0;
      if (!hasAnyRecord) {
        state = 'unlogged';
        unloggedDays++;
      } else {
        const habitRatio =
          scheduledHabits.length > 0 ? (hComp + hPart * 0.5) / scheduledHabits.length : 0;
        const taskRatio = Math.min(1, tasksCompleted / 4);
        const focusRatio = Math.min(1, focusMinutes / 90);
        score = Math.round((habitRatio * 0.5 + taskRatio * 0.3 + focusRatio * 0.2) * 100);
        if (score === 0 && hMiss > 0) {
          state = 'missed';
          missedOnlyDays++;
        } else if (score === 0) {
          state = 'unlogged';
          unloggedDays++;
        } else {
          activeDays++;
          if (score >= 85) {
            state = 'level4';
            perfectDays++;
          } else if (score >= 60) {
            state = 'level3';
          } else if (score >= 30) {
            state = 'level2';
          } else {
            state = 'level1';
          }
        }
      }
      totalHabitCompletions += hComp;
    }

    cells.push({
      dateKey: dk,
      jy,
      jm,
      jd,
      weekdayIdx,
      state,
      score,
      habitsScheduled: scheduledHabits.length,
      habitsCompleted: hComp,
      habitsPartial: hPart,
      habitsMissed: hMiss,
      habitsUnlogged: hUnlog,
      tasksCompleted,
      focusMinutes,
    });
  }

  return {
    cells,
    activeDays,
    perfectDays,
    missedOnlyDays,
    unloggedDays,
    totalHabitCompletions,
  };
}

export interface SmartInsight {
  id: string;
  category: 'habit_growth' | 'postponed' | 'best_weekday' | 'monthly_delta' | 'recommendation';
  title: string;
  body: string;
  metricBadge?: string;
}

export interface MonthlyReviewReport {
  jy: number;
  jm: number;
  monthLabel: string;
  hasAnyData: boolean;
  totalTasks: number;
  completedTasks: number;
  overdueTasks: number;
  taskSuccessRate: number;
  totalFocusMinutes: number;
  focusSessionsCount: number;
  habitAdherenceRate: number;
  habitCompletedLogs: number;
  habitMissedLogs: number;
  habitUnloggedCount: number;
  overallSuccessRate: number;
  bestDay: { dateKey: string; label: string; score: number } | null;
  mostProductiveWeek: { weekIndex: number; label: string; score: number; completedTasks: number; focusMinutes: number } | null;
  dailyCurve: { dateKey: string; day: number; score: number; tasksDone: number; focusMinutes: number }[];
  comparison: {
    prevMonthLabel: string;
    hasPrevData: boolean;
    taskSuccessDelta: number;
    habitAdherenceDelta: number;
    focusMinutesDelta: number;
    completedTasksDelta: number;
  };
  insights: SmartInsight[];
}

function computeRawMonthMetrics(
  jy: number,
  jm: number,
  tasks: Task[],
  habits: Habit[],
  habitLogs: HabitLog[],
  focusSessions: FocusSession[],
  todayKey: string
) {
  const keys = getDateKeysInJalaaliMonth(jy, jm);
  const keySet = new Set(keys);
  const elapsedKeys = keys.filter((k) => k <= todayKey);
  const monthStart = keys[0];
  const monthEnd = keys[keys.length - 1];

  const monthTasks = tasks.filter(
    (t) =>
      (t.dueDate && keySet.has(t.dueDate)) ||
      (t.completedDate && keySet.has(t.completedDate))
  );

  const totalTasks = monthTasks.length;
  const completedTasks = monthTasks.filter((t) => t.status === 'completed').length;
  const overdueTasks = monthTasks.filter(
    (t) => t.status !== 'completed' && t.dueDate !== null && t.dueDate < todayKey && t.dueDate >= monthStart && t.dueDate <= monthEnd
  ).length;
  const taskSuccessRate = totalTasks > 0 ? Math.round((completedTasks / totalTasks) * 100) : 0;

  const monthSessions = focusSessions.filter((s) => keySet.has(s.dateKey));
  const totalFocusMinutes = Math.round(
    monthSessions.reduce((acc, s) => acc + s.durationMinutes, 0)
  );

  const activeHabits = habits.filter((h) => !h.archived);
  const monthLogs = habitLogs.filter((l) => keySet.has(l.dateKey));
  const logLookup = new Map(monthLogs.map((l) => [`${l.habitId}::${l.dateKey}`, l]));

  let habitCompletedLogs = 0;
  let habitPartialLogs = 0;
  let habitMissedLogs = 0;
  let habitUnloggedCount = 0;

  const perHabitAdherence = new Map<string, { completed: number; partial: number; logged: number }>();
  for (const h of activeHabits) {
    perHabitAdherence.set(h.id, { completed: 0, partial: 0, logged: 0 });
  }

  for (const dk of elapsedKeys) {
    for (const h of activeHabits) {
      if (!isHabitScheduledOnDate(h, dk)) continue;
      const log = logLookup.get(`${h.id}::${dk}`);
      const bucket = perHabitAdherence.get(h.id)!;
      if (!log) {
        habitUnloggedCount++;
      } else if (log.status === 'completed') {
        habitCompletedLogs++;
        bucket.completed++;
        bucket.logged++;
      } else if (log.status === 'partial') {
        habitPartialLogs++;
        bucket.partial++;
        bucket.logged++;
      } else {
        habitMissedLogs++;
        bucket.logged++;
      }
    }
  }

  const totalLoggedHabits = habitCompletedLogs + habitPartialLogs + habitMissedLogs;
  const habitAdherenceRate =
    totalLoggedHabits > 0
      ? Math.round(((habitCompletedLogs + habitPartialLogs * 0.5) / totalLoggedHabits) * 100)
      : 0;

  const hasAnyData =
    totalTasks > 0 || monthSessions.length > 0 || totalLoggedHabits > 0;

  // Overall score weights only active dimensions that actually have records
  const parts: number[] = [];
  if (totalTasks > 0) parts.push(taskSuccessRate);
  if (totalLoggedHabits > 0) parts.push(habitAdherenceRate);
  if (monthSessions.length > 0) {
    // Target ~30 min/day elapsed benchmark for focus component
    const targetMin = Math.max(60, elapsedKeys.length * 30);
    parts.push(Math.min(100, Math.round((totalFocusMinutes / targetMin) * 100)));
  }
  const overallSuccessRate =
    parts.length > 0 ? Math.round(parts.reduce((a, b) => a + b, 0) / parts.length) : 0;

  return {
    keys,
    elapsedKeys,
    monthTasks,
    monthSessions,
    monthLogs,
    logLookup,
    activeHabits,
    perHabitAdherence,
    totalTasks,
    completedTasks,
    overdueTasks,
    taskSuccessRate,
    totalFocusMinutes,
    focusSessionsCount: monthSessions.length,
    habitAdherenceRate,
    habitCompletedLogs,
    habitPartialLogs,
    habitMissedLogs,
    habitUnloggedCount,
    overallSuccessRate,
    hasAnyData,
  };
}

export function computeMonthlyReview(
  jy: number,
  jm: number,
  tasks: Task[],
  habits: Habit[],
  habitLogs: HabitLog[],
  focusSessions: FocusSession[],
  todayKey: string = getTodayKey()
): MonthlyReviewReport {
  const cur = computeRawMonthMetrics(jy, jm, tasks, habits, habitLogs, focusSessions, todayKey);
  const prevRef = shiftJalaaliMonth(jy, jm, -1);
  const prev = computeRawMonthMetrics(
    prevRef.jy,
    prevRef.jm,
    tasks,
    habits,
    habitLogs,
    focusSessions,
    todayKey
  );

  // Build daily performance curve + find best day & most productive week
  const tasksDoneByDate = new Map<string, number>();
  cur.monthTasks.forEach((t) => {
    if (t.status === 'completed' && t.completedDate) {
      tasksDoneByDate.set(t.completedDate, (tasksDoneByDate.get(t.completedDate) ?? 0) + 1);
    }
  });
  const focusByDate = new Map<string, number>();
  cur.monthSessions.forEach((s) => {
    focusByDate.set(s.dateKey, (focusByDate.get(s.dateKey) ?? 0) + s.durationMinutes);
  });

  const dailyCurve: MonthlyReviewReport['dailyCurve'] = [];
  let bestDay: MonthlyReviewReport['bestDay'] = null;

  const weekBuckets = new Map<
    number,
    { scoreSum: number; activeDays: number; completedTasks: number; focusMinutes: number }
  >();
  const weekdayStats = Array.from({ length: 7 }, () => ({ scoreSum: 0, count: 0 }));

  cur.keys.forEach((dk, idx) => {
    const day = idx + 1;
    const isFuture = dk > todayKey;
    const tasksDone = tasksDoneByDate.get(dk) ?? 0;
    const focusMin = Math.round(focusByDate.get(dk) ?? 0);

    let hComp = 0;
    let hPart = 0;
    let hSched = 0;
    if (!isFuture) {
      for (const h of cur.activeHabits) {
        if (!isHabitScheduledOnDate(h, dk)) continue;
        hSched++;
        const l = cur.logLookup.get(`${h.id}::${dk}`);
        if (l?.status === 'completed') hComp++;
        else if (l?.status === 'partial') hPart++;
      }
    }

    const hasActivity = tasksDone > 0 || focusMin > 0 || hComp + hPart > 0;
    let score = 0;
    if (hasActivity && !isFuture) {
      const hPartScore = hSched > 0 ? ((hComp + hPart * 0.5) / hSched) * 50 : 0;
      const tPartScore = Math.min(1, tasksDone / 3) * 30;
      const fPartScore = Math.min(1, focusMin / 60) * 20;
      score = Math.round(hPartScore + tPartScore + fPartScore);
    }

    dailyCurve.push({ dateKey: dk, day, score, tasksDone, focusMinutes: focusMin });

    if (hasActivity && !isFuture) {
      if (!bestDay || score > bestDay.score) {
        bestDay = { dateKey: dk, label: formatJalaaliShort(dk), score };
      }

      const wIdx = Math.floor(idx / 7) + 1;
      const wb = weekBuckets.get(wIdx) ?? {
        scoreSum: 0,
        activeDays: 0,
        completedTasks: 0,
        focusMinutes: 0,
      };
      wb.scoreSum += score;
      wb.activeDays += 1;
      wb.completedTasks += tasksDone;
      wb.focusMinutes += focusMin;
      weekBuckets.set(wIdx, wb);

      const wd = getPersianWeekdayIndex(dk);
      weekdayStats[wd].scoreSum += score;
      weekdayStats[wd].count += 1;
    }
  });

  let mostProductiveWeek: MonthlyReviewReport['mostProductiveWeek'] = null;
  weekBuckets.forEach((wb, weekIndex) => {
    const avgScore = Math.round(wb.scoreSum / Math.max(1, wb.activeDays));
    if (!mostProductiveWeek || avgScore > mostProductiveWeek.score) {
      mostProductiveWeek = {
        weekIndex,
        label: `هفته ${weekIndex}`,
        score: avgScore,
        completedTasks: wb.completedTasks,
        focusMinutes: wb.focusMinutes,
      };
    }
  });

  // ===== REAL SMART INSIGHTS (strictly derived from actual records) =====
  const insights: SmartInsight[] = [];

  if (!cur.hasAnyData) {
    insights.push({
      id: 'ins_empty',
      category: 'recommendation',
      title: 'داده‌ای در این ماه ثبت نشده است',
      body: 'برای مشاهده تحلیل‌های هوشمند واقعی، وظایف، عادت‌ها یا جلسات تمرکز خود را در این ماه ثبت کنید.',
    });
  } else {
    // 1. Most improved habit vs previous month
    let topGrowthHabit: { name: string; curRate: number; delta: number } | null = null;
    for (const h of cur.activeHabits) {
      const cB = cur.perHabitAdherence.get(h.id);
      const pB = prev.perHabitAdherence.get(h.id);
      if (!cB || cB.logged === 0) continue;
      const cRate = Math.round(((cB.completed + cB.partial * 0.5) / cB.logged) * 100);
      const pRate =
        pB && pB.logged > 0
          ? Math.round(((pB.completed + pB.partial * 0.5) / pB.logged) * 100)
          : 0;
      const delta = prev.hasAnyData && pB && pB.logged > 0 ? cRate - pRate : cRate;
      if (!topGrowthHabit || delta > topGrowthHabit.delta) {
        topGrowthHabit = { name: h.name, curRate: cRate, delta };
      }
    }
    if (topGrowthHabit) {
      insights.push({
        id: 'ins_habit_growth',
        category: 'habit_growth',
        title: 'بیشترین پیشرفت در عادت‌ها',
        body: prev.hasAnyData
          ? `عادت «${topGrowthHabit.name}» با پایبندی ${topGrowthHabit.curRate}% بهترین روند را ثبت کرد (${topGrowthHabit.delta >= 0 ? `+${topGrowthHabit.delta}%` : `${topGrowthHabit.delta}%`} نسبت به ماه قبل).`
          : `عادت «${topGrowthHabit.name}» با نرخ پایبندی ${topGrowthHabit.curRate}% قوی‌ترین عادت این ماه شما بوده است.`,
        metricBadge: `${topGrowthHabit.curRate}%`,
      });
    }

    // 2. Most postponed / delayed tasks
    const delayedTasks = cur.monthTasks
      .filter((t) => t.postponeCount > 0 || (t.status !== 'completed' && t.dueDate && t.dueDate < todayKey))
      .sort((a, b) => b.postponeCount - a.postponeCount);

    if (delayedTasks.length > 0) {
      const top = delayedTasks[0];
      const catLabel = CATEGORY_META[top.category].label;
      insights.push({
        id: 'ins_postponed',
        category: 'postponed',
        title: 'کارهای بیشتر به تعویق افتاده',
        body:
          top.postponeCount > 0
            ? `وظیفه «${top.title}» در دسته ${catLabel} (${top.postponeCount} بار جابه‌جایی) بیشترین تعویق را داشته است. شکستن آن به زیرکارهای کوچک‌تر توصیه می‌شود.`
            : `${delayedTasks.length} وظیفه عقب‌افتاده در این ماه دارید؛ از جمله «${top.title}» در دسته ${catLabel}.`,
        metricBadge: `${delayedTasks.length} مورد`,
      });
    } else if (cur.totalTasks > 0) {
      insights.push({
        id: 'ins_postponed_none',
        category: 'postponed',
        title: 'تعهد زمانی عالی در وظایف',
        body: 'هیچ وظیفه عقب‌افتاده یا مکرراً به تعویق افتاده‌ای در این ماه ثبت نشده است.',
        metricBadge: '0 تأخیر',
      });
    }

    // 3. Best performing weekday
    let bestWdIdx = -1;
    let bestWdAvg = -1;
    weekdayStats.forEach((st, idx) => {
      if (st.count > 0) {
        const avg = Math.round(st.scoreSum / st.count);
        if (avg > bestWdAvg) {
          bestWdAvg = avg;
          bestWdIdx = idx;
        }
      }
    });
    if (bestWdIdx >= 0) {
      insights.push({
        id: 'ins_weekday',
        category: 'best_weekday',
        title: 'پربازده‌ترین روز هفته شما',
        body: `روزهای «${PERSIAN_WEEKDAYS_FULL[bestWdIdx]}» بالاترین میانگین عملکرد (${bestWdAvg} امتیاز) را در این ماه داشته‌اید. کارهای استراتژیک Q2 را در این روز متمرکز کنید.`,
        metricBadge: `${bestWdAvg} pts`,
      });
    }

    // 4. Month-over-month delta
    if (prev.hasAnyData) {
      const adhDelta = cur.habitAdherenceRate - prev.habitAdherenceRate;
      const focusDelta = cur.totalFocusMinutes - prev.totalFocusMinutes;
      const taskDelta = cur.taskSuccessRate - prev.taskSuccessRate;
      insights.push({
        id: 'ins_delta',
        category: 'monthly_delta',
        title: `تغییر نسبت به ${getJalaaliMonthName(prevRef.jm)}`,
        body: `موفقیت وظایف ${taskDelta >= 0 ? `+${taskDelta}%` : `${taskDelta}%`}، پایبندی عادت‌ها ${adhDelta >= 0 ? `+${adhDelta}%` : `${adhDelta}%`} و زمان تمرکز ${focusDelta >= 0 ? `+${focusDelta}` : `${focusDelta}`} دقیقه تغییر داشته است.`,
        metricBadge: `${adhDelta >= 0 ? '+' : ''}${adhDelta}%`,
      });
    }

    // 5. Actionable recommendation for next month
    let weakestHabit: { name: string; rate: number } | null = null;
    for (const h of cur.activeHabits) {
      const cB = cur.perHabitAdherence.get(h.id);
      if (!cB || cB.logged === 0) continue;
      const rate = Math.round(((cB.completed + cB.partial * 0.5) / cB.logged) * 100);
      if (!weakestHabit || rate < weakestHabit.rate) {
        weakestHabit = { name: h.name, rate };
      }
    }

    if (cur.overdueTasks >= 2) {
      insights.push({
        id: 'ins_rec',
        category: 'recommendation',
        title: 'پیشنهاد بهبود برای ماه آینده',
        body: `تعداد ${cur.overdueTasks} وظیفه عقب‌افتاده ثبت شده است. در ماه آینده سقف وظایف روزانه را سبک‌تر نگه دارید و کارهای Q1 را پیش از ظهر ببندید.`,
      });
    } else if (weakestHabit && weakestHabit.rate < 70) {
      insights.push({
        id: 'ins_rec',
        category: 'recommendation',
        title: 'پیشنهاد بهبود برای ماه آینده',
        body: `عادت «${weakestHabit.name}» با پایبندی ${weakestHabit.rate}% بیشترین ظرفیت رشد را دارد؛ کاهش هدف روزانه یا تنظیم یادآور مشخص، تداوم آن را تثبیت می‌کند.`,
      });
    } else if (cur.totalFocusMinutes < 180) {
      insights.push({
        id: 'ins_rec',
        category: 'recommendation',
        title: 'پیشنهاد بهبود برای ماه آینده',
        body: 'افزودن تنها یک جلسه 25 دقیقه‌ای پومودورو در ابتدای روز، جهش محسوسی در عمق کار و تکمیل وظایف ماه آینده ایجاد می‌کند.',
      });
    } else {
      insights.push({
        id: 'ins_rec',
        category: 'recommendation',
        title: 'پیشنهاد بهبود برای ماه آینده',
        body: 'ریتم عملکرد شما متعادل و پایدار است. در ماه آینده زمان تمرکز عمیق را روی پروژه‌های کلیدی بخش «مهم و غیرفوری» افزایش دهید.',
      });
    }
  }

  return {
    jy,
    jm,
    monthLabel: `${getJalaaliMonthName(jm)} ${jy}`,
    hasAnyData: cur.hasAnyData,
    totalTasks: cur.totalTasks,
    completedTasks: cur.completedTasks,
    overdueTasks: cur.overdueTasks,
    taskSuccessRate: cur.taskSuccessRate,
    totalFocusMinutes: cur.totalFocusMinutes,
    focusSessionsCount: cur.focusSessionsCount,
    habitAdherenceRate: cur.habitAdherenceRate,
    habitCompletedLogs: cur.habitCompletedLogs,
    habitMissedLogs: cur.habitMissedLogs,
    habitUnloggedCount: cur.habitUnloggedCount,
    overallSuccessRate: cur.overallSuccessRate,
    bestDay,
    mostProductiveWeek,
    dailyCurve,
    comparison: {
      prevMonthLabel: `${getJalaaliMonthName(prevRef.jm)} ${prevRef.jy}`,
      hasPrevData: prev.hasAnyData,
      taskSuccessDelta: cur.taskSuccessRate - prev.taskSuccessRate,
      habitAdherenceDelta: cur.habitAdherenceRate - prev.habitAdherenceRate,
      focusMinutesDelta: cur.totalFocusMinutes - prev.totalFocusMinutes,
      completedTasksDelta: cur.completedTasks - prev.completedTasks,
    },
    insights,
  };
}

export interface AnnualMonthSummary {
  jm: number;
  label: string;
  hasData: boolean;
  overallScore: number;
  completedTasks: number;
  totalTasks: number;
  habitAdherence: number;
  focusMinutes: number;
}

export interface AnnualReviewReport {
  jy: number;
  hasAnyData: boolean;
  totalCompletedTasks: number;
  totalTasks: number;
  totalFocusMinutes: number;
  averageHabitAdherence: number;
  activeDays: number;
  months: AnnualMonthSummary[];
  bestMonth: AnnualMonthSummary | null;
  weakestMonth: AnnualMonthSummary | null;
}

export function computeAnnualReview(
  jy: number,
  tasks: Task[],
  habits: Habit[],
  habitLogs: HabitLog[],
  focusSessions: FocusSession[],
  todayKey: string = getTodayKey()
): AnnualReviewReport {
  const months: AnnualMonthSummary[] = [];
  let totalCompletedTasks = 0;
  let totalTasks = 0;
  let totalFocusMinutes = 0;
  let adherenceSum = 0;
  let adherenceMonths = 0;

  for (let jm = 1; jm <= 12; jm++) {
    const raw = computeRawMonthMetrics(jy, jm, tasks, habits, habitLogs, focusSessions, todayKey);
    totalCompletedTasks += raw.completedTasks;
    totalTasks += raw.totalTasks;
    totalFocusMinutes += raw.totalFocusMinutes;
    if (raw.habitCompletedLogs + raw.habitPartialLogs + raw.habitMissedLogs > 0) {
      adherenceSum += raw.habitAdherenceRate;
      adherenceMonths++;
    }
    months.push({
      jm,
      label: getJalaaliMonthName(jm),
      hasData: raw.hasAnyData,
      overallScore: raw.overallSuccessRate,
      completedTasks: raw.completedTasks,
      totalTasks: raw.totalTasks,
      habitAdherence: raw.habitAdherenceRate,
      focusMinutes: raw.totalFocusMinutes,
    });
  }

  const activeMonths = months.filter((m) => m.hasData);
  const bestMonth =
    activeMonths.length > 0
      ? activeMonths.reduce((best, cur) => (cur.overallScore > best.overallScore ? cur : best))
      : null;
  const weakestMonth =
    activeMonths.length > 1
      ? activeMonths.reduce((weak, cur) => (cur.overallScore < weak.overallScore ? cur : weak))
      : null;

  const heatmap = computeAnnualHeatmap({
    jalaaliYear: jy,
    habits,
    habitLogs,
    tasks,
    focusSessions,
    todayKey,
    mode: 'overall',
  });

  return {
    jy,
    hasAnyData: activeMonths.length > 0,
    totalCompletedTasks,
    totalTasks,
    totalFocusMinutes,
    averageHabitAdherence: adherenceMonths > 0 ? Math.round(adherenceSum / adherenceMonths) : 0,
    activeDays: heatmap.activeDays,
    months,
    bestMonth,
    weakestMonth,
  };
}

export interface DailyDashboardSummary {
  todayKey: string;
  todayTasksTotal: number;
  todayTasksCompleted: number;
  overdueCount: number;
  todayHabitsScheduled: number;
  todayHabitsCompleted: number;
  todayFocusMinutes: number;
  focusGoalMinutes: number;
  dailyProgressPercent: number;
}

export function computeDailyDashboardSummary(
  tasks: Task[],
  habits: Habit[],
  habitLogs: HabitLog[],
  focusSessions: FocusSession[],
  todayKey: string = getTodayKey(),
  settings?: UserSettings
): DailyDashboardSummary {
  const activeTasks = tasks.filter((t) => !t.archived);
  const todayTasks = activeTasks.filter(
    (t) => t.dueDate === todayKey || (t.status === 'completed' && t.completedDate === todayKey)
  );
  const todayTasksTotal = todayTasks.length;
  const todayTasksCompleted = todayTasks.filter((t) => t.status === 'completed').length;
  const overdueCount = activeTasks.filter(
    (t) => t.status !== 'completed' && t.dueDate !== null && t.dueDate < todayKey
  ).length;

  const scheduledHabits = habits.filter((h) => !h.archived && isHabitScheduledOnDate(h, todayKey));
  const todayLogMap = new Map(
    habitLogs.filter((l) => l.dateKey === todayKey).map((l) => [l.habitId, l])
  );
  let todayHabitsCompleted = 0;
  let todayHabitPoints = 0;
  for (const h of scheduledHabits) {
    const l = todayLogMap.get(h.id);
    if (l?.status === 'completed') {
      todayHabitsCompleted++;
      todayHabitPoints += 1;
    } else if (l?.status === 'partial') {
      todayHabitPoints += 0.5;
    }
  }

  const todayFocusMinutes = Math.round(
    focusSessions
      .filter((s) => s.dateKey === todayKey)
      .reduce((acc, s) => acc + s.durationMinutes, 0)
  );
  const focusGoalMinutes = settings?.dailyFocusGoalMinutes ?? 120;

  // Weighted progress using only dimensions active today so progress is honest & responsive
  const components: { weight: number; ratio: number }[] = [];
  if (todayTasksTotal > 0) {
    components.push({ weight: 0.45, ratio: todayTasksCompleted / todayTasksTotal });
  }
  if (scheduledHabits.length > 0) {
    components.push({ weight: 0.35, ratio: todayHabitPoints / scheduledHabits.length });
  }
  if (focusGoalMinutes > 0 && (todayFocusMinutes > 0 || components.length > 0)) {
    components.push({ weight: 0.2, ratio: Math.min(1, todayFocusMinutes / focusGoalMinutes) });
  }

  const totalWeight = components.reduce((s, c) => s + c.weight, 0);
  const dailyProgressPercent =
    totalWeight > 0
      ? Math.round(
          (components.reduce((s, c) => s + c.weight * c.ratio, 0) / totalWeight) * 100
        )
      : 0;

  return {
    todayKey,
    todayTasksTotal,
    todayTasksCompleted,
    overdueCount,
    todayHabitsScheduled: scheduledHabits.length,
    todayHabitsCompleted,
    todayFocusMinutes,
    focusGoalMinutes,
    dailyProgressPercent,
  };
}
