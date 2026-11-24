import { beforeEach, describe, expect, it } from 'vitest';
import { db, exportBackup, importBackup, upsertJournalEntry, deleteJournalEntry } from '@/lib/db';
import { computeJournalStats } from '@/lib/journal';
import { importCalendarEvents, planCalendarImport } from '@/lib/calendarImport';
import { tasksToCsv, habitLogsToCsv } from '@/lib/csv';
import { addDays, getTodayKey } from '@/lib/date';
import type { JournalEntry, Task } from '@/types/domain';

const today = getTodayKey();

const emptyTask = (partial: Partial<Task>): Task => ({
  id: partial.id ?? 't1',
  title: partial.title ?? 'کار',
  description: '',
  dueDate: partial.dueDate ?? null,
  dueTime: partial.dueTime ?? null,
  priority: 'medium',
  quadrant: 'q2',
  category: 'personal',
  tags: [],
  status: 'todo',
  subtasks: [],
  recurrence: 'none',
  reminderEnabled: false,
  reminderOffsetMin: 0,
  archived: false,
  postponeCount: 0,
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
  completedDate: null,
  completedAt: null,
  ...partial,
} as Task);

const entryOn = (dateKey: string, mood: number): JournalEntry => ({
  id: `journal-${dateKey}`,
  dateKey,
  mood,
  note: '',
  highlight: '',
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z',
});

describe('journal — یک روز، یک ردیف', () => {
  beforeEach(async () => {
    await db.transaction('rw', [db.journal], async () => {
      await db.journal.clear();
    });
  });

  it('edits today instead of stacking a second entry', async () => {
    await upsertJournalEntry(today, { mood: 2, note: 'روز شلوغ' });
    const second = await upsertJournalEntry(today, { mood: 4, highlight: 'گزارش تمام شد' });

    const rows = await db.journal.toArray();
    expect(rows).toHaveLength(1);
    // The patch merges: the note written first survives the later mood.
    expect(second.mood).toBe(4);
    expect(second.note).toBe('روز شلوغ');
    expect(second.highlight).toBe('گزارش تمام شد');
  });

  it('deletes a day cleanly', async () => {
    await upsertJournalEntry(today, { mood: 3 });
    await deleteJournalEntry(today);
    expect(await db.journal.count()).toBe(0);
  });

  it('counts a streak that survives an unwritten today', () => {
    const entries = [entryOn(addDays(today, -1), 4), entryOn(addDays(today, -2), 3)];
    expect(computeJournalStats(entries, today).streak).toBe(2);

    const withToday = [...entries, entryOn(today, 5)];
    expect(computeJournalStats(withToday, today).streak).toBe(3);

    // A gap breaks the chain honestly.
    const gapped = [entryOn(today, 5), entryOn(addDays(today, -2), 3)];
    expect(computeJournalStats(gapped, today).streak).toBe(1);
  });

  it('averages only the days that exist', () => {
    const entries = [entryOn(today, 5), entryOn(addDays(today, -3), 3), entryOn(addDays(today, -20), 1)];
    const stats = computeJournalStats(entries, today);
    expect(stats.weekAverage).toBe(4); // (5 + 3) / 2 — the old day is outside the week
    expect(stats.todayMood).toBe(5);
    expect(stats.weekWritten.filter(Boolean)).toHaveLength(2);
    expect(computeJournalStats([], today).weekAverage).toBeNull();
  });

  it('travels inside the backup and comes back', async () => {
    await upsertJournalEntry(today, { mood: 4, highlight: 'یک روز خوب' });
    const backup = await exportBackup();
    expect(backup.journal).toHaveLength(1);

    await deleteJournalEntry(today);
    expect(await db.journal.count()).toBe(0);

    await importBackup(backup);
    const restored = await db.journal.get(`journal-${today}`);
    expect(restored?.highlight).toBe('یک روز خوب');
  });
});

describe('calendar import — از فایل ics تا وظیفه', () => {
  const events = [
    { uid: 'a', title: 'جلسه', dateKey: addDays(today, 1), startTime: '09:00', endTime: null, allDay: false, location: '', description: '' },
    { uid: 'b', title: 'سفر', dateKey: addDays(today, 3), startTime: null, endTime: null, allDay: true, location: '', description: '' },
    { uid: 'c', title: 'گذشته', dateKey: addDays(today, -10), startTime: null, endTime: null, allDay: true, location: '', description: '' },
  ];

  it('deduplicates simultaneous imports even with stale UI snapshots', async () => {
    const [first, second] = await Promise.all([
      importCalendarEvents(events, [], today),
      importCalendarEvents(events, [], today),
    ]);
    expect(first.toCreate.length + second.toCreate.length).toBe(2);
    expect(await db.tasks.count()).toBe(2);
  });

  it('skips the past, mirrors times, and never imports twice', () => {
    const first = planCalendarImport(events, [], today);
    expect(first.toCreate).toHaveLength(2);
    expect(first.skippedPast).toBe(1);

    const existing = [
      emptyTask({ id: 'x', title: 'جلسه', dueDate: addDays(today, 1), dueTime: '09:00' }),
    ];
    const second = planCalendarImport(events, existing, today);
    expect(second.toCreate).toHaveLength(1);
    expect(second.duplicates).toBe(1);
    expect(second.toCreate[0].event.title).toBe('سفر');
  });
});

describe('csv — دادهٔ کاربر، قابل خواندن در اکسل', () => {
  it('writes a header, quotes commas, and starts with a BOM', () => {
    const csv = tasksToCsv([
      emptyTask({ id: 't1', title: 'خرید, شیر و نان', dueDate: '2026-11-20', dueTime: '18:00' }),
    ]);
    expect(csv.startsWith('\uFEFF')).toBe(true);
    expect(csv).toContain('id,title,status,due_date');
    expect(csv).toContain('"خرید, شیر و نان"');
    expect(csv.split('\r\n').filter(Boolean)).toHaveLength(2);
  });

  it('names habits in the log export instead of leaking ids', () => {
    const csv = habitLogsToCsv(
      [
        {
          id: 'h1',
          name: 'مطالعه',
          description: '',
          icon: 'book',
          accentColor: '#6D4AFF',
          scheduleType: 'daily',
          scheduledDays: [],
          weeklyTarget: 7,
          trackingMode: 'simple',
          targetValue: 1,
          unit: '',
          reminderTime: null,
          startDate: '2026-01-01',
          archived: false,
          createdAt: '2026-01-01T00:00:00.000Z',
          updatedAt: '2026-01-01T00:00:00.000Z',
        } as never,
      ],
      [
        { id: 'l1', habitId: 'h1', dateKey: '2026-11-20', status: 'completed', value: 1, note: '', createdAt: '2026-11-20T10:00:00.000Z' } as never,
      ]
    );
    expect(csv).toContain('date,habit,status,value,note');
    expect(csv).toContain('2026-11-20,مطالعه,completed,1');
  });
});
