import type { Goal, Habit, HabitLog, Project, ProjectStage, Task, FocusSession } from '@/types/domain';
import { addDays, formatJalaaliShort } from './date';
import { categoryFocusEstimates, habitsAtRisk, bestFocusWindow, stalledGoals } from './insights';
import { buildDayPlan, measureCapacity } from './planner';
import { projectRisks } from './projects';
import { fa } from './commands';

/**
 * The manager.
 *
 * Not another dashboard. A manager does three things a list cannot:
 *   1. decides what matters today and says *why*, in one sentence;
 *   2. names what is going wrong before the person notices;
 *   3. never gives advice it cannot back with a number from the user's own rows.
 *
 * The honesty rules are part of the type system here: a recommendation without a
 * reason and a confidence is impossible to construct, and a brief with too little
 * history returns `'empty'` instead of a friendly but invented summary.
 */

export type Confidence = 'high' | 'medium' | 'low';

export interface Recommendation {
  /** The one thing to do. Imperative, short. */
  text: string;
  /** Why this and not something else, quoting real data. */
  reason: string;
  confidence: Confidence;
  /** Where the person goes to act on it. */
  tab: 'tasks' | 'projects' | 'focus' | 'habits' | 'analytics';
}

export interface DailyBrief {
  /** 'empty' means: not enough recorded history to act like a manager today. */
  verdict: 'empty' | 'clear' | 'balanced' | 'tight' | 'overloaded';
  headline: string;
  lines: string[];
  recommendation: Recommendation | null;
}

export interface WeeklyReview {
  verdict: 'empty' | 'quiet' | 'steady' | 'strong';
  headline: string;
  lines: string[];
  wins: string[];
  risks: string[];
  nextActions: string[];
}

export interface ManagerInput {
  tasks: Task[];
  habits: Habit[];
  habitLogs: HabitLog[];
  sessions: FocusSession[];
  goals: Goal[];
  projects: Project[];
  stages: ProjectStage[];
  todayKey: string;
  goalMinutes?: number;
}

const open = (task: Task) => !task.archived && task.status !== 'completed';

/* ── Today ─────────────────────────────────────────────────────────────────── */

export function buildDailyBrief(input: ManagerInput): DailyBrief {
  const { tasks, projects, stages, todayKey, sessions } = input;
  const goalMinutes = input.goalMinutes ?? 120;

  const openTasks = tasks.filter(open);
  const overdue = openTasks.filter((task) => task.dueDate !== null && task.dueDate < todayKey);
  const dueToday = openTasks.filter((task) => task.dueDate === todayKey);
  const noDate = openTasks.filter((task) => task.dueDate === null);

  if (openTasks.length === 0) {
    return {
      verdict: 'clear',
      headline: 'امروز هیچ کار بازی نداری',
      lines: ['صف خالی است. اگر کاری در ذهن داری، همین حالا بنویسش تا صف پُر بماند.'],
      recommendation: null,
    };
  }

  const capacity = measureCapacity(sessions, { todayKey, goalMinutes });
  const plan = buildDayPlan({ tasks, sessions, todayKey, goalMinutes });
  const risks = projectRisks({ projects, stages, tasks, todayKey });
  const riskyHabits = habitsAtRisk({
    habits: input.habits,
    habitLogs: input.habitLogs,
    todayKey,
  });

  const lines: string[] = [];
  // Demand, not the plan: the plan stops at capacity, so measuring the plan
  // against capacity would always look fine. Overflow is where the truth is.
  const demand =
    plan.plannedMinutes + plan.overflow.reduce((sum, block) => sum + block.minutes, 0);
  const load = demand / Math.max(capacity.minutes, 1);

  lines.push(
    capacity.unknown
      ? `ظرفیت امروز اندازه‌گیری نشده است؛ فعلاً با ${fa(capacity.minutes)} دقیقه (${fa(
          capacity.note
        )}) حساب می‌کنم.`
      : `ظرفیت امروز: ${fa(capacity.minutes)} دقیقه (${fa(capacity.note)}). کارهای امروز ${fa(
          demand
        )} دقیقه است.`
  );
  if (overdue.length > 0) {
    lines.push(
      `${fa(overdue.length)} کار موعدش گذشته است — قدیمی‌ترین: «${overdue[0].title}» از ${fa(
        formatJalaaliShort(overdue[0].dueDate!)
      )}.`
    );
  }
  if (plan.overflow.length > 0) {
    lines.push(
      `${fa(plan.overflow.length)} کار در ظرفیت امروز جا نمی‌شود؛ پنهانشان نمی‌کنم.`
    );
  }
  if (risks.length > 0) {
    lines.push(`پروژهٔ در خطر: «${risks[0].title}» — ${risks[0].reason === 'overdue' ? 'موعدش گذشته' : risks[0].reason === 'blocked' ? 'منتظر کارهای دیگر است' : 'مدتی است حرکتی نداشته'}.`);
  }
  if (riskyHabits.length > 0) {
    lines.push(`عادت در خطر: «${riskyHabits[0].habit.name}» — زنجیره‌اش شکننده است.`);
  }

  const verdict: DailyBrief['verdict'] =
    load > 1.6 ? 'overloaded' : load > 1.1 ? 'tight' : 'balanced';

  const headline =
    verdict === 'overloaded'
      ? `بار امروز از ظرفیتت بیشتر است: ${fa(demand)} دقیقه کار در ${fa(
          capacity.minutes
        )} دقیقه ظرفیت`
      : verdict === 'tight'
        ? `امروز پُر است ولی شدنی: ${fa(demand)} در ${fa(capacity.minutes)} دقیقه`
        : `${fa(plan.blocks.length)} کار در ${fa(capacity.minutes)} دقیقه ظرفیت امروز جا می‌شود`;

  return {
    verdict,
    headline,
    lines,
    recommendation: recommendNext({
      ...input,
      plan,
      overdue,
      dueToday,
      noDate,
      risks,
      riskyHabits,
      capacityUnknown: capacity.unknown,
    }),
  };
}

