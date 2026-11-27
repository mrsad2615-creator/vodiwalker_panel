import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from '@/App';
import { db } from '@/lib/db';
import { getTodayKey } from '@/lib/date';
import { completeFirstRun } from '@/test/helpers/firstRun';
import { createConfirmedTodayTasks, parseTaskLines, validateTaskTitles, wantsTodayTaskList } from '@/lib/assistantTaskDraft';

beforeEach(async () => {
  window.localStorage.clear();
  await db.tasks.clear();
  await db.eventHistory.clear();
  await db.assistantHistory.clear();
  await db.settings.clear();
});

describe('assistant task creation', () => {
  it('recognises creation requests without intercepting questions or other days', () => {
    expect(wantsTodayTaskList('برای امروز لیست وظایف بساز')).toBe(true);
    expect(wantsTodayTaskList('وظایف امروز چیست؟')).toBe(false);
    expect(wantsTodayTaskList('برای فردا لیست وظایف بساز')).toBe(false);
    expect(wantsTodayTaskList('برای امروز و فردا لیست وظایف بساز')).toBe(false);
    expect(wantsTodayTaskList('امروز لیست وظایف را حذف کن')).toBe(false);
  });

  it('validates input and never writes without a valid explicit confirmation', async () => {
    expect(parseTaskLines('۱. گزارش\n- تماس\n\n')).toEqual(['گزارش', 'تماس']);
    expect(validateTaskTitles(['گزارش', 'گزارش'])).toBeTruthy();
    expect(validateTaskTitles(Array(21).fill('کار'))).toBeTruthy();
    await expect(createConfirmedTodayTasks(['گزارش'], getTodayKey(), false)).rejects.toThrow();
    await expect(createConfirmedTodayTasks(['گزارش'], '2020-01-01', true)).rejects.toThrow();
    expect(await db.tasks.count()).toBe(0);
  });

  it('rolls back the whole batch if a task ID collides', async () => {
    const spy = vi.spyOn(crypto, 'randomUUID').mockReturnValue('11111111-1111-4111-8111-111111111111');
    try {
      await expect(createConfirmedTodayTasks(['یک', 'دو'], getTodayKey(), true)).rejects.toThrow();
      expect(await db.tasks.count()).toBe(0);
      expect(await db.eventHistory.count()).toBe(0);
    } finally { spy.mockRestore(); }
  });

  it('asks for details, previews, supports cancel, and saves exactly what is confirmed', async () => {
    render(<App />);
    fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
    await completeFirstRun();
    await screen.findByTestId('home-ready');
    fireEvent.click(within(screen.getByRole('navigation', { name: 'ناوبری اصلی' })).getByRole('button', { name: 'دستیار' }));
    const dialog = await screen.findByRole('region', { name: 'مرکز فرمان' });
    fireEvent.change(within(dialog).getByLabelText('فرمان'), { target: { value: 'برای امروز لیست وظایف بساز' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'بپرس' }));
    const draft = within(dialog).getByRole('region', { name: 'ساخت فهرست وظایف امروز' });
    fireEvent.change(within(draft).getByLabelText('عنوان وظایف'), { target: { value: 'گزارش\nتماس' } });
    fireEvent.click(within(draft).getByRole('button', { name: 'پیش‌نمایش' }));
    expect(within(draft).getByRole('region', { name: 'پیش‌نمایش وظایف' })).toHaveTextContent('گزارش');
    expect(await db.tasks.count()).toBe(0);
    fireEvent.click(within(draft).getByRole('button', { name: 'انصراف' }));
    expect(await db.tasks.count()).toBe(0);
    fireEvent.click(within(dialog).getByRole('button', { name: 'برای امروز لیست وظایف بساز' }));
    const newDraft = within(dialog).getByRole('region', { name: 'ساخت فهرست وظایف امروز' });
    fireEvent.change(within(newDraft).getByLabelText('عنوان وظایف'), { target: { value: 'گزارش\nتماس' } });
    fireEvent.click(within(newDraft).getByRole('button', { name: 'پیش‌نمایش' }));
    fireEvent.click(within(newDraft).getByRole('button', { name: 'تأیید و ساخت وظایف' }));
    await waitFor(async () => expect(await db.tasks.count()).toBe(2));
    const tasks = await db.tasks.toArray();
    expect(tasks.map((task) => task.title).sort()).toEqual(['تماس', 'گزارش'].sort());
    expect(tasks.every((task) => task.dueDate === getTodayKey() && task.dueTime === null && !task.reminderEnabled)).toBe(true);
    await waitFor(async () => expect(await db.eventHistory.where('kind').equals('task.created').count()).toBe(2));
  });
});
