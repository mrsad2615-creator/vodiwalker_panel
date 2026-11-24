import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
vi.mock('@capacitor/filesystem', () => ({ Filesystem: { writeFile: vi.fn() }, Directory: { Cache: 'CACHE' } }));
vi.mock('@capacitor/share', () => ({ Share: { share: vi.fn() } }));

import { Capacitor } from '@capacitor/core';
import { Share } from '@capacitor/share';
import { Filesystem } from '@capacitor/filesystem';
import App from '@/App';
import { clearAllUserData, createHabit, createTask, db, exportBackup, importBackup } from '@/lib/db';
import { populateStarterWorkspace } from '@/lib/seed';
import { saveTextFile } from '@/lib/backup';
import { validateBackup } from '@/lib/backupValidation';
import { getTodayKey } from '@/lib/date';

async function makeTask(title: string) {
  return createTask({
    title, description: '', dueDate: null, dueTime: null, priority: 'medium', quadrant: 'q2',
    category: 'work', tags: [], status: 'todo', subtasks: [], recurrence: 'none',
    reminderEnabled: false, reminderOffsetMin: 0,
  });
}

beforeEach(async () => {
  window.localStorage.clear();
  await clearAllUserData();
  await db.activeRun.clear();
});
afterEach(() => vi.restoreAllMocks());

describe('protected local data', () => {
  it('merges new rows without replacing current rows or settings and is idempotent', async () => {
    const task = await makeTask('نسخهٔ فعلی');
    const backup = await exportBackup();
    backup.tasks[0] = { ...task, title: 'نسخهٔ قدیمی' };
    backup.tasks.push({ ...task, id: 'new-backup-id', title: 'بازیابی‌شده' });
    backup.settings.displayName = 'نام در پشتیبان';
    const beforeSettings = await db.settings.get('default');
    expect(await importBackup(backup)).toEqual({ added: 1, skipped: 1 });
    expect((await db.tasks.get(task.id))?.title).toBe('نسخهٔ فعلی');
    expect((await db.tasks.get('new-backup-id'))?.title).toBe('بازیابی‌شده');
    expect(await db.settings.get('default')).toEqual(beforeSettings);
    expect(await importBackup(backup)).toEqual({ added: 0, skipped: 2 });
  });

  it('rejects incompatible, duplicate and malformed rows without mutating data', async () => {
    const current = await makeTask('باقی بمان');
    const backup = await exportBackup();
    for (const broken of [
      { ...backup, version: 99 },
      { ...backup, habits: 'not an array' },
      { ...backup, tasks: [backup.tasks[0], backup.tasks[0]] },
      { ...backup, tasks: [{ ...backup.tasks[0], subtasks: [null] }] },
      { ...backup, tasks: [{ ...backup.tasks[0], status: 'bogus' }] },
    ]) {
      await expect(importBackup(broken as never)).rejects.toThrow('invalid-backup');
      expect(await db.tasks.get(current.id)).toEqual(current);
    }
  });

  it('keeps legacy optional collections intact and accepts empty journal text', async () => {
    const backup = await exportBackup();
    const legacy = { ...backup, version: 1, journal: undefined, projects: undefined, projectStages: undefined };
    expect(() => validateBackup(legacy)).not.toThrow();
    await db.journal.put({ id: 'journal-old', dateKey: '2025-01-01', mood: 3, note: '', highlight: '', createdAt: 'x', updatedAt: 'x' });
    await importBackup(legacy);
    expect(await db.journal.count()).toBe(1);
    expect(() => validateBackup({ ...backup, journal: [{ id: 'j', dateKey: '2025-01-01', note: '', highlight: '', mood: 3, createdAt: 'x', updatedAt: 'x' }] })).not.toThrow();
  });

  it('rolls back all new rows if a later write fails', async () => {
    await makeTask('داخل فایل');
    await createHabit({ name: 'داخل فایل', description: '', icon: 'book', accentColor: '#6D4AFF', scheduleType: 'daily', scheduledDays: [0], weeklyTarget: 1, trackingMode: 'boolean', targetValue: 1, unit: '', reminderTime: null, startDate: '2025-01-01' });
    const backup = await exportBackup();
    await clearAllUserData();
    const current = await makeTask('موجود');
    vi.spyOn(db.habits, 'bulkAdd').mockRejectedValueOnce(new Error('disk-failure'));
    await expect(importBackup(backup)).rejects.toThrow('disk-failure');
    expect(await db.tasks.toArray()).toEqual([current]);
    expect(await db.habits.count()).toBe(0);
  });

  it('adds marked sample data alongside real data without replacing it', async () => {
    const task = await makeTask('واقعی');
    await populateStarterWorkspace();
    expect(await db.tasks.get(task.id)).toEqual(task);
    expect((await db.tasks.toArray()).some((row) => row.sample && row.title.startsWith('نمونه ·'))).toBe(true);
    expect((await db.settings.get('default'))?.hasSampleData).toBe(true);
    const count = await db.tasks.count();
    await expect(populateStarterWorkspace()).rejects.toThrow('sample-already-loaded');
    expect(await db.tasks.count()).toBe(count);
  });

  it('keeps an existing project when mixing in sample data', async () => {
    await db.projects.put({ id: 'p', title: 'پروژه', description: '', category: 'work', status: 'active', goalId: null, startDate: null, dueDate: null, createdAt: 'x', updatedAt: 'x' });
    await populateStarterWorkspace();
    expect(await db.projects.count()).toBe(1);
    expect(await db.tasks.count()).toBeGreaterThan(0);
  });

  it('never reports closing the Android share sheet as a completed export', async () => {
    vi.spyOn(Capacitor, 'isNativePlatform').mockReturnValue(true);
    vi.spyOn(Filesystem, 'writeFile').mockResolvedValue({ uri: 'file:///cache/test.json' });
    vi.spyOn(Share, 'share').mockRejectedValue(new Error('cancelled'));
    const result = await saveTextFile('test.json', '{}', 'application/json', 'backup');
    expect(result.ok).toBe(false);
    expect(result.error).toBe('share-not-completed');
  });
});

