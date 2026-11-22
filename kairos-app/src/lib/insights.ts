import type {
  FocusSession,
  Habit,
  HabitLog,
  JournalEntry,
  Task,
} from '@/types/domain';
import type { NavTab } from '@/types/domain';
import { addDays, diffDays } from './date';
import { isHabitScheduledOnDate } from './analytics';

/**
 * Local intelligence.
 *
 * Everything here is arithmetic over rows the user already owns — no network, no
 * model, no invented number. The contract is honesty: every insight carries the
 * sample it was computed from, and an insight that cannot be trusted is not
 * returned at all. A card that says "داده کافی نیست" is worth more than a card
 * that guesses.
 */

export interface InsightAction {
  label: string;
  tab: NavTab;
}

export interface Insight {
  id: string;
  /** Headline, already a Persian sentence with the real number in it. */
  title: string;
  /** One line of consequence — what to do about it. */
  body: string;
  /** What it was computed from, e.g. «۹ جلسه · ۵ روز». Never optional. */
  evidence: string;
  action: InsightAction | null;
  /** Higher wins. Ties keep insertion order. */
  priority: number;
}

export const INSIGHT_MIN = {
  /** Sessions before a time-of-day pattern is allowed to speak. */
  windowSessions: 5,
  windowDays: 3,
  /** Sessions per category before an estimate is quoted. */
  estimateSamples: 3,
  /** Paired days (mood + focus) before a correlation is claimed. */
  moodPairs: 7,
  /** A mood effect smaller than this percentage is called noise. */
  moodLiftPercent: 20,
} as const;

/** Local hour a session began in, taken from its own ISO timestamp. */
export function sessionStartHour(session: FocusSession): number {
  const started = new Date(session.startedAt);
  return Number.isNaN(started.getTime()) ? 0 : started.getHours();
}

function effectiveMinutes(session: FocusSession): number {
  if (session.durationMinutes > 0) return session.durationMinutes;
  return session.durationSeconds / 60;
}

function usableSessions(sessions: FocusSession[]): FocusSession[] {
  return sessions.filter(
    (session) =>
      effectiveMinutes(session) >= 3 && // a 30-second tap is not a pattern
      session.dateKey &&
      !Number.isNaN(new Date(session.startedAt).getTime())
  );
}

export interface FocusWindow {
  startHour: number;
  endHour: number;
  minutes: number;
  sessions: number;
  days: number;
}

/**
 * The best two-hour block of the day, by average minutes actually focused.
 *
 * Two hours on purpose: one hour is too narrow to plan around, and a four-hour
 * "best window" is just the whole working day wearing a hat.
 */
export function bestFocusWindow(sessions: FocusSession[]): FocusWindow | null {
  const list = usableSessions(sessions);
  if (list.length < INSIGHT_MIN.windowSessions) return null;

  const days = new Set(list.map((session) => session.dateKey));
  if (days.size < INSIGHT_MIN.windowDays) return null;

  // Weight each hour by the minutes that landed in it (a 50-minute block is not
  // the same evidence as a 5-minute one), spread evenly across its span.
  const byHour = new Array<number>(24).fill(0);
  for (const session of list) {
    const start = sessionStartHour(session);
    const span = Math.max(1, Math.round(effectiveMinutes(session) / 60));
    const per = effectiveMinutes(session) / span;
    for (let i = 0; i < span; i++) byHour[(start + i) % 24] += per;
  }

  let best: FocusWindow | null = null;
  for (let start = 0; start < 24; start++) {
    const minutes = byHour[start] + byHour[(start + 1) % 24];
    if (!best || minutes > best.minutes) {
      best = {
        startHour: start,
        endHour: (start + 2) % 24,
        minutes: Math.round(minutes),
        sessions: list.length,
        days: days.size,
      };
    }
  }
  if (!best || best.minutes <= 0) return null;
  return best;
}

/** Median focused minutes per task category, learned from sessions tied to tasks. */
export function categoryFocusEstimates(
  sessions: FocusSession[],
  tasks: Task[]
): Map<string, { minutes: number; samples: number }> {
  const categoryOf = new Map(tasks.map((task) => [task.id, task.category]));
  const buckets = new Map<string, number[]>();

  for (const session of sessions) {
    if (!session.taskId) continue;
    const category = categoryOf.get(session.taskId);
    if (!category) continue;
    const minutes = effectiveMinutes(session);
    if (minutes < 3) continue;
    const list = buckets.get(category) ?? [];
    list.push(minutes);
    buckets.set(category, list);
  }

  const result = new Map<string, { minutes: number; samples: number }>();
  for (const [category, values] of buckets) {
    if (values.length < INSIGHT_MIN.estimateSamples) continue;
    const sorted = [...values].sort((a, b) => a - b);
    const middle = Math.floor(sorted.length / 2);
    const median =
      sorted.length % 2 === 0 ? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle];
    result.set(category, { minutes: Math.round(median), samples: values.length });
  }
  return result;
}

