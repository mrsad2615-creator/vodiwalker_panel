import { describe, expect, it } from 'vitest';
import { buildDayPlan, measureCapacity, schedulePlan } from '@/lib/planner';
import { blockedTasks, computeProjectProgress, projectRisks } from '@/lib/projects';
import type { FocusSession, Project, ProjectStage, Task } from '@/types/domain';

/**
 * These three engines are the part of the product that gives advice, so the tests
 * are written around the danger: advice that is confidently wrong.
 *   · capacity must come from measured days, never from the clock;
 *   · a project at 60% must mean something specific, not "some tasks are done";
 *   · blocked work must be named as blocked, not quietly reordered.
 */

const TODAY = '2025-06-20';

function task(overrides: Partial<Task>): Task {
  return {
    id: `t-${Math.random().toString(36).slice(2, 8)}`,
    title: 'کار',
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
    goalId: null,
    projectId: null,
    estimateMin: null,
    dependsOn: [],
    reminderEnabled: false,
    reminderOffsetMin: 0,
    archived: false,
    postponeCount: 0,
    createdAt: '2025-06-01T00:00:00.000Z',
    updatedAt: '2025-06-01T00:00:00.000Z',
    completedDate: null,
    completedAt: null,
    ...overrides,
  } as Task;
}

function session(dateKey: string, minutes: number): FocusSession {
  return {
    id: `s-${dateKey}-${Math.random().toString(36).slice(2, 6)}`,
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

function project(overrides: Partial<Project>): Project {
  return {
    id: 'prj-1',
    goalId: null,
    title: 'پروژه',
    description: '',
    category: 'work',
    status: 'active',
    startDate: null,
    dueDate: null,
    createdAt: '2025-06-01T00:00:00.000Z',
    updatedAt: '2025-06-18T00:00:00.000Z',
    ...overrides,
  } as Project;
}

function stage(overrides: Partial<ProjectStage>): ProjectStage {
  return {
    id: `stg-${Math.random().toString(36).slice(2, 7)}`,
    projectId: 'prj-1',
    title: 'مرحله',
    kind: 'stage',
    order: 0,
    weight: 1,
    done: false,
    dueDate: null,
    completedAt: null,
    createdAt: '2025-06-01T00:00:00.000Z',
    updatedAt: '2025-06-01T00:00:00.000Z',
    ...overrides,
  } as ProjectStage;
}

describe('measured capacity', () => {
  it('refuses to promise a day without three measured days', () => {
    const sessions = [session('2025-06-19', 60), session('2025-06-18', 45)];
    const capacity = measureCapacity(sessions, { todayKey: TODAY, goalMinutes: 120 });
    expect(capacity.basis).toBe('goal');
    expect(capacity.unknown).toBe(true);
    expect(capacity.note).toContain('دادهٔ واقعی');
  });

  it('uses the median of real days, not the mean', () => {
    // Three normal days and one heroic day: the hero must not set the plan.
    const sessions = [
      session('2025-06-14', 40),
      session('2025-06-15', 50),
      session('2025-06-16', 60),
      session('2025-06-17', 300),
    ];
    const capacity = measureCapacity(sessions, { todayKey: TODAY, goalMinutes: 120 });
    expect(capacity.basis).toBe('history');
    expect(capacity.unknown).toBe(false);
    expect(capacity.minutes).toBe(55);
    expect(capacity.days).toBe(4);
  });

  it('ignores days outside the window and sub-3-minute taps', () => {
    const sessions = [
      session('2025-05-01', 90),
      session('2025-06-19', 2),
      session('2025-06-18', 30),
      session('2025-06-17', 30),
      session('2025-06-16', 30),
    ];
    const capacity = measureCapacity(sessions, { todayKey: TODAY, goalMinutes: 120 });
    expect(capacity.days).toBe(3);
    expect(capacity.minutes).toBe(30);
  });
});

describe('day plan', () => {
  const sessions = [
    session('2025-06-17', 60),
    session('2025-06-18', 60),
    session('2025-06-19', 60),
  ];

  it('puts overdue work first and explains every block', () => {
    const overdue = task({ title: 'مالیات', dueDate: '2025-06-10', priority: 'low' });
    const today = task({ title: 'گزارش', dueDate: TODAY, priority: 'medium' });
    const open = task({ title: 'ایده', priority: 'high' });

    const plan = buildDayPlan({
      tasks: [open, today, overdue],
      sessions,
      todayKey: TODAY,
      goalMinutes: 120,
    });

    expect(plan.blocks[0].taskId).toBe(overdue.id);
    expect(plan.blocks[0].reason).toBe('موعدش گذشته');
    expect(plan.blocks.every((block) => block.reason.length > 3)).toBe(true);
    expect(plan.capacityMinutes).toBe(60);
  });

  it('keeps what does not fit visible instead of dropping it', () => {
    const many = Array.from({ length: 6 }, (_, index) =>
      task({ title: `کار ${index}`, dueDate: TODAY, estimateMin: 40 })
    );
    const plan = buildDayPlan({ tasks: many, sessions, todayKey: TODAY, goalMinutes: 120 });

    expect(plan.blocks).toHaveLength(1); // 60 measured minutes, 40 each → one block
    expect(plan.overflow).toHaveLength(5);
    expect(plan.plannedMinutes).toBe(40);
  });

  it('proposes exactly one block when capacity is unknown, and says so', () => {
    const many = Array.from({ length: 3 }, (_, index) => task({ title: `کار ${index}` }));
    const plan = buildDayPlan({ tasks: many, sessions: [], todayKey: TODAY, goalMinutes: 0 });

    expect(plan.capacityUnknown).toBe(true);
    expect(plan.blocks).toHaveLength(1);
    expect(plan.overflow).toHaveLength(2);
  });

  it('prefers a history estimate over the self estimate, and names the sample', () => {
    const target = task({ id: 't-learn', title: 'کد', category: 'work', estimateMin: 15 });
    const learnedSessions = [
      { ...session('2025-06-17', 50), taskId: 't-learn' },
      { ...session('2025-06-18', 50), taskId: 't-learn' },
      { ...session('2025-06-19', 50), taskId: 't-learn' },
    ];
    const plan = buildDayPlan({
      tasks: [target],
      sessions: learnedSessions,
      todayKey: TODAY,
      goalMinutes: 120,
    });

    expect(plan.blocks[0].minutes).toBe(50);
    expect(plan.blocks[0].estimateSource).toBe('history');
    expect(plan.blocks[0].samples).toBe(3);
  });

  it('schedules blocks into free times without colliding', () => {
    const a = task({ id: 'a', title: 'الف', dueDate: TODAY, estimateMin: 40 });
    const b = task({ id: 'b', title: 'ب', dueDate: TODAY, estimateMin: 40 });
    const plan = buildDayPlan({
      tasks: [a, b],
      sessions: [session('2025-06-19', 120)],
      todayKey: TODAY,
      goalMinutes: 120,
    });

    const busy = new Map<string, string | null>([['x', '09:00']]);
    const result = schedulePlan(plan, busy, '09:00');
    expect(result.scheduled[0]).toEqual({ taskId: plan.blocks[0].taskId, dueTime: '09:05' });
  });
});

describe('project progress', () => {
  it('is weighted by stages, not by task count alone', () => {
    const stages = [
      stage({ id: 's1', weight: 3, done: true }),
      stage({ id: 's2', weight: 1, done: false }),
    ];
    // One important stage done out of two, and one tiny task out of two.
    const tasks = [
      task({ projectId: 'prj-1', status: 'completed' }),
      task({ projectId: 'prj-1', status: 'todo' }),
    ];
    const progress = computeProjectProgress(stages, tasks);

    expect(progress.basis).toBe('stages+tasks');
    // stages: 3/4 = 0.75 → 60% share = 0.45 · tasks: 0.5 → 40% share = 0.20 → 65%
    expect(progress.percent).toBe(65);
    expect(progress.stagesDone).toBe(1);
    expect(progress.tasksDone).toBe(1);
  });

  it('says "none" rather than inventing a percentage', () => {
    const progress = computeProjectProgress([], []);
    expect(progress.basis).toBe('none');
    expect(progress.percent).toBe(0);
  });

  it('ignores archived tasks', () => {
    const tasks = [
      task({ projectId: 'prj-1', status: 'completed' }),
      task({ projectId: 'prj-1', status: 'todo', archived: true }),
    ];
    const progress = computeProjectProgress([], tasks);
    expect(progress.tasksTotal).toBe(1);
    expect(progress.percent).toBe(100);
  });
});

describe('project risk and blockers', () => {
  it('flags an overdue project with its own reason', () => {
    const p = project({ id: 'prj-late', dueDate: '2025-06-10' });
    const risks = projectRisks({
      projects: [p],
      stages: [],
      tasks: [task({ projectId: 'prj-late', status: 'todo' })],
      todayKey: TODAY,
    });
    expect(risks).toHaveLength(1);
    expect(risks[0].reason).toBe('overdue');
    expect(risks[0].days).toBe(10);
  });

  it('flags a project whose open work is entirely blocked', () => {
    const dep = task({ id: 'dep', status: 'todo' });
    const blocked = task({ projectId: 'prj-b', dependsOn: ['dep'] });
    const risks = projectRisks({
      projects: [project({ id: 'prj-b' })],
      stages: [],
      tasks: [dep, blocked],
      todayKey: TODAY,
    });
    const blockedRisk = risks.find((risk) => risk.projectId === 'prj-b');
    expect(blockedRisk?.reason).toBe('blocked');
  });

  it('does not call a finished dependency a block', () => {
    const done = task({ id: 'done', status: 'completed' });
    const next = task({ dependsOn: ['done'] });
    expect(blockedTasks([done, next])).toHaveLength(0);
  });

  it('names what a blocked task is waiting on', () => {
    const dep = task({ id: 'dep-1', title: 'قرارداد' });
    const waiting = task({ title: 'شروع کار', dependsOn: ['dep-1'] });
    const result = blockedTasks([dep, waiting]);
    expect(result).toHaveLength(1);
    expect(result[0].waitingOn[0].title).toBe('قرارداد');
  });
});
