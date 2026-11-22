import { describe, expect, it } from 'vitest';
import { buildDailyBrief, buildWeeklyReview } from '@/lib/coach';
import { addDays, getTodayKey } from '@/lib/date';
import type { FocusSession, Goal, Habit, HabitLog, Project, ProjectStage, Task } from '@/types/domain';

const today = getTodayKey();

function task(over: Partial<Task> & { id: string; title: string }): Task {
  return {
    description: '',
    dueDate: null,
    dueTime: null,
    priority: 'medium',
    quadrant: 'q2',
    category: 'work',
    tags: [],
    status: 'todo',
    subtasks: [],
    recurrence: 'none',
    reminderEnabled: false,
    reminderOffsetMin: 0,
    archived: false,
    postponeCount: 0,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    completedDate: null,
    completedAt: null,
    goalId: null,
    projectId: null,
    estimateMin: null,
    dependsOn: null,
    ...over,
  } as Task;
}

function session(dateKey: string, minutes: number, id: string): FocusSession {
  return {
    id,
    mode: 'pomodoro',
    durationMinutes: minutes,
    durationSeconds: minutes * 60,
    dateKey,
    taskId: null,
    taskTitleSnapshot: null,
    completedNaturally: true,
    startedAt: `${dateKey}T09:00:00.000Z`,
    endedAt: `${dateKey}T10:00:00.000Z`,
  } as FocusSession;
}

const base = {
  habits: [] as Habit[],
  habitLogs: [] as HabitLog[],
  goals: [] as Goal[],
  projects: [] as Project[],
  stages: [] as ProjectStage[],
  todayKey: today,
  goalMinutes: 120,
};

describe('daily brief', () => {
  it('refuses to act like a manager when the queue is empty, without inventing work', () => {
    const brief = buildDailyBrief({ ...base, tasks: [], sessions: [] });
    expect(brief.verdict).toBe('clear');
    expect(brief.recommendation).toBeNull();
    expect(brief.headline).toContain('هیچ کار بازی');
  });

  it('puts the most-postponed overdue task first and quotes its own history', () => {
    const brief = buildDailyBrief({
      ...base,
      tasks: [
        task({ id: 't1', title: 'مالیات', dueDate: addDays(today, -6), postponeCount: 4 }),
        task({ id: 't2', title: 'گزارش', dueDate: addDays(today, -1), postponeCount: 0 }),
      ],
      sessions: [session(addDays(today, -1), 60, 's1'), session(addDays(today, -2), 60, 's2')],
    });
    expect(brief.recommendation?.text).toContain('مالیات');
    expect(brief.recommendation?.reason).toContain('۴ بار');
    expect(brief.recommendation?.confidence).toBe('high');
    // Two measured days is below the threshold, so capacity must admit it.
    expect(brief.lines[0]).toContain('ظرفیت امروز');
  });

  it('calls an overloaded day overloaded instead of calling it a plan', () => {
    const tasks = Array.from({ length: 6 }, (_, index) =>
      task({ id: `t${index}`, title: `کار ${index}`, dueDate: today, estimateMin: 60 })
    );
    const brief = buildDailyBrief({
      ...base,
      tasks,
      sessions: [
        session(addDays(today, -1), 40, 'a'),
        session(addDays(today, -2), 40, 'b'),
        session(addDays(today, -3), 40, 'c'),
      ],
    });
    expect(brief.verdict).toBe('overloaded');
    expect(brief.headline).toContain('بیشتر');
    expect(brief.lines.some((line) => line.includes('جا نمی‌شود'))).toBe(true);
  });

  it('keeps its mouth shut about capacity when there is no history', () => {
    const brief = buildDailyBrief({
      ...base,
      tasks: [task({ id: 't1', title: 'تنها کار', dueDate: today })],
      sessions: [],
    });
    expect(brief.lines[0]).toContain('اندازه‌گیری نشده');
    expect(brief.recommendation?.confidence).not.toBe('high');
  });

  it('prefers a late project over an arbitrary dated task', () => {
    const project: Project = {
      id: 'p1',
      title: 'کتاب',
      description: '',
      category: 'work',
      goalId: null,
      status: 'active',
      startDate: addDays(today, -30),
      dueDate: addDays(today, -5),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    } as Project;
    const brief = buildDailyBrief({
      ...base,
      projects: [project],
      tasks: [task({ id: 't1', title: 'یک کار امروز', dueDate: today })],
      sessions: [],
    });
    expect(brief.lines.some((line) => line.includes('کتاب'))).toBe(true);
  });
});

describe('weekly review', () => {
  it('says plainly that there is nothing to review', () => {
    const review = buildWeeklyReview({ ...base, tasks: [], sessions: [] });
    expect(review.verdict).toBe('empty');
    expect(review.wins).toHaveLength(0);
    expect(review.nextActions.length).toBeGreaterThan(0);
  });

  it('compares against the previous week instead of showing a bare number', () => {
    const review = buildWeeklyReview({
      ...base,
      tasks: [
        task({
          id: 't1',
          title: 'الف',
          status: 'completed',
          completedDate: addDays(today, -1),
        }),
        task({
          id: 't2',
          title: 'ب',
          status: 'completed',
          completedDate: addDays(today, -10),
        }),
      ],
      sessions: [
        session(addDays(today, -1), 50, 's1'),
        session(addDays(today, -2), 50, 's2'),
        session(addDays(today, -3), 50, 's3'),
        session(addDays(today, -9), 30, 's4'),
      ],
    });
    expect(review.verdict).not.toBe('empty');
    expect(review.lines.some((line) => line.includes('نسبت به هفتهٔ قبل'))).toBe(true);
    expect(review.lines.some((line) => line.includes('روزهای فعال'))).toBe(true);
    expect(review.wins.length).toBeGreaterThan(0);
  });

  it('names an overloaded backlog as a risk and gives one next action', () => {
    const review = buildWeeklyReview({
      ...base,
      tasks: [
        task({ id: 't1', title: 'الف', dueDate: addDays(today, -3) }),
        task({ id: 't2', title: 'ب', dueDate: addDays(today, -2) }),
        task({ id: 't3', title: 'ج', dueDate: addDays(today, -1), status: 'completed', completedDate: today }),
      ],
      sessions: [session(today, 30, 's1')],
    });
    expect(review.risks.some((risk) => risk.includes('عقب‌افتاده'))).toBe(true);
    expect(review.nextActions.length).toBeLessThanOrEqual(2);
  });
});