/**
 * Habits whose adherence is falling: strong the week before, thin this week.
 * Deliberately asymmetric — a habit that was always slack is not "at risk", it
 * is simply not a habit yet, and telling the user that is not an insight.
 */
export function habitsAtRisk(params: {
  habits: Habit[];
  habitLogs: HabitLog[];
  todayKey: string;
}): { habit: Habit; before: number; now: number; missed: number }[] {
  const { habits, habitLogs, todayKey } = params;
  const logged = new Set(
    habitLogs.filter((log) => log.status === 'completed').map((log) => `${log.habitId}|${log.dateKey}`)
  );

  const adherenceIn = (habit: Habit, startKey: string, days: number): { ratio: number; missed: number; scheduled: number } => {
    let scheduled = 0;
    let done = 0;
    for (let i = 0; i < days; i++) {
      const dateKey = addDays(startKey, i);
      if (dateKey > todayKey) continue;
      if (!isHabitScheduledOnDate(habit, dateKey)) continue;
      scheduled += 1;
      if (logged.has(`${habit.id}|${dateKey}`)) done += 1;
    }
    return { ratio: scheduled === 0 ? 0 : done / scheduled, missed: scheduled - done, scheduled };
  };

  const risk: { habit: Habit; before: number; now: number; missed: number }[] = [];
  for (const habit of habits) {
    if (habit.archived) continue;
    const nowWindow = adherenceIn(habit, addDays(todayKey, -6), 7);
    const beforeWindow = adherenceIn(habit, addDays(todayKey, -13), 7);
    if (nowWindow.scheduled < 3 || beforeWindow.scheduled < 3) continue;
    if (beforeWindow.ratio < 0.6) continue;
    if (nowWindow.ratio > 0.4) continue;
    risk.push({
      habit,
      before: Math.round(beforeWindow.ratio * 100),
      now: Math.round(nowWindow.ratio * 100),
      missed: nowWindow.missed,
    });
  }
  return risk.sort((a, b) => a.now - b.now);
}

/**
 * Does a good mood come with more focus, on this person's own days?
 * Only asks when there are enough paired days, and refuses to speak about a gap
 * smaller than the noise floor.
 */
export function moodFocusLink(params: {
  journal: JournalEntry[];
  sessions: FocusSession[];
}): { highDays: number; highAvg: number; lowDays: number; lowAvg: number; liftPercent: number } | null {
  const minutesByDay = new Map<string, number>();
  for (const session of usableSessions(params.sessions)) {
    minutesByDay.set(session.dateKey, (minutesByDay.get(session.dateKey) ?? 0) + effectiveMinutes(session));
  }

  const high: number[] = [];
  const low: number[] = [];
  for (const entry of params.journal) {
    if (typeof entry.mood !== 'number' || entry.mood < 1) continue;
    const minutes = minutesByDay.get(entry.dateKey);
    if (minutes === undefined) continue;
    if (entry.mood >= 4) high.push(minutes);
    else if (entry.mood <= 2) low.push(minutes);
  }

  if (high.length + low.length < INSIGHT_MIN.moodPairs) return null;
  if (high.length === 0 || low.length === 0) return null;

  const mean = (values: number[]) => values.reduce((sum, value) => sum + value, 0) / values.length;
  const highAvg = mean(high);
  const lowAvg = mean(low);
  const liftPercent = lowAvg === 0 ? 0 : ((highAvg - lowAvg) / lowAvg) * 100;
  if (liftPercent < INSIGHT_MIN.moodLiftPercent) return null;

  return {
    highDays: high.length,
    highAvg: Math.round(highAvg),
    lowDays: low.length,
    lowAvg: Math.round(lowAvg),
    liftPercent: Math.round(liftPercent),
  };
}

/** Tasks that keep being pushed: the honest "this needs breaking up" signal. */
export function postponeRisks(tasks: Task[], todayKey: string): Task[] {
  return tasks
    .filter(
      (task) =>
        !task.archived &&
        task.status !== 'completed' &&
        task.postponeCount >= 2 &&
        (task.dueDate === null || task.dueDate <= todayKey)
    )
    .sort((a, b) => b.postponeCount - a.postponeCount);
}

/** Goals with linked work that has not moved at all. */
export function stalledGoals(params: {
  goals: { id: string; title: string; completed: boolean }[];
  tasks: Task[];
  todayKey: string;
}): { goalId: string; title: string; done: number; total: number; stalledDays: number }[] {
  const out: { goalId: string; title: string; done: number; total: number; stalledDays: number }[] = [];
  for (const goal of params.goals) {
    if (goal.completed) continue;
    const linked = params.tasks.filter((task) => task.goalId === goal.id && !task.archived);
    if (linked.length === 0) continue;
    const done = linked.filter((task) => task.status === 'completed').length;
    if (done === linked.length) continue;
    const lastTouched = linked
      .map((task) => task.updatedAt)
      .sort()
      .reverse()[0];
    const stalledDays = lastTouched
      ? Math.max(0, diffDays(lastTouched.slice(0, 10), params.todayKey))
      : 0;
    if (stalledDays < 7) continue;
    out.push({ goalId: goal.id, title: goal.title, done, total: linked.length, stalledDays });
  }
  return out.sort((a, b) => b.stalledDays - a.stalledDays);
}