function recommendNext(params: {
  tasks: Task[];
  sessions: FocusSession[];
  todayKey?: string;
  plan: ReturnType<typeof buildDayPlan>;
  overdue: Task[];
  dueToday: Task[];
  noDate: Task[];
  risks: ReturnType<typeof projectRisks>;
  riskyHabits: ReturnType<typeof habitsAtRisk>;
  capacityUnknown: boolean;
}): Recommendation | null {
  const { plan, overdue, dueToday, noDate, risks, riskyHabits, tasks, sessions } = params;

  // 1) The most expensive thing that is already late.
  if (overdue.length > 0) {
    const worst = [...overdue].sort((a, b) => (b.postponeCount ?? 0) - (a.postponeCount ?? 0))[0];
    const pushed = (worst.postponeCount ?? 0) > 1;
    return {
      text: `اول «${worst.title}»؛ ${worst.estimateMin ? `${fa(worst.estimateMin)} دقیقه` : 'یک بلوک کوتاه'} برایش کنار بگذار.`,
      reason: pushed
        ? `این کار ${fa(worst.postponeCount)} بار جابه‌جا شده و موعدش ${fa(
            formatJalaaliShort(worst.dueDate!)
          )} بوده است.`
        : `موعدش ${fa(formatJalaaliShort(worst.dueDate!))} گذشته و هنوز باز است.`,
      confidence: pushed ? 'high' : 'medium',
      tab: 'tasks',
    };
  }

  // 2) A project that is late or stuck outranks an arbitrary task.
  if (risks.length > 0 && risks[0].reason !== 'blocked') {
    return {
      text: `به «${risks[0].title}» یک مرحلهٔ کوچک اضافه کن یا مرحله‌ای را ببند.`,
      reason:
        risks[0].reason === 'overdue'
          ? 'موعد این پروژه گذشته و درصد پیشرفتش حرکت نمی‌کند.'
          : 'بیش از ده روز است که در این پروژه حرکتی ثبت نشده.',
      confidence: 'medium',
      tab: 'projects',
    };
  }

  // 3) A habit about to break is cheaper to save today than to rebuild.
  if (riskyHabits.length > 0) {
    return {
      text: `«${riskyHabits[0].habit.name}» را امروز ثبت کن، حتی در کوچک‌ترین شکلش.`,
      reason: 'این عادت به آستانهٔ شکستن زنجیره رسیده است.',
      confidence: 'medium',
      tab: 'habits',
    };
  }

  // 4) Something due today that also fits the measured capacity.
  const first = plan.blocks[0];
  if (first) {
    return {
      text: `با «${first.title}» شروع کن — ${fa(first.minutes)} دقیقه.`,
      reason: first.reason,
      confidence: params.capacityUnknown ? 'low' : 'high',
      tab: 'tasks',
    };
  }

  // 5) Nothing dated: name the honest next step instead of inventing urgency.
  if (noDate.length > 0) {
    return {
      text: `«${noDate[0].title}» را برای امروز تاریخ بگذار تا از صف بی‌تاریخ بیرون بیاید.`,
      reason: `${fa(noDate.length)} کار بدون تاریخ داری؛ کار بی‌تاریخ هیچ‌وقت «امروز» نمی‌شود.`,
      confidence: 'medium',
      tab: 'tasks',
    };
  }

  // 6) No plan but there is at least one task: point at the estimate that is missing.
  const unestimated = tasks.filter(open).find((task) => !task.estimateMin);
  if (unestimated) {
    return {
      text: `برای «${unestimated.title}» یک تخمین دقیقه بگذار.`,
      reason: 'بدون تخمین، ظرفیت روز قابل محاسبه نیست و برنامه حدس می‌شود.',
      confidence: 'low',
      tab: 'tasks',
    };
  }

  if (sessions.length === 0) {
    return {
      text: 'یک جلسهٔ تمرکز ۲۵ دقیقه‌ای شروع کن.',
      reason: 'هنوز هیچ جلسه‌ای ثبت نشده؛ بدون آن ظرفیت واقعی‌ات اندازه‌گیری نمی‌شود.',
      confidence: 'low',
      tab: 'focus',
    };
  }

  if (dueToday.length > 0) {
    return {
      text: `«${dueToday[0].title}» را ببند.`,
      reason: 'موعدش امروز است و در ظرفیت امروز هم جا می‌شود.',
      confidence: 'medium',
      tab: 'tasks',
    };
  }

  return null;
}

