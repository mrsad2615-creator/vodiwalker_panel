import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { AppProvider } from '@/context/AppContext';
import { CommandCenter } from '@/features/assistant/CommandCenter';
import { clearAllUserData, createTask, db, ensureSettings, exportBackup, importBackup, updateSettings } from '@/lib/db';
import { validateBackup } from '@/lib/backupValidation';
import { deleteAssistantHistorySnapshot } from '@/lib/assistantHistory';
import type { AssistantHistoryEntry } from '@/types/domain';

const entry: AssistantHistoryEntry = { id: 'saved', createdAt: '2026-01-01T12:00:00.000Z',
  prompt: 'متن خصوصی', intent: 'help', title: 'پاسخ خصوصی', lines: ['دادهٔ شخصی'], sample: false };

beforeEach(async () => {
  window.localStorage.clear();
  await db.transaction('rw', db.tables, () => Promise.all(db.tables.map((table) => table.clear())));
  await ensureSettings();
});
afterEach(() => vi.restoreAllMocks());

function mount() { return render(<AppProvider><CommandCenter /></AppProvider>); }

describe('assistant history durability and recovery', () => {
  it('persists an answer across unmount and requires confirmation to delete it', async () => {
    const first = mount();
    fireEvent.change(screen.getByLabelText('فرمان'), { target: { value: 'سلام' } });
    fireEvent.click(screen.getByRole('button', { name: 'بپرس' }));
    await waitFor(async () => expect(await db.assistantHistory.count()).toBe(1));
    first.unmount();
    mount();
    await screen.findByText('تو: سلام');
    fireEvent.click(await screen.findByRole('button', { name: 'پاک‌کردن تاریخچهٔ گفت‌وگو' }));
    const confirm = screen.getByRole('region', { name: 'تأیید پاک‌کردن گفت‌وگو' });
    fireEvent.click(within(confirm).getByRole('button', { name: 'انصراف' }));
    expect(await db.assistantHistory.count()).toBe(1);
    fireEvent.click(screen.getByRole('button', { name: 'پاک‌کردن تاریخچهٔ گفت‌وگو' }));
    fireEvent.click(screen.getByRole('button', { name: 'تأیید پاک‌کردن گفت‌وگو' }));
    await waitFor(async () => expect(await db.assistantHistory.count()).toBe(0));
  });

  it('keeps a failed answer on screen and supports retry instead of claiming it was saved', async () => {
    const fail = vi.spyOn(db.assistantHistory, 'add').mockRejectedValueOnce(new Error('storage full'));
    mount();
    fireEvent.change(screen.getByLabelText('فرمان'), { target: { value: 'سلام' } });
    fireEvent.click(screen.getByRole('button', { name: 'بپرس' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('ذخیره نشده');
    expect(screen.getByText('تو: سلام')).toBeInTheDocument();
    expect(await db.assistantHistory.count()).toBe(0);
    fail.mockRestore();
    fireEvent.click(screen.getByRole('button', { name: 'تلاش دوباره برای ذخیره' }));
    await waitFor(async () => expect(await db.assistantHistory.count()).toBe(1));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  });

  it('hides sample-derived conversation reversibly', async () => {
    await db.assistantHistory.bulkAdd([entry, { ...entry, id: 'sample', title: 'پاسخ نمونه', sample: true }]);
    await updateSettings({ sampleVisible: false });
    mount();
    await screen.findByText('پاسخ خصوصی');
    expect(screen.queryByText('پاسخ نمونه')).not.toBeInTheDocument();
    await updateSettings({ sampleVisible: true });
    expect(await screen.findByText('پاسخ نمونه')).toBeInTheDocument();
    expect(await db.assistantHistory.count()).toBe(2);
  });

  it('deletes only the confirmed snapshot and rejects stale edits atomically', async () => {
    const second = { ...entry, id: 'second' };
    await db.assistantHistory.bulkAdd([entry, second]);
    await db.assistantHistory.update('second', { title: 'ویرایش هم‌زمان' });
    await expect(deleteAssistantHistorySnapshot([entry, second])).rejects.toThrow('history-changed');
    expect(await db.assistantHistory.get('saved')).toEqual(entry);
    await db.assistantHistory.put({ ...entry, id: 'new-after-confirm' });
    await deleteAssistantHistorySnapshot([entry]);
    expect(await db.assistantHistory.get('new-after-confirm')).toBeDefined();
    expect(await db.assistantHistory.get('second')).toBeDefined();
  });

  it('rolls back imported conversations with all other data on a later error', async () => {
    await db.assistantHistory.put(entry);
    const task = await createTask({ title: 'برای بازیابی', description: '', dueDate: null, dueTime: null, priority: 'medium', quadrant: 'q2', category: 'work', tags: [], status: 'todo', subtasks: [], recurrence: 'none', reminderEnabled: false, reminderOffsetMin: 0 });
    const backup = await exportBackup();
    await db.assistantHistory.clear();
    await db.tasks.clear();
    const fail = vi.spyOn(db.assistantHistory, 'bulkAdd').mockRejectedValueOnce(new Error('disk full'));
    await expect(importBackup(backup)).rejects.toThrow('disk full');
    expect(await db.assistantHistory.count()).toBe(0);
    expect(await db.tasks.get(task.id)).toBeUndefined();
    fail.mockRestore();
  });

  it('round-trips history in v4 backups, preserves collisions, validates rows, and wipes it', async () => {
    await db.assistantHistory.put(entry);
    const backup = await exportBackup();
    expect(backup.version).toBe(4);
    expect(backup.assistantHistory).toEqual([entry]);
    const corrupt = { ...backup, assistantHistory: [{ ...entry, action: { label: 'x', tab: 'delete' } }] };
    expect(() => validateBackup(corrupt)).toThrow('invalid-backup');
    expect(() => validateBackup({ ...backup, assistantHistory: undefined })).toThrow('invalid-backup');
    await db.assistantHistory.clear();
    expect(await importBackup(backup)).toEqual({ added: 1, skipped: 0 });
    expect(await db.assistantHistory.get(entry.id)).toEqual(entry);
    await db.assistantHistory.update(entry.id, { title: 'تغییر جدید' });
    expect(await importBackup(backup)).toEqual({ added: 0, skipped: 1 });
    expect((await db.assistantHistory.get(entry.id))?.title).toBe('تغییر جدید');
    await clearAllUserData();
    expect(await db.assistantHistory.count()).toBe(0);
    // Older backups had no conversation collection.
    expect(() => validateBackup({ ...backup, version: 3, assistantHistory: undefined })).not.toThrow();
  });
});