describe('restore confirmation', () => {
  it('does not change data until explicit confirmation and allows cancellation', async () => {
    const current = await makeTask('کار فعلی');
    const backup = await exportBackup();
    backup.tasks.push({ ...current, id: 'incoming', title: 'از فایل' });
    render(<App />);
    fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
    await screen.findByTestId('home-ready');
    fireEvent.click(screen.getByRole('button', { name: 'بیشتر' }));
    fireEvent.click(await screen.findByRole('button', { name: /^بخش تنظیمات$/ }));
    await screen.findByText('ذخیره‌سازی آفلاین و پشتیبان‌گیری');
    const input = document.querySelector('input[type="file"][accept="application/json"]') as HTMLInputElement;
    const file = new File([JSON.stringify(backup)], 'backup.json', { type: 'application/json' });
    fireEvent.change(input, { target: { files: [file] } });
    const preview = await screen.findByRole('region', { name: 'پیش‌نمایش بازیابی' });
    expect(await db.tasks.get('incoming')).toBeUndefined();
    fireEvent.click(within(preview).getByRole('button', { name: 'انصراف' }));
    expect(await db.tasks.get('incoming')).toBeUndefined();
    fireEvent.change(input, { target: { files: [file] } });
    const previewAgain = await screen.findByRole('region', { name: 'پیش‌نمایش بازیابی' });
    fireEvent.click(within(previewAgain).getByRole('button', { name: 'تأیید و افزودن' }));
    await waitFor(async () => expect((await db.tasks.get('incoming'))?.title).toBe('از فایل'));
    expect((await db.tasks.get(current.id))?.title).toBe('کار فعلی');
  });
});


describe('offline local workflow', () => {
  it('does not populate fictional activity on a new install', async () => {
    await db.settings.clear();
    render(<App />);
    fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
    await screen.findByTestId('home-ready');
    expect(await db.tasks.count()).toBe(0);
    expect(await db.habits.count()).toBe(0);
    expect(await db.focusSessions.count()).toBe(0);
    expect((await db.settings.get('default'))?.hasSampleData).toBe(false);
  });

  it('starts, navigates and answers from local data with fetch unavailable', async () => {
    const fetchSpy = vi.fn().mockRejectedValue(new Error('network blocked'));
    vi.stubGlobal('fetch', fetchSpy);
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    try {
      const task = await makeTask('آزمون آفلاین');
      await db.tasks.update(task.id, { dueDate: getTodayKey() });
      await db.settings.update('default', { aiEnabled: true, aiKey: 'old-key', aiModel: 'old' });
      render(<App />);
      fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
      await screen.findByTestId('home-ready');
      fireEvent.click(screen.getByRole('button', { name: 'وظایف' }));
      await screen.findByText('مدیریت وظایف');
      fireEvent.click(screen.getByRole('button', { name: 'مرکز فرمان' }));
      const input = await screen.findByLabelText('فرمان');
      fireEvent.change(input, { target: { value: 'وظایف امروز من چیست؟' } });
      fireEvent.keyDown(input, { key: 'Enter' });
      await waitFor(() => expect(screen.getAllByText(/آزمون آفلاین/).length).toBeGreaterThan(0));
      expect(fetchSpy).not.toHaveBeenCalled();
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
