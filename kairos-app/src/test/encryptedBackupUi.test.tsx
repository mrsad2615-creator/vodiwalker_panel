import { webcrypto } from 'node:crypto';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from '@/App';
import { completeFirstRun } from '@/test/helpers/firstRun';
import { clearAllUserData, createTask, db, exportBackup } from '@/lib/db';
import { encryptBackup } from '@/lib/backupCrypto';

beforeEach(async () => {
  vi.stubGlobal('crypto', webcrypto);
  window.localStorage.clear();
  await clearAllUserData();
  await db.activeRun.clear();
});
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

describe('encrypted restore UI', () => {
  it('asks for a password, retries on failure, and never writes before confirmation', async () => {
    const task = await createTask({ title: 'خصوصی', description: '', dueDate: null, dueTime: null,
      priority: 'medium', quadrant: 'q2', category: 'work', tags: [], status: 'todo', subtasks: [],
      recurrence: 'none', reminderEnabled: false, reminderOffsetMin: 0 });
    const backup = await exportBackup();
    backup.tasks.push({ ...task, id: 'encrypted-incoming', title: 'دادهٔ رمزگذاری‌شده' });
    const envelope = await encryptBackup(backup, 'یک گذرواژهٔ طولانی');
    render(<App />);
    fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
    await completeFirstRun();
    await screen.findByTestId('home-ready');
    fireEvent.click(screen.getByRole('button', { name: 'بیشتر' }));
    fireEvent.click(await screen.findByRole('button', { name: /^بخش تنظیمات$/ }));
    await screen.findByText('ذخیره‌سازی آفلاین و پشتیبان‌گیری');
    const input = document.querySelector('input[type="file"][accept="application/json"]') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [new File([JSON.stringify(envelope)], 'backup.json')] } });
    const prompt = await screen.findByRole('region', { name: 'گذرواژهٔ پشتیبان' });
    fireEvent.change(within(prompt).getByLabelText('گذرواژهٔ فایل'), { target: { value: 'نادرست' } });
    fireEvent.click(within(prompt).getByRole('button', { name: 'بررسی فایل' }));
    await screen.findByText('فایل قابل بررسی نیست');
    expect(await db.tasks.get('encrypted-incoming')).toBeUndefined();
    fireEvent.change(within(prompt).getByLabelText('گذرواژهٔ فایل'), { target: { value: 'یک گذرواژهٔ طولانی' } });
    fireEvent.click(within(prompt).getByRole('button', { name: 'بررسی فایل' }));
    const preview = await screen.findByRole('region', { name: 'پیش‌نمایش بازیابی' });
    expect(await db.tasks.get('encrypted-incoming')).toBeUndefined();
    fireEvent.click(within(preview).getByRole('button', { name: 'تأیید و افزودن' }));
    await waitFor(async () => expect((await db.tasks.get('encrypted-incoming'))?.title).toBe('دادهٔ رمزگذاری‌شده'));
  });
});
