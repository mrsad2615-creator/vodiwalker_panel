import { describe, expect, it } from 'vitest';
import type { Task } from '@/types/domain';
import { buildWidgetSnapshot } from '@/lib/widgetSnapshot';
import { widgetDestination } from '@/components/settings/WidgetSync';
import { detectIntent } from '@/lib/commands';

const task = (overrides: Partial<Task>): Task => ({
  id: '1', title: 'گزارش', description: '', dueDate: '2025-03-21', dueTime: null,
  priority: 'medium', quadrant: 'q2', category: 'personal', tags: [], status: 'todo',
  subtasks: [], recurrence: 'none', reminderEnabled: false, reminderOffsetMin: 0,
  archived: false, postponeCount: 0, createdAt: '', updatedAt: '', completedDate: null, completedAt: null,
  ...overrides,
});

describe('Android widget snapshots', () => {
  it('counts real daily and solar-year completed tasks with accurate status and sample visibility', () => {
    const items = [task({}), task({ id: '2', title: 'انجام شد', status: 'completed', completedDate: '2025-03-21' }),
      task({ id: '3', title: 'نمونه', sample: true, status: 'completed', completedDate: '2025-04-21' }),
      task({ id: '4', archived: true, status: 'completed', completedDate: '2025-03-21' })];
    const snapshot = buildWidgetSnapshot(items, '2025-03-21', true, false);
    expect(snapshot.year).toBe(1404);
    expect(snapshot.todayTotal).toBe(2);
    expect(snapshot.todayDone).toBe(1);
    expect(snapshot.tasks.map((row) => row.status)).toEqual(['todo', 'completed']);
    expect(snapshot.monthlyDone).toEqual([1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
    expect(buildWidgetSnapshot(items, '2025-03-21', true, true).tasks[2].sample).toBe(true);
  });
  it('removes private titles completely while retaining aggregate progress', () => {
    const snapshot = buildWidgetSnapshot([task({ title: 'خیلی خصوصی' })], '2025-03-21', false, true);
    expect(JSON.stringify(snapshot)).not.toContain('خیلی خصوصی');
    expect(snapshot.tasks).toEqual([]);
    expect(snapshot.todayTotal).toBe(1);
  });
  it('recognizes direct coaching requests without claiming a language model', () => {
    expect(detectIntent('یک پیشنهاد بده')).toBe('brief.today');
  });
  it('only routes recognized widget links, never data-changing actions', () => {
    expect(widgetDestination('kairos-widget://open/tasks')).toBe('tasks');
    expect(widgetDestination('kairos-widget://open/analytics')).toBe('analytics');
    expect(widgetDestination('kairos-widget://open/focus')).toBe('focus');
    expect(widgetDestination('kairos-widget://open/tasks?complete=1')).toBeNull();
  });
});
