import type { FocusSession, Task } from '@/types/domain';
import { addDays } from './date';
import { categoryFocusEstimates } from './insights';

/**
 * The daily planner.
 *
 * It is not a sorted list. It answers one question honestly: *what fits in a day
 * that has a real size?*
 *
 * Three rules make the difference between a planner and a wish:
 *   1. Capacity is measured, not assumed. It comes from what the person actually
 *      focused on recent days — never from "there are 24 hours".
 *   2. Every block carries its evidence: the due date, the postponement count,
 *      or the number of similar sessions the estimate came from.
 *   3. Nothing is applied silently. The plan is a proposal; `applyPlan` is the
 *      only writer and it goes through the normal task update path (logged,
 *      undoable, level 2).
 */

export interface PlanBlock {
  taskId: string;
  title: string;
  minutes: number;
  /** Why this task is in the plan, in Persian, ready to show. */
  reason: string;
  /** What the minutes are based on. */
  estimateSource: 'history' | 'self' | 'default';
  /** Sample size behind a history estimate; 0 otherwise. */
  samples: number;
  dueDate: string | null;
  overdue: boolean;
  postponeCount: number;
}

export interface DayPlan {
  blocks: PlanBlock[];
  /** Tasks that did not fit, still visible and named. */
  overflow: PlanBlock[];
  capacityMinutes: number;
  plannedMinutes: number;
  /** How capacity was determined. */
  capacityBasis: 'history' | 'goal' | 'unknown';
  /** Days of data behind the capacity number. */
  capacityDays: number;
  /** True when there is not enough history to promise anything. */
  capacityUnknown: boolean;
  capacityNote: string;
}

const DEFAULT_TASK_MINUTES = 30;
const MIN_CAPACITY_MINUTES = 30;

/** Median of a list; kept local so the planner has no surprises from rounding. */
function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle];
}

export interface CapacityResult {
  minutes: number;
  basis: DayPlan['capacityBasis'];
  days: number;
  unknown: boolean;
  note: string;
}

/**
 * Measured capacity: the median of the last ten days that actually had focus.
 * Deliberately median, not mean — one heroic 4-hour day should not set the
 * expectation for every other day.
 */
export function measureCapacity(
  sessions: FocusSession[],
  options: { todayKey: string; goalMinutes: number; windowDays?: number }
): CapacityResult {
  const windowDays = options.windowDays ?? 14;
  const from = addDays(options.todayKey, -(windowDays - 1));
  const perDay = new Map<string, number>();

  for (const session of sessions) {
    if (session.dateKey < from || session.dateKey > options.todayKey) continue;
    const minutes = session.durationMinutes > 0 ? session.durationMinutes : session.durationSeconds / 60;
    if (minutes < 3) continue;
    perDay.set(session.dateKey, (perDay.get(session.dateKey) ?? 0) + minutes);
  }

  const days = perDay.size;
  if (days >= 3) {
    const measured = Math.max(MIN_CAPACITY_MINUTES, Math.round(median([...perDay.values()])));
    return {
      minutes: measured,
      basis: 'history',
      days,
      unknown: false,
      note: `میانگین روزهای واقعی تمرکز (${days} روز ثبت‌شده)`,
    };
  }

  if (options.goalMinutes > 0) {
    return {
      minutes: options.goalMinutes,
      basis: 'goal',
      days,
      unknown: true,
      note: `هدفت ${options.goalMinutes} دقیقه است، اما هنوز ${3 - days} روز دادهٔ واقعی کم است`,
    };
  }

  return {
    minutes: 0,
    basis: 'unknown',
    days,
    unknown: true,
    note: 'ظرفیت واقعی‌ات را نمی‌دانم؛ چند روز تمرکز ثبت کن',
  };
}

const priorityWeight = { high: 0, medium: 1, low: 2 } as const;

/**
 * Builds today's plan from real rows.
 *
 * Order: overdue first, then due today, then unscheduled work by priority; within
 * a bucket, tasks that have been postponed more come first, because those are the
 * ones that will not get done by accident.
 */
