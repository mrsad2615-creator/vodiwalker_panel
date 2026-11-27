import { beforeEach, describe, expect, it } from 'vitest';
import type { Task } from '@/types/domain';
import { buildWidgetSnapshot } from '@/lib/widgetSnapshot';
import { resolveWidgetTask, widgetCreateTask, widgetDestination, widgetTaskId } from '@/components/settings/WidgetSync';
import { db, ensureSettings } from '@/lib/db';
import { ACCENT_PRESETS, contrastRatio, WIDGET_SURFACE } from '@/lib/theme';
import { detectIntent } from '@/lib/commands';
import { restorableSettings } from '@/lib/backupSettings';

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
    // Archived completed tasks remain part of historical reporting.
    expect(snapshot.monthlyDone).toEqual([2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
    expect(buildWidgetSnapshot(items, '2025-03-21', true, true).tasks[2].sample).toBe(true);
  });
  it('removes private titles completely while retaining aggregate progress', () => {
    const snapshot = buildWidgetSnapshot([task({ title: 'خیلی خصوصی' })], '2025-03-21', false, true);
    expect(JSON.stringify(snapshot)).not.toContain('خیلی خصوصی');
    expect(JSON.stringify(snapshot)).not.toContain('"id"');
    expect(snapshot.tasks).toEqual([]);
    expect(snapshot.todayTotal).toBe(1);
  });
  it('puts open work first and chooses a readable accent', () => {
    const rows = [task({ id: 'a', status: 'completed' }), task({ id: 'b', dueTime: '11:00' }), task({ id: 'c', dueTime: '09:00' })];
    for (const preset of ACCENT_PRESETS) {
      const snapshot = buildWidgetSnapshot(rows, '2025-03-21', true, true, preset.hex, 42);
      expect(snapshot.tasks.map((entry) => entry.id)).toEqual(['c', 'b', 'a']);
      expect(contrastRatio(snapshot.accent, WIDGET_SURFACE)).toBeGreaterThanOrEqual(4.5);
      expect(snapshot.updatedAt).toBe(42);
    }
  });
  it('filters widget rows without changing the full-day progress or annual totals', () => {
    const rows = [task({ id: 'done', status: 'completed', completedDate: '2025-03-21' }), task({ id: 'open' })];
    const open = buildWidgetSnapshot(rows, '2025-03-21', true, true, undefined, 42, 'open');
    expect(open.tasks.map((entry) => entry.id)).toEqual(['open']);
    expect(open.todayTotal).toBe(2);
    expect(open.todayDone).toBe(1);
    expect(open.monthlyDone[0]).toBe(1);
    expect(buildWidgetSnapshot(rows, '2025-03-21', false, true, undefined, 42, 'open').tasks).toEqual([]);
    expect(restorableSettings({ widgetTaskFilter: 'open' })).toEqual({ widgetTaskFilter: 'open' });
    expect(() => restorableSettings({ widgetTaskFilter: 'other' })).toThrow();
  });
  it('recognizes direct coaching requests without claiming a language model', () => {
    expect(detectIntent('یک پیشنهاد بده')).toBe('brief.today');
  });
  it('only routes recognized widget links, never data-changing actions', () => {
    expect(widgetCreateTask('kairos-widget://open/create-task')).toBe(true);
    expect(widgetCreateTask('kairos-widget://open/create-task?title=test')).toBe(false);
    expect(widgetDestination('kairos-widget://open/tasks')).toBe('tasks');
    expect(widgetDestination('kairos-widget://open/analytics')).toBe('analytics');
    expect(widgetDestination('kairos-widget://open/focus')).toBe('focus');
    expect(widgetDestination('kairos-widget://open/tasks?complete=1')).toBeNull();
    expect(widgetTaskId('kairos-widget://open/task/tsk-1_abc')).toBe('tsk-1_abc');
    expect(widgetTaskId('kairos-widget://open/task/tsk-1?complete=1')).toBeNull();
    expect(widgetTaskId('kairos-widget://open/task/../../settings')).toBeNull();
  });
});

describe('task widget navigation safety', () => {
  beforeEach(async () => {
    await db.tasks.clear();
    await db.settings.clear();
    await ensureSettings();
  });
  it('resolves a current visible task without changing it, and fails closed for stale or hidden items', async () => {
    const item = task({ id: 'tsk-1', sample: true });
    await db.tasks.put(item);
    expect(await resolveWidgetTask(item.id, '2025-03-21')).toEqual(item);
    expect(await resolveWidgetTask(item.id, '2025-03-22')).toBeNull();
    await db.settings.update('default', { sampleVisible: false });
    expect(await resolveWidgetTask(item.id, '2025-03-21')).toBeNull();
    await db.settings.update('default', { sampleVisible: true, widgetShowTitles: false });
    expect(await resolveWidgetTask(item.id, '2025-03-21')).toBeNull();
    await db.settings.update('default', { widgetShowTitles: true, widgetTaskFilter: 'open' });
    await db.tasks.update(item.id, { status: 'completed' });
    expect(await resolveWidgetTask(item.id, '2025-03-21')).toBeNull();
    await db.tasks.update(item.id, { status: 'todo', archived: true });
    expect(await resolveWidgetTask(item.id, '2025-03-21')).toBeNull();
    await db.tasks.delete(item.id);
    expect(await resolveWidgetTask(item.id, '2025-03-21')).toBeNull();
  });
});
