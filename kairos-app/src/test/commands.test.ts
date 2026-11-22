import { describe, expect, it } from 'vitest';
import { detectIntent, normalizeCommand, QUICK_COMMANDS, runCommand, unknownAnswer } from '@/lib/commands';
import type { CommandContext } from '@/lib/commands';
import type { Habit, HabitLog, Project, Task } from '@/types/domain';

/**
 * The assistant has no model, so it has no excuse to guess. These tests pin the
 * two halves of that promise: a recognised Persian sentence gets an answer built
 * from real rows, and an unrecognised one gets an honest "نمیدانم" with the list
 * of what it understands — never a plausible-sounding invention.
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

const baseContext: CommandContext = {
  tasks: [],
  habits: [],
  habitLogs: [],
  goals: [],
  projects: [],
  stages: [],
  series: Array.from({ length: 14 }, (_, index) => ({
    dateKey: `2025-06-${String(index + 7).padStart(2, '0')}`,
    focusMinutes: index >= 7 ? 30 : 0,
    tasksDone: 0,
    habitsDone: 0,
    activity: 0,
  })),
  todayKey: TODAY,
};

describe('normalisation', () => {
  it('treats Arabic Yeh/Kaf, ZWNJ and spacing as the same sentence', () => {
    expect(normalizeCommand('وظايف امروز')).toBe('وظایف امروز');
    expect(normalizeCommand('وظایف‌امروز')).toBe('وظایف امروز');
    expect(normalizeCommand('  کارهای   امروز؟ ')).toBe('کارهای امروز');
  });
});

describe('intent detection', () => {
  it('recognises every quick command', () => {
    const intents = QUICK_COMMANDS.map((command) => detectIntent(command));
    expect(intents).not.toContain('help');
    expect(new Set(intents).size).toBeGreaterThanOrEqual(5);
  });

  it('reads "چرا" as a request for a reason before anything else', () => {
    expect(detectIntent('چرا این پیشنهاد را دادی؟')).toBe('why.suggestion');
  });
});

describe('answers from real rows', () => {
  it('lists today’s open work with its numbers', () => {
    const answer = runCommand('وظایف امروز من چیست؟', {
      ...baseContext,
      tasks: [
        task({ title: 'گزارش', dueDate: TODAY, dueTime: '10:00' }),
        task({ title: 'مالیات', dueDate: '2025-06-15' }),
        task({ title: 'تمام‌شده', status: 'completed' }),
      ],
    });

    expect(answer.intent).toBe('today.tasks');
    expect(answer.title).toContain('۲');
    expect(answer.lines.join(' ')).toContain('گزارش');
    expect(answer.lines.join(' ')).toContain('عقب‌افتاده');
  });

  it('says plainly when there is nothing overdue, instead of inventing urgency', () => {
    const answer = runCommand('کدام کارها عقب افتاده؟', baseContext);
    expect(answer.intent).toBe('overdue.tasks');
    expect(answer.title).toContain('عقب نیفتاده');
  });

  it('answers the weekly focus report from the series', () => {
    const answer = runCommand('گزارش تمرکز این هفته را نشان بده', baseContext);
    expect(answer.intent).toBe('focus.week');
    expect(answer.title).toContain('۲۱۰');
    expect(answer.lines.join(' ')).toContain('۷ روز');
  });

  it('reports habit completion for today only', () => {
    const habit = {
      id: 'h1',
      name: 'کتاب',
      archived: false,
    } as Habit;
    const logs = [
      { id: 'l1', habitId: 'h1', dateKey: TODAY, status: 'completed', value: 1 },
      { id: 'l2', habitId: 'h1', dateKey: '2025-06-19', status: 'completed', value: 1 },
    ] as HabitLog[];

    const answer = runCommand('وضعیت عادت‌ها', {
      ...baseContext,
      habits: [habit],
      habitLogs: logs,
    });
    expect(answer.title).toContain('۱ از ۱');
    expect(answer.lines.join(' ')).toContain('ثبت شد');
  });

  it('explains a suggestion when asked, and admits when there is none', () => {
    const withReason = runCommand('چرا این پیشنهاد را دادی؟', {
      ...baseContext,
      lastSuggestionReason: '«مالیات» سه بار جابه‌جا شده است.',
    });
    expect(withReason.title).toBe('دلیل پیشنهاد');
    expect(withReason.lines[0]).toContain('مالیات');

    const without = runCommand('چرا این پیشنهاد را دادی؟', baseContext);
    expect(without.title).toContain('پیشنهاد تازه‌ای');
  });

  it('names projects that are drifting', () => {
    const project = {
      id: 'p1',
      title: 'کتاب',
      status: 'active',
      dueDate: '2025-06-01',
      updatedAt: '2025-06-18T00:00:00.000Z',
    } as Project;

    const answer = runCommand('کدام پروژه عقب افتاده؟', {
      ...baseContext,
      projects: [project],
      tasks: [task({ projectId: 'p1' })],
    });
    expect(answer.intent).toBe('projects.risk');
    expect(answer.lines.join(' ')).toContain('کتاب');
    expect(answer.lines.join(' ')).toContain('روز');
  });
});

describe('honesty', () => {
  it('does not pretend to understand an unknown sentence', () => {
    const answer = runCommand('هوا امروز چطور است', baseContext);
    expect(answer.unknown).toBe(true);
    expect(answer.title).toContain('نمی‌شناسم');
    expect(answer.lines.join(' ')).toContain('این‌ها را می‌فهمم');
  });

  it('matches the standalone unknown answer', () => {
    expect(runCommand('یک جملهٔ بی‌ربط', baseContext).title).toBe(unknownAnswer().title);
  });

  it('never returns an empty answer for any quick command', () => {
    for (const command of QUICK_COMMANDS) {
      const answer = runCommand(command, baseContext);
      expect(answer.title.length).toBeGreaterThan(3);
      expect(answer.lines.length).toBeGreaterThan(0);
      expect(answer.lines.every((line) => line.trim().length > 0)).toBe(true);
    }
  });
});