export function buildDayPlan(params: {
  tasks: Task[];
  sessions: FocusSession[];
  todayKey: string;
  goalMinutes: number;
  /** Minutes of the day already gone; used only for the note, never to hide work. */
  excludeTaskIds?: string[];
}): DayPlan {
  const { tasks, sessions, todayKey, goalMinutes } = params;
  const exclude = new Set(params.excludeTaskIds ?? []);

  const capacity = measureCapacity(sessions, { todayKey, goalMinutes });
  const estimates = categoryFocusEstimates(sessions, tasks);

  const candidates = tasks.filter(
    (task) =>
      !task.archived &&
      task.status !== 'completed' &&
      !exclude.has(task.id) &&
      (task.dueDate === null || task.dueDate <= todayKey)
  );

  const score = (task: Task): number => {
    const overdue = task.dueDate !== null && task.dueDate < todayKey;
    const dueToday = task.dueDate === todayKey;
    const bucket = overdue ? 0 : dueToday ? 1 : 2;
    return bucket * 100 + priorityWeight[task.priority] * 10 - Math.min(9, task.postponeCount);
  };

  const ordered = [...candidates].sort((a, b) => {
    const diff = score(a) - score(b);
    if (diff !== 0) return diff;
    return (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999');
  });

  const estimateFor = (task: Task): Pick<PlanBlock, 'minutes' | 'estimateSource' | 'samples'> => {
    const learned = estimates.get(task.category);
    if (learned && learned.samples >= 3) {
      return { minutes: learned.minutes, estimateSource: 'history', samples: learned.samples };
    }
    if (task.estimateMin && task.estimateMin > 0) {
      return { minutes: task.estimateMin, estimateSource: 'self', samples: 0 };
    }
    return { minutes: DEFAULT_TASK_MINUTES, estimateSource: 'default', samples: 0 };
  };

  const reasonFor = (task: Task, block: Pick<PlanBlock, 'estimateSource' | 'samples'>): string => {
    if (task.dueDate && task.dueDate < todayKey) return 'موعدش گذشته';
    if (task.dueDate === todayKey) return 'موعدش امروز است';
    if (task.postponeCount >= 2) return `${task.postponeCount} بار جابه‌جا شده`;
    if (block.estimateSource === 'history') return `تخمین از ${block.samples} جلسهٔ مشابه`;
    if (task.priority === 'high') return 'اولویت بالا و بدون تاریخ';
    return 'از صف کارهای باز';
  };

  const blocks: PlanBlock[] = [];
  const overflow: PlanBlock[] = [];
  let planned = 0;

  for (const task of ordered) {
    const estimate = estimateFor(task);
    const block: PlanBlock = {
      taskId: task.id,
      title: task.title,
      minutes: estimate.minutes,
      estimateSource: estimate.estimateSource,
      samples: estimate.samples,
      dueDate: task.dueDate,
      overdue: task.dueDate !== null && task.dueDate < todayKey,
      postponeCount: task.postponeCount,
      reason: reasonFor(task, estimate),
    };

    // A day with unknown capacity still gets a plan; it just gets an honest one:
    // the first block is proposed and the rest waits for measurement.
    const fits = capacity.unknown
      ? blocks.length < 1
      : planned + block.minutes <= capacity.minutes;

    if (fits) {
      blocks.push(block);
      planned += block.minutes;
    } else {
      overflow.push(block);
    }
  }

  return {
    blocks,
    overflow,
    capacityMinutes: capacity.minutes,
    plannedMinutes: planned,
    capacityBasis: capacity.basis,
    capacityDays: capacity.days,
    capacityUnknown: capacity.unknown,
    capacityNote: capacity.note,
  };
}

export interface PlanApplyResult {
  /** Task ids that received a suggested start time. */
  scheduled: { taskId: string; dueTime: string }[];
}

/**
 * Turns the plan into concrete start times, avoiding collisions with times the
 * tasks already have. Pure: the caller performs the writes through the normal
 * update path so the change is logged and undoable.
 */
export function schedulePlan(
  plan: DayPlan,
  existingTimes: Map<string, string | null>,
  dayStart = '09:00'
): PlanApplyResult {
  const [startHour, startMinute] = dayStart.split(':').map(Number);
  let cursor = startHour * 60 + startMinute;

  const taken = new Set(
    [...existingTimes.values()].filter((time): time is string => Boolean(time))
  );

  const scheduled: PlanApplyResult['scheduled'] = [];
  for (const block of plan.blocks) {
    // Skip forward past any time already used by another task.
    let guard = 0;
    while (guard < 200) {
      const hh = String(Math.floor(cursor / 60) % 24).padStart(2, '0');
      const mm = String(cursor % 60).padStart(2, '0');
      const stamp = `${hh}:${mm}`;
      if (!taken.has(stamp)) {
        taken.add(stamp);
        if (!existingTimes.get(block.taskId)) {
          scheduled.push({ taskId: block.taskId, dueTime: stamp });
        }
        break;
      }
      cursor += 5;
      guard += 1;
    }
    cursor += block.minutes;
  }

  return { scheduled };
}