/* ── The week ──────────────────────────────────────────────────────────────── */

function categoryLabel(category: string): string {
  const labels: Record<string, string> = {
    work: 'کار',
    personal: 'شخصی',
    study: 'مطالعه',
    health: 'سلامت',
    finance: 'مالی',
    home: 'خانه',
    social: 'اجتماعی',
  };
  return labels[category] ?? category;
}

export function buildWeeklyReview(input: ManagerInput): WeeklyReview {
  const { tasks, habitLogs, sessions, todayKey, projects, stages, goals } = input;
  const start = addDays(todayKey, -6);
  const prevStart = addDays(todayKey, -13);
  const prevEnd = addDays(todayKey, -7);

  const inWindow = (key: string | null | undefined, from: string, to: string) =>
    key !== null && key !== undefined && key >= from && key <= to;

  const doneThisWeek = tasks.filter(
    (task) =>
      task.status === 'completed' && inWindow(task.completedDate ?? null, start, todayKey)
  );
  const doneLastWeek = tasks.filter(
    (task) =>
      task.status === 'completed' && inWindow(task.completedDate ?? null, prevStart, prevEnd)
  );
  const sessionsThisWeek = sessions.filter((session) => inWindow(session.dateKey, start, todayKey));
  const sessionsLastWeek = sessions.filter((session) =>
    inWindow(session.dateKey, prevStart, prevEnd)
  );

  const focusMinutes = sessionsThisWeek.reduce((sum, s) => sum + s.durationMinutes, 0);
  const prevFocusMinutes = sessionsLastWeek.reduce((sum, s) => sum + s.durationMinutes, 0);
  const logsThisWeek = habitLogs.filter((log) => inWindow(log.dateKey, start, todayKey));

  const activeDays = new Set([
    ...doneThisWeek.map((task) => task.completedDate!),
    ...sessionsThisWeek.map((session) => session.dateKey),
    ...logsThisWeek.map((log) => log.dateKey),
  ]).size;

  if (activeDays === 0) {
    return {
      verdict: 'empty',
      headline: 'این هفته چیزی ثبت نشده',
      lines: ['داده‌ای نیست که مرور شود. یک کار ببند یا یک جلسهٔ تمرکز ثبت کن تا هفتهٔ بعد قابلِ سنجش شود.'],
      wins: [],
      risks: [],
      nextActions: ['امروز یک کار کوچک را تمام کن تا ثبت شروع شود'],
    };
  }

  const delta = prevFocusMinutes > 0 ? Math.round(((focusMinutes - prevFocusMinutes) / prevFocusMinutes) * 100) : null;
  const verdict: WeeklyReview['verdict'] =
    activeDays >= 5 && focusMinutes >= (input.goalMinutes ?? 120) * 3
      ? 'strong'
      : activeDays >= 3
        ? 'steady'
        : 'quiet';

  const lines = [
    `تمرکز: ${fa(focusMinutes)} دقیقه در ${fa(sessionsThisWeek.length)} جلسه${
      delta === null ? '' : ` (${delta > 0 ? '+' : ''}${fa(delta)}٪ نسبت به هفتهٔ قبل)`
    }`,
    `کارهای بسته‌شده: ${fa(doneThisWeek.length)}${
      doneLastWeek.length > 0 ? ` (هفتهٔ قبل ${fa(doneLastWeek.length)})` : ''
    }`,
    `روزهای فعال: ${fa(activeDays)} از ۷`,
  ];

  const window = bestFocusWindow(sessionsThisWeek.length > 0 ? sessionsThisWeek : sessions);
  if (window) {
    lines.push(
      `بهترین بازهٔ تمرکزت: ${fa(String(window.startHour))} تا ${fa(
        String(window.endHour)
      )} — میانگین ${fa(window.minutes)} دقیقه در آن دو ساعت`
    );
  }
  const estimates = categoryFocusEstimates(
    sessionsThisWeek.length > 0 ? sessionsThisWeek : sessions,
    tasks
  );
  const category = [...estimates.entries()].sort((a, b) => b[1].minutes - a[1].minutes)[0];
  if (category) {
    lines.push(
      `بیشترین وقتت رفته برای دستهٔ «${categoryLabel(category[0])}» (${fa(category[1].minutes)} دقیقه از ${fa(
        category[1].samples
      )} نمونه).`
    );
  }

  const wins: string[] = [];
  if (doneThisWeek.length > 0) wins.push(`${fa(doneThisWeek.length)} کار بسته شد`);
  if (delta !== null && delta > 0) wins.push(`تمرکز ${fa(delta)}٪ بیشتر از هفتهٔ قبل`);
  if (activeDays >= 5) wins.push('پنج روز از هفت روز فعال بود');

  const risks: string[] = [];
  const openTasks = tasks.filter(open);
  const overdue = openTasks.filter((task) => task.dueDate !== null && task.dueDate < todayKey);
  if (overdue.length > 0) risks.push(`${fa(overdue.length)} کار عقب‌افتاده روی هم انبار شده`);
  if (delta !== null && delta < -20) risks.push(`تمرکز ${fa(Math.abs(delta))}٪ افت کرده`);
  const stalled = stalledGoals({ goals, tasks, todayKey });
  if (stalled.length > 0) risks.push(`${fa(stalled.length)} هدف بی‌حرکت مانده`);
  const projectRisk = projectRisks({ projects, stages, tasks, todayKey });
  if (projectRisk.length > 0) risks.push(`${fa(projectRisk.length)} پروژه در خطر است`);
  if (activeDays <= 2) risks.push('فقط دو روز از هفته داده داشته');

  const nextActions: string[] = [];
  if (overdue.length > 0) nextActions.push(`یکی از ${fa(overdue.length)} کار عقب‌افتاده را امروز ببند`);
  if (projectRisk.length > 0) nextActions.push(`برای «${projectRisk[0].title}» یک مرحلهٔ کوچک تعریف کن`);
  if (stalled.length > 0) nextActions.push(`هدف «${stalled[0].title}» را یا شروع کن یا آرشیو`);
  if (nextActions.length === 0) nextActions.push('همین ریتم را نگه دار و یک کار متوسط اضافه کن');

  const headline =
    verdict === 'strong'
      ? 'هفتهٔ قوی بود — ریتم را نگه دار'
      : verdict === 'steady'
        ? 'هفتهٔ متوسط و پایدار'
        : 'هفتهٔ کم‌فعالیت';

  return { verdict, headline, lines, wins, risks, nextActions: nextActions.slice(0, 2) };
}
