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

describe('the manager layer', () => {
  it('answers "بریف امروز" with a decision, a reason and a confidence', () => {
    const answer = runCommand('بریف امروز', {
      ...baseContext,
      tasks: [
        task({ title: 'مالیات', dueDate: '2025-06-14', postponeCount: 3 }),
        task({ title: 'گزارش', dueDate: TODAY }),
      ],
    });
    expect(answer.intent).toBe('brief.today');
    expect(answer.lines.some((line) => line.includes('مالیات'))).toBe(true);
    expect(answer.lines.some((line) => line.includes('دلیل:'))).toBe(true);
    expect(answer.lines.some((line) => line.includes('قواعد محلی'))).toBe(true);
    expect(answer.lines.some((line) => line.includes('اطمینان:'))).toBe(false);
    // Numbers are Persian, like the rest of the product.
    expect(answer.title).not.toMatch(/[0-9]/);
  });

  it('reviews the week without inventing one when nothing was recorded', () => {
    const answer = runCommand('این هفته چطور بودم؟', baseContext);
    expect(answer.intent).toBe('review.week');
    expect(answer.title).toContain('ثبت نشده');
    expect(answer.lines.some((line) => line.includes('قدم بعد'))).toBe(true);
  });

  it('finds a project by its name instead of only listing risks', () => {
    const project: Project = {
      id: 'p1',
      title: 'کتاب چاپ اول',
      description: '',
      category: 'work',
      goalId: null,
      status: 'active',
      startDate: '2025-06-01',
      dueDate: null,
      createdAt: '2025-06-01T00:00:00.000Z',
      updatedAt: '2025-06-01T00:00:00.000Z',
    } as Project;
    const answer = runCommand('وضعیت پروژه کتاب چاپ اول', {
      ...baseContext,
      projects: [project],
      stages: [
        {
          id: 's1',
          projectId: 'p1',
          title: 'فصل اول',
          kind: 'stage',
          order: 0,
          weight: 1,
          done: true,
          dueDate: null,
          completedAt: '2025-06-10T00:00:00.000Z',
          createdAt: '2025-06-01T00:00:00.000Z',
          updatedAt: '2025-06-10T00:00:00.000Z',
        },
      ],
    });
    expect(answer.intent).toBe('project.status');
    expect(answer.title).toContain('کتاب چاپ اول');
    expect(answer.title).toContain('۱۰۰');
  });

  it('explains its own recommendation when asked "چرا" with nothing on screen', () => {
    const answer = runCommand('چرا؟', {
      ...baseContext,
      tasks: [task({ title: 'مالیات', dueDate: '2025-06-14', postponeCount: 2 })],
    });
    expect(answer.intent).toBe('why.suggestion');
    expect(answer.lines[0]).toContain('جابه‌جا');
  });
});

describe('offline small talk', () => {
  it('answers a greeting instead of claiming not to understand it', () => {
    const answer = runCommand('سلام', baseContext);
    expect(answer.intent).toBe('smalltalk.greeting');
    expect(answer.title).toContain('سلام');
    expect(answer.unknown).toBeUndefined();
  });

  it('says which day it is, in Persian digits, from the real calendar', () => {
    const answer = runCommand('امروز چند شنبه است؟', baseContext);
    expect(answer.intent).toBe('smalltalk.date');
    expect(answer.lines[0]).toMatch(/شنبه|یکشنبه|دوشنبه|سه‌شنبه|چهارشنبه|پنجشنبه|جمعه/);
    expect(answer.lines[0]).not.toMatch(/[0-9]/);
  });

  it('reads the clock in Persian digits', () => {
    const answer = runCommand('ساعت چند است؟', baseContext);
    expect(answer.intent).toBe('smalltalk.time');
    expect(answer.lines[0]).not.toMatch(/[0-9]/);
  });

  it('does not let a greeting steal a real question', () => {
    const answer = runCommand('سلام، وظایف امروز چیه؟', {
      ...baseContext,
      tasks: [task({ title: 'مالیات', dueDate: TODAY })],
    });
    expect(answer.intent).toBe('today.tasks');
    expect(answer.lines.join(' ')).toContain('مالیات');
  });

  it('still says "I do not know" for something genuinely outside its world', () => {
    const answer = runCommand('قیمت بیت‌کوین چنده؟', baseContext);
    expect(answer.intent).toBe('help');
    expect(answer.unknown).toBe(true);
  });
});