export interface InsightInput {
  sessions: FocusSession[];
  tasks: Task[];
  habits: Habit[];
  habitLogs: HabitLog[];
  journal: JournalEntry[];
  goals: { id: string; title: string; completed: boolean }[];
  todayKey: string;
}

const two = (hour: number) => String(hour).padStart(2, '0');

/**
 * All insights that are honestly supported by the data, strongest first.
 * Callers render `[0]` and use the rest only if they want a list.
 */
export function pickInsights(input: InsightInput): Insight[] {
  const insights: Insight[] = [];

  const risk = postponeRisks(input.tasks, input.todayKey);
  if (risk.length > 0) {
    const top = risk[0];
    insights.push({
      id: 'postpone',
      title:
        risk.length === 1
          ? `«${top.title}» ${top.postponeCount} بار جابه‌جا شده`
          : `${risk.length} وظیفه بارها جابه‌جا شده‌اند`,
      body: 'وظیفه‌ای که مدام عقب می‌افتد معمولاً بزرگ است، نه تنبل. بشکنش به دو زیرکار.',
      evidence: `از ${input.tasks.length} وظیفهٔ ثبت‌شده`,
      action: { label: 'دیدن وظایف', tab: 'tasks' },
      priority: 90,
    });
  }

  const atRisk = habitsAtRisk(input);
  if (atRisk.length > 0) {
    const top = atRisk[0];
    insights.push({
      id: 'habit-risk',
      title: `«${top.habit.name}» دارد سست می‌شود`,
      body: `هفتهٔ قبل ${top.before}٪ بود، این هفته ${top.now}٪. امروز یک بار ثبتش کن تا زنجیره نشکند.`,
      evidence: 'مقایسهٔ دو هفتهٔ گذشته',
      action: { label: 'دیدن عادت‌ها', tab: 'habits' },
      priority: 85,
    });
  }

  const window = bestFocusWindow(input.sessions);
  if (window) {
    insights.push({
      id: 'focus-window',
      title: `بهترین پنجرهٔ تمرکزت ${two(window.startHour)} تا ${two(window.endHour)} است`,
      body: `در این دو ساعت بیشترین دقیقه‌های واقعی را ثبت کرده‌ای. جلسهٔ بعدی را همین‌جا بگذار.`,
      evidence: `${window.sessions} جلسه · ${window.days} روز`,
      action: { label: 'شروع تمرکز', tab: 'focus' },
      priority: 70,
    });
  }

  const mood = moodFocusLink({ journal: input.journal, sessions: input.sessions });
  if (mood) {
    insights.push({
      id: 'mood-focus',
      title: `روزهای خوب تو ${mood.liftPercent}٪ تمرکز بیشتری دارند`,
      body: `میانگین ${mood.highAvg} دقیقه در روزهای با حال خوب، ${mood.lowAvg} دقیقه در روزهای بد. ژورنال یک عادت نیست؛ یک ابزار اندازه‌گیری است.`,
      evidence: `${mood.highDays} روز خوب · ${mood.lowDays} روز بد`,
      action: { label: 'دیدن تحلیل', tab: 'analytics' },
      priority: 60,
    });
  }

  const stalled = stalledGoals({ goals: input.goals, tasks: input.tasks, todayKey: input.todayKey });
  if (stalled.length > 0) {
    const top = stalled[0];
    insights.push({
      id: 'goal-stalled',
      title: `هدف «${top.title}» ${top.stalledDays} روز تکان نخورده`,
      body: `${top.done} از ${top.total} کارش انجام شده. یک کار کوچک به آن وصل کن تا دوباره راه بیفتد.`,
      evidence: 'بر اساس کارهای پیوندشده',
      action: { label: 'دیدن خانه', tab: 'dashboard' },
      priority: 50,
    });
  }

  return insights.sort((a, b) => b.priority - a.priority);
}

/**
 * When nothing is confident yet, say exactly what would make it confident.
 * This is the empty state: it teaches the data model instead of hiding it.
 */
export function nextDataStep(input: InsightInput): string {
  const sessions = usableSessions(input.sessions);
  if (sessions.length < INSIGHT_MIN.windowSessions) {
    const left = INSIGHT_MIN.windowSessions - sessions.length;
    return `${left} جلسهٔ تمرکز دیگر ثبت کن تا بهترین ساعت تمرکزت را پیدا کنم.`;
  }
  const journalled = input.journal.filter((entry) => entry.mood >= 1).length;
  if (journalled < INSIGHT_MIN.moodPairs) {
    return `${INSIGHT_MIN.moodPairs - journalled} روز ژورنال دیگر لازم است تا رابطهٔ حال و تمرکز را بسنجم.`;
  }
  return 'الان الگوی تازه‌ای نمی‌بینم؛ همین‌طور ادامه بده تا داده‌ات حرف بزند.';
}

